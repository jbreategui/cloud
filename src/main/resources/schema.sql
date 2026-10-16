CREATE TABLE IF NOT EXISTS images (
    id BIGSERIAL PRIMARY KEY,
    object_key VARCHAR(512) UNIQUE NOT NULL,
    original_filename VARCHAR(255),
    content_type VARCHAR(100),
    size_bytes BIGINT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Contenido sensible (ver docs/migrations/002_sensitive_images.sql). Idempotente para
-- bases ya creadas: las filas existentes quedan como no sensibles y sin estado.
ALTER TABLE images ADD COLUMN IF NOT EXISTS sensitive BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE images ADD COLUMN IF NOT EXISTS processing_status VARCHAR(20)
    CHECK (processing_status IN ('PENDING', 'DONE', 'FAILED'));
ALTER TABLE images ADD COLUMN IF NOT EXISTS blurred_key VARCHAR(512);
