package com.pgds.assistant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LexicalRetrieverTest {

    private static LexicalRetriever retriever;

    @BeforeAll
    static void load() throws Exception { retriever = new LexicalRetriever(new KnowledgeBase().chunks()); }

    @Test
    void findsEntitlementRules() {
        var top = retriever.search("What is the monthly entitlement of an AAY card?", 3);
        assertFalse(top.isEmpty());
        assertTrue(top.stream().anyMatch(p -> p.chunk().text().contains("35 kg")), "an AAY passage should be retrieved");
    }

    @Test
    void routesComplaintQuestionsToTheComplaintGuideline() {
        var top = retriever.search("Which department handles overcharging complaints?", 3);
        assertEquals("complaints-sop.md", top.get(0).chunk().source());
    }

    @Test
    void findsAlertThresholds() {
        var top = retriever.search("when is a stock level critical", 3);
        assertTrue(top.stream().anyMatch(p -> p.chunk().source().equals("ai-alerts-guide.md")));
    }

    @Test
    void nonsenseFindsNothing() { assertTrue(retriever.search("zzzz qqqq", 3).isEmpty()); }

    @Test
    void stopWordsAndPluralsAreNormalised() {
        assertEquals(java.util.List.of("ration", "card"), LexicalRetriever.tokens("The ration cards"));
    }
}
