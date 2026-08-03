package com.fourth.ykd.ai.service.rag;

import com.fourth.ykd.ai.dto.PersistedChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通过调用小型可配置的大语言模型，将多轮对话压缩为独立的检索查询的工具类。
 *
 * <p>模型名称从 {@link RagProperties.Rewrite#getModel()} 获取，
 * 而不是使用分散的 {@code @Value} 注解。</p>
 */
@Slf4j
@Component
public class QueryRewriter {
    //要求模型所干的东西
    private static final String REWRITE_PROMPT_TEMPLATE = """
            你是一个 RAG（检索增强生成）系统的查询重写器。
            根据对话历史和用户的最新提问，将用户的问题重写为一个适合文档检索的、独立且完整的查询。
            
            规则：
            - 将代词（它 ，她 ，他 , 他们 , 这个, 那个 等）解析为对话历史中的具体指代对象。
            - 包含对话中隐含但仅凭最新问题无法体现的重要上下文信息。
            - 不要回答该问题。
            - 仅输出重写后的查询，且必须为单行。
            
            对话历史：
            %s
            
            用户的最新提问：
            %s
            
            重写后的独立查询：
            """;

    private final ChatClient chatClient;
    private final String rewriteModel;

    public QueryRewriter(ChatClient chatClient, RagProperties ragProperties) {
        this.chatClient = chatClient;
        this.rewriteModel = ragProperties.getRewrite().getModel();
    }


    //拼接历史
    /**
     * 使用对话历史作为上下文，将用户当前的查询重写为独立的检索查询。
     *
     * @param history      对话中的历史消息（可为空）
     * @param currentQuery 用户的最新问题
     * @return 适用于向量搜索的独立查询
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