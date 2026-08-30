package com.tamojit.nasorchestrator.client;

import com.tamojit.nasorchestrator.dto.FileEntry;
import jcifs.CIFSContext;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class SmbFileClient {
    private final CIFSContext cifsContext;
    private final String shareBaseUrl;

    public SmbFileClient(
        CIFSContext cifsContext,
        @Value("${smb.share-base-url}") String shareBaseUrl
    ) {
        this.cifsContext = cifsContext;
        this.shareBaseUrl = shareBaseUrl.endsWith("/") ? shareBaseUrl : shareBaseUrl + "/";
    }

    private SmbFile resolve(String relativePath) throws IOException {
        if (relativePath.contains("..")) {
            throw new IllegalArgumentException("Path traversal not allowed: " + relativePath);
        }

        String cleanPath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        return new SmbFile(shareBaseUrl + cleanPath, cifsContext);
    }

    public void upload(String relativePath, MultipartFile file) throws IOException {
        SmbFile target = resolve(relativePath);

        try (SmbFile parentDir = new SmbFile(target.getParent(), cifsContext)) {
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
        }

        try (InputStream inputStream = file.getInputStream();
             OutputStream outputStream = new SmbFileOutputStream(target)) {
            inputStream.transferTo(outputStream);
        }
    }

    public InputStream download(String relativePath) throws IOException {
        SmbFile target = resolve(relativePath);

        if (!target.exists()) {
            target.close();
            throw new FileNotFoundException("Not found on NAS: " + relativePath);
        }

        return new SmbFileInputStream(target);
    }

    public List<FileEntry> list(String relativePath) throws IOException {
        String dirPath = relativePath.isEmpty() || relativePath.endsWith("/")
            ? relativePath
            : relativePath + "/";

        List<FileEntry> entries = new ArrayList<>();

        try (SmbFile dir = resolve(dirPath)) {
            for (SmbFile file : dir.listFiles()) {
                try (file) {
                    entries.add(new FileEntry(
                        file.getName(),
                        file.length(),
                        file.isDirectory(),
                        file.lastModified()
                    ));
                }
            }
        }

        return entries;
    }
}
