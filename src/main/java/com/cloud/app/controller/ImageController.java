package com.cloud.app.controller;

import com.cloud.app.service.ImageStorageService;
import com.cloud.app.service.ImageSummary;
import com.cloud.app.service.LikeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class ImageController {

    private static final Logger log = LoggerFactory.getLogger(ImageController.class);

    private final ImageStorageService imageStorageService;

    public ImageController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @PostMapping("/api/images")
    public ResponseEntity<ImageSummary> upload(@RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "sensitive", defaultValue = "false") boolean sensitive) {
        log.info("Request de subida recibida: nombre original={}, size={} bytes, sensitive={}",
                file.getOriginalFilename(), file.getSize(), sensitive);
        ImageSummary image = imageStorageService.upload(file, sensitive);
        return ResponseEntity.status(HttpStatus.CREATED).body(image);
    }

    @GetMapping("/api/images")
    public List<ImageSummary> list() {
        log.info("Request de listado de imagenes recibida");
        return imageStorageService.listImages();
    }

    @PostMapping("/api/images/{filename}/like")
    public LikeResult like(@PathVariable String filename) {
        log.info("Request de like recibida: filename={}", filename);
        return imageStorageService.like(filename);
    }

    @PostMapping("/api/images/{filename}/blur")
    public ImageSummary retryBlur(@PathVariable String filename) {
        log.info("Request de reintento de desenfoque recibida: filename={}", filename);
        return imageStorageService.retryBlur(filename);
    }
}
