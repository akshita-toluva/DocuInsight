package com.docuinsight.docuinsight.model;

import java.time.LocalDateTime;

public class AskQuestionResponse {

    private Long reportId;
    private String fileName;
    private String question;
    private String answer;
    private LocalDateTime answeredAt;

    public AskQuestionResponse(Long reportId, String fileName, String question, String answer, LocalDateTime answeredAt) {
        this.reportId = reportId;
        this.fileName = fileName;
        this.question = question;
        this.answer = answer;
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

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }
}
