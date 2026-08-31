package com.tamojit.nasorchestrator.service;

import com.tamojit.nasorchestrator.client.SmbFileClient;
import com.tamojit.nasorchestrator.dto.FileListResponse;
import com.tamojit.nasorchestrator.dto.FileUploadResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
public class FileService {
    private final SmbFileClient smbFileClient;

    public FileService(SmbFileClient smbFileClient) {
        this.smbFileClient = smbFileClient;
    }

    public FileUploadResponse upload(String path, MultipartFile file) throws IOException {
        smbFileClient.upload(path, file);
        String dirPath = path.endsWith("/") ? path : path + "/";
        String storedPath = dirPath + file.getOriginalFilename();

        return new FileUploadResponse(
            storedPath,
            file.getSize(),
            "uploaded"
        );
    }

    public void download(String path, HttpServletResponse response) throws IOException {
        smbFileClient.download(path, response);
    }

    public InputStream preview(String path) throws IOException {
        return smbFileClient.preview(path);
    }

    public FileListResponse list(String path) throws IOException {
        return new FileListResponse(
            path,
            smbFileClient.list(path)
        );
    }

    public void delete(String path) throws IOException {
        smbFileClient.delete(path);
    }
}
