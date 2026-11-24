"""Creates the FastAPI app, configures startup, and mounts API routers."""

from contextlib import asynccontextmanager
import logging
import json
from time import perf_counter

from fastapi import FastAPI
from fastapi import Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from sqlalchemy import text
from starlette.responses import JSONResponse, Response

from app.config import API_PREFIX, validate_settings
from app.database import Base, engine, prepare_development_schema
from app.routers import auth, listings
from app import models  # noqa: F401 - registers SQLAlchemy models on Base

api_logger = logging.getLogger("herebuy.api")
api_logger.setLevel(logging.INFO)
api_logger.propagate = False
if not api_logger.handlers:
    api_handler = logging.StreamHandler()
    api_handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    api_logger.addHandler(api_handler)


@asynccontextmanager
async def lifespan(_: FastAPI):
    """Runs configuration checks and database initialization before serving requests."""
    validate_settings()
    prepare_development_schema()
    Base.metadata.create_all(bind=engine)
    yield


app = FastAPI(
    title="HereBuy API",
    version="0.1.0",
    description="Local marketplace API for HereBuy.",
    lifespan=lifespan,
)
app.include_router(auth.router, prefix=API_PREFIX)
app.include_router(listings.router, prefix=API_PREFIX)


@app.exception_handler(RequestValidationError)
async def log_validation_error(request: Request, error: RequestValidationError) -> JSONResponse:
    """Logs validation locations and messages without logging submitted field values."""
    safe_errors = [
        {
            "field": ".".join(str(part) for part in item.get("loc", ())),
            "message": item.get("msg", "Invalid value"),
            "type": item.get("type", "validation_error"),
        }
        for item in error.errors()
    ]
    api_logger.warning(
        "Validation failed %s %s details=%s",
        request.method,
        request.url.path,
        json.dumps(safe_errors, ensure_ascii=False),
    )
    return JSONResponse(status_code=422, content={"detail": jsonable_encoder(error.errors())})


@app.middleware("http")
async def log_api_requests(request: Request, call_next) -> Response:
    """Writes one method, path, status, duration, and client line for each API request."""
    if not request.url.path.startswith(f"{API_PREFIX}/") and request.url.path != API_PREFIX:
        return await call_next(request)

    started_at = perf_counter()
    client_host = request.client.host if request.client else "unknown"
    try:
        response = await call_next(request)
    except Exception:
        duration_ms = (perf_counter() - started_at) * 1_000
        api_logger.exception(
            "%s %s status=500 duration_ms=%.1f client=%s",
            request.method,
            request.url.path,
            duration_ms,
            client_host,
        )
        raise

    duration_ms = (perf_counter() - started_at) * 1_000
    api_logger.info(
        "%s %s status=%s duration_ms=%.1f client=%s",
        request.method,
        request.url.path,
        response.status_code,
        duration_ms,
        client_host,
    )
    return response


@app.get(f"{API_PREFIX}/health", tags=["health"])
def health() -> JSONResponse:
    """Checks database connectivity and returns the service health response."""
    with engine.connect() as connection:
        connection.execute(text("SELECT 1"))
    return JSONResponse({"status": "ok", "service": "herebuy-api", "database": "connected"})
