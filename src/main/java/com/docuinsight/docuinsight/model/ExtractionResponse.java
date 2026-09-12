package com.docuinsight.docuinsight.model;

import lombok.AllArgsConstructor;
import lombok.Data;

// DTO = Data Transfer Object
// This is what the /extract endpoint sends BACK to the frontend (or Postman)
// It never exposes internal DB details — only what the client needs
@Data
@AllArgsConstructor
public class ExtractionResponse {
    private Long fileId;            // Which file was extracted — frontend uses this for next API call
    private String fileName;        // Show user "extracted from: report.pdf"
    private String extractedText;   // The actual text content — will be sent to Gemini AI later
    private int characterCount;     // Useful stat — also lets you validate extraction worked
    private String message;         // "Extraction successful" or "Already extracted, returning cached"
}

