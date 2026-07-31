package com.fourth.ykd.ai.service;

import org.springframework.ai.document.Document;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface RagService {


     void addKnowledge(String text);


     List<Document> search(String question);

     String getKnowledge(String question);

    boolean needSearch(String question);
}
