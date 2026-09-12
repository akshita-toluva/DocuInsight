package com.docuinsight.docuinsight.service;

import com.docuinsight.docuinsight.model.*;
import com.docuinsight.docuinsight.repository.MultiDocumentReportRepository;
import com.docuinsight.docuinsight.repository.UploadedFileRepository;
import com.docuinsight.docuinsight.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MultiDocumentReportService {

    private static final Logger log= LoggerFactory.getLogger(MultiDocumentReportService.class);

    private final MultiDocumentReportRepository multiDocumentReportRepository;
    private final UserRepository userRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final TextExtractionService textExtractionService;
    private final LLMService llmService;
    private final PromptTemplateService promptTemplateService;

    public MultiDocumentReportService(MultiDocumentReportRepository multiDocumentReportRepository, UserRepository userRepository, UploadedFileRepository uploadedFileRepository, TextExtractionService textExtractionService, LLMService llmService, PromptTemplateService promptTemplateService) {
        this.multiDocumentReportRepository = multiDocumentReportRepository;
        this.userRepository = userRepository;
        this.uploadedFileRepository = uploadedFileRepository;
        this.textExtractionService = textExtractionService;
        this.llmService = llmService;
        this.promptTemplateService = promptTemplateService;
    }

    @Transactional
    public MultiDocumentReportResponse generateMultiReport(MultiDocumentReportRequest request,String email){
        //step 1 : Validate customer prompt
        if(request.getReportType() == MultiDocumentReport.ReportType.CUSTOM  && (request.getCustomPrompt()==null || request.getCustomPrompt().isBlank()))
        {
            throw new IllegalArgumentException(
                    "Custom Prompt is required when report type is CUSTOM"
            );
        }

        //step 2 : Load User
        User user=userRepository.findByEmail(email)
                .orElseThrow(()->
                        new RuntimeException("User not found: " + email));

        //step 3 : Load files and verify ownership of each
        List<UploadedFile> files = new ArrayList<>();
        for (Long fileId : request.getFileIds()) {
                UploadedFile file = uploadedFileRepository.findById(fileId)
                    .orElseThrow(() ->
                            new RuntimeException("File not found with ID: " + fileId));
            if (!file.getUser().getId().equals(user.getId())) {
                throw new SecurityException(
                        "Access denied: you do not own file ID " + fileId);
            }
            files.add(file);
        }

        //step 4 : Extract text from each file and combine
        StringBuilder combinedText=new StringBuilder();
        for(UploadedFile file : files)
        {
            combinedText.append("===Document: ")
                    .append(file.getFileName())
                    .append(" ===\n");

            try{
                ExtractionResponse extractionResponse=textExtractionService.extractText(file.getId(), user.getEmail());
                String text=extractionResponse.getExtractedText();
                if(text == null || text.isBlank())
                {
                    combinedText.append("[No text extracted]\n\n");
                }
                else
                {
                    combinedText.append(text.trim()).append("\n\n");
                }
            } catch (Exception e) {
                combinedText.append("[Extraction failed: ")
                        .append(e.getMessage()).append("]\n\n");
            }
        }

        //step 5 : Build multi_document prompt
        List<String> fileNames=files.stream()
                .map(UploadedFile :: getFileName)
                .collect(Collectors.toList());

        String prompt=promptTemplateService.buildMultiDocumentPrompt(
                request.getReportType(),
                request.getCustomPrompt(),
                fileNames);

        //step 6 : Save as processing
        MultiDocumentReport report=new MultiDocumentReport();
        report.setUser(user);
        report.setFiles(files);
        report.setReportType(request.getReportType());
        report.setStatus(MultiDocumentReport.ReportStatus.PROCESSING);
        report.setCustomPrompt(request.getCustomPrompt());
        report = multiDocumentReportRepository.save(report);

        // Step 7: Call LLM
        try {
            String content = llmService.generateReport(
                    combinedText.toString(), prompt);
            report.setReportContent(content);
            report.setStatus(MultiDocumentReport.ReportStatus.COMPLETED);
            report.setCompletedAt(LocalDateTime.now());
            report = multiDocumentReportRepository.save(report);
            log.info("Multi-doc report ID={} generated for {} files",
                    report.getId(), files.size());
            return MultiDocumentReportResponse.from(report);
        } catch (Exception e) {
            log.error("Multi-doc report failed ID={}", report.getId(), e);
            report.setStatus(MultiDocumentReport.ReportStatus.FAILED);
            report.setErrorMessage(e.getMessage() != null
                    ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500))
                    : "Unknown error");
            report.setCompletedAt(LocalDateTime.now());
            multiDocumentReportRepository.save(report);
            throw new RuntimeException(
                    "Multi-document report failed: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<MultiDocumentReportResponse> getMyMultiReports(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found: " + email));
        return multiDocumentReportRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(MultiDocumentReportResponse::from)
                .collect(Collectors.toList());
    }
    @Transactional(readOnly = true)
    public MultiDocumentReportResponse getMultiReport(
            Long reportId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found: " + email));
        MultiDocumentReport report = multiDocumentReportRepository
                .findByIdAndUserId(reportId, user.getId())
                .orElseThrow(() -> new RuntimeException(
                        "Multi-document report not found with ID: " + reportId));
        return MultiDocumentReportResponse.from(report);
    }
}
