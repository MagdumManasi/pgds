package com.pgds.assistant;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeBaseTest {

    @Test
    void loadsAndSplitsAllNotes() throws Exception {
        var kb = new KnowledgeBase();
        assertTrue(kb.chunks().size() >= 20, "expected passages from five notes, got " + kb.chunks().size());
        assertEquals(kb.chunks().size(), new HashSet<>(kb.chunks().stream().map(KnowledgeBase.Chunk::id).toList()).size(), "ids are unique");
        kb.chunks().forEach(c -> { assertFalse(c.title().isBlank()); assertFalse(c.text().isBlank()); });
    }

    @Test
    void splitsOnHeadingsAndKeepsTitles() {
        var chunks = KnowledgeBase.split("x.md", "# Doc\n\nintro ignored\n\n## One\nfirst body\n\n## Two\nsecond body\n");
        assertEquals(2, chunks.size());
        assertEquals("Doc - One", chunks.get(0).title());
        assertEquals("second body", chunks.get(1).text());
    }

    @Test
    void longSectionsAreSplitIntoSeveralPassages() {
        String para = "word ".repeat(120).trim();          // about 600 characters
        var chunks = KnowledgeBase.split("x.md", "# D\n\n## Long\n" + para + "\n\n" + para + "\n\n" + para + "\n");
        assertTrue(chunks.size() >= 2);
    }
}
