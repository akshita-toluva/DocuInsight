package com.docuinsight.docuinsight.controller;

import com.docuinsight.docuinsight.model.FileUploadResponse;
import com.docuinsight.docuinsight.model.UploadedFile;
import com.docuinsight.docuinsight.service.FileService;
import io.jsonwebtoken.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FileController {
    private final FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file")MultipartFile file,
            @AuthenticationPrincipal String email) throws IOException, java.io.IOException {
        FileUploadResponse response=fileService.uploadFile(file,email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-files")
    public ResponseEntity<List<UploadedFile>> getMyFiles(
            @AuthenticationPrincipal String email) {
        List<UploadedFile> files = fileService.getUserFiles(email);
        return ResponseEntity.ok(files);
    }

}
