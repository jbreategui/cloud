package com.cloud.app.service;

import com.cloud.app.exception.ImageNotFoundException;
import com.cloud.app.exception.InvalidImageException;
import com.cloud.app.mapper.ImageMapper;
import com.cloud.app.model.ImageRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageStorageServiceTest {

    private static final String BUCKET = "test-bucket";
    private static final String REGION = "us-east-2";

    @Mock
    private S3Client s3Client;
    @Mock
    private S3Presigner s3Presigner;
    @Mock
    private ImageMapper imageMapper;
    @Mock
    private LikeService likeService;

    private ImageStorageService service;

    @BeforeEach
    void setUp() {
        service = new ImageStorageService(s3Client, s3Presigner, imageMapper, likeService, BUCKET, REGION);
    }

    @Test
    void upload_conArchivoValido_subeAS3YRegistraEnBaseDeDatos() {
        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", "contenido".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        ImageSummary result = service.upload(file);

        assertThat(result.key()).startsWith("images/").endsWith(".jpg");
        assertThat(result.originalFilename()).isEqualTo("foto.jpg");
        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.likes()).isZero();
        assertThat(result.url()).isEqualTo("https://%s.s3.%s.amazonaws.com/%s".formatted(BUCKET, REGION, result.key()));

        verify(imageMapper).insert(any(ImageRecord.class));
    }

    @Test
    void upload_conArchivoVacio_lanzaInvalidImageException() {
        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.upload(file)).isInstanceOf(InvalidImageException.class);
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void upload_conContentTypeNoPermitido_lanzaInvalidImageException() {
        MockMultipartFile file = new MockMultipartFile("file", "documento.pdf", "application/pdf", "contenido".getBytes());

        assertThatThrownBy(() -> service.upload(file)).isInstanceOf(InvalidImageException.class);
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void upload_siFallaS3_noRegistraEnBaseDeDatos() {
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", "contenido".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkException.create("fallo de S3", null));

        assertThatThrownBy(() -> service.upload(file)).isInstanceOf(SdkException.class);
        verify(imageMapper, never()).insert(any(ImageRecord.class));
    }

    @Test
    void upload_siFallaLaBaseDeDatos_revierteElObjetoEnS3() {
        MockMultipartFile file = new MockMultipartFile("file", "foto.webp", "image/webp", "contenido".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        doThrow(new RuntimeException("error de BD")).when(imageMapper).insert(any(ImageRecord.class));

        assertThatThrownBy(() -> service.upload(file)).isInstanceOf(RuntimeException.class);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).startsWith("images/").endsWith(".webp");
    }

    @Test
    void like_conImagenExistente_incrementaYDevuelveLaKeyCompleta() {
        when(imageMapper.existsByObjectKey("images/foto.jpg")).thenReturn(true);
        when(likeService.like("images/foto.jpg")).thenReturn(5L);

        LikeResult result = service.like("foto.jpg");

        assertThat(result.key()).isEqualTo("images/foto.jpg");
        assertThat(result.likes()).isEqualTo(5L);
    }

    @Test
    void like_conImagenInexistente_lanzaImageNotFoundExceptionYNoLlamaADynamo() {
        when(imageMapper.existsByObjectKey("images/no-existe.jpg")).thenReturn(false);

        assertThatThrownBy(() -> service.like("no-existe.jpg")).isInstanceOf(ImageNotFoundException.class);
        verify(likeService, never()).like(any());
    }

    @Test
    void listImages_combinaMetadataDeLaBaseConLikesDeDynamo() {
        ImageRecord conLikes = new ImageRecord("images/a.jpg", "a.jpg", "image/jpeg", 100);
        conLikes.setUploadedAt(Instant.parse("2026-01-01T00:00:00Z"));
        ImageRecord sinLikes = new ImageRecord("images/b.png", "b.png", "image/png", 200);
        sinLikes.setUploadedAt(Instant.parse("2026-01-02T00:00:00Z"));

        when(imageMapper.findAllOrderByUploadedAtDesc()).thenReturn(List.of(conLikes, sinLikes));
        when(likeService.getLikes(List.of("images/a.jpg", "images/b.png")))
                .thenReturn(Map.of("images/a.jpg", 3L));

        List<ImageSummary> result = service.listImages();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).filename()).isEqualTo("a.jpg");
        assertThat(result.get(0).likes()).isEqualTo(3L);
        assertThat(result.get(1).filename()).isEqualTo("b.png");
        assertThat(result.get(1).likes()).isZero();
    }
}
