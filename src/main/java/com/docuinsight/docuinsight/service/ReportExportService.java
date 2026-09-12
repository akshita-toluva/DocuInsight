package com.docuinsight.docuinsight.service;

import com.docuinsight.docuinsight.model.MultiDocumentReport;
import com.docuinsight.docuinsight.model.MultiDocumentReportResponse;
import com.docuinsight.docuinsight.model.Report;
import com.docuinsight.docuinsight.model.User;
import com.docuinsight.docuinsight.repository.MultiDocumentReportRepository;
import com.docuinsight.docuinsight.repository.ReportRepository;
import com.docuinsight.docuinsight.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class ReportExportService {
    public final ReportRepository reportRepository;
    public final UserRepository userRepository;
    public final MultiDocumentReportRepository multiDocumentReportRepository;


    //entry_point called by controller
    @Transactional(readOnly = true)
    public byte[] exportReport(Long reportId,String format,String email) throws IOException
    {
        //load the logged in user
        User user=userRepository.findByEmail(email)
                .orElseThrow(()->new RuntimeException(
                        "User not found: " + email
                ));

        //load the report and verify ownership
        Report report=reportRepository.findByIdAndUserId(reportId,user.getId())
                .orElseThrow(()->new RuntimeException(
                        "Report not found or access denied. ID: " +reportId
                ));

        //Only completed reports can be exported
        if(report.getStatus()!=Report.ReportStatus.COMPLETED){
            throw new IllegalStateException(
                    "Cannot export a report that is not COMPLETED. " +
                            "Current status of the report is: " + report.getStatus());
        }
        //Route to correct format
        if("pdf".equalsIgnoreCase(format)){
            return generatePdf(report);
        } else if ("docx".equalsIgnoreCase(format)) {
            return generateDocx(report);
        } else{
            throw new IllegalArgumentException(
                    "Unsupported format: '" + format +
                            "'. Use 'pdf' or 'docx'."
            );
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportMultiReport(Long reportId,String format,String email) throws IOException{
        User user=userRepository.findByEmail(email)
                .orElseThrow(()->new RuntimeException("User not found: " + email));

        MultiDocumentReport report=multiDocumentReportRepository
                .findByIdAndUserId(reportId,user.getId())
                .orElseThrow(()->new RuntimeException(
                        "MultiDocument report not found. ID: " + reportId));
        if(report.getStatus()!= MultiDocumentReport.ReportStatus.COMPLETED){
            throw new IllegalStateException(
                    "Cannot export report that is not completed. " +
                            "Current status is: " + report.getStatus());
        }

        //Build file name labels for the header
        String fileNames=report.getFiles().stream()
                .map(f->f.getFileName())
                .collect(java.util.stream.Collectors.joining(", "));

        if("pdf".equalsIgnoreCase(format))
        {
            return generateMultiPdf(report, fileNames);
        } else if ("docx".equalsIgnoreCase(format)) {
            return generateMultiDocx(report, fileNames);
        }else{
            throw new IllegalArgumentException("Unsupported format: '" + format + "'. Use 'pdf' or 'docx'.");
        }
    }

    private byte[] generateMultiPdf(MultiDocumentReport report, String fileNames) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();

        try(PDDocument doc=new PDDocument()){
            String content=report.getReportContent();
            List<String> lines=wrapText(content,85);

            float margin     = 50f;
            float yStart     = PDRectangle.A4.getHeight() - margin;
            float lineHeight = 14f;

            PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font bodyFont  = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float y = yStart;
            boolean headerWritten = false;

            for (String line : lines) {
                if (y < margin + lineHeight) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = yStart;
                }
                if(!headerWritten){
                    cs.beginText();
                    cs.setFont(titleFont, 16);
                    cs.newLineAtOffset(margin, y);
                    cs.showText("Multi-Document Report — DocuInsight");
                    cs.endText();
                    y -= lineHeight * 1.5f;

                    cs.beginText();
                    cs.setFont(bodyFont, 10);
                    cs.newLineAtOffset(margin, y);
                    String safeNames = fileNames.replaceAll("[^\\x20-\\x7E]", "");
                    cs.showText("Files: " + safeNames);
                    cs.endText();
                    y -= lineHeight;

                    cs.beginText();
                    cs.setFont(bodyFont, 10);
                    cs.newLineAtOffset(margin, y);
                    cs.showText("Type: " + report.getReportType().name());
                    cs.endText();
                    y -= lineHeight * 2;
                    headerWritten = true;
                }
                cs.beginText();
                cs.setFont(bodyFont, 11);
                cs.newLineAtOffset(margin, y);
                String safe = line.replaceAll("[^\\x20-\\x7E]", "");
                cs.showText(safe);
                cs.endText();
                y -= lineHeight;
            }
            cs.close();
            doc.save(out);
        }
        return out.toByteArray();
    }

    private byte[] generateMultiDocx(MultiDocumentReport report, String fileNames) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (XWPFDocument docx = new XWPFDocument()) {
            XWPFParagraph titlePara = docx.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText("Multi-Document Report — DocuInsight");
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            titleRun.setFontFamily("Arial");
            titleRun.addBreak();

            XWPFParagraph metaPara = docx.createParagraph();
            metaPara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun metaRun = metaPara.createRun();
            metaRun.setText("Files: " + fileNames + "   |   Type: " +
                    report.getReportType().name() + "   |   Generated: " +
                    (report.getCompletedAt() != null ?
                            report.getCompletedAt().toString() : "N/A"));
            metaRun.setFontSize(10);
            metaRun.setColor("555555");
            metaRun.setFontFamily("Arial");

            docx.createParagraph();

            String[] contentLines = report.getReportContent().split("\n");
            for (String line : contentLines) {
                XWPFParagraph para = docx.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(line.isEmpty() ? " " : line);
                run.setFontSize(11);
                run.setFontFamily("Arial");
            }

            docx.write(out);
        }
        return out.toByteArray();
    }

    // PDF generation using Apache PDFBox
    private byte[] generatePdf(Report report) throws IOException{
        ByteArrayOutputStream out =new ByteArrayOutputStream();

        try(PDDocument doc=new PDDocument()){
            String content=report.getReportContent();
            List<String> lines = wrapText(content,85);

            float margin     = 50f;
            float yStart     = PDRectangle.A4.getHeight() - margin;
            float lineHeight = 14f;

            PDType1Font titleFont=new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font bodyFont=new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA);

            PDPage page=new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs=new PDPageContentStream(doc,page);
            float y=yStart;
            boolean headerWritten=false;

            for(String line:lines)
            {
                //new page when space runs out
                if(y < margin + lineHeight)
                {
                    cs.close();
                    page=new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs=new PDPageContentStream(doc,page);
                    y=yStart;
                }
                //Write title in the first page
                if(!headerWritten)
                {
                    cs.beginText();
                    cs.setFont(titleFont,16);
                    cs.newLineAtOffset(margin,y);
                    cs.showText("Report Generated by DocuInsight");
                    cs.endText();
                    y-=lineHeight * 1.5f;
                    cs.beginText();
                    cs.setFont(bodyFont,11);
                    cs.newLineAtOffset(margin,y);
                    cs.showText("Type:" + report.getReportType().name());
                    cs.endText();
                    y-=lineHeight * 2;
                    headerWritten=true;
                }
                //write the actual data
                cs.beginText();
                cs.setFont(bodyFont, 11);
                cs.newLineAtOffset(margin, y);
                // Remove non-ASCII characters to avoid PDFBox errors
                String safe = line.replaceAll("[^\\x20-\\x7E]", "");
                cs.showText(safe);
                cs.endText();
                y -= lineHeight;
            }
            cs.close();
            doc.save(out);
        }
        return out.toByteArray();
    }

    private byte[] generateDocx(Report report) throws IOException{
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try(XWPFDocument docx=new XWPFDocument()){
            //Title paragraph
            XWPFParagraph titlePara=docx.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun=titlePara.createRun();
            titleRun.setText("DocuInsight Report");
            titleRun.setBold(true);
            titleRun.setFontSize(20);
            titleRun.setFontFamily("Arial");
            titleRun.addBreak();

            //subtitle report type and date
            XWPFParagraph metaPara=docx.createParagraph();
            metaPara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun metaRun=metaPara.createRun();
            metaRun.setText("Report Type: " + report.getReportType().name() +
                    "   |   Generated: " +
                    (report.getCompletedAt() != null ?
                            report.getCompletedAt().toString() : "N/A"));
            metaRun.setFontSize(11);
            metaRun.setColor("555555");
            metaRun.setFontFamily("Arial");

            //blank spacer paragraph
            docx.createParagraph();

            //Add report content line by line
            String content=report.getReportContent();
            String[] contentLines=content.split("\n");
            for (String line : contentLines) {
                XWPFParagraph para = docx.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(line.isEmpty() ? " " : line);
                run.setFontSize(11);
                run.setFontFamily("Arial");
            }

            docx.write(out);
        }
        return out.toByteArray();
    }

    //Helper: breaks long text into lines that fit PDF page width
    private List<String> wrapText(String text, int maxCharsPerLine) {
       List<String> result=new ArrayList<>();
       if(text==null || text.isEmpty())
           return result;
       String[] paragraphs=text.split("\n");
       for(String para: paragraphs){
           if(para.isEmpty()){
               result.add("");
               continue;
           }
           String[] words =para.split(" ");
           StringBuilder currentLine=new StringBuilder();
           for(String word: words)
           {
               if(currentLine.length() + word.length() + 1 > maxCharsPerLine){
                   result.add(currentLine.toString().trim());
                   currentLine=new StringBuilder();
               }
               currentLine.append(word).append(" ");
           }
           if(!currentLine.isEmpty()){
               result.add(currentLine.toString().trim());
           }
       }
       return  result;
    }


}
