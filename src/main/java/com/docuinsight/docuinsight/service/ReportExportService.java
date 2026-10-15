package com.docuinsight.docuinsight.service;

import com.docuinsight.docuinsight.model.MultiDocumentReport;
import com.docuinsight.docuinsight.model.Report;
import com.docuinsight.docuinsight.model.User;
import com.docuinsight.docuinsight.repository.MultiDocumentReportRepository;
import com.docuinsight.docuinsight.repository.ReportRepository;
import com.docuinsight.docuinsight.repository.UserRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportExportService {
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final MultiDocumentReportRepository multiDocumentReportRepository;
    private final MarkdownRenderer markdownRenderer;
    private final DocxReportWriter docxReportWriter;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    //entry_point called by controller
    @Transactional(readOnly = true)
    public byte[] exportReport(Long reportId, String format, String email) throws IOException {
        //load the loggedin user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        //load the report and verify ownership
        Report report = reportRepository.findByIdAndUserId(reportId, user.getId())
                .orElseThrow(() -> new RuntimeException(
                        "Report not found or access denied. ID: " + reportId));

        //Only completed reports can be exported
        if (report.getStatus() != Report.ReportStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Cannot export a report that is not COMPLETED. " +
                            "Current status of the report is: " + report.getStatus());
        }
        //Route to correct format
        if ("pdf".equalsIgnoreCase(format)) {
            return generatePdf(report);
        } else if ("docx".equalsIgnoreCase(format)) {
            return generateDocx(report);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported format: '" + format + "'. Use 'pdf' or 'docx'.");
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportMultiReport(Long reportId, String format, String email) throws IOException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        MultiDocumentReport report = multiDocumentReportRepository
                .findByIdAndUserId(reportId, user.getId())
                .orElseThrow(() -> new RuntimeException(
                        "MultiDocument report not found. ID: " + reportId));
        if (report.getStatus() != MultiDocumentReport.ReportStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Cannot export report that is not completed. " +
                            "Current status is: " + report.getStatus());
        }

        //Build file name labels for the header
        String fileNames = report.getFiles().stream()
                .map(f -> f.getFileName())
                .collect(Collectors.joining(", "));

        if ("pdf".equalsIgnoreCase(format)) {
            return generateMultiPdf(report, fileNames);
        } else if ("docx".equalsIgnoreCase(format)) {
            return generateMultiDocx(report, fileNames);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported format: '" + format + "'. Use 'pdf' or 'docx'.");
        }
    }

    // ---------------- PDF: Markdown -> HTML -> PDF ----------------

    private byte[] generatePdf(Report report) throws IOException {
        return renderPdf(
                typeLabel(report.getReportType().name()),
                report.getUploadedFile().getFileName(),
                "Generated " + formatDate(report.getCompletedAt()),
                report.getReportContent());
    }

    // Embed DejaVu Sans so characters like ‑ → ≥ print instead of '#'
    private void registerFonts(PdfRendererBuilder builder) {
        addFont(builder, "DejaVuSans.ttf", 400, FontStyle.NORMAL);
        addFont(builder, "DejaVuSans-Bold.ttf", 700, FontStyle.NORMAL);
        addFont(builder, "DejaVuSans-Oblique.ttf", 400, FontStyle.ITALIC);
        addFont(builder, "DejaVuSans-BoldOblique.ttf", 700, FontStyle.ITALIC);
    }

    private void addFont(PdfRendererBuilder builder, String file, int weight, FontStyle style) {
        String path = "/fonts/" + file;
        if (getClass().getResource(path) == null) {
            throw new IllegalStateException("Missing font file: src/main/resources" + path);
        }
        // subset = true: embed only the letters actually used -> small PDF
        builder.useFont(() -> getClass().getResourceAsStream(path), "DocuSans", weight, style, true);
    }

    private byte[] generateMultiPdf(MultiDocumentReport report, String fileNames) throws IOException {
        return renderPdf(
                "Multi-document · " + typeLabel(report.getReportType().name()),
                "Multi-Document Report",
                "Files: " + fileNames + "  ·  Generated " + formatDate(report.getCompletedAt()),
                report.getReportContent());
    }

    // One PDF pipeline for both report kinds
    private byte[] renderPdf(String typeLabel, String title, String meta, String markdown) throws IOException {
        String bodyHtml = markdownRenderer.toHtml(markdown);
        String html = PDF_TEMPLATE
                .replace("{{TYPE}}", escapeHtml(typeLabel))
                .replace("{{TITLE}}", escapeHtml(title))
                .replace("{{META}}", escapeHtml(meta))
                .replace("{{BODY}}", bodyHtml);   // already safe: commonmark escapes raw HTML

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        registerFonts(builder);        builder.withHtmlContent(html, null);
        builder.toStream(out);
        builder.run();
        return out.toByteArray();
    }

    private static final String PDF_TEMPLATE = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8" />
            <style>
              @page {
                size: A4;
                margin: 22mm 20mm 20mm 20mm;
                @bottom-center {
                  content: "DocuInsight  ·  Page " counter(page) " of " counter(pages);
                  font-family: "DocuSans",sans-serif; font-size: 8pt; color: #9ca3af;
                }
              }
              body { font-family: "DocuSans",sans-serif; font-size: 10.5pt; line-height: 1.55; color: #1f2937; }

              .header { border-top: 4px solid #1f4e79; border-bottom: 1px solid #e5e7eb;
                        padding: 10px 0 10px 0; margin-bottom: 18px; }
              .type { display: inline-block; background: #e8f0f8; color: #1f4e79; font-size: 8pt;
                      font-weight: bold; padding: 2px 8px; border-radius: 9px; text-transform: uppercase; }
              .title { font-size: 18pt; font-weight: bold; color: #111827; margin: 8px 0 4px 0; }
              .meta { font-size: 9pt; color: #6b7280; }

              h1, h2, h3, h4 { color: #1f4e79; page-break-after: avoid; }
              h1 { font-size: 15pt; margin: 18px 0 8px 0; }
              h2 { font-size: 13.5pt; margin: 18px 0 8px 0; padding-bottom: 3px; border-bottom: 1px solid #dbe4ee; }
              h3 { font-size: 11.5pt; margin: 14px 0 6px 0; }
              h4 { font-size: 10.5pt; margin: 12px 0 4px 0; }
              p { margin: 6px 0; }
              ul, ol { margin: 6px 0; padding-left: 20px; }
              li { margin: 3px 0; }
              strong { color: #111827; }

              table { width: 100%; border-collapse: collapse; margin: 10px 0; font-size: 9.5pt; }
              tr { page-break-inside: avoid; }
              th, td { border: 1px solid #d1d5db; padding: 5px 7px; text-align: left; vertical-align: top; }
              th { background: #f1f5f9; color: #111827; }

              code { font-family: monospace; font-size: 9pt; background: #f3f4f6; padding: 0 3px; }
              pre { background: #f3f4f6; padding: 8px; font-size: 9pt; white-space: pre-wrap; }
              pre code { background: none; padding: 0; }
              blockquote { border-left: 3px solid #1f4e79; margin: 8px 0; padding: 2px 12px; color: #4b5563; }
              hr { border: none; border-top: 1px solid #e5e7eb; margin: 14px 0; }
            </style>
            </head>
            <body>
              <div class="header">
                <span class="type">{{TYPE}}</span>
                <div class="title">{{TITLE}}</div>
                <div class="meta">{{META}}</div>
              </div>
              <div class="content">
            {{BODY}}
              </div>
            </body>
            </html>
            """;

    // ---------------- DOCX: Markdown tree -> Word ----------------

    private byte[] generateDocx(Report report) throws IOException {
        return docxReportWriter.write(
                typeLabel(report.getReportType().name()),
                report.getUploadedFile().getFileName(),
                "Generated " + formatDate(report.getCompletedAt()),
                report.getReportContent());
    }

    private byte[] generateMultiDocx(MultiDocumentReport report, String fileNames) throws IOException {
        return docxReportWriter.write(
                "Multi-document · " + typeLabel(report.getReportType().name()),
                "Multi-Document Report",
                "Files: " + fileNames + "  ·  Generated " + formatDate(report.getCompletedAt()),
                report.getReportContent());
    }

    // ---------------- helpers ----------------

    private String typeLabel(String type) {
        return switch (type) {
            case "EXECUTIVE" -> "Executive Summary";
            case "TECHNICAL" -> "Technical Analysis";
            case "CUSTOM" -> "Custom Report";
            default -> type;
        };
    }

    private String formatDate(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_FORMAT) : "N/A";
    }

    // File names and labels go into HTML: escape them so "a<b&c.pdf" can't break the page
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}