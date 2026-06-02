package com.docuinsight.docuinsight.service;

import com.docuinsight.docuinsight.model.*;
import com.docuinsight.docuinsight.repository.ReportRepository;
import com.docuinsight.docuinsight.repository.UploadedFileRepository;
import com.docuinsight.docuinsight.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ReportService {
    private static final Logger log= LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reportRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final UserRepository userRepository;
    private final TextExtractionService textExtractionService;
    private final LLMService llmService;
    private final PromptTemplateService promptTemplateService;


    public ReportService(ReportRepository reportRepository, UploadedFileRepository uploadedFileRepository, UserRepository userRepository, TextExtractionService textExtractionService, LLMService llmService, PromptTemplateService promptTemplateService) {
        this.reportRepository = reportRepository;
        this.uploadedFileRepository = uploadedFileRepository;
        this.userRepository = userRepository;
        this.textExtractionService = textExtractionService;
        this.llmService = llmService;
        this.promptTemplateService = promptTemplateService;
    }

    @Transactional
    public ReportResponse generateReport(Long fieldId, ReportRequest request,String email) {

        //validate custom Prompt
        if (request.getReportType() == Report.ReportType.CUSTOM && (request.getCustomPrompt() == null || request.getCustomPrompt().isBlank())) {
            throw new IllegalArgumentException(
                    "custom prompt is required when reportType is CUSTOM"
            );
        }

        //load User
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User with email not found: " + email));

        //load file and verify ownership
        UploadedFile file = uploadedFileRepository.findById(fieldId)
                .orElseThrow(() -> new RuntimeException("File not found with ID: " + fieldId));

        if (!file.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Access denied: you do not own this file.");
        }

        // Return cached report if available (not for CUSTOM)
        if (!request.isForceRegenerate() && request.getReportType() != Report.ReportType.CUSTOM) {
            Optional<Report> cached = reportRepository
                    .findByUploadedFileIdAndReportTypeAndStatus(
                            file.getId(), request.getReportType(), Report.ReportStatus.COMPLETED);

            if (cached.isPresent()) {
                log.info("Returning cached report ID={} for file ID={}", cached.get().getId(), file.getId());
                return ReportResponse.from(cached.get(), true);
            }
        }

        //Extract Text
        ExtractionResponse extractText;
        try {
            extractText = textExtractionService.extractText(file.getId(), user.getEmail());
        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not extract text from file: " + e.getMessage(), e);
        }

        String extractedText = extractText.getExtractedText();

        if (extractedText == null || extractedText.isBlank()) {
            throw new RuntimeException("The file contains no extractable text. Cannot generate a report.");
        }

        //buildPrompt
        String prompt = promptTemplateService.buildPrompt(request.getReportType(), request.getCustomPrompt(), file.getFileName());

        // Save report as PROCESSING
        Report report = new Report();
        report.setUploadedFile(file);
        report.setUser(user);
        report.setReportType(request.getReportType());
        report.setStatus(Report.ReportStatus.PROCESSING);
        report.setCustomPrompt(report.getCustomPrompt());
        report = reportRepository.save(report);

        //Call Gemini
        try {
            String reportContent = llmService.generateReport(extractedText, prompt);
            report.setReportContent(reportContent);
            report.setStatus(Report.ReportStatus.COMPLETED);
            report.setCompletedAt(LocalDateTime.now());
            report = reportRepository.save(report);
            log.info("Report generated successfully. Report ID={}", report.getId());
            return ReportResponse.from(report, false);
        } catch (Exception e) {
            log.error("Gemini generation failed for report ID={}", report.getId(), e);
            report.setStatus(Report.ReportStatus.FAILED);
            report.setErrorMessage(e.getMessage() != null ?
                    e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "Unknown error");
            report.setCompletedAt(LocalDateTime.now());
            reportRepository.save(report);
            throw new RuntimeException("Report generation failed: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> getUserReports(String email)
    {
        User user=userRepository.findByEmail(email)
                .orElseThrow(()->new RuntimeException("User with email not found: " + email));

        return reportRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(ReportResponse::from)
                .collect(Collectors.toList());
    }
    @Transactional(readOnly = true)
    public List<ReportResponse> getReportsForFile(Long fileId, String email)
    {
        User user=userRepository.findByEmail(email)
                .orElseThrow(()->new RuntimeException("User with email not found: " + email));

        UploadedFile file=uploadedFileRepository.findById(fileId)
                .orElseThrow(()-> new RuntimeException("File not found with ID: " + fileId));

        if(!file.getUser().getId().equals(user.getId())){
            throw new SecurityException("Access denied: you do not own this file.");
        }
        return reportRepository.findByUploadedFileIdOrderByCreatedAtDesc(fileId)
                .stream()
                .map(ReportResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(Long reportId, String email)
    {
        User user=userRepository.findByEmail(email)
                .orElseThrow(()-> new RuntimeException("\"User with email not found: \" + email"));

        Report report=reportRepository.findByIdAndUserId(reportId,user.getId())
                .orElseThrow(()->new RuntimeException("Report not found with ID: " + reportId));

        return ReportResponse.from(report);
    }

    @Transactional(readOnly = true)
    public void deleteReport(Long reportId,String email)
    {
        User user=userRepository.findByEmail(email)
                .orElseThrow(()-> new RuntimeException("\"User with email not found: \" + email"));

        Report report=reportRepository.findByIdAndUserId(reportId,user.getId())
                .orElseThrow(()->new RuntimeException("Report not found with ID: " + reportId));

        reportRepository.delete(report);
        log.info("Report ID={} deleted by user '{}'", reportId, email);

    }

}
