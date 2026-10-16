-- Migracion: contenido sensible.
-- En prod spring.sql.init.mode=never, asi que se aplica a mano una sola vez en el EC2 de Postgres:
--   psql -h localhost -U <DB_USERNAME> -d <DB_NAME> -f 002_sensitive_images.sql
-- Es idempotente (IF NOT EXISTS) y no toca datos: las imagenes existentes quedan con
-- sensitive = FALSE y processing_status / blurred_key en NULL (no sensibles, sin copia).

ALTER TABLE images ADD COLUMN IF NOT EXISTS sensitive BOOLEAN NOT NULL DEFAULT FALSE;

-- Estado de la copia desenfocada generada por Lambda. NULL para imagenes no sensibles.
ALTER TABLE images ADD COLUMN IF NOT EXISTS processing_status VARCHAR(20)
    CHECK (processing_status IN ('PENDING', 'DONE', 'FAILED'));

-- Key S3 de la copia desenfocada: sensitive/{uuid}.webp (mismo UUID que la original).
ALTER TABLE images ADD COLUMN IF NOT EXISTS blurred_key VARCHAR(512);
