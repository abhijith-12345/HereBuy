"""Implements password, access-token, and signup-code cryptographic helpers."""

import hashlib
import hmac
import secrets
from datetime import datetime, timedelta, timezone

import jwt
from pwdlib import PasswordHash

from app.config import ACCESS_TOKEN_EXPIRE_MINUTES, JWT_SECRET_KEY


password_hash = PasswordHash.recommended()
JWT_ALGORITHM = "HS256"


def hash_password(password: str) -> str:
    """Creates a salted password hash for storage."""
    return password_hash.hash(password)


def verify_password(password: str, stored_hash: str) -> bool:
    """Checks a password against its stored salted hash."""
    return password_hash.verify(password, stored_hash)


def create_access_token(user_id: str) -> tuple[str, int]:
    """Creates a signed bearer token and returns its expiry duration."""
    expires_in = ACCESS_TOKEN_EXPIRE_MINUTES * 60
    expires_at = datetime.now(timezone.utc) + timedelta(seconds=expires_in)
    token = jwt.encode(
        {"sub": user_id, "exp": expires_at},
        JWT_SECRET_KEY,
        algorithm=JWT_ALGORITHM,
    )
    return token, expires_in


def decode_access_token(token: str) -> str:
    """Validates a bearer token and extracts its user identifier."""
    payload = jwt.decode(token, JWT_SECRET_KEY, algorithms=[JWT_ALGORITHM])
    subject = payload.get("sub")
    if not isinstance(subject, str) or not subject:
        raise jwt.InvalidTokenError("Token subject is missing")
    return subject


def generate_signup_otp() -> str:
    """Generates a cryptographically random six-digit signup code."""
    return f"{secrets.randbelow(1_000_000):06d}"


def hash_signup_otp(challenge_id: str, code: str) -> str:
    """Hashes a signup code with its challenge identifier."""
    message = f"signup:{challenge_id}:{code}".encode("utf-8")
    return hmac.new(JWT_SECRET_KEY.encode("utf-8"), message, hashlib.sha256).hexdigest()


def verify_signup_otp(challenge_id: str, code: str, stored_hash: str) -> bool:
    """Compares a provided signup code with its stored hash."""
    return hmac.compare_digest(hash_signup_otp(challenge_id, code), stored_hash)


