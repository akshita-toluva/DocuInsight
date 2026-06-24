package com.docuinsight.docuinsight.controller;

import com.docuinsight.docuinsight.model.MultiDocumentReport;
import com.docuinsight.docuinsight.model.MultiDocumentReportRequest;
import com.docuinsight.docuinsight.model.MultiDocumentReportResponse;
import com.docuinsight.docuinsight.service.MultiDocumentReportService;
import com.docuinsight.docuinsight.service.ReportExportService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/multi-reports")
public class MultiDocumentReportController{

    private static final Logger log= LoggerFactory.getLogger(MultiDocumentReportController.class);
    private final MultiDocumentReportService multiDocumentReportService;
    private final ReportExportService reportExportService;


    public MultiDocumentReportController(MultiDocumentReportService multiDocumentReportService,ReportExportService reportExportService)
    {
        this.multiDocumentReportService=multiDocumentReportService;
        this.reportExportService=reportExportService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateMultiReport(@Valid @RequestBody MultiDocumentReportRequest request, Authentication authentication) {
        try {
            MultiDocumentReportResponse response = multiDocumentReportService.generateMultiReport(request, authentication.getName());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", e.getMessage()));
            }
            log.error("Multi-report error for user={}", authentication.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/my-reports")
    public ResponseEntity<?> getMyMultiReports(Authentication authentication)
    {
        try{
            List<MultiDocumentReportResponse> reports=multiDocumentReportService.getMyMultiReports(authentication.getName());
            return ResponseEntity.ok(
                    Map.of("reports",reports,"count",reports.size())
            );
        }catch (Exception e)
        {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body((Map.of("error",e.getMessage())));
        }
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<?> getMultiReport(@PathVariable Long reportId,Authentication authentication)
    {
        try{
            MultiDocumentReportResponse report=multiDocumentReportService.getMultiReport(reportId, authentication.getName());
            return ResponseEntity.ok(report);
        }catch (RuntimeException e)
        {
            if(e.getMessage()!=null && e.getMessage().contains("not found"))
            {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error",e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body((Map.of("error",e.getMessage())));
        }
    }

    @GetMapping("/{reportId}/export")
    public ResponseEntity<?> exportMultiReport(
            @PathVariable Long reportId,
            @RequestParam(defaultValue = "pdf") String format,
            Authentication authentication) {
        try {
            byte[] fileBytes = reportExportService.exportMultiReport(
                    reportId, format, authentication.getName());

            HttpHeaders headers = new HttpHeaders();
            if ("docx".equalsIgnoreCase(format)) {
                headers.setContentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
                headers.setContentDispositionFormData("attachment",
                        "multi-report-" + reportId + ".docx");
            } else {
                headers.setContentType(MediaType.APPLICATION_PDF);
                headers.setContentDispositionFormData("attachment",
                        "multi-report-" + reportId + ".pdf");
            }
            headers.setContentLength(fileBytes.length);
            return ResponseEntity.ok().headers(headers).body(fileBytes);

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Export failed: " + e.getMessage()));
        }
    }
}

