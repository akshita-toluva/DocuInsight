package com.docuinsight.docuinsight.service;

import com.docuinsight.docuinsight.model.Report;
import org.springframework.stereotype.Service;

@Service
public class PromptTemplateService {
    public String buildPrompt(Report.ReportType reportType,String customPrompt,String fileName){
        return switch (reportType){
            case EXECUTIVE -> buildExecutivePrompt(fileName);
            case TECHNICAL -> buildTechnicalPrompt(fileName);
            case CUSTOM -> buildCustomPrompt(customPrompt,fileName);
        };
    }

    private String buildExecutivePrompt(String fileName) {
        return "You are a senior business analyst. Analyze the provided document and generate a " +
               "professional Executive Summary Report.\n\n" +
               "File: " + fileName + "\n\n" +
               "Structure your response as follows:\n\n" +
               "# Executive Summary\n\n" +
               "## Overview\n" +
               "[2-3 sentence high-level summary]\n\n" +
               "## Key Findings\n" +
               "[3-5 most important findings as bullet points]\n\n" +
               "## Business Impact\n" +
               "[How do these findings affect business decisions?]\n\n" +
               "## Recommendations\n" +
               "[2-4 actionable recommendations]\n\n" +
               "## Conclusion\n" +
               "[Brief closing statement]\n\n" +
               "Keep the tone professional and concise. Focus on business value and and decision-making.";
    }

    private String buildTechnicalPrompt(String fileName)
    {
        return """
                You are a senior software/systems engineer. Analyze the provided document and generate \
                a comprehensive Technical Analysis Report.
                            
                File: %s
                            
                Structure your response EXACTLY as follows:
                            
                # Technical Analysis Report
                            
                ## Document Overview
                [What type of document is this? What system/technology/process does it describe?]
                            
                ## Technical Details
                [Deep dive into the technical content — architecture, specifications, methods, etc.]
                            
                ## Data Analysis
                [If data is present — patterns, anomalies, metrics, statistics]
                            
                ## Technical Issues & Risks
                [Any technical problems, inconsistencies, or risks identified]
                            
                ## System Dependencies
                [Technologies, tools, APIs, or systems referenced or required]
                                
                ## Technical Recommendations
                [Specific, actionable technical improvements or next steps]
                       
                ## Code/Configuration Notes
                [Any code snippets, config values, or technical specifications worth highlighting]
                           
                Use precise technical language. Include specific values, names, and details from the document.
                """.formatted(fileName);

    }

    private String buildCustomPrompt(String customPrompt,String fileName){
        if(customPrompt==null || customPrompt.isBlank()){
            throw new IllegalArgumentException(
                    "Custom prompt is required for CUSTOM report type."
            );
        }
        return "You are an expert document analyst. Analyze the provided document " +
               "according to the following instructions.\n\n" +
               "File: " + fileName + "\n\n" +
               "Instructions:\n" + customPrompt.trim() + "\n\n" +
               "Be thorough and specific, referencing actual content from the document.";
    }


}
