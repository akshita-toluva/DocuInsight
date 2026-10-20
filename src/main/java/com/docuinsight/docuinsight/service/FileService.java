package com.docuinsight.docuinsight.service;

import com.docuinsight.docuinsight.model.FileSummaryResponse;
import com.docuinsight.docuinsight.model.FileUploadResponse;
import com.docuinsight.docuinsight.model.UploadedFile;
import com.docuinsight.docuinsight.model.User;
import com.docuinsight.docuinsight.repository.UploadedFileRepository;
import com.docuinsight.docuinsight.repository.UserRepository;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FileService {
    private final UploadedFileRepository fileRepository;
    private final UserRepository userRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public FileUploadResponse uploadFile(MultipartFile file,String email) throws IOException, java.io.IOException {
        //step 1 Validate FileType
        String contentType=file.getContentType();
        Set<String> allowedTypes= Set.of(
                "application/pdf",
                "text/csv",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain",
                "image/png",
                "image/jpeg",
                "image/webp"
        );
       // if(contentType==null || (!contentType.equals("application/pdf")) && (!contentType.equals("text/csv")))
        if(contentType==null || !allowedTypes.contains(contentType))
        {
            throw new IllegalArgumentException("Unsupported file type: " + contentType + ". Only PDF, DOCX, TXT, CSV and image (PNG, JPG, WEBP) files are allowed.");
        }

        byte[] header = file.getInputStream().readNBytes(12);
        if (!matchesSignature(contentType, header))
        {
            throw new IllegalArgumentException("File content does not match declared type: " + contentType);
        }

        //step 2 Find User
        User user=userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        //step 3 Create unique fileName
        String originalFileName=file.getOriginalFilename();
        String safeName = Paths.get(originalFileName).getFileName().toString();
        String uniqueFileName=user.getId() + "_"
                + System.currentTimeMillis()
                + "_" + safeName;

        //step 4 Create uploads folder if it does not exists
        Path uploadPath= Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        //Files.createDirectories(uploadPath);

        //step 5 save file to disk
        Path filePath=uploadPath.resolve(uniqueFileName).normalize();
        if(!filePath.startsWith(uploadPath.normalize()))
        {
            throw new IllegalArgumentException("Invalid file name");
        }
        Files.copy(file.getInputStream(),filePath, StandardCopyOption.REPLACE_EXISTING);

        //step 6 save file metadata to database
        UploadedFile uploadedFile = UploadedFile.builder()
                .user(user)
                .fileName(originalFileName)
                .fileType(contentType)
                .filePath(filePath.toString())
                .fileSize(file.getSize())
                .build();

        UploadedFile saved=fileRepository.save(uploadedFile);

        //step 7 return response
        return new FileUploadResponse(
                saved.getId(),
                saved.getFileName(),
                saved.getFileType(),
                saved.getFileSize(),
                "File uploaded successfully"
        );
    }

    public List<FileSummaryResponse> getUserFiles(String email)
    {
        User user=userRepository.findByEmail(email)
                .orElseThrow(()->
                        new RuntimeException("User not found"));
        return fileRepository.findByUserIdOrderByUploadedAtDesc(user.getId())
                .stream()
                .map(FileSummaryResponse::from)
                .toList();
    }

    private boolean matchesSignature(String contentType, byte[] header) {
        switch (contentType) {
            case "application/pdf":
                return header.length >= 4
                        && (header[0] & 0xFF) == 0x25 && (header[1] & 0xFF) == 0x50
                        && (header[2] & 0xFF) == 0x44 && (header[3] & 0xFF) == 0x46; // %PDF
            case "image/png":
                return header.length >= 8
                        && (header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50
                        && (header[2] & 0xFF) == 0x4E && (header[3] & 0xFF) == 0x47;
            case "image/jpeg":
                return header.length >= 3
                        && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8
                        && (header[2] & 0xFF) == 0xFF;
            case "image/webp":
                return header.length >= 12
                        && header[0]=='R' && header[1]=='I' && header[2]=='F' && header[3]=='F'
                        && header[8]=='W' && header[9]=='E' && header[10]=='B' && header[11]=='P';
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                return header.length >= 4
                        && (header[0] & 0xFF) == 0x50 && (header[1] & 0xFF) == 0x4B
                        && (header[2] & 0xFF) == 0x03 && (header[3] & 0xFF) == 0x04; // ZIP/DOCX
            case "text/csv":
            case "text/plain":
                return true; // no reliable magic bytes for plain text
            default:
                return false;
        }
    }
    
}
