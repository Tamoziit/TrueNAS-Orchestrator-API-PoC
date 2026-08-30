package com.tamojit.nasorchestrator.service;

import com.tamojit.nasorchestrator.client.SmbFileClient;
import com.tamojit.nasorchestrator.dto.FileListResponse;
import com.tamojit.nasorchestrator.dto.FileUploadResponse;
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
        return new FileUploadResponse(
            path,
            file.getSize(),
            "uploaded"
        );
    }

    public InputStream download(String path) throws IOException {
        return smbFileClient.download(path);
    }

    public FileListResponse list(String path) throws IOException {
        return new FileListResponse(
            path,
            smbFileClient.list(path)
        );
    }
}
