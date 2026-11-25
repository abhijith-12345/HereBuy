"""Implements authenticated listing creation, discovery, retrieval, update, and deletion routes."""

import math
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import and_, or_, select
from sqlalchemy.orm import Session

from app.database import get_db
from app.dependencies import get_current_user
from app.models import Listing, PushDevice, User
from app.push_notifications import notify_nearby_users
from app.schemas import ListingAlertsResponse, ListingCreate, ListingPublic, ListingUpdate, NearbyListing


router = APIRouter(prefix="/listings", tags=["listings"])
EARTH_RADIUS_METERS = 6_371_000


def distance_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculates the great-circle distance between two latitude/longitude points."""
    lat1_rad, lat2_rad = math.radians(lat1), math.radians(lat2)
    delta_lat = math.radians(lat2 - lat1)
    delta_lon = math.radians(lon2 - lon1)
    haversine = (
        math.sin(delta_lat / 2) ** 2
        + math.cos(lat1_rad) * math.cos(lat2_rad) * math.sin(delta_lon / 2) ** 2
    )
    return 2 * EARTH_RADIUS_METERS * math.asin(math.sqrt(haversine))


@router.get("/nearby", response_model=list[NearbyListing])
def get_nearby_listings(
    latitude: float = Query(ge=-90, le=90),
    longitude: float = Query(ge=-180, le=180),
    radius_meters: int = Query(default=5_000, ge=1_000, le=20_000),
    limit: int = Query(default=100, ge=1, le=200),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[NearbyListing]:
    # Prototype implementation. Use PostGIS for indexed radius searches at scale.
    """Returns available listings within the requested radius."""
    candidates = db.scalars(
        select(Listing).where(Listing.status == "available").order_by(Listing.created_at.desc())
    ).all()
    nearby: list[NearbyListing] = []
    for listing in candidates:
        distance = distance_meters(latitude, longitude, listing.latitude, listing.longitude)
        if distance <= radius_meters:
            item = ListingPublic.model_validate(listing).model_dump()
            nearby.append(NearbyListing(**item, distance_meters=round(distance, 1)))
    nearby.sort(key=lambda item: item.distance_meters)
    return nearby[:limit]


@router.get("/viewport", response_model=list[NearbyListing])
def get_listings_in_viewport(
    west: float = Query(ge=-180, le=180),
    south: float = Query(ge=-90, le=90),
    east: float = Query(ge=-180, le=180),
    north: float = Query(ge=-90, le=90),
    center_latitude: float = Query(ge=-90, le=90),
    center_longitude: float = Query(ge=-180, le=180),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[NearbyListing]:
    """Returns all available listings within the current map viewport."""
    if south > north:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Viewport south latitude must not exceed north latitude.",
        )

    longitude_filter = (
        and_(Listing.longitude >= west, Listing.longitude <= east)
        if west <= east
        else or_(Listing.longitude >= west, Listing.longitude <= east)
    )
    candidates = db.scalars(
        select(Listing).where(
            Listing.status == "available",
            Listing.latitude >= south,
            Listing.latitude <= north,
            longitude_filter,
        )
    ).all()

    visible: list[NearbyListing] = []
    for listing in candidates:
        distance = distance_meters(
            center_latitude,
            center_longitude,
            listing.latitude,
            listing.longitude,
        )
        item = ListingPublic.model_validate(listing).model_dump()
        visible.append(NearbyListing(**item, distance_meters=round(distance, 1)))
    visible.sort(key=lambda item: item.distance_meters)
    return visible


@router.get("/alerts", response_model=ListingAlertsResponse)
def get_item_alerts(
    latitude: float = Query(ge=-90, le=90),
    longitude: float = Query(ge=-180, le=180),
    radius_meters: int = Query(ge=1_000, le=100_000),
    since: datetime | None = Query(default=None),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ListingAlertsResponse:
    """Returns new, still-available listings from other sellers inside an alert radius."""
    checked_at = datetime.now(timezone.utc)
    filters = [Listing.status == "available", Listing.seller_id != user.id]
    if since is not None:
        filters.append(Listing.created_at > since)

    candidates = (
        db.scalars(select(Listing).where(*filters).order_by(Listing.created_at.desc())).all()
        if since is not None
        else []
    )
    alerts: list[NearbyListing] = []
    for listing in candidates:
        distance = distance_meters(latitude, longitude, listing.latitude, listing.longitude)
        if distance <= radius_meters:
            item = ListingPublic.model_validate(listing).model_dump()
            alerts.append(NearbyListing(**item, distance_meters=round(distance, 1)))
    return ListingAlertsResponse(listings=alerts, checked_at=checked_at)


@router.post("", response_model=ListingPublic, status_code=status.HTTP_201_CREATED)
def create_listing(
    payload: ListingCreate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> Listing:
    """Validates, commits, and returns a new listing owned by the current user."""
    listing = Listing(**payload.model_dump(), seller_id=user.id)
    db.add(listing)
    db.commit()
    db.refresh(listing)
    devices = db.scalars(select(PushDevice)).all()
    notify_nearby_users(listing, user, devices)
    return listing


@router.get("/mine", response_model=list[ListingPublic])
def get_my_listings(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[Listing]:
    """Returns the authenticated seller's listings, newest first."""
    return db.scalars(
        select(Listing)
        .where(Listing.seller_id == user.id)
        .order_by(Listing.created_at.desc())
    ).all()


@router.get("/{listing_id}", response_model=ListingPublic)
def get_listing(
    listing_id: str,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> Listing:
    """Returns one listing by identifier or reports that it does not exist."""
    listing = db.get(Listing, listing_id)
    if listing is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Listing not found")
    return listing


@router.patch("/{listing_id}", response_model=ListingPublic)
def update_listing(
    listing_id: str,
    payload: ListingUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> Listing:
    """Applies allowed changes to a listing owned by the current user."""
    listing = db.get(Listing, listing_id)
    if listing is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Listing not found")
    if listing.seller_id != user.id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only the seller can edit this listing")
    for key, value in payload.model_dump(exclude_unset=True).items():
        setattr(listing, key, value)
    db.commit()
    db.refresh(listing)
    return listing


@router.delete("/{listing_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_listing(
    listing_id: str,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> Response:
    """Deletes a listing owned by the current user."""
    listing = db.get(Listing, listing_id)
    if listing is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Listing not found")
    if listing.seller_id != user.id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only the seller can delete this listing")
    db.delete(listing)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
