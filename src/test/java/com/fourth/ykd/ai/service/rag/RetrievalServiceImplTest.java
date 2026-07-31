package com.fourth.ykd.ai.service.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceImplTest {

    @Mock
    private VectorStore store;

    private RetrievalServiceImpl service;

    @BeforeEach
    void setUp() {
        RagProperties props = new RagProperties();
        props.getRetrieval().setTopK(3);
        props.getRetrieval().setSimilarityThreshold(0.7);

        service = new RetrievalServiceImpl(store, props);
        service.setSelf(service);
    }

    @Test
    void search_should_return_results_sorted_by_similarity_descending() {
        // Given — mock Document.getScore() since setScore() is not exposed in this version
        Document doc1 = scoreMock(0.50);
        Document doc2 = scoreMock(0.95);
        Document doc3 = scoreMock(0.71);

        when(store.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc1, doc2, doc3));

        // When
        List<Document> result = service.search("test query");

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getScore()).isEqualTo(0.95);
        assertThat(result.get(1).getScore()).isEqualTo(0.71);
        assertThat(result.get(2).getScore()).isEqualTo(0.50);
    }

    @Test
    void getKnowledge_should_include_metadata_source_and_title() {
        // Given
        Document doc = mock(Document.class);
        when(doc.getScore()).thenReturn(0.90);
        when(doc.getMetadata()).thenReturn(Map.of("source", "wiki", "title", "Getting Started"));
        when(doc.getText()).thenReturn("This is the chunk content");

        when(store.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));

        // When
        String knowledge = service.getKnowledge("how to start");

        // Then
        assertThat(knowledge)
                .contains("source: wiki")
                .contains("title: Getting Started")
                .contains("This is the chunk content")
                .contains("【知识来源】")
                .contains("【内容】");
    }

    @Test
    void getKnowledge_should_return_empty_when_no_doc_passes_threshold() {
        // Given
        Document low = scoreMock(0.60);

        when(store.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(low));

        // When
        String knowledge = service.getKnowledge("query");

        // Then
        assertThat(knowledge).isEmpty();
    }

    @Test
    void getKnowledge_should_return_empty_when_no_results() {
        when(store.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        assertThat(service.getKnowledge("query")).isEmpty();
    }

    /** Helper: create a mock Document whose getScore() returns the given value. */
    private static Document scoreMock(double score) {
        Document d = mock(Document.class);
        when(d.getScore()).thenReturn(score);
        return d;
    }
}