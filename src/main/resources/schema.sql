CREATE TABLE IF NOT EXISTS images (
    id BIGSERIAL PRIMARY KEY,
    object_key VARCHAR(512) UNIQUE NOT NULL,
    original_filename VARCHAR(255),
    content_type VARCHAR(100),
    size_bytes BIGINT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT now()
);
