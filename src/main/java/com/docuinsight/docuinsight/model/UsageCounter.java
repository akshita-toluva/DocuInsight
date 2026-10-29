package com.docuinsight.docuinsight.model;

import jakarta.persistence.*;

import java.time.LocalDate;

// One row per user, per day, per action: "user 7 asked 12 questions on 2026-10-29"
// The entity exists so Hibernate (ddl-auto=update) creates the table and the unique constraint.
// Counting itself is done with one atomic SQL statement in QuotaService.
@Entity
@Table(name = "usage_counters",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "usage_date", "action"}))
public class UsageCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(nullable = false)
    private int used;

    protected UsageCounter() {
        // for JPA
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getUsageDate() { return usageDate; }
    public String getAction() { return action; }
    public int getUsed() { return used; }
}