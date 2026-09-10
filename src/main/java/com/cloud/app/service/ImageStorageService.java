package com.cloud.app.service;

import com.cloud.app.exception.ImageNotFoundException;
import com.cloud.app.exception.InvalidImageException;
import com.cloud.app.mapper.ImageMapper;
import com.cloud.app.model.ImageRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    public static final String KEY_PREFIX = "images/";

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Duration URL_DURATION = Duration.ofMinutes(15);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ImageMapper imageMapper;
    private final LikeService likeService;
    private final String bucket;
    private final String region;

    public ImageStorageService(S3Client s3Client, S3Presigner s3Presigner, ImageMapper imageMapper,
                                LikeService likeService,
                                @Value("${app.s3.bucket}") String bucket,
                                @Value("${app.s3.region}") String region) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.imageMapper = imageMapper;
        this.likeService = likeService;
        this.bucket = bucket;
        this.region = region;
    }

    public ImageSummary upload(MultipartFile file) {
        if (file.isEmpty()) {
            log.warn("Intento de subida con archivo vacio, nombre original={}", file.getOriginalFilename());
            throw new InvalidImageException("El archivo esta vacio");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            log.warn("Intento de subida con content-type no permitido: {}", contentType);
            throw new InvalidImageException("Tipo de archivo no permitido: " + contentType);
        }

        String key = KEY_PREFIX + UUID.randomUUID() + extensionFor(contentType);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        log.info("Subiendo imagen a S3: bucket={}, key={}, size={} bytes, contentType={}",
                bucket, key, file.getSize(), contentType);

        try {
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            log.error("Error leyendo el archivo subido antes de enviarlo a S3, key={}", key, e);
            throw new UncheckedIOException("Error leyendo el archivo subido", e);
        } catch (SdkException e) {
            log.error("Error subiendo a S3, bucket={}, key={}", bucket, key, e);
            throw e;
        }

        log.info("Imagen subida con exito: bucket={}, key={}", bucket, key);

        ImageRecord record = new ImageRecord(key, file.getOriginalFilename(), contentType, file.getSize());
        record.setUploadedAt(Instant.now());
        try {
            imageMapper.insert(record);
        } catch (RuntimeException e) {
            log.error("Error registrando la imagen en la base de datos, revirtiendo subida a S3: key={}", key, e);
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            throw e;
        }

        return toSummary(record, 0L);
    }

    public long like(String objectKey) {
        if (!imageMapper.existsByObjectKey(objectKey)) {
            throw new ImageNotFoundException("No existe una imagen con key: " + objectKey);
        }
        return likeService.like(objectKey);
    }

    public List<ImageSummary> listImages() {
        List<ImageRecord> records = imageMapper.findAllOrderByUploadedAtDesc();
        List<String> objectKeys = records.stream().map(ImageRecord::getObjectKey).toList();
        Map<String, Long> likesByKey = likeService.getLikes(objectKeys);

        List<ImageSummary> images = records.stream()
                .map(record -> toSummary(record, likesByKey.getOrDefault(record.getObjectKey(), 0L)))
                .toList();

        log.info("Listado de imagenes desde la base de datos: total={}", images.size());
        return images;
    }

    private ImageSummary toSummary(ImageRecord record, long likes) {
        String url = publicUrl(record.getObjectKey());
        String filename = record.getObjectKey().substring(KEY_PREFIX.length());
        return new ImageSummary(record.getObjectKey(), filename, record.getOriginalFilename(), record.getContentType(),
                record.getSizeBytes(), record.getUploadedAt(), url, likes);
    }

    /**
     * URL publica directa (sin firma). El bucket/objeto debe ser publico para que funcione.
     */
    private String publicUrl(String key) {
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
    }

    /**
     * Se deja disponible para el proximo servicio que si necesite acceso privado con expiracion.
     */
    private String presignedUrl(String key) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(URL_DURATION)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }
}
