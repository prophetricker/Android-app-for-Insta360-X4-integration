import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from routers.analyze import router as analyze_router

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup and shutdown events."""
    logger.info("Starting OmniEye Cloud Backend...")
    logger.info("Endpoints:")
    logger.info("  - POST /analyze - Image depth analysis")
    logger.info("  - POST /semantic-analyze - Semantic image analysis")
    logger.info("  - GET /health - Health check")
    yield
    logger.info("Shutting down OmniEye Cloud Backend...")


app = FastAPI(
    title="OmniEye Cloud Backend",
    description="Backend service for OmniEye app performance telemetry and upload fastpath",
    version="1.0.0",
    lifespan=lifespan
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routers
app.include_router(analyze_router)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
