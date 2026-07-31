package com.fourth.ykd.ai.service.rag;

import com.fourth.ykd.ai.dto.PersistedChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Utility that compresses multi-turn conversation into a standalone
 * retrieval query by calling a small, configurable LLM.
 *
 * <p>Model name is sourced from {@link RagProperties.Rewrite#getModel()}
 * instead of a scattered {@code @Value} annotation.</p>
 */
@Slf4j
@Component
public class QueryRewriter {

    private static final String REWRITE_PROMPT_TEMPLATE = """
            You are a query rewriter for a RAG (Retrieval-Augmented Generation) system.
            Given a conversation history and the user's latest question, rewrite the user's
            question into a standalone, self-contained query suitable for document retrieval.

            Rules:
            - Resolve pronouns (it, they, this, that) to their concrete referents from the history.
            - Include any important context implied by the conversation but missing from the latest question alone.
            - Do NOT answer the question.
            - Output ONLY the rewritten query on a single line.

            Conversation history:
            %s

            User's latest question:
            %s

            Rewritten standalone query:
            """;

    private final ChatClient chatClient;
    private final String rewriteModel;

    public QueryRewriter(ChatClient chatClient, RagProperties ragProperties) {
        this.chatClient = chatClient;
        this.rewriteModel = ragProperties.getRewrite().getModel();
    }

    /**
     * Rewrite the user's current query into a standalone retrieval query
     * using the conversation history for context.
     *
     * @param history      previous messages in the conversation (may be empty)
     * @param currentQuery the user's latest question
     * @return a standalone query suitable for vector search
     */
    public String rewrite(List<PersistedChatMessage> history, String currentQuery) {
        if (history == null || history.isEmpty()) {
            log.debug("[RAG][REWRITE] no history, using original query: \"{}\"", currentQuery);
            return currentQuery;
        }

        String historyText = formatHistory(history);
        String prompt = REWRITE_PROMPT_TEMPLATE.formatted(historyText, currentQuery);

        log.debug("[RAG][REWRITE] rewriting query with model={}, historyMessages={}",
                rewriteModel, history.size());

        try {
            String rewritten = chatClient.prompt()
                    .user(prompt)
                    .options(OpenAiChatOptions.builder()
                            .model(rewriteModel)
                            .temperature(0.1)
                            .maxTokens(256)
                            .build())
                    .call()
                    .content();

            if (rewritten == null || rewritten.isBlank()) {
                log.warn("[RAG][REWRITE] model returned empty response, falling back to original query");
                return currentQuery;
            }

            String trimmed = rewritten.trim();
            log.debug("[RAG][REWRITE] original=\"{}\" -> standalone=\"{}\"", currentQuery, trimmed);
            return trimmed;
        } catch (Exception e) {
            log.warn("[RAG][REWRITE] rewrite failed: {}, falling back to original query", e.getMessage());
            return currentQuery;
        }
    }

    private String formatHistory(List<PersistedChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        for (PersistedChatMessage msg : history) {
            sb.append(msg.role())
                    .append(": ")
                    .append(msg.content())
                    .append("\n");
        }
        return sb.toString();
    }
}