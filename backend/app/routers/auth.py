"""Implements signup verification, login, and current-user API routes."""

import uuid
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy import delete, or_, select
from sqlalchemy.orm import Session

from app.config import (
    SIGNUP_OTP_MAX_ATTEMPTS,
    SIGNUP_OTP_MAX_RESENDS,
    SIGNUP_OTP_RESEND_COOLDOWN_SECONDS,
    SIGNUP_OTP_TTL_SECONDS,
)
from app.database import get_db
from app.dependencies import get_current_user
from app.email_sender import send_signup_otp
from app.models import SignupVerification, User
from app.schemas import (
    AuthResponse,
    SignupOtpResponse,
    SignupResend,
    SignupStart,
    SignupVerify,
    UserLogin,
    UserPublic,
)
from app.security import (
    create_access_token,
    generate_signup_otp,
    hash_password,
    hash_signup_otp,
    verify_password,
    verify_signup_otp,
)


router = APIRouter(prefix="/auth", tags=["auth"])


def make_auth_response(user: User) -> AuthResponse:
    """Builds the shared authentication response for a user."""
    token, expires_in = create_access_token(user.id)
    return AuthResponse(
        access_token=token,
        expires_in=expires_in,
        user=UserPublic.model_validate(user),
    )


def _now() -> datetime:
    """Returns the current UTC time used for signup challenge expiry checks."""
    return datetime.now(timezone.utc)


def _aware(value: datetime) -> datetime:
    # SQLite returns naive datetimes even for timezone-aware columns.
    """Makes a database datetime timezone-aware for consistent comparisons."""
    return value.replace(tzinfo=timezone.utc) if value.tzinfo is None else value


def _send_challenge(challenge: SignupVerification, db: Session, code: str) -> None:
    """Persists and sends the signup verification code."""
    try:
        send_signup_otp(challenge.email, code)
    except Exception as exc:
        db.delete(challenge)
        db.commit()
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Could not send the verification email. Check the email address or try again later.",
        ) from exc


@router.post("/signup", response_model=SignupOtpResponse, status_code=status.HTTP_202_ACCEPTED)
def start_signup(payload: SignupStart, db: Session = Depends(get_db)) -> SignupOtpResponse:
    """Creates a pending signup challenge and sends its verification code."""
    username = payload.username.strip()
    email = str(payload.email).strip().lower()
    existing = db.scalar(
        select(User.id).where((User.username == username) | (User.email == email))
    )
    if existing:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Username or email is already registered")

    now = _now()
    active_challenge = db.scalar(
        select(SignupVerification)
        .where(
            or_(SignupVerification.email == email, SignupVerification.username == username),
            SignupVerification.consumed_at.is_(None),
            SignupVerification.expires_at > now,
        )
        .limit(1)
    )
    if active_challenge is not None:
        if active_challenge.email == email and active_challenge.username == username:
            return SignupOtpResponse(
                challenge_id=active_challenge.id,
                message="A verification code was already sent. Check your email or request a resend.",
            )
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Username or email has a pending signup")

    db.execute(delete(SignupVerification).where(
        or_(SignupVerification.email == email, SignupVerification.username == username)
    ))

    challenge_id = str(uuid.uuid4())
    code = generate_signup_otp()
    challenge = SignupVerification(
        id=challenge_id,
        username=username,
        email=email,
        password_hash=hash_password(payload.password),
        code_hash=hash_signup_otp(challenge_id, code),
        created_at=now,
        expires_at=now + timedelta(seconds=SIGNUP_OTP_TTL_SECONDS),
    )
    db.add(challenge)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        active_challenge = db.scalar(
            select(SignupVerification).where(
                SignupVerification.email == email,
                SignupVerification.username == username,
                SignupVerification.expires_at > now,
            )
        )
        if active_challenge is not None:
            return SignupOtpResponse(
                challenge_id=active_challenge.id,
                message="A verification code was already sent. Check your email or request a resend.",
            )
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Username or email has a pending signup") from None
    _send_challenge(challenge, db, code)
    return SignupOtpResponse(challenge_id=challenge_id, message="A verification code was sent to your email.")


@router.post("/signup/resend", response_model=SignupOtpResponse)
def resend_signup_code(payload: SignupResend, db: Session = Depends(get_db)) -> SignupOtpResponse:
    """Checks resend limits and sends another signup verification code."""
    challenge = db.get(SignupVerification, payload.challenge_id)
    if challenge is None or challenge.consumed_at is not None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Signup verification is no longer available")
    now = _now()
    elapsed = (now - _aware(challenge.created_at)).total_seconds()
    if elapsed < SIGNUP_OTP_RESEND_COOLDOWN_SECONDS:
        wait = max(1, int(SIGNUP_OTP_RESEND_COOLDOWN_SECONDS - elapsed))
        raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail=f"Please wait {wait} seconds before requesting another code")
    if challenge.resend_count >= SIGNUP_OTP_MAX_RESENDS:
        raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail="Resend limit reached. Start signup again after this code expires.")

    code = generate_signup_otp()
    challenge.code_hash = hash_signup_otp(challenge.id, code)
    challenge.created_at = now
    challenge.expires_at = now + timedelta(seconds=SIGNUP_OTP_TTL_SECONDS)
    challenge.attempts = 0
    challenge.resend_count += 1
    db.commit()
    _send_challenge(challenge, db, code)
    return SignupOtpResponse(challenge_id=challenge.id, message="A new verification code was sent to your email.")


@router.post("/signup/verify", response_model=AuthResponse)
def verify_signup(payload: SignupVerify, db: Session = Depends(get_db)) -> AuthResponse:
    """Checks the signup code and creates the user account and session."""
    challenge = db.get(SignupVerification, payload.challenge_id)
    if challenge is None or challenge.consumed_at is not None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Verification code is invalid or expired")

    now = _now()
    if _aware(challenge.expires_at) <= now:
        db.delete(challenge)
        db.commit()
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Verification code is invalid or expired")
    if challenge.attempts >= SIGNUP_OTP_MAX_ATTEMPTS:
        raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail="Too many incorrect codes. Request a new code.")
    if not verify_signup_otp(challenge.id, payload.code, challenge.code_hash):
        challenge.attempts += 1
        db.commit()
        remaining = max(0, SIGNUP_OTP_MAX_ATTEMPTS - challenge.attempts)
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Incorrect code. {remaining} attempts remaining.")

    existing = db.scalar(
        select(User.id).where((User.username == challenge.username) | (User.email == challenge.email))
    )
    if existing:
        db.delete(challenge)
        db.commit()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Username or email is already registered")

    user = User(
        username=challenge.username,
        phone_number=None,
        email=challenge.email,
        display_name=challenge.username,
        password_hash=challenge.password_hash,
        created_at=now,
    )
    db.add(user)
    challenge.consumed_at = now
    db.delete(challenge)
    db.commit()
    db.refresh(user)
    return make_auth_response(user)


@router.post("/login", response_model=AuthResponse)
def log_in(payload: UserLogin, db: Session = Depends(get_db)) -> AuthResponse:
    """Authenticates by username, phone, or email and returns a session."""
    identifier = payload.identifier.strip()
    if "@" in identifier:
        account_filter = User.email == identifier.lower()
    else:
        account_filter = User.phone_number == identifier
    user = db.scalar(select(User).where(account_filter))
    if user is None or not verify_password(payload.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Phone number/email or password is incorrect",
        )
    return make_auth_response(user)


@router.get("/me", response_model=UserPublic)
def read_me(user: User = Depends(get_current_user)) -> UserPublic:
    """Returns the profile for the authenticated user."""
    return UserPublic.model_validate(user)
