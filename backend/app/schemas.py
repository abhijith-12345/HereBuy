"""Defines request and response validation models for the API."""

from datetime import datetime
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, EmailStr, Field, StringConstraints


PhoneNumber = Annotated[str, StringConstraints(pattern=r"^\+[1-9]\d{7,14}$")]
Username = Annotated[str, StringConstraints(min_length=3, max_length=32, pattern=r"^[A-Za-z0-9_.-]+$")]


class SignupStart(BaseModel):
    """Validates account creation details."""
    username: Username
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)


class SignupVerify(BaseModel):
    """Validates an email verification challenge and code."""
    challenge_id: str
    code: str = Field(pattern=r"^\d{6}$")


class SignupResend(BaseModel):
    """Validates a request to resend the signup code."""
    challenge_id: str


class SignupOtpResponse(BaseModel):
    """Shapes the response returned after a signup code is sent."""
    challenge_id: str
    message: str


class UserLogin(BaseModel):
    """Validates credentials for login."""
    identifier: str = Field(min_length=3, max_length=320)
    password: str = Field(min_length=8, max_length=128)


class UserPublic(BaseModel):
    """Shapes user fields safe to return to clients."""
    model_config = ConfigDict(from_attributes=True)

    id: str
    username: str | None
    phone_number: PhoneNumber | None
    email: EmailStr | None
    display_name: str
    created_at: datetime


class AuthResponse(BaseModel):
    """Shapes a successful login or signup response."""
    access_token: str
    token_type: Literal["bearer"] = "bearer"
    expires_in: int
    user: UserPublic


class ListingCreate(BaseModel):
    """Validates fields needed to create a listing."""
    title: str = Field(min_length=1, max_length=100)
    description: str = Field(default="", max_length=2000)
    category: Literal["Produce", "Food", "Home", "Clothing", "Other"]
    price: float = Field(ge=0, le=100000000)
    photo_urls: list[str] = Field(default_factory=list, max_length=10)
    pickup_area: str = Field(default="", max_length=120)
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)


class ListingUpdate(BaseModel):
    """Validates optional fields that can be changed on a listing."""
    title: str | None = Field(default=None, min_length=1, max_length=100)
    description: str | None = Field(default=None, max_length=2000)
    category: Literal["Produce", "Food", "Home", "Clothing", "Other"] | None = None
    price: float | None = Field(default=None, ge=0, le=100000000)
    photo_urls: list[str] | None = Field(default=None, max_length=10)
    pickup_area: str | None = Field(default=None, max_length=120)
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)
    status: Literal["available", "reserved", "sold"] | None = None


class ListingPublic(BaseModel):
    """Shapes listing fields returned to an authenticated client."""
    model_config = ConfigDict(from_attributes=True)

    id: str
    seller_id: str
    title: str
    description: str
    category: str
    price: float
    photo_urls: list[str]
    pickup_area: str
    latitude: float
    longitude: float
    status: str
    created_at: datetime
    updated_at: datetime


class NearbyListing(ListingPublic):
    """Adds distance in meters to a public listing response."""
    distance_meters: float


class ListingAlertsResponse(BaseModel):
    """New live listings inside the configured alert radius and server query watermark."""
    listings: list[NearbyListing]
    checked_at: datetime


class PushDeviceRegistration(BaseModel):
    """Validates an FCM token and optional current nearby-alert settings."""
    token: str = Field(min_length=20, max_length=512)
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)
    radius_meters: int = Field(default=5_000, ge=1_000, le=100_000)
