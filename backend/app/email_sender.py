"""Sends signup verification codes by configured SMTP."""

import smtplib
import ssl
from email.message import EmailMessage

from app.config import (
    SMTP_FROM_EMAIL,
    SMTP_HOST,
    SMTP_PASSWORD,
    SMTP_PORT,
    SMTP_USE_SSL,
    SMTP_USERNAME,
)


def send_signup_otp(recipient: str, code: str) -> None:
    """Sends a signup verification code using configured email delivery."""
    if not SMTP_HOST:
        raise RuntimeError("SMTP is not configured")

    if not SMTP_FROM_EMAIL:
        raise RuntimeError("SMTP_FROM_EMAIL must be configured when SMTP_HOST is set")

    message = EmailMessage()
    message["Subject"] = "Verify your HereBuy account"
    message["From"] = SMTP_FROM_EMAIL
    message["To"] = recipient
    message.set_content(
        f"Your HereBuy verification code is {code}. It expires in 5 minutes. "
        "If you did not request this code, you can ignore this email."
    )

    context = ssl.create_default_context()
    if SMTP_USE_SSL:
        with smtplib.SMTP_SSL(SMTP_HOST, SMTP_PORT, context=context, timeout=15) as server:
            if SMTP_USERNAME:
                server.login(SMTP_USERNAME, SMTP_PASSWORD)
            server.send_message(message)
    else:
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=15) as server:
            server.ehlo()
            server.starttls(context=context)
            server.ehlo()
            if SMTP_USERNAME:
                server.login(SMTP_USERNAME, SMTP_PASSWORD)
            server.send_message(message)
