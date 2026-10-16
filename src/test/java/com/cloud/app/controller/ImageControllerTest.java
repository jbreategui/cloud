package com.cloud.app.controller;

import com.cloud.app.exception.GlobalExceptionHandler;
import com.cloud.app.exception.InvalidImageException;
import com.cloud.app.model.ProcessingStatus;
import com.cloud.app.service.ImageStorageService;
import com.cloud.app.service.ImageSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ImageControllerTest {

    @Mock
    private ImageStorageService imageStorageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ImageController(imageStorageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void upload_sinParametroSensitive_subeComoImagenNormal() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", "contenido".getBytes());
        when(imageStorageService.upload(any(), eq(false))).thenReturn(summary(false, null, null));

        mockMvc.perform(multipart("/api/images").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sensitive").value(false));

        verify(imageStorageService).upload(any(), eq(false));
    }

    @Test
    void upload_conSensitiveTrue_devuelveEstadoYUrlDeLaCopia() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", "contenido".getBytes());
        when(imageStorageService.upload(any(), eq(true)))
                .thenReturn(summary(true, ProcessingStatus.DONE, "sensitive/abc.webp"));

        mockMvc.perform(multipart("/api/images").file(file).param("sensitive", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sensitive").value(true))
                .andExpect(jsonPath("$.processingStatus").value("DONE"))
                .andExpect(jsonPath("$.blurredKey").value("sensitive/abc.webp"))
                .andExpect(jsonPath("$.blurredUrl").value("https://bucket/sensitive/abc.webp"));
    }

    @Test
    void retryBlur_devuelveLaImagenActualizada() throws Exception {
        when(imageStorageService.retryBlur("abc.jpg"))
                .thenReturn(summary(true, ProcessingStatus.DONE, "sensitive/abc.webp"));

        mockMvc.perform(post("/api/images/abc.jpg/blur"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("DONE"));
    }

    @Test
    void retryBlur_conImagenNoSensible_responde400() throws Exception {
        when(imageStorageService.retryBlur("abc.jpg")).thenThrow(new InvalidImageException("no es sensible"));

        mockMvc.perform(post("/api/images/abc.jpg/blur"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("no es sensible"));
    }

    private static ImageSummary summary(boolean sensitive, ProcessingStatus status, String blurredKey) {
        return new ImageSummary("images/abc.jpg", "abc.jpg", "foto.jpg", "image/jpeg", 9,
                Instant.parse("2026-01-01T00:00:00Z"), "https://bucket/images/abc.jpg", 0, sensitive, status,
                blurredKey, blurredKey == null ? null : "https://bucket/" + blurredKey);
    }
}
