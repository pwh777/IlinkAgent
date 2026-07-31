package com.fourth.ykd.ai.ragtest;

import com.fourth.ykd.ai.service.rag.IngestionService;
import com.fourth.ykd.ai.service.rag.RetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagController {

    private final RetrievalService retrievalService;
    private final IngestionService ingestionService;

    @GetMapping("/add")
    public String add() {
        ingestionService.addKnowledge(
                "ykd项目支持微信语音回复，使用iLink接收消息，然后ASR识别，再调用大模型"
        );
        return "ok";
    }

    @GetMapping("/search")
    public List<Document> search(String question) {
        return retrievalService.search(question);
    }
}