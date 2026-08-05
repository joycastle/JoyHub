package com.iflytek.skillhub.search;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** Chinese semantic embeddings powered by the in-process BGE-small-zh-v1.5 ONNX model. */
@Primary
@Service
public class BgeSearchEmbeddingService implements SearchEmbeddingService {
    private final EmbeddingModel model = new BgeSmallZhV15QuantizedEmbeddingModel();
    private final Map<String, float[]> queryCache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                    return size() > 128;
                }
            });

    @Override
    public String embed(String text) {
        float[] vector = model.embed(normalizeInput(text)).content().vector();
        return Arrays.stream(toDoubleArray(vector))
                .mapToObj(value -> String.format(Locale.ROOT, "%.7f", value))
                .collect(Collectors.joining(","));
    }

    @Override
    public double similarity(String text, String serializedVector) {
        if (serializedVector == null || serializedVector.isBlank()) {
            return 0D;
        }
        String normalizedText = normalizeInput(text);
        float[] query = queryCache.computeIfAbsent(
                normalizedText, ignored -> model.embed(normalizedText).content().vector());
        String[] values = serializedVector.split(",");
        if (values.length != query.length) {
            return 0D;
        }
        double dot = 0D;
        double leftMagnitude = 0D;
        double rightMagnitude = 0D;
        for (int index = 0; index < values.length; index++) {
            double right = Double.parseDouble(values[index]);
            dot += query[index] * right;
            leftMagnitude += query[index] * query[index];
            rightMagnitude += right * right;
        }
        if (leftMagnitude == 0D || rightMagnitude == 0D) {
            return 0D;
        }
        return dot / Math.sqrt(leftMagnitude * rightMagnitude);
    }

    private static String normalizeInput(String text) {
        if (text == null || text.isBlank()) {
            return "空内容";
        }
        return text.length() <= 1200 ? text : text.substring(0, 1200);
    }

    private static double[] toDoubleArray(float[] values) {
        double[] result = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[index];
        }
        return result;
    }
}
