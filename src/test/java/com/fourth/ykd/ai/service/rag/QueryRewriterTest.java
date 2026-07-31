package com.fourth.ykd.ai.service.rag;

import com.fourth.ykd.ai.dto.PersistedChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryRewriterTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    private QueryRewriter rewriter;

    @BeforeEach
    void setUp() {
        RagProperties props = new RagProperties();
        props.getRewrite().setModel("test-model");
        rewriter = new QueryRewriter(chatClient, props);
    }

    @Test
    void rewrite_should_return_original_query_when_history_empty() {
        String result = rewriter.rewrite(List.of(), "current question");

        assertThat(result).isEqualTo("current question");
        verifyNoInteractions(chatClient);
    }

    @Test
    void rewrite_should_return_original_query_when_history_null() {
        String result = rewriter.rewrite(null, "current question");

        assertThat(result).isEqualTo("current question");
        verifyNoInteractions(chatClient);
    }

    @Test
    void rewrite_should_call_chat_client_when_history_not_empty() {
        // Given
        List<PersistedChatMessage> history = List.of(
                new PersistedChatMessage(1L, "conv1", "USER", "What is RAG?", null, null),
                new PersistedChatMessage(2L, "conv1", "ASSISTANT",
                        "RAG is Retrieval-Augmented Generation.", null, null)
        );

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("standalone query about RAG");

        // When
        String result = rewriter.rewrite(history, "How does it work?");

        // Then
        assertThat(result).isEqualTo("standalone query about RAG");
        verify(chatClient).prompt();
        verify(requestSpec).user(anyString());
        verify(requestSpec).options(any());
    }
}