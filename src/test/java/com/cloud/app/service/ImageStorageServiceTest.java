package com.cloud.app.service;

import com.cloud.app.exception.BlurProcessingException;
import com.cloud.app.exception.ImageNotFoundException;
import com.cloud.app.exception.InvalidImageException;
import com.cloud.app.mapper.ImageMapper;
import com.cloud.app.model.ImageRecord;
import com.cloud.app.model.ProcessingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private BlurService blurService;

    private ImageStorageService service;

    @BeforeEach
    void setUp() {
        service = new ImageStorageService(s3Client, s3Presigner, imageMapper, likeService, blurService, BUCKET, REGION);
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
        assertThat(result.sensitive()).isFalse();
        assertThat(result.processingStatus()).isNull();
        assertThat(result.blurredUrl()).isNull();

        verify(imageMapper).insert(any(ImageRecord.class));
        verifyNoInteractions(blurService);
        verify(imageMapper, never()).updateProcessing(any());
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
    void upload_sensible_guardaOriginalYLuegoGeneraCopiaDesenfocada() {
        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", "contenido".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(blurService.blur(any())).thenAnswer(inv -> blurredKeyFor(inv.getArgument(0)));

        ImageSummary result = service.upload(file, true);

        String uuid = result.filename().substring(0, result.filename().lastIndexOf('.'));
        assertThat(result.sensitive()).isTrue();
        assertThat(result.processingStatus()).isEqualTo(ProcessingStatus.DONE);
        assertThat(result.blurredKey()).isEqualTo("sensitive/" + uuid + ".webp");
        assertThat(result.blurredUrl())
                .isEqualTo("https://%s.s3.%s.amazonaws.com/sensitive/%s.webp".formatted(BUCKET, REGION, uuid));

        // Orden: original en S3 -> metadatos PENDING en BD -> Lambda -> estado DONE en BD.
        ArgumentCaptor<ImageRecord> insertCaptor = ArgumentCaptor.forClass(ImageRecord.class);
        InOrder order = inOrder(s3Client, imageMapper, blurService);
        order.verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        order.verify(imageMapper).insert(insertCaptor.capture());
        order.verify(blurService).blur(result.key());
        order.verify(imageMapper).updateProcessing(any(ImageRecord.class));
        assertThat(insertCaptor.getValue().isSensitive()).isTrue();
    }

    @Test
    void upload_sensible_siFallaLambda_conservaOriginalYMarcaFailed() {
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", "contenido".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(blurService.blur(any())).thenThrow(new BlurProcessingException("timeout"));

        ImageSummary result = service.upload(file, true);

        assertThat(result.sensitive()).isTrue();
        assertThat(result.processingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(result.blurredKey()).isNull();
        assertThat(result.blurredUrl()).isNull();

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        ArgumentCaptor<ImageRecord> captor = ArgumentCaptor.forClass(ImageRecord.class);
        verify(imageMapper).updateProcessing(captor.capture());
        assertThat(captor.getValue().getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
    }

    @Test
    void upload_sensible_siFallaLaBaseDeDatos_noInvocaLambda() {
        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", "contenido".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        doThrow(new RuntimeException("error de BD")).when(imageMapper).insert(any(ImageRecord.class));

        assertThatThrownBy(() -> service.upload(file, true)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(blurService);
    }

    @Test
    void retryBlur_conEstadoFailed_regeneraLaCopiaSinVolverASubirElOriginal() {
        ImageRecord record = sensitiveRecord("images/abc.jpg", ProcessingStatus.FAILED, null);
        when(imageMapper.findByObjectKey("images/abc.jpg")).thenReturn(record);
        when(blurService.blur("images/abc.jpg")).thenReturn("sensitive/abc.webp");
        when(likeService.getLikes(List.of("images/abc.jpg"))).thenReturn(Map.of("images/abc.jpg", 4L));

        ImageSummary result = service.retryBlur("abc.jpg");

        assertThat(result.processingStatus()).isEqualTo(ProcessingStatus.DONE);
        assertThat(result.blurredKey()).isEqualTo("sensitive/abc.webp");
        assertThat(result.likes()).isEqualTo(4L);
        verify(imageMapper).updateProcessing(record);
        verify(imageMapper, never()).insert(any(ImageRecord.class));
        verifyNoInteractions(s3Client);
    }

    @Test
    void retryBlur_siLambdaVuelveAFallar_mantieneFailed() {
        ImageRecord record = sensitiveRecord("images/abc.jpg", ProcessingStatus.FAILED, null);
        when(imageMapper.findByObjectKey("images/abc.jpg")).thenReturn(record);
        when(blurService.blur("images/abc.jpg")).thenThrow(new BlurProcessingException("sigue fallando"));

        ImageSummary result = service.retryBlur("abc.jpg");

        assertThat(result.processingStatus()).isEqualTo(ProcessingStatus.FAILED);
        verify(imageMapper).updateProcessing(record);
    }

    @Test
    void retryBlur_conCopiaYaGenerada_noInvocaLambdaNiActualiza() {
        ImageRecord record = sensitiveRecord("images/abc.jpg", ProcessingStatus.DONE, "sensitive/abc.webp");
        when(imageMapper.findByObjectKey("images/abc.jpg")).thenReturn(record);

        ImageSummary result = service.retryBlur("abc.jpg");

        assertThat(result.processingStatus()).isEqualTo(ProcessingStatus.DONE);
        verifyNoInteractions(blurService);
        verify(imageMapper, never()).updateProcessing(any());
    }

    @Test
    void retryBlur_conImagenNoSensible_lanzaInvalidImageException() {
        when(imageMapper.findByObjectKey("images/abc.jpg"))
                .thenReturn(new ImageRecord("images/abc.jpg", "abc.jpg", "image/jpeg", 10));

        assertThatThrownBy(() -> service.retryBlur("abc.jpg")).isInstanceOf(InvalidImageException.class);
        verifyNoInteractions(blurService);
    }

    @Test
    void retryBlur_conImagenInexistente_lanzaImageNotFoundException() {
        assertThatThrownBy(() -> service.retryBlur("no-existe.jpg")).isInstanceOf(ImageNotFoundException.class);
        verifyNoInteractions(blurService);
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

    @Test
    void listImages_devuelveIndicadorSensibleEstadoYUrlDeLaCopia() {
        ImageRecord normal = new ImageRecord("images/a.jpg", "a.jpg", "image/jpeg", 100);
        ImageRecord procesada = sensitiveRecord("images/b.jpg", ProcessingStatus.DONE, "sensitive/b.webp");
        ImageRecord fallida = sensitiveRecord("images/c.jpg", ProcessingStatus.FAILED, null);
        when(imageMapper.findAllOrderByUploadedAtDesc()).thenReturn(List.of(normal, procesada, fallida));
        when(likeService.getLikes(any())).thenReturn(Map.of());

        List<ImageSummary> result = service.listImages();

        assertThat(result.get(0).sensitive()).isFalse();
        assertThat(result.get(0).processingStatus()).isNull();
        assertThat(result.get(0).blurredUrl()).isNull();

        assertThat(result.get(1).sensitive()).isTrue();
        assertThat(result.get(1).processingStatus()).isEqualTo(ProcessingStatus.DONE);
        assertThat(result.get(1).blurredKey()).isEqualTo("sensitive/b.webp");
        assertThat(result.get(1).blurredUrl())
                .isEqualTo("https://%s.s3.%s.amazonaws.com/sensitive/b.webp".formatted(BUCKET, REGION));
        assertThat(result.get(1).url()).endsWith("/images/b.jpg");

        assertThat(result.get(2).processingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(result.get(2).blurredUrl()).isNull();
    }

    private static ImageRecord sensitiveRecord(String key, ProcessingStatus status, String blurredKey) {
        ImageRecord record = new ImageRecord(key, "original.jpg", "image/jpeg", 10);
        record.setUploadedAt(Instant.parse("2026-01-01T00:00:00Z"));
        record.setSensitive(true);
        record.setProcessingStatus(status);
        record.setBlurredKey(blurredKey);
        return record;
    }

    private static String blurredKeyFor(String objectKey) {
        String name = objectKey.substring("images/".length());
        return "sensitive/" + name.substring(0, name.lastIndexOf('.')) + ".webp";
    }
}
