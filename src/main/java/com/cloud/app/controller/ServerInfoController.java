package com.cloud.app.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ServerInfoController {

    private final String serverName;

    public ServerInfoController(@Value("${app.server.name}") String serverName) {
        this.serverName = serverName;
    }

    @GetMapping("/api/server")
    public Map<String, String> serverInfo() {
        return Map.of("server", serverName);
    }
}
