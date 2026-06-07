package com.docuinsight.docuinsight.service;

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
        if(contentType==null || (!contentType.equals("application/pdf")) && (!contentType.equals("text/csv")))
        {
            throw new IllegalArgumentException("Unsupported file type: " + contentType + ". Only PDF and CSV files are allowed.");
        }

        //step 2 Find User
        User user=userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        //step 3 Create unique fileName
        String originalFileName=file.getOriginalFilename();
        String uniqueFileName=user.getId() + "_"
                + System.currentTimeMillis()
                + "_" + originalFileName;

        //step 4 Create uploads folder if it does not exists
        Path uploadPath= Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        //Files.createDirectories(uploadPath);

        //step 5 save file to disk
        Path filePath=uploadPath.resolve(uniqueFileName);
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

    public List<UploadedFile> getUserFiles(String email)
    {
        User user=userRepository.findByEmail(email)
                .orElseThrow(()->
                        new RuntimeException("User not found"));
        return fileRepository.findByUserId(user.getId());
    }
    
}
