package com.cloud.app.service;

import com.cloud.app.exception.BlurProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Invoca la Lambda que genera la copia desenfocada (sensitive/{uuid}.webp) de una imagen ya
 * guardada en S3. Contrato con lambda/handler.py:
 * <pre>
 *   request : {"bucket": "...", "key": "images/{uuid}.jpg"}
 *   response: {"status": "ok", "blurredKey": "sensitive/{uuid}.webp", ...}
 *           | {"status": "error", "errorType": "...", "message": "..."}
 * </pre>
 */
@Service
public class BlurService {

    private static final Logger log = LoggerFactory.getLogger(BlurService.class);

    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;
    private final String functionName;
    private final String bucket;

    public BlurService(LambdaClient lambdaClient, ObjectMapper objectMapper,
                       @Value("${app.lambda.function-name}") String functionName,
                       @Value("${app.s3.bucket}") String bucket) {
        this.lambdaClient = lambdaClient;
        this.objectMapper = objectMapper;
        this.functionName = functionName;
        this.bucket = bucket;
    }

    /**
     * @return la key S3 de la copia desenfocada
     * @throws BlurProcessingException si Lambda no se pudo invocar o no genero la copia
     */
    public String blur(String objectKey) {
        String payload = objectMapper.writeValueAsString(Map.of("bucket", bucket, "key", objectKey));
        InvokeRequest request = InvokeRequest.builder()
                .functionName(functionName)
                .invocationType(InvocationType.REQUEST_RESPONSE)
                .payload(SdkBytes.fromUtf8String(payload))
                .build();

        log.info("Invocando Lambda de desenfoque: function={}, key={}", functionName, objectKey);

        InvokeResponse response;
        try {
            response = lambdaClient.invoke(request);
        } catch (SdkException e) {
            throw new BlurProcessingException("No se pudo invocar la Lambda " + functionName, e);
        }

        String body = response.payload() == null ? "" : response.payload().asUtf8String();

        // Error no controlado dentro de la funcion (excepcion, timeout, memoria): Lambda responde
        // 200 igualmente, el fallo solo se ve en FunctionError.
        if (response.functionError() != null) {
            throw new BlurProcessingException("La Lambda fallo (%s): %s".formatted(response.functionError(), body));
        }

        JsonNode json;
        try {
            json = objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new BlurProcessingException("Respuesta de Lambda no es JSON valido: " + body, e);
        }

        if (!"ok".equals(text(json, "status"))) {
            throw new BlurProcessingException("La Lambda no genero la copia (%s): %s"
                    .formatted(text(json, "errorType"), text(json, "message")));
        }

        String blurredKey = text(json, "blurredKey");
        if (blurredKey == null || blurredKey.isBlank()) {
            throw new BlurProcessingException("Respuesta de Lambda sin blurredKey: " + body);
        }

        log.info("Copia desenfocada generada: key={}, blurredKey={}", objectKey, blurredKey);
        return blurredKey;
    }

    private static String text(JsonNode json, String field) {
        JsonNode node = json == null ? null : json.get(field);
        return node == null || node.isNull() ? null : node.asString();
    }
}
