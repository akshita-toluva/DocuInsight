package com.docuinsight.docuinsight.service;

import com.docuinsight.docuinsight.model.*;
import com.docuinsight.docuinsight.repository.ReportRepository;
import com.docuinsight.docuinsight.repository.UploadedFileRepository;
import com.docuinsight.docuinsight.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final RetrievalService retrievalService;
    private static final String REFUSAL = "I couldn't find information about this in the document.";
    private static final Pattern CITATION = Pattern.compile("[\\[【](\\d+)(?:†[^\\]】]*)?[\\]】]");
    private final QuotaService quotaService;

    public ReportService(ReportRepository reportRepository, UploadedFileRepository uploadedFileRepository,
                         UserRepository userRepository, TextExtractionService textExtractionService,
                         LLMService llmService, PromptTemplateService promptTemplateService,
                         RetrievalService retrievalService , QuotaService quotaService) {
        this.reportRepository = reportRepository;
        this.uploadedFileRepository = uploadedFileRepository;
        this.userRepository = userRepository;
        this.textExtractionService = textExtractionService;
        this.llmService = llmService;
        this.promptTemplateService = promptTemplateService;
        this.retrievalService=retrievalService;
        this.quotaService=quotaService;
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

        //only reports that really call the AI count (cached ones above are free)
        quotaService.consume(user.getId(), QuotaService.Action.REPORT);

        // Save report as PROCESSING
        Report report = new Report();
        report.setUploadedFile(file);
        report.setUser(user);
        report.setReportType(request.getReportType());
        report.setStatus(Report.ReportStatus.PROCESSING);
        report.setCustomPrompt(request.getCustomPrompt());
        report = reportRepository.save(report);

        //Call llm
        try {
            String reportContent = llmService.generateReport(extractedText, prompt);
            report.setReportContent(reportContent);
            report.setStatus(Report.ReportStatus.COMPLETED);
            report.setCompletedAt(LocalDateTime.now());
            report = reportRepository.save(report);
            log.info("Report generated successfully. Report ID={}", report.getId());
            return ReportResponse.from(report, false);
        } catch (Exception e) {
            quotaService.refund(user.getId(), QuotaService.Action.REPORT);
            log.error("LLM generation failed for report ID={}", report.getId(), e);
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
                .orElseThrow(()-> new RuntimeException("User with email not found: " + email));

        Report report=reportRepository.findByIdAndUserId(reportId,user.getId())
                .orElseThrow(()->new RuntimeException("Report not found with ID: " + reportId));

        return ReportResponse.from(report);
    }

    @Transactional
    public void deleteReport(Long reportId,String email)
    {
        User user=userRepository.findByEmail(email)
                .orElseThrow(()-> new RuntimeException("User with email not found: " + email));

        Report report=reportRepository.findByIdAndUserId(reportId,user.getId())
                .orElseThrow(()->new RuntimeException("Report not found with ID: " + reportId));

        reportRepository.delete(report);
        log.info("Report ID={} deleted by user '{}'", reportId, email);

    }

    @Transactional(readOnly = true)
    public AskQuestionResponse askQuestion(Long reportId,String question,String email)
    {
        //Load and verify user
        User user=userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(
                        "User not found: " + email
                ));
        //Load report and verify ownership
        Report report=reportRepository.findByIdAndUserId(reportId,user.getId())
                .orElseThrow(()->new RuntimeException(
                        "Report not found with ID: " + reportId
                ));
        //Only completed reports have usable content
        if(report.getStatus()!=Report.ReportStatus.COMPLETED)
        {
            throw new IllegalStateException(
                    "You can ask questions only on completed reports. " +
                            "Current status:  " + report.getStatus()
            );
        }

        quotaService.consume(user.getId(), QuotaService.Action.QUESTION);

        RetrievalService.RetrievalResult retrieval = retrievalService.retrieve(
                report.getUploadedFile(), user.getId(), question);

        String answer = REFUSAL;
        List<SourcePassage> sources = List.of();

        if (!retrieval.passages().isEmpty()) {
            String raw;
            try {
                raw = llmService.answerQuestion(retrieval.context(), question);
            } catch (RuntimeException e) {
                quotaService.refund(user.getId(), QuotaService.Action.QUESTION);
                throw e;
            }
            if (!raw.strip().startsWith(LLMService.NOT_IN_DOCUMENT)) {
                answer = CITATION.matcher(raw).replaceAll("[$1]");   // every style becomes [n]
                sources = citedSources(raw, retrieval.passages());
            }
        }

        return new AskQuestionResponse(
                reportId,
                report.getUploadedFile().getFileName(),
                question,
                answer,
                sources,
                java.time.LocalDateTime.now()
        );
    }

    private List<SourcePassage> citedSources(String answer, List<SourcePassage> passages) {
        Set<Integer> cited = new LinkedHashSet<>();
        Matcher m = CITATION.matcher(answer);
        while (m.find()) {
            cited.add(Integer.parseInt(m.group(1)));
        }
        List<SourcePassage> result = passages.stream()
                .filter(p -> cited.contains(p.number()))
                .toList();
        if (result.isEmpty()) {
            log.warn("Answer cited no valid passage numbers; cited={}", cited);
            // Large doc: all retrieved passages (≤5) are still honest evidence. Small doc: too many to be useful.
            return passages.size() <= RetrievalService.TOP_K ? passages : List.of();
        }
        return result;
    }

}
