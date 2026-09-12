package com.docuinsight.docuinsight.service;

import com.docuinsight.docuinsight.model.ExtractionResponse;
import com.docuinsight.docuinsight.model.UploadedFile;
import com.docuinsight.docuinsight.repository.UploadedFileRepository;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

@Service
public class TextExtractionService {

    private final UploadedFileRepository fileRepository;
    private final ImageAnalysisService imageAnalysisService;


    public TextExtractionService(UploadedFileRepository fileRepository,ImageAnalysisService imageAnalysisService)
    {
        this.fileRepository = fileRepository;
        this.imageAnalysisService = imageAnalysisService;
    }

    // METHOD called by FileController when POST /api/files/{fileId}/extract is hit
    public ExtractionResponse extractText(Long fieldId,String email) throws Exception {

        // Step 1: Find the file record in DB by ID
        // If ID doesn't exist, throw a clear error
        UploadedFile file=fileRepository.findById(fieldId)
                .orElseThrow(()-> new RuntimeException("File not found with id: " + fieldId));

        //check ownership
        if (!file.getUser().getEmail().equals(email)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Access denied: you do not own this file.");
        }

        // Step 2: Check if text was already extracted before
        // If yes — skip re-processing, return cached text from DB
        if(file.getExtractedText()!=null && !file.getExtractedText().isEmpty())
        {
            return  new ExtractionResponse(
                    file.getId(),
                    file.getFileName(),
                    file.getExtractedText(),
                    file.getExtractedText().length(),
                    "Already extracted returning cached text"
            );
        }
        // Step 3: Route to correct extractor based on file type  saved as "PDF" or "CSV" during upload in FileService
        String extractedText;
        if (file.getFileType().equalsIgnoreCase("PDF") ||
                file.getFileType().equalsIgnoreCase("application/pdf"))
        {
            extractedText = extractFromPDFWithImages(file.getFilePath());
        }
        else if (file.getFileType().equalsIgnoreCase("CSV") ||
                file.getFileType().equalsIgnoreCase("text/csv") ||
                file.getFileType().equalsIgnoreCase("application/csv"))
        {
            extractedText = extractFromCSV(file.getFilePath());
        }
        else if(file.getFileType().equalsIgnoreCase("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        {
           extractedText=extractFromDOCX(file.getFilePath());
        }
        else if(file.getFileType().equalsIgnoreCase("text/plain") || file.getFileType().equalsIgnoreCase("TXT"))
        {
            extractedText=extractFromTXT(file.getFilePath());
        }
        else if(file.getFileType().equalsIgnoreCase("image/png") ||
                file.getFileType().equalsIgnoreCase("image/jpeg") ||
                file.getFileType().equalsIgnoreCase("image/webp"))
        {
            extractedText= imageAnalysisService.analyseImage(
                    file.getFilePath(),file.getFileType()
            );
        }
        else
        {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported file type: " + file.getFileType());
        }

        // Step 4: Save extracted text back into the DB record
        // Next time /extract is called, Step 2 above will catch it and return instantly
        file.setExtractedText(extractedText);
        fileRepository.save(file);

        // Step 5: Return structured response to controller → controller sends to frontend
        return new ExtractionResponse(
                file.getId(),
                file.getFileName(),
                extractedText,
                extractedText.length(),
                "Extraction Successful"
        );

    }


    /**********  PDF Extraction using Apache PDFBox ***********/
    private String extractFromPDF(String filePath) throws   Exception{

        // Try normal PDFBox text extraction first (fast, no API call)
        String textContent;
        try(PDDocument document= Loader.loadPDF(Paths.get(filePath).toFile())){
            PDFTextStripper stripper=new PDFTextStripper();
            textContent= stripper.getText(document);
        }
        catch (java.io.IOException e) {
            throw new IOException("PDF parsing failed: " + e.getMessage(),e);
        }
        //If more than 100 chars means real text was found -- return it directly
        if(textContent!=null && textContent.trim().length() > 100)
        {
            return textContent;
        }
         //Fewer than 100 chars — scanned or image-heavy PDF
        // Fall back to vision model
        return extractFromPDFUsingVision(filePath);

    }

    private String extractFromPDFUsingVision(String filePath) throws Exception{
        StringBuilder fullText=new StringBuilder();

        try(PDDocument document=Loader.loadPDF(Paths.get(filePath).toFile())){
            PDFRenderer pdfRenderer=new PDFRenderer(document);
            int pageCount=document.getNumberOfPages();

            for(int i=0;i<pageCount;i++)
            {
                //Render this page as an image at 150 DPI
                BufferedImage pageImage=pdfRenderer.renderImageWithDPI(i,150);

                //convert in-memory image to raw bytes in PNG format
                ByteArrayOutputStream baos=new ByteArrayOutputStream();
                ImageIO.write(pageImage,"PNG",baos);
                byte[] imageBytes=baos.toByteArray();

                //Encode bytes to Base64 text string
                String base64= Base64.getEncoder().encodeToString(imageBytes);

                //Send To vision model and get description
                String pageDescription = imageAnalysisService.analyseImageFromBase64(base64,"image/png");

                //Append page label + description
                fullText.append("-----Page ").append(i + 1).append("-----\n");
                fullText.append(pageDescription).append("\n\n");
            }
        }
        return fullText.toString();
    }

    private String extractFromPDFWithImages(String filePath) throws  Exception{
        StringBuilder combined=new StringBuilder();
        try(PDDocument document=Loader.loadPDF(Paths.get(filePath).toFile()))
        {
            //Part 1 extract all text using PDFBOX
            PDFTextStripper stripper=new PDFTextStripper();
            String allText=stripper.getText(document);

            if(allText!=null && !allText.isBlank())
            {
                combined.append("=== TEXT CONTENT ===\n");
                combined.append(allText.trim()).append("\n\n");
            }
            //Part 2 Check each page for embedded images
            PDFRenderer renderer=new PDFRenderer(document);
            int pageCount=document.getNumberOfPages();
            boolean foundImages=false;
            for(int i=0;i<pageCount;i++)
            {
                PDPage page=document.getPage(i);
                PDResources resources = page.getResources();

                boolean pageHasImage=false;
                for(COSName name : resources.getXObjectNames())
                {
                    PDXObject obj=resources.getXObject(name);
                    if(obj instanceof PDImageXObject)
                    {
                        pageHasImage=true;
                        break;
                    }
                }
                if(pageHasImage)
                {
                    if(!foundImages)
                    {
                        combined.append("=== IMAGE ANALYSIS ===\n");
                        foundImages=true;
                    }
                    BufferedImage pageImage=renderer.renderImageWithDPI(i,150);
                    ByteArrayOutputStream baos=new ByteArrayOutputStream();
                    ImageIO.write(pageImage,"PNG",baos);
                    String base64=Base64.getEncoder().encodeToString(baos.toByteArray());

                    String imageDesc=imageAnalysisService.analyseImageFromBase64(base64,"image/png");
                    combined.append("Page ").append(i+1).append("(images):\n");
                    combined.append(imageDesc).append("\n\n");
                }
            }
        }
        return combined.toString();

    }

    /****************** CSV Extraction using OpenCSV **************/
    private String extractFromCSV(String filePath) throws IOException{

        StringBuilder sb = new StringBuilder();

        try(CSVReader reader=new CSVReader(new FileReader(filePath))){
            String[] row;
            while((row=reader.readNext())!=null)
            {
                sb.append(String.join(",",row)).append("\n");
            }
        }catch (CsvValidationException e) {
            throw new IOException("CSV parsing failed: " + e.getMessage(), e);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "File not found on disk: " + filePath);
        }

        return sb.toString();
    }
    /****************** CSV Extraction using Apache POI **************/
    private String extractFromDOCX(String filePath) throws IOException{

        StringBuilder sb=new StringBuilder();

        try(XWPFDocument document=new XWPFDocument(
                java.nio.file.Files.newInputStream(Paths.get(filePath)))){
            for(XWPFParagraph para: document.getParagraphs()){
                String text=para.getText();
                if(text!=null && !text.isEmpty()){
                    sb.append(text).append("\n");
                }
            }
        }
        return sb.toString();
    }

    /********** TXT Extraction — plain file read **********/
    private String extractFromTXT(String filePath) throws IOException{
        return Files.readString(Paths.get(filePath));
    }

}

