package com.docuinsight.docuinsight.controller;
import com.docuinsight.docuinsight.model.ReportRequest;
import com.docuinsight.docuinsight.model.ReportResponse;
import com.docuinsight.docuinsight.service.ReportService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    public static final Logger log= LoggerFactory.getLogger(ReportController.class);

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/generate/{fileId}")
    public ResponseEntity<?> generateReport(
            @PathVariable Long fileId,
            @Valid @RequestBody ReportRequest request,
            Authentication authentication)
    {
        try{
            ReportResponse response=reportService.generateReport(fileId,request, authentication.getName());
            return ResponseEntity.ok(response);
        }catch (IllegalArgumentException e){
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        }catch (RuntimeException e) {
            String message = e.getMessage() != null ? e.getMessage() : "Report generation failed.";
            if (message.contains("rate limit")) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error", message));
            }
            if (message.contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", message));
            }
            log.error("Report generation error: fileId={}, user={}",
                    fileId, authentication.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", message));
        }
    }

    @GetMapping("/my-reports")
    public ResponseEntity<?> getMyReports(Authentication authentication){
        try{
            List<ReportResponse> reports=reportService.getUserReports(authentication.getName());
            return ResponseEntity.ok(Map.of("reports", reports, "count", reports.size()));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/file/{fileId}")
    public ResponseEntity<?> getReportsForFile(
            @PathVariable Long fileId,
            Authentication authentication){
        try {
            List<ReportResponse> reports = reportService.getReportsForFile(
                    fileId, authentication.getName());
            return ResponseEntity.ok(Map.of("reports", reports, "count", reports.size()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<?> getReport(
            @PathVariable Long reportId,
            Authentication authentication) {
        try {
            ReportResponse report = reportService.getReport(reportId, authentication.getName());
            return ResponseEntity.ok(report);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{reportId}")
    public ResponseEntity<?> deleteReport(
            @PathVariable Long reportId,
            Authentication authentication) {
        try {
            reportService.deleteReport(reportId, authentication.getName());
            return ResponseEntity.ok(Map.of("message", "Report deleted successfully."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
