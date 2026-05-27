import io
import logging
from PIL import Image
from typing import Dict, Any

logger = logging.getLogger(__name__)


def get_image_info(image_bytes: bytes) -> Dict[str, Any]:
    """Extract image metadata from bytes."""
    try:
        image = Image.open(io.BytesIO(image_bytes))
        width, height = image.size
        img_format = image.format or "unknown"
        
        info = {
            "width": width,
            "height": height,
            "size_bytes": len(image_bytes),
            "format": img_format
        }
        
        logger.info(f"Image info: {width}x{height}, {len(image_bytes)} bytes, format={img_format}")
        return info
    except Exception as e:
        logger.error(f"Failed to get image info: {e}")
        raise


def validate_image_format(image_bytes: bytes) -> bool:
    """Validate that the image is a supported format."""
    try:
        image = Image.open(io.BytesIO(image_bytes))
        return image.format in ["JPEG", "JPG", "PNG"]
    except Exception:
        return False


def decode_image(image_bytes: bytes) -> Image.Image:
    """Decode image bytes to PIL Image."""
    return Image.open(io.BytesIO(image_bytes))
