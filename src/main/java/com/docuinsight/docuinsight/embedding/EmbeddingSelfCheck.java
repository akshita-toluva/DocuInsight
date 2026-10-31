package com.docuinsight.docuinsight.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.embedding.self-check", havingValue = "true")
public class EmbeddingSelfCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingSelfCheck.class);

    private final JdbcTemplate jdbc;
    private final EmbeddingModel embeddingModel;

    public EmbeddingSelfCheck(JdbcTemplate jdbc, EmbeddingModel embeddingModel) {
        this.jdbc = jdbc;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT content, embedding::text AS vec FROM vector_store ORDER BY random() LIMIT 5");
        double worst = 1.0;
        for (Map<String, Object> row : rows) {
            float[] stored = parseVector((String) row.get("vec"));
            float[] fresh = embeddingModel.embed((String) row.get("content"));
            double cosine = cosine(stored, fresh);
            worst = Math.min(worst, cosine);
            log.info("Embedding self-check: cosine={}", String.format("%.6f", cosine));
        }
        if (rows.isEmpty()) {
            log.warn("Embedding self-check: vector_store is empty, nothing to compare");
        } else if (worst >= 0.999) {
            log.info("Embedding self-check PASSED: worst cosine {} over {} chunks", String.format("%.6f", worst), rows.size());
        } else {
            log.error("Embedding self-check FAILED: worst cosine {} — vectors differ, do NOT use this model with existing data",
                    String.format("%.6f", worst));
        }
    }

    // pgvector text form: "[0.1,0.2,...]"
    private static float[] parseVector(String text) {
        String[] parts = text.substring(1, text.length() - 1).split(",");
        float[] v = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            v[i] = Float.parseFloat(parts[i]);
        }
        return v;
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}