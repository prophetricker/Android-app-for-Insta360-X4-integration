import time
import logging
from fastapi import APIRouter, UploadFile, File, Form, Header, HTTPException
from fastapi.responses import JSONResponse

from models.schemas import AnalyzeResponse, SemanticAnalyzeResponse, ImageInfo
from services.image_processor import get_image_info, validate_image_format, decode_image

router = APIRouter(prefix="", tags=["analyze"])
logger = logging.getLogger(__name__)


@router.post("/analyze", response_model=AnalyzeResponse)
async def analyze_image(
    image: UploadFile = File(...),
    x_upload_quality: str = Header(None, alias="X-Upload-Quality")
):
    """
    Analyze image for depth and scene understanding.
    
    Returns distance, depth level, confidence, and scene text.
    Logs image metadata and processing latency.
    """
    start_time = time.perf_counter()
    
    try:
        image_bytes = await image.read()
        
        # Log received image info
        img_info = get_image_info(image_bytes)
        logger.info(
            f"[ANALYZE] Received image: {img_info['width']}x{img_info['height']}, "
            f"{img_info['size_bytes']} bytes, quality={x_upload_quality or 'default'}"
        )
        
        # Validate format
        if not validate_image_format(image_bytes):
            raise HTTPException(status_code=400, detail="Unsupported image format")
        
        # Simulate processing (fallback mode - no DAP/GPU)
        # In real implementation, this would call DAP inference
        processing_time = time.perf_counter() - start_time
        latency_ms = processing_time * 1000
        
        logger.info(
            f"[ANALYZE] Processing complete: {latency_ms:.2f}ms "
            f"(fallback mode - no DAP inference)"
        )
        
        return AnalyzeResponse(
            distance_m=2.5,
            level="medium",
            confidence=0.75,
            scene_text="indoor environment",
            latency_ms=round(latency_ms, 2)
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[ANALYZE] Error processing image: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/semantic-analyze", response_model=SemanticAnalyzeResponse)
async def semantic_analyze_image(
    image: UploadFile = File(...),
    mode: str = Form("surroundings"),
    x_upload_quality: str = Header(None, alias="X-Upload-Quality")
):
    """
    Perform semantic analysis on image based on mode.
    
    Modes: surroundings, traffic, objects, product
    Logs image metadata and processing latency.
    """
    start_time = time.perf_counter()
    
    try:
        image_bytes = await image.read()
        
        # Log received image info
        img_info = get_image_info(image_bytes)
        logger.info(
            f"[SEMANTIC] Received image: {img_info['width']}x{img_info['height']}, "
            f"{img_info['size_bytes']} bytes, mode={mode}, quality={x_upload_quality or 'default'}"
        )
        
        # Validate format
        if not validate_image_format(image_bytes):
            raise HTTPException(status_code=400, detail="Unsupported image format")
        
        # Simulate processing (fallback mode - no OpenAI/DAP)
        # In real implementation, this would call semantic model
        processing_time = time.perf_counter() - start_time
        latency_ms = processing_time * 1000
        
        logger.info(
            f"[SEMANTIC] Processing complete: {latency_ms:.2f}ms "
            f"(fallback mode - no semantic model inference)"
        )
        
        return SemanticAnalyzeResponse(
            mode=mode,
            summary="This appears to be an indoor environment with typical surroundings.",
            objects=["wall", "floor", "ceiling", "door"],
            traffic_light="none",
            target_found=False,
            product_name="unknown",
            confidence=0.70,
            latency_ms=round(latency_ms, 2),
            fallback_reason="Running in fallback mode without OpenAI API"
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[SEMANTIC] Error processing image: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "service": "OmniEye Cloud Backend"}
