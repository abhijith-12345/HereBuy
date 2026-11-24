from contextlib import asynccontextmanager

from fastapi import FastAPI
from sqlalchemy import text
from starlette.responses import JSONResponse

from app.config import API_PREFIX, validate_settings
from app.database import Base, engine, prepare_development_schema
from app.routers import auth, listings
from app import models  # noqa: F401 - registers SQLAlchemy models on Base


@asynccontextmanager
async def lifespan(_: FastAPI):
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


@app.get(f"{API_PREFIX}/health", tags=["health"])
def health() -> JSONResponse:
    with engine.connect() as connection:
        connection.execute(text("SELECT 1"))
    return JSONResponse({"status": "ok", "service": "herebuy-api", "database": "connected"})
