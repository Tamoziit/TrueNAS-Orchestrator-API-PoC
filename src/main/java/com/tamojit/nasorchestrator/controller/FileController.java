package com.tamojit.nasorchestrator.controller;

import com.tamojit.nasorchestrator.dto.FileListResponse;
import com.tamojit.nasorchestrator.dto.FileUploadResponse;
import com.tamojit.nasorchestrator.service.FileService;
import com.tamojit.nasorchestrator.util.MimeTypeResolver;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/v1/nas-orchestrator/files")
@Validated
public class FileController {
    private static final String NO_TRAVERSAL_REGEX = "^(?!.*\\.\\.).*$";
    private static final String NO_TRAVERSAL_MSG = "Path cannot contain '..' segments";
    private static final String NO_BACKSLASH_REGEX = "^[^\\\\]*$";
    private static final String NO_BACKSLASH_MSG = "Path cannot contain backslashes";

    private final FileService fileService;
    private final MimeTypeResolver mimeTypeResolver;

    public FileController(FileService fileService, MimeTypeResolver mimeTypeResolver) {
        this.fileService = fileService;
        this.mimeTypeResolver = mimeTypeResolver;
    }

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> upload(
        @RequestParam("path")
        @NotBlank(message = "Path is required")
        @Pattern(regexp = NO_TRAVERSAL_REGEX, message = NO_TRAVERSAL_MSG)
        @Pattern(regexp = NO_BACKSLASH_REGEX, message = NO_BACKSLASH_MSG)
        String path,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        return ResponseEntity.ok(fileService.upload(path, file));
    }

    @GetMapping("/download")
    public void download(
        @RequestParam("path")
        @NotBlank(message = "Path is required")
        @Pattern(regexp = NO_TRAVERSAL_REGEX, message = NO_TRAVERSAL_MSG)
        @Pattern(regexp = NO_BACKSLASH_REGEX, message = NO_BACKSLASH_MSG)
        String path,
        HttpServletResponse response
    ) throws IOException {
//        InputStream inputStream = fileService.download(path);
//        String filename = path.substring(path.lastIndexOf('/') + 1);
//
//        return ResponseEntity.ok()
//            .contentType(MediaType.APPLICATION_OCTET_STREAM)
//            .header(HttpHeaders.CONTENT_DISPOSITION,
//                ContentDisposition.attachment().filename(filename).build().toString()) // download to local file path
//            .body(new InputStreamResource(inputStream));
        fileService.download(path, response);
    }

    @GetMapping("/preview")
    public ResponseEntity<InputStreamResource> preview(
        @RequestParam("path")
        @NotBlank(message = "Path is required")
        @Pattern(regexp = NO_TRAVERSAL_REGEX, message = NO_TRAVERSAL_MSG)
        @Pattern(regexp = NO_BACKSLASH_REGEX, message = NO_BACKSLASH_MSG)
        String path
    ) throws IOException {
        String filename = path.substring(path.lastIndexOf('/') + 1);

        if (!mimeTypeResolver.isPreviewable(filename)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .build();
        }

        InputStream inputStream = fileService.preview(path);
        MediaType mediaType = mimeTypeResolver.resolve(filename);

        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(filename).build().toString()) // browser inline/tab render without download
            .body(new InputStreamResource(inputStream));
    }

    @GetMapping("/list")
    public ResponseEntity<FileListResponse> list(
        @RequestParam(value = "path", defaultValue = "")
        @Pattern(regexp = NO_TRAVERSAL_REGEX, message = NO_TRAVERSAL_MSG)
        @Pattern(regexp = NO_BACKSLASH_REGEX, message = NO_BACKSLASH_MSG)
        String path
    ) throws IOException {
        return ResponseEntity.ok(fileService.list(path));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> delete(
        @RequestParam("path")
        @NotBlank(message = "Path is required")
        @Pattern(regexp = NO_TRAVERSAL_REGEX, message = NO_TRAVERSAL_MSG)
        @Pattern(regexp = NO_BACKSLASH_REGEX, message = NO_BACKSLASH_MSG)
        String path
    ) throws IOException {
        fileService.delete(path);
        return ResponseEntity.noContent().build();
    }
}
