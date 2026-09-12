package com.docuinsight.docuinsight.service;

import com.docuinsight.docuinsight.model.MultiDocumentReport;
import com.docuinsight.docuinsight.model.MultiDocumentReportResponse;
import com.docuinsight.docuinsight.model.Report;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public String buildMultiDocumentPrompt(MultiDocumentReport.ReportType type, String customPrompt, List<String> fileNames)
    {
        int count=fileNames.size();
        String fileList=String.join(",", fileNames);
        String intro="You are analysing " + count + " douments together: " + fileList + ".\n\n";

        return switch (type)
                {
                    case EXECUTIVE -> intro +
                            "Provide a unified executive summary synthesising all documents.\n" +
                            "Structure your response exactly as:\n\n" +
                            "## OVERALL SUMMARY\n" +
                            "[Combined key insights from all documents]\n\n" +
                            "## COMMON THEMES\n" +
                            "[Topics and findings that appear across multiple documents]\n\n" +
                            "## KEY DIFFERENCES\n" +
                            "[Important contrasts and contradictions between documents]\n\n" +
                            "## COMBINED RECOMMENDATIONS\n" +
                            "[Actionable conclusions drawing from all documents]";

                    case TECHNICAL -> intro +
                            "Provide a technical analysis comparing all documents.\n" +
                            "Structure your response exactly as:\n\n" +
                            "## TECHNICAL OVERVIEW\n" +
                            "[What each document covers technically]\n\n" +
                            "## AGREEMENTS\n" +
                            "[Where documents align in technical content]\n\n" +
                            "## CONTRADICTIONS\n" +
                            "[Where documents conflict or differ technically]\n\n" +
                            "## GAPS\n" +
                            "[Information missing or unclear across documents]\n\n" +
                            "## COMBINED CONCLUSION\n" +
                            "[Synthesised technical conclusion]";

                    case CUSTOM -> intro + customPrompt;
                };
    }

}
