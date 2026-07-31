package com.fourth.ykd.ai.service.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Unified RAG configuration properties bound under {@code rag.*}.
 *
 * <p>
 * Consolidates all previously scattered properties:
 * <ul>
 *   <li>{@code rag.retrieval.*} — top-k / similarity threshold</li>
 *   <li>{@code rag.ingestion.*}  — chunk size / overlap</li>
 *   <li>{@code rag.embedding.*}  — embedding model name</li>
 *   <li>{@code rag.rewrite.*}    — query rewriter model</li>
 * </ul>
 * </p>
 */
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private final Retrieval retrieval = new Retrieval();
    private final Ingestion ingestion = new Ingestion();
    private final Embedding embedding = new Embedding();
    private final Rewrite rewrite = new Rewrite();

    public Retrieval getRetrieval() { return retrieval; }
    public Ingestion getIngestion()   { return ingestion; }
    public Embedding getEmbedding()   { return embedding; }
    public Rewrite getRewrite()       { return rewrite; }

    // ─── nested config groups ──────────────────────────────

    public static class Retrieval {
        private int topK = 3;
        private double similarityThreshold = 0.7;

        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
    }

    public static class Ingestion {
        private int chunkSize = 800;
        private int chunkOverlap = 100;

        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
        public int getChunkOverlap() { return chunkOverlap; }
        public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
    }

    public static class Embedding {
        private String modelName = "text-embedding-v4";

        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
    }

    public static class Rewrite {
        private String model = "deepseek-chat";

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }
}