"""Tests de la Lambda con S3 simulado. Ejecutar desde esta carpeta:

    python -m unittest -v

Solo requieren Pillow: boto3/botocore se sustituyen por stubs para no depender de AWS.
"""

import io
import sys
import types
import unittest
from unittest import mock

from PIL import Image


class _ClientError(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.response = {"Error": {"Code": code}}


class _BotoCoreError(Exception):
    pass


# Stubs instalados antes de importar el handler (que crea el cliente S3 al cargarse).
_boto3 = types.ModuleType("boto3")
_boto3.client = lambda *args, **kwargs: mock.MagicMock()
_exceptions = types.ModuleType("botocore.exceptions")
_exceptions.ClientError = _ClientError
_exceptions.BotoCoreError = _BotoCoreError
_botocore = types.ModuleType("botocore")
_botocore.exceptions = _exceptions
sys.modules.setdefault("boto3", _boto3)
sys.modules.setdefault("botocore", _botocore)
sys.modules.setdefault("botocore.exceptions", _exceptions)

import handler  # noqa: E402

UUID = "3f2a6c1e-9b7d-4e2a-8c51-0d4b7e9a1f23"
SOURCE_KEY = f"images/{UUID}.jpg"
BUCKET = "test-bucket"


def _image_bytes(fmt="JPEG", mode="RGB", size=(400, 300)):
    out = io.BytesIO()
    Image.new(mode, size, color=(200, 30, 30) if mode == "RGB" else (200, 30, 30, 128)).save(out, format=fmt)
    return out.getvalue()


class HandlerTest(unittest.TestCase):

    def setUp(self):
        self.s3 = mock.MagicMock()
        patcher = mock.patch.object(handler, "s3", self.s3)
        patcher.start()
        self.addCleanup(patcher.stop)

    def _given_source(self, data):
        self.s3.get_object.return_value = {"Body": io.BytesIO(data)}

    def test_genera_copia_webp_con_el_mismo_uuid(self):
        self._given_source(_image_bytes())

        result = handler.handler({"bucket": BUCKET, "key": SOURCE_KEY}, None)

        self.assertEqual(result["status"], "ok")
        self.assertEqual(result["blurredKey"], f"sensitive/{UUID}.webp")
        self.assertEqual(result["sourceKey"], SOURCE_KEY)
        self.s3.get_object.assert_called_once_with(Bucket=BUCKET, Key=SOURCE_KEY)

        kwargs = self.s3.put_object.call_args.kwargs
        self.assertEqual(kwargs["Bucket"], BUCKET)
        self.assertEqual(kwargs["Key"], f"sensitive/{UUID}.webp")
        self.assertEqual(kwargs["ContentType"], "image/webp")
        with Image.open(io.BytesIO(kwargs["Body"])) as written:
            self.assertEqual(written.format, "WEBP")
            self.assertEqual(written.size, (400, 300))

    def test_desenfoca_la_imagen(self):
        # Mitad negra / mitad blanca: tras el desenfoque el borde central queda gris.
        img = Image.new("L", (200, 100), 0)
        img.paste(255, (100, 0, 200, 100))
        out = io.BytesIO()
        img.convert("RGB").save(out, format="PNG")

        webp, _, _ = handler.blur_to_webp(out.getvalue())

        with Image.open(io.BytesIO(webp)) as blurred:
            r, _, _ = blurred.convert("RGB").getpixel((100, 50))
            self.assertTrue(60 < r < 200, f"el borde deberia quedar gris, valor={r}")

    def test_reduce_imagenes_grandes(self):
        webp, width, height = handler.blur_to_webp(_image_bytes(size=(4000, 2000)))

        self.assertEqual(max(width, height), handler.MAX_SIDE)
        self.assertEqual((width, height), (handler.MAX_SIDE, handler.MAX_SIDE // 2))
        self.assertGreater(len(webp), 0)

    def test_conserva_transparencia_de_png(self):
        webp, _, _ = handler.blur_to_webp(_image_bytes(fmt="PNG", mode="RGBA"))

        with Image.open(io.BytesIO(webp)) as blurred:
            self.assertEqual(blurred.mode, "RGBA")

    def test_usa_bucket_de_variable_de_entorno_si_no_viene_en_el_evento(self):
        self._given_source(_image_bytes())

        with mock.patch.dict("os.environ", {"BUCKET_NAME": "env-bucket"}):
            result = handler.handler({"key": SOURCE_KEY}, None)

        self.assertEqual(result["status"], "ok")
        self.assertEqual(self.s3.put_object.call_args.kwargs["Bucket"], "env-bucket")

    def test_key_invalida_devuelve_error_sin_tocar_s3(self):
        for key in (None, "", "images/no-es-uuid.jpg", f"sensitive/{UUID}.webp", f"images/{UUID}.gif"):
            with self.subTest(key=key):
                result = handler.handler({"bucket": BUCKET, "key": key}, None)
                self.assertEqual(result["status"], "error")
                self.assertEqual(result["errorType"], "InvalidRequest")
        self.s3.get_object.assert_not_called()
        self.s3.put_object.assert_not_called()

    def test_sin_bucket_devuelve_error(self):
        with mock.patch.dict("os.environ", {}, clear=True):
            result = handler.handler({"key": SOURCE_KEY}, None)

        self.assertEqual(result["errorType"], "InvalidRequest")

    def test_original_inexistente_devuelve_source_not_found(self):
        self.s3.get_object.side_effect = _ClientError("NoSuchKey")

        result = handler.handler({"bucket": BUCKET, "key": SOURCE_KEY}, None)

        self.assertEqual(result["status"], "error")
        self.assertEqual(result["errorType"], "SourceNotFound")
        self.s3.put_object.assert_not_called()

    def test_error_de_permisos_al_leer_devuelve_s3_error(self):
        self.s3.get_object.side_effect = _ClientError("AccessDenied")

        result = handler.handler({"bucket": BUCKET, "key": SOURCE_KEY}, None)

        self.assertEqual(result["errorType"], "S3Error")

    def test_archivo_que_no_es_imagen_devuelve_invalid_image(self):
        self._given_source(b"esto no es una imagen")

        result = handler.handler({"bucket": BUCKET, "key": SOURCE_KEY}, None)

        self.assertEqual(result["status"], "error")
        self.assertEqual(result["errorType"], "InvalidImage")
        self.s3.put_object.assert_not_called()

    def test_error_al_escribir_la_copia_devuelve_s3_error(self):
        self._given_source(_image_bytes())
        self.s3.put_object.side_effect = _BotoCoreError("timeout")

        result = handler.handler({"bucket": BUCKET, "key": SOURCE_KEY}, None)

        self.assertEqual(result["status"], "error")
        self.assertEqual(result["errorType"], "S3Error")

    def test_reintentar_sobrescribe_la_misma_key(self):
        self._given_source(_image_bytes())
        handler.handler({"bucket": BUCKET, "key": SOURCE_KEY}, None)
        self._given_source(_image_bytes())
        handler.handler({"bucket": BUCKET, "key": SOURCE_KEY}, None)

        keys = {c.kwargs["Key"] for c in self.s3.put_object.call_args_list}
        self.assertEqual(keys, {f"sensitive/{UUID}.webp"})


if __name__ == "__main__":
    unittest.main()
