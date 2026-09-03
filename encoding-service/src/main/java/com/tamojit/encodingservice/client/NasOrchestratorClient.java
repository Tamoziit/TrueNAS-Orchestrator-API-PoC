package com.tamojit.encodingservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class NasOrchestratorClient {
    private final RestClient restClient;

    public NasOrchestratorClient(@Value("${nas.orchestrator.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void downloadToFile(String relativePath, Path destination) throws IOException {
        byte[] bytes = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v1/nas-orchestrator/files/preview")
                .queryParam("path", relativePath)
                .build())
            .retrieve()
            .body(byte[].class);

        if (bytes == null) {
            throw new IOException("Failed to download file: Received empty/null body from server for path " + relativePath);
        }

        Files.write(destination, bytes);
    }

    public void uploadFolder(String nasBasePath, File localDir) throws IOException {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("path", nasBasePath);
        collectFiles(localDir, localDir, body);

        restClient.post()
            .uri("/api/v1/nas-orchestrator/files/upload/folder")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }

    private void collectFiles(File root, File dir, MultiValueMap<String, Object> body) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                collectFiles(root, file, body);
                continue;
            }

            String relative = root.toPath()
                .relativize(file.toPath())
                .toString()
                .replace("\\", "/");

            body.add("relativePaths", relative);
            body.add("files", new FileSystemResource(file));
        }
    }
}
