package com.docuinsight.docuinsight.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ReportRequest {

    @NotNull(message = "ReportType is required")
    private Report.ReportType reportType;

    @Size(min = 10, max = 2000, message = "Custom prompt must be between 10 and 2000 characters")
    private String customPrompt;

    // If true, skip cache and regenerate even if a report exists
    private boolean forceRegenerate = false;

    public Report.ReportType getReportType() {
        return reportType;
    }

    public void setReportType(Report.ReportType reportType) {
        this.reportType = reportType;
    }

    public String getCustomPrompt() {
        return customPrompt;
    }

    public void setCustomPrompt(String customPrompt) {
        this.customPrompt = customPrompt;
    }

    public boolean isForceRegenerate() {
        return forceRegenerate;
    }

    public void setForceRegenerate(boolean forceRegenerate) {
        this.forceRegenerate = forceRegenerate;
    }
}
