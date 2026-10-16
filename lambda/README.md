# Lambda `thaki-blur-image`

Genera la copia desenfocada de las imágenes marcadas como **contenido sensible**.

```
POST /api/images (sensitive=true)
  └─ Spring Boot: original -> S3 images/{uuid}.ext
                  metadatos -> Postgres (processing_status = PENDING)
                  invoca Lambda (síncrono, AWS SDK v2) ──► handler.py
                                                            ├─ lee images/{uuid}.ext
                                                            ├─ reduce a 1200px máx. + GaussianBlur
                                                            └─ escribe sensitive/{uuid}.webp
                  processing_status = DONE (+ blurred_key)  ó  FAILED

POST /api/images/{filename}/blur   → reintenta sin volver a subir el original
```

Si Lambda falla, el original y sus metadatos **se conservan** y la imagen queda en `FAILED`. La galería ofrece "Reintentar". La copia siempre se escribe en la misma key (`sensitive/{uuid}.webp`), así que reintentar no genera duplicados.

## Archivos

| Archivo | Qué es |
|---|---|
| `handler.py` | Código de la función (handler: `handler.handler`) |
| `requirements.txt` | Pillow (boto3 ya viene en el runtime de Lambda) |
| `test_handler.py` | Tests con S3 simulado: `python -m unittest -v` (solo requiere Pillow) |

## Contrato

```jsonc
// Evento
{ "bucket": "bucket-jbrr", "key": "images/3f2a6c1e-9b7d-4e2a-8c51-0d4b7e9a1f23.jpg" }

// OK
{ "status": "ok", "sourceKey": "images/…jpg", "blurredKey": "sensitive/….webp",
  "width": 1200, "height": 800, "sizeBytes": 34567 }

// Error controlado (la función no lanza excepciones)
{ "status": "error", "errorType": "InvalidRequest | SourceNotFound | InvalidImage | S3Error",
  "message": "…", "sourceKey": "…" }
```

## Pasos pendientes para configurarla en AWS (región `us-east-2`)

Nada de esto lo hace el código: hay que configurarlo una vez a mano o con IaC.

1. **Migrar la base de datos de prod** (en prod `spring.sql.init.mode=never`). En el EC2 de Postgres:
   ```bash
   psql -h localhost -U <DB_USERNAME> -d <DB_NAME> -f 002_sensitive_images.sql
   ```
   El archivo está en `docs/migrations/`. Es idempotente y solo agrega columnas: las imágenes existentes quedan como no sensibles.

2. **Empaquetar Pillow como Layer.** Hay que compilarlo para Amazon Linux, no para Windows:
   ```bash
   pip install --platform manylinux2014_x86_64 --implementation cp --python-version 3.13 \
       --only-binary=:all: --target layer/python -r requirements.txt
   cd layer && zip -r ../pillow-layer.zip python
   ```
   Publica `pillow-layer.zip` como Lambda Layer, compatible con Python 3.13 y x86_64.

3. **Crear la función.**
   - Nombre: `thaki-blur-image`. Si usas otro, configura `APP_LAMBDA_FUNCTION_NAME` en el `.env` de cada EC2.
   - Runtime Python 3.13, arquitectura x86_64 y handler `handler.handler`.
   - Código: un zip que contenga solo `handler.py`. Agrega la Layer del paso 2.
   - **Timeout de 30 s** (menor que los 60 s de `LambdaConfig` en Spring) y **memoria de 1024 MB**. Pillow usa CPU, y en Lambda la CPU escala con la memoria.
   - Variables de entorno opcionales: `BUCKET_NAME=bucket-jbrr`, `MAX_SIDE`, `BLUR_RADIUS_DIVISOR` y `WEBP_QUALITY`.

4. **Rol IAM de la Lambda** (mínimo privilegio):
   - `AWSLambdaBasicExecutionRole` para los logs de CloudWatch.
   - `s3:GetObject` sobre `arn:aws:s3:::bucket-jbrr/images/*`.
   - `s3:PutObject` sobre `arn:aws:s3:::bucket-jbrr/sensitive/*`.

5. **Rol IAM de las EC2 de la app:** agregar `lambda:InvokeFunction` sobre el ARN de `thaki-blur-image`.

6. **Lectura pública de `sensitive/*`.** La galería sirve la copia por URL directa de S3, igual que `images/*`. La bucket policy que hoy permite `s3:GetObject` en `images/*` tiene que cubrir también `sensitive/*`.

7. **Redesplegar el backend** en cada EC2 (`docker compose up -d --build`) y el frontend (`npm run build` y subir `dist/` al bucket de CloudFront).

8. **Prueba de humo:** sube una imagen con «Contenido sensible» marcado. Comprueba que aparece `sensitive/{uuid}.webp` en el bucket y que la galería muestra la versión desenfocada. Revisa los logs en CloudWatch (`/aws/lambda/thaki-blur-image`).

> **Nota:** el desenfoque es una protección de **experiencia de usuario**, no de acceso. El original sigue siendo público en `images/{uuid}.ext` y la API devuelve su URL para poder revelarlo con un clic.
