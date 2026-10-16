package com.cloud.app.service;

import com.cloud.app.exception.BlurProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlurServiceTest {

    private static final String FUNCTION_NAME = "thaki-blur-image";
    private static final String BUCKET = "test-bucket";

    @Mock
    private LambdaClient lambdaClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BlurService blurService;

    @BeforeEach
    void setUp() {
        blurService = new BlurService(lambdaClient, objectMapper, FUNCTION_NAME, BUCKET);
    }

    @Test
    void blur_conRespuestaOk_devuelveLaKeyDeLaCopiaYEnviaBucketYKey() {
        when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(response(
                "{\"status\":\"ok\",\"sourceKey\":\"images/abc.jpg\",\"blurredKey\":\"sensitive/abc.webp\"}", null));

        String blurredKey = blurService.blur("images/abc.jpg");

        assertThat(blurredKey).isEqualTo("sensitive/abc.webp");

        ArgumentCaptor<InvokeRequest> captor = ArgumentCaptor.forClass(InvokeRequest.class);
        verify(lambdaClient).invoke(captor.capture());
        assertThat(captor.getValue().functionName()).isEqualTo(FUNCTION_NAME);
        assertThat(captor.getValue().invocationType()).isEqualTo(InvocationType.REQUEST_RESPONSE);
        var payload = objectMapper.readTree(captor.getValue().payload().asUtf8String());
        assertThat(payload.get("bucket").asString()).isEqualTo(BUCKET);
        assertThat(payload.get("key").asString()).isEqualTo("images/abc.jpg");
    }

    @Test
    void blur_conErrorControladoDeLaLambda_lanzaBlurProcessingException() {
        when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(response(
                "{\"status\":\"error\",\"errorType\":\"InvalidImage\",\"message\":\"no es una imagen\"}", null));

        assertThatThrownBy(() -> blurService.blur("images/abc.jpg"))
                .isInstanceOf(BlurProcessingException.class)
                .hasMessageContaining("InvalidImage");
    }

    @Test
    void blur_conFunctionError_lanzaBlurProcessingException() {
        when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(response(
                "{\"errorMessage\":\"Task timed out after 30.00 seconds\"}", "Unhandled"));

        assertThatThrownBy(() -> blurService.blur("images/abc.jpg"))
                .isInstanceOf(BlurProcessingException.class)
                .hasMessageContaining("Unhandled");
    }

    @Test
    void blur_siFallaLaInvocacion_envuelveLaExcepcionDelSdk() {
        when(lambdaClient.invoke(any(InvokeRequest.class)))
                .thenThrow(SdkClientException.create("sin conexion"));

        assertThatThrownBy(() -> blurService.blur("images/abc.jpg"))
                .isInstanceOf(BlurProcessingException.class)
                .hasCauseInstanceOf(SdkClientException.class);
    }

    @Test
    void blur_conRespuestaNoJson_lanzaBlurProcessingException() {
        when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(response("no-json", null));

        assertThatThrownBy(() -> blurService.blur("images/abc.jpg")).isInstanceOf(BlurProcessingException.class);
    }

    @Test
    void blur_conRespuestaOkSinBlurredKey_lanzaBlurProcessingException() {
        when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(response("{\"status\":\"ok\"}", null));

        assertThatThrownBy(() -> blurService.blur("images/abc.jpg")).isInstanceOf(BlurProcessingException.class);
    }

    private static InvokeResponse response(String payload, String functionError) {
        return InvokeResponse.builder()
                .statusCode(200)
                .functionError(functionError)
                .payload(SdkBytes.fromUtf8String(payload))
                .build();
    }
}
