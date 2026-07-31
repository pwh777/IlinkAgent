package com.fourth.ykd.ai.service.impl;

import com.fourth.ykd.ai.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {
    private final VectorStore store;
    private final TokenTextSplitter splitter = new TokenTextSplitter();

    //添加新知识
    @Override
    public void addKnowledge(String text) {

        List<Document> document = splitter.split(List.of(new Document(
                text,
                Map.of("source", "knowledge"))));
        store.add(document);
    }

    //检索知识
    @Override
    public List<Document> search(String question) {
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(3)
                .similarityThreshold(0.7)
                .build();
        return store.similaritySearch(request);
    }

    @Override
    public String getKnowledge(String question) {
        //Metadata是获取知识来源，得到的大概就“”“里面的形式
        return search(String.valueOf(question))
                .stream()
                .map(document ->
                        """
                             【知识来源】
                             %s
                            【内容】
                             %s
                            """.formatted(document.getMetadata(),document.getText()))
                .collect(Collectors.joining("\n"));

    }



    @Override
    public boolean needSearch(String question) {
        return question.contains("项目")
                || question.contains("代码")
                || question.contains("功能")
                || question.contains("怎么实现");
    }
}
