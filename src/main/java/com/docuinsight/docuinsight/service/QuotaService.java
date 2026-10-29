package com.docuinsight.docuinsight.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.docuinsight.docuinsight.model.UsageResponse;
import org.springframework.jdbc.core.RowCallbackHandler;
import java.util.HashMap;
import java.util.Map;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

// Per-user daily limits for the calls that spend Groq quota (Day 17).
// Counts live in PostgreSQL, so they survive restarts (Render's free tier restarts often).
@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    public enum Action { REPORT, QUESTION }

    // Atomic "add one and tell me the new total": no read-then-write race between two requests
    private static final String CONSUME_SQL =
            "INSERT INTO usage_counters (user_id, usage_date, action, used) VALUES (?, ?, ?, 1) " +
                    "ON CONFLICT (user_id, usage_date, action) DO UPDATE SET used = usage_counters.used + 1 " +
                    "RETURNING used";

    private static final String REFUND_SQL =
            "UPDATE usage_counters SET used = used - 1 " +
                    "WHERE user_id = ? AND usage_date = ? AND action = ? AND used > 0";

    private final JdbcTemplate jdbc;
    private final int reportsPerDay;
    private final int questionsPerDay;

    public QuotaService(JdbcTemplate jdbc,
                        @Value("${app.limits.reports-per-day}") int reportsPerDay,
                        @Value("${app.limits.questions-per-day}") int questionsPerDay) {
        this.jdbc = jdbc;
        this.reportsPerDay = reportsPerDay;
        this.questionsPerDay = questionsPerDay;
    }

    // Own transaction: works even when the caller is read-only (askQuestion),
    // and the throw below rolls back this +1 so a blocked request does not count.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consume(Long userId, Action action) {
        int limit = (action == Action.REPORT) ? reportsPerDay : questionsPerDay;
        Integer used = jdbc.queryForObject(CONSUME_SQL, Integer.class, userId, today(), action.name());

        if (used == null || used > limit) {
            log.warn("Quota exceeded: user={} action={} limit={}", userId, action, limit);
            throw new QuotaExceededException(
                    "Daily limit reached: " + limit + " " + label(action) + " per day. Resets in " + formatReset(secondsUntilReset()) + ".",
                    secondsUntilReset());
        }
        log.info("Quota: user={} action={} used {}/{}", userId, action, used, limit);
    }

    // Give the unit back when the AI call failed: the user should not pay for our error
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refund(Long userId, Action action) {
        jdbc.update(REFUND_SQL, userId, today(), action.name());
    }

    public UsageResponse usage(Long userId) {
        Map<String, Integer> used = new HashMap<>();
        jdbc.query("SELECT action, used FROM usage_counters WHERE user_id = ? AND usage_date = ?",
                (RowCallbackHandler) rs -> used.put(rs.getString("action"), rs.getInt("used")),
                userId, today());
        return new UsageResponse(
                used.getOrDefault(Action.REPORT.name(), 0), reportsPerDay,
                used.getOrDefault(Action.QUESTION.name(), 0), questionsPerDay,
                secondsUntilReset());
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static long secondsUntilReset() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC);
        return Duration.between(now, midnight).getSeconds();
    }

    private static String label(Action action) {
        return action == Action.REPORT ? "reports" : "questions";
    }

    private static String formatReset(long seconds) {
        long hours = seconds / 3600, minutes = (seconds % 3600) / 60;
        return hours > 0 ? hours + " h " + minutes + " min" : minutes + " min";
    }
}