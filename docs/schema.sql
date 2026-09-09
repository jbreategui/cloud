-- Tabla de metadata de imagenes, referenciando el objeto real almacenado en S3.
-- Esta copia es solo documentacion; la que se ejecuta de verdad esta en
-- src/main/resources/schema.sql (Spring la corre al arrancar con spring.sql.init.mode=always).
-- Nota: aun no hay herramienta de migracion versionada (Flyway/Liquibase); eso queda para otro desarrollo.

CREATE TABLE IF NOT EXISTS images (
    id BIGSERIAL PRIMARY KEY,
    object_key VARCHAR(512) UNIQUE NOT NULL,
    original_filename VARCHAR(255),
    content_type VARCHAR(100),
    size_bytes BIGINT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT now()
);
