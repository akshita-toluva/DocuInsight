package com.docuinsight.docuinsight.model;

import java.time.LocalDateTime;
import java.util.List;

public class AskQuestionResponse {

    private Long reportId;
    private String fileName;
    private String question;
    private String answer;
    private List<SourcePassage> sources;   // passages the answer cited; empty when refused (Day 14)
    private LocalDateTime answeredAt;

    public AskQuestionResponse(Long reportId, String fileName, String question, String answer,
                               List<SourcePassage> sources, LocalDateTime answeredAt) {
        this.reportId = reportId;
        this.fileName = fileName;
        this.question = question;
        this.answer = answer;
        this.sources = sources;
        this.answeredAt = answeredAt;
    }

    public Long getReportId() {
        return reportId;
    }
    public String getFileName() {
        return fileName;
    }
    public String getQuestion() {
        return question;
    }
    public String getAnswer() {
        return answer;
    }
    public List<SourcePassage> getSources() {
        return sources;
    }
    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }
}