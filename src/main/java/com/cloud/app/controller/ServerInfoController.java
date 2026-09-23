package com.cloud.app.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@RestController
public class ServerInfoController {

    private static final Logger log = LoggerFactory.getLogger(ServerInfoController.class);

    private static final URI TOKEN_URI = URI.create("http://169.254.169.254/latest/api/token");
    private static final URI LOCAL_IPV4_URI = URI.create("http://169.254.169.254/latest/meta-data/local-ipv4");
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final String serverName;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    // La IP privada de la EC2 no cambia durante la vida de la instancia: se
    // captura una sola vez (primer request) y se cachea.
    private volatile String cachedIp;

    public ServerInfoController(@Value("${app.server.name}") String serverName) {
        this.serverName = serverName;
    }

    @GetMapping("/api/server")
    public Map<String, String> serverInfo() {
        return Map.of("server", serverName, "ip", resolvePrivateIp());
    }

    private String resolvePrivateIp() {
        String ip = cachedIp;
        if (ip != null) {
            return ip;
        }
        ip = fetchPrivateIpFromInstanceMetadata();
        cachedIp = ip;
        return ip;
    }

    private String fetchPrivateIpFromInstanceMetadata() {
        try {
            HttpRequest tokenRequest = HttpRequest.newBuilder(TOKEN_URI)
                    .timeout(TIMEOUT)
                    .header("X-aws-ec2-metadata-token-ttl-seconds", "21600")
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            String token = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString()).body();

            HttpRequest ipRequest = HttpRequest.newBuilder(LOCAL_IPV4_URI)
                    .timeout(TIMEOUT)
                    .header("X-aws-ec2-metadata-token", token)
                    .GET()
                    .build();
            return httpClient.send(ipRequest, HttpResponse.BodyHandlers.ofString()).body().trim();
        } catch (Exception e) {
            log.warn("No se pudo obtener la IP privada desde el metadata service de EC2 (¿no corre en EC2?): {}",
                    e.getMessage());
            return "unknown";
        }
    }
}
