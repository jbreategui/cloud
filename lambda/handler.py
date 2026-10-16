"""Lambda de Thaki: genera la copia desenfocada de una imagen sensible.

La invoca Spring Boot (BlurService) de forma sincrona justo despues de guardar el original.

Evento:
    {"bucket": "bucket-jbrr", "key": "images/{uuid}.jpg"}
    ("bucket" es opcional si la funcion tiene la variable de entorno BUCKET_NAME)

Respuesta exitosa:
    {"status": "ok", "sourceKey": "images/{uuid}.jpg", "blurredKey": "sensitive/{uuid}.webp",
     "width": 1200, "height": 800, "sizeBytes": 34567}

Respuesta de error (controlada, nunca lanza excepcion):
    {"status": "error", "errorType": "InvalidRequest|SourceNotFound|InvalidImage|S3Error",
     "message": "...", "sourceKey": "..."}

La copia siempre se escribe en la misma key (sensitive/{uuid}.webp, mismo UUID que el
original), asi que reintentar sobrescribe la copia anterior en vez de duplicarla.
"""

import io
import logging
import os
import re

import boto3
from botocore.exceptions import BotoCoreError, ClientError
from PIL import Image, ImageFilter, ImageOps, UnidentifiedImageError

logger = logging.getLogger()
logger.setLevel(logging.INFO)

SOURCE_KEY_PATTERN = re.compile(
    r"^images/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\.(jpg|png|webp)$"
)
TARGET_PREFIX = "sensitive/"

# La copia solo se usa como vista previa: se reduce antes de desenfocar (mucho menos CPU) y el
# radio es proporcional al tamano para que el resultado sea igual de ilegible en cualquier imagen.
MAX_SIDE = int(os.environ.get("MAX_SIDE", "1200"))
BLUR_RADIUS_DIVISOR = int(os.environ.get("BLUR_RADIUS_DIVISOR", "20"))
MIN_BLUR_RADIUS = 8
WEBP_QUALITY = int(os.environ.get("WEBP_QUALITY", "75"))

s3 = boto3.client("s3")


def handler(event, context):
    event = event if isinstance(event, dict) else {}
    bucket = event.get("bucket") or os.environ.get("BUCKET_NAME")
    source_key = event.get("key")

    if not bucket:
        return _error("InvalidRequest", "Falta el bucket (evento o BUCKET_NAME)", source_key)

    match = SOURCE_KEY_PATTERN.match(source_key or "")
    if not match:
        return _error("InvalidRequest", "Key no valida, se espera images/{uuid}.{jpg|png|webp}", source_key)

    blurred_key = f"{TARGET_PREFIX}{match.group(1)}.webp"
    logger.info("Procesando imagen sensible: bucket=%s key=%s -> %s", bucket, source_key, blurred_key)

    try:
        original = s3.get_object(Bucket=bucket, Key=source_key)["Body"].read()
    except ClientError as e:
        code = e.response.get("Error", {}).get("Code", "")
        if code in ("NoSuchKey", "404", "NotFound"):
            return _error("SourceNotFound", f"No existe el objeto {source_key}", source_key)
        return _error("S3Error", f"Error leyendo de S3 ({code}): {e}", source_key)
    except BotoCoreError as e:
        return _error("S3Error", f"Error leyendo de S3: {e}", source_key)

    try:
        webp, width, height = blur_to_webp(original)
    except (UnidentifiedImageError, Image.DecompressionBombError, OSError, ValueError) as e:
        return _error("InvalidImage", f"No se pudo procesar la imagen: {e}", source_key)

    try:
        s3.put_object(
            Bucket=bucket,
            Key=blurred_key,
            Body=webp,
            ContentType="image/webp",
            CacheControl="public, max-age=31536000, immutable",
        )
    except (ClientError, BotoCoreError) as e:
        return _error("S3Error", f"Error escribiendo la copia en S3: {e}", source_key)

    logger.info("Copia desenfocada guardada: %s (%dx%d, %d bytes)", blurred_key, width, height, len(webp))
    return {
        "status": "ok",
        "sourceKey": source_key,
        "blurredKey": blurred_key,
        "width": width,
        "height": height,
        "sizeBytes": len(webp),
    }


def blur_to_webp(data):
    """Devuelve (bytes_webp, ancho, alto) de una version reducida y desenfocada de la imagen."""
    with Image.open(io.BytesIO(data)) as img:
        img = ImageOps.exif_transpose(img)
        has_alpha = img.mode in ("RGBA", "LA") or (img.mode == "P" and "transparency" in img.info)
        img = img.convert("RGBA" if has_alpha else "RGB")
        img.thumbnail((MAX_SIDE, MAX_SIDE))

        radius = max(MIN_BLUR_RADIUS, max(img.size) // BLUR_RADIUS_DIVISOR)
        blurred = img.filter(ImageFilter.GaussianBlur(radius))

        out = io.BytesIO()
        blurred.save(out, format="WEBP", quality=WEBP_QUALITY)
        return out.getvalue(), blurred.width, blurred.height


def _error(error_type, message, source_key):
    logger.error("Fallo procesando %s: [%s] %s", source_key, error_type, message)
    return {"status": "error", "errorType": error_type, "message": message, "sourceKey": source_key}
