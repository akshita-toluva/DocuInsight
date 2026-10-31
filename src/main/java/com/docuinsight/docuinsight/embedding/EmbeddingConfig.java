package com.docuinsight.docuinsight.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class EmbeddingConfig {

    @Bean
    public EmbeddingModel embeddingModel(@Value("${app.embedding.model-dir}") String modelDir) throws Exception {
        Path dir = Path.of(modelDir);
        return new LocalOnnxEmbeddingModel(dir.resolve("model.onnx"), dir.resolve("tokenizer.json"));
    }
}