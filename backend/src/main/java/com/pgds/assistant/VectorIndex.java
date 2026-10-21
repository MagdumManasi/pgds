package com.pgds.assistant;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/** RAG retrieval with Spring AI: embeds the knowledge base once and answers similarity searches. Only active in llm mode. */
@Service
public class VectorIndex {

    private static final Logger log = LoggerFactory.getLogger(VectorIndex.class);

    private final ObjectProvider<VectorStore> store;
    private final KnowledgeBase kb;
    private final boolean enabled;
    private final File file;
    private volatile boolean ready;
    private volatile String error;

    public VectorIndex(ObjectProvider<VectorStore> store, KnowledgeBase kb,
                       @Value("${pgds.assistant.mode:local}") String mode,
                       @Value("${pgds.assistant.store-file:data/vectorstore.json}") String storeFile) {
        this.store = store;
        this.kb = kb;
        this.enabled = "llm".equalsIgnoreCase(mode);
        this.file = new File(storeFile);
    }

    @PostConstruct
    void start() {
        if (!enabled) return;
        Thread t = new Thread(() -> build(false), "assistant-indexer");   // embedding calls must not block startup
        t.setDaemon(true);
        t.start();
    }

    public boolean ready() { return ready; }
    public String error() { return error; }

    public synchronized void rebuild() { build(true); }

    private synchronized void build(boolean force) {
        try {
            VectorStore vs = store.getIfAvailable();
            if (vs == null) throw new IllegalStateException("No vector store configured");
            if (!force && file.exists() && vs instanceof SimpleVectorStore svs) {
                svs.load(file);
                ready = true; error = null;
                log.info("Assistant vector store loaded from {}", file);
                return;
            }
            List<Document> docs = kb.chunks().stream().map(c -> Document.builder().id(c.id()).text(c.title() + "\n" + c.text())
                    .metadata(Map.of("source", c.source(), "title", c.title())).build()).toList();
            if (force) {
                try { vs.delete(docs.stream().map(Document::getId).toList()); } catch (Exception ignored) { }
            }
            vs.add(docs);
            if (vs instanceof SimpleVectorStore svs) {
                if (file.getParentFile() != null) file.getParentFile().mkdirs();
                svs.save(file);
            }
            ready = true; error = null;
            log.info("Assistant indexed {} passages", docs.size());
        } catch (Exception e) {
            ready = false;
            error = e.getMessage();
            log.warn("Assistant vector index unavailable, using keyword retrieval: {}", e.toString());
        }
    }

    public List<Passage> search(String query, int k) {
        VectorStore vs = store.getIfAvailable();
        if (vs == null || !ready) return List.of();
        Map<String, KnowledgeBase.Chunk> byId = kb.chunks().stream().collect(Collectors.toMap(KnowledgeBase.Chunk::id, c -> c));
        List<Passage> out = new ArrayList<>();
        for (Document d : vs.similaritySearch(SearchRequest.builder().query(query).topK(k).build())) {
            KnowledgeBase.Chunk c = byId.get(d.getId());
            if (c != null) out.add(new Passage(c, d.getScore() == null ? 0 : d.getScore()));
        }
        return out;
    }
}
