"""Loads environment settings and validates required backend configuration."""

import os
from pathlib import Path

from dotenv import load_dotenv


BACKEND_DIR = Path(__file__).resolve().parents[1]
load_dotenv(BACKEND_DIR / ".env")

DATABASE_URL = os.getenv(
    "DATABASE_URL",
    f"sqlite:///{(BACKEND_DIR / 'data' / 'herebuy.db').as_posix()}",
)
JWT_SECRET_KEY = os.getenv("JWT_SECRET_KEY", "")
ACCESS_TOKEN_EXPIRE_MINUTES = int(os.getenv("ACCESS_TOKEN_EXPIRE_MINUTES", "10080"))
APP_ENV = os.getenv("APP_ENV", "development").lower()
SIGNUP_OTP_TTL_SECONDS = 300
SIGNUP_OTP_RESEND_COOLDOWN_SECONDS = 60
SIGNUP_OTP_MAX_ATTEMPTS = 5
SIGNUP_OTP_MAX_RESENDS = 3
SMTP_HOST = os.getenv("SMTP_HOST", "").strip()
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_USERNAME = os.getenv("SMTP_USERNAME", "").strip()
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD", "")
SMTP_FROM_EMAIL = os.getenv("SMTP_FROM_EMAIL", "").strip() or SMTP_USERNAME
SMTP_USE_SSL = os.getenv("SMTP_USE_SSL", "false").lower() == "true"
API_PREFIX = "/api/v1"


def validate_settings() -> None:
    """Validates required environment configuration before startup."""
    if len(JWT_SECRET_KEY) < 32 or JWT_SECRET_KEY.startswith("replace-this"):
        raise RuntimeError(
            "Set JWT_SECRET_KEY in backend/.env to a random value of at least 32 characters."
        )
