"""Sends best-effort FCM alerts without allowing push outages to fail listing creation."""

import logging
import os
from pathlib import Path

from app.models import Listing, PushDevice, User

logger = logging.getLogger("herebuy.push")
_firebase_app = None


def distance_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Computes great-circle distance in meters for device-to-listing matching."""
    import math

    lat1_rad, lat2_rad = math.radians(lat1), math.radians(lat2)
    delta_lat, delta_lon = math.radians(lat2 - lat1), math.radians(lon2 - lon1)
    haversine = math.sin(delta_lat / 2) ** 2 + math.cos(lat1_rad) * math.cos(lat2_rad) * math.sin(delta_lon / 2) ** 2
    return 12_742_000 * math.asin(math.sqrt(haversine))


def _messaging_module():
    """Loads Firebase Admin only when server credentials have been configured."""
    global _firebase_app
    credential_path = os.getenv("FIREBASE_CREDENTIALS_PATH", "").strip()
    if not credential_path or not Path(credential_path).is_file():
        return None
    try:
        import firebase_admin
        from firebase_admin import credentials, messaging

        if _firebase_app is None:
            _firebase_app = firebase_admin.initialize_app(
                credentials.Certificate(credential_path),
                name="herebuy-messaging",
            )
        return messaging
    except Exception:
        logger.exception("Firebase push initialization failed")
        return None


def notify_nearby_users(listing: Listing, seller: User, devices: list[PushDevice]) -> None:
    """Sends one data-only high-priority push for each matching nearby device."""
    messaging = _messaging_module()
    if messaging is None:
        return
    for device in devices:
        if device.user_id == seller.id or device.latitude is None or device.longitude is None:
            continue
        distance = distance_meters(device.latitude, device.longitude, listing.latitude, listing.longitude)
        if distance > device.radius_meters:
            continue
        distance_km = distance / 1_000
        body = f"{listing.title} was posted {distance_km:.1f} km away."
        message = messaging.Message(
            token=device.token,
            data={"title": "New nearby product", "body": body, "listing_id": listing.id},
            android=messaging.AndroidConfig(priority="high"),
        )
        try:
            messaging.send(message, app=_firebase_app)
        except Exception:
            logger.exception("Could not send nearby listing push")
