"""Registers device push tokens and their nearby-alert preferences."""

from fastapi import APIRouter, Depends, Response, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.dependencies import get_current_user
from app.models import PushDevice, User
from app.schemas import PushDeviceRegistration

router = APIRouter(prefix="/notifications", tags=["notifications"])


@router.put("/device", status_code=status.HTTP_204_NO_CONTENT)
def register_device(
    payload: PushDeviceRegistration,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> Response:
    """Adds or updates the current user's FCM token and alert location."""
    device = db.get(PushDevice, payload.token)
    if device is None:
        device = PushDevice(token=payload.token, user_id=user.id)
        db.add(device)
    device.user_id = user.id
    if payload.latitude is not None and payload.longitude is not None:
        device.latitude = payload.latitude
        device.longitude = payload.longitude
    device.radius_meters = payload.radius_meters
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.delete("/device", status_code=status.HTTP_204_NO_CONTENT)
def unregister_device(
    payload: PushDeviceRegistration,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> Response:
    """Removes the current user's push token when they sign out or disable alerts."""
    device = db.get(PushDevice, payload.token)
    if device is not None and device.user_id == user.id:
        db.delete(device)
        db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
