package com.cloud.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;

import java.time.Duration;

@Configuration
public class LambdaConfig {

    /**
     * Tope para la invocacion sincrona: debe ser mayor que el timeout configurado en la funcion
     * Lambda, para que un desenfoque lento falle en Lambda (respuesta controlada) y no aqui.
     */
    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(60);

    @Bean
    public LambdaClient lambdaClient(AwsCredentialsProvider credentialsProvider,
                                     @Value("${app.lambda.region}") String region) {
        return LambdaClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .overrideConfiguration(c -> c.apiCallTimeout(API_CALL_TIMEOUT))
                .build();
    }
}
