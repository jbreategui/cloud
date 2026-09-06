package com.cloud.app.controller;

import com.cloud.app.service.ImageStorageService;
import com.cloud.app.service.ImageSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;

import java.util.List;
import java.util.Map;

@RestController
public class ImageController {

    private static final Logger log = LoggerFactory.getLogger(ImageController.class);

    private final ImageStorageService imageStorageService;

    public ImageController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @PostMapping("/api/images")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        log.info("Request de subida recibida: nombre original={}, size={} bytes",
                file.getOriginalFilename(), file.getSize());
        String key = imageStorageService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("key", key));
    }

    @GetMapping("/api/images")
    public List<ImageSummary> list() {
        log.info("Request de listado de imagenes recibida");
        return imageStorageService.listImages();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidFile(IllegalArgumentException e) {
        log.warn("Subida rechazada: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(SdkException.class)
    public ResponseEntity<Map<String, String>> handleS3Error(SdkException e) {
        log.error("Fallo al subir a S3", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "No se pudo subir el archivo, intenta de nuevo"));
    }
}
