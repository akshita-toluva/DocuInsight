package com.docuinsight.docuinsight.model;

import jakarta.validation.constraints.NotBlank;

public class AskQuestionRequest {

    // Spring @Valid runs this check before the method executes
    // If question is empty, Spring returns 400 automat
    @NotBlank(message = "Question cannot be empty")
    private String question;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
