package com.cloud.app.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

/**
 * Solo prod: el Postgres real esta en una IP privada de la VPC (10.0.0.85), inalcanzable
 * fuera del EC2, asi que en dev no tiene sentido traer estas credenciales igual - dev usa
 * un Postgres local via spring.datasource.* (application-dev.properties).
 */
@Configuration
@Profile("prod")
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(SecretsManagerClient secretsManagerClient,
                                  ObjectMapper objectMapper,
                                  @Value("${app.secrets.db-secret-id}") String secretId) {
        String secretJson = secretsManagerClient.getSecretValue(
                GetSecretValueRequest.builder().secretId(secretId).build()).secretString();

        JsonNode secret = objectMapper.readTree(secretJson);
        String host = secret.get("host").asString();
        String port = secret.get("port").asString();
        String dbname = secret.get("dbname").asString();
        String username = secret.get("username").asString();
        String password = secret.get("password").asString();

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://%s:%s/%s".formatted(host, port, dbname));
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }
}
