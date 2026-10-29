package com.docuinsight.docuinsight.model;

// Today's usage for the UI counters
public record UsageResponse(int reportsUsed, int reportsLimit,
                            int questionsUsed, int questionsLimit,
                            long resetsInSeconds) {
}