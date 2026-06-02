package com.docuinsight.docuinsight.service;

import com.docuinsight.docuinsight.model.ExtractionResponse;
import com.docuinsight.docuinsight.model.UploadedFile;
import com.docuinsight.docuinsight.repository.UploadedFileRepository;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Paths;

@Service
public class TextExtractionService {

    private final UploadedFileRepository fileRepository;


    public TextExtractionService(UploadedFileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    // METHOD called by FileController when POST /api/files/{fileId}/extract is hit
    public ExtractionResponse extractText(Long fieldId,String email) throws IOException{

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
                file.getFileType().equalsIgnoreCase("application/pdf")) {
            extractedText = extractFromPDF(file.getFilePath());
        }
        else if (file.getFileType().equalsIgnoreCase("CSV") ||
                file.getFileType().equalsIgnoreCase("text/csv") ||
                file.getFileType().equalsIgnoreCase("application/csv")) {
            extractedText = extractFromCSV(file.getFilePath());
        }
        else {
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
    private String extractFromPDF(String filePath) throws IOException{

        try(PDDocument document= Loader.loadPDF(Paths.get(filePath).toFile())){


            PDFTextStripper stripper=new PDFTextStripper();

            return stripper.getText(document);

        } catch (java.io.IOException e) {
            throw new IOException("PDF parsing failed: " + e.getMessage(),e);
        }
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

}

