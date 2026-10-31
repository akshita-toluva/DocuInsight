package com.docuinsight.docuinsight.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LocalOnnxEmbeddingModel implements EmbeddingModel, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LocalOnnxEmbeddingModel.class);
    private static final String OUTPUT_NAME = "last_hidden_state";
    private static final int DIMENSIONS = 384;

    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final Set<String> inputNames;

    public LocalOnnxEmbeddingModel(Path modelPath, Path tokenizerPath) throws OrtException, IOException {
        this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setCPUArenaAllocator(false);
            options.setMemoryPatternOptimization(false);
            options.setIntraOpNumThreads(1);
            options.setInterOpNumThreads(1);
            this.session = environment.createSession(modelPath.toString(), options);
        }
        this.inputNames = session.getInputNames();
        log.info("Local ONNX embedding model loaded from {} (inputs: {})", modelPath, inputNames);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        Encoding[] encodings = tokenizer.batchEncode(texts);

        int batch = encodings.length;
        long[][] inputIds = new long[batch][];
        long[][] attentionMask = new long[batch][];
        long[][] tokenTypeIds = new long[batch][];
        for (int i = 0; i < batch; i++) {
            inputIds[i] = encodings[i].getIds();
            attentionMask[i] = encodings[i].getAttentionMask();
            tokenTypeIds[i] = encodings[i].getTypeIds();
        }

        try (OnnxTensor ids = OnnxTensor.createTensor(environment, inputIds);
             OnnxTensor mask = OnnxTensor.createTensor(environment, attentionMask);
             OnnxTensor types = OnnxTensor.createTensor(environment, tokenTypeIds)) {

            Map<String, OnnxTensor> inputs = new HashMap<>();
            if (inputNames.contains("input_ids")) inputs.put("input_ids", ids);
            if (inputNames.contains("attention_mask")) inputs.put("attention_mask", mask);
            if (inputNames.contains("token_type_ids")) inputs.put("token_type_ids", types);

            try (OrtSession.Result result = session.run(inputs)) {
                // [batch][tokens][384]: one vector per token
                float[][][] tokenVectors = (float[][][]) result.get(OUTPUT_NAME)
                        .orElseThrow(() -> new IllegalStateException("Model output '" + OUTPUT_NAME + "' missing"))
                        .getValue();

                List<Embedding> embeddings = new ArrayList<>(batch);
                for (int i = 0; i < batch; i++) {
                    embeddings.add(new Embedding(meanPool(tokenVectors[i], attentionMask[i]), i));
                }
                return new EmbeddingResponse(embeddings);
            }
        } catch (OrtException e) {
            throw new IllegalStateException("Embedding failed: " + e.getMessage(), e);
        }
    }

    // Same formula as Spring AI 2.0.1: sum(token * mask) / max(sum(mask), 1e-9). No normalisation.
    static float[] meanPool(float[][] tokenVectors, long[] mask) {
        int dims = tokenVectors[0].length;
        float[] sum = new float[dims];
        float count = 0f;
        for (int t = 0; t < tokenVectors.length; t++) {
            if (mask[t] == 0) {
                continue; // padding token: ignored
            }
            for (int d = 0; d < dims; d++) {
                sum[d] += tokenVectors[t][d] * mask[t];
            }
            count += mask[t];
        }
        float divisor = Math.max(count, 1e-9f);
        for (int d = 0; d < dims; d++) {
            sum[d] /= divisor;
        }
        return sum;
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS; // avoids a test embedding at startup
    }

    @Override
    public void close() throws Exception {
        session.close();
        tokenizer.close();
    }
}