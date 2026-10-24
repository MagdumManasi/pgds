package com.pgds.card;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeneficiaryServiceTest {

    @Test
    void nameSimilarityIgnoresCaseAccentsAndPunctuation() {
        assertEquals(100, BeneficiaryService.nameSimilarityPercent("José  D'Souza", "jose d souza"));
    }

    @Test
    void nameSimilarityFindsSmallSpellingDifferences() {
        assertTrue(BeneficiaryService.nameSimilarityPercent("Ramesh Patil", "Ramesh Pati") >= 85);
        assertTrue(BeneficiaryService.nameSimilarityPercent("Ramesh Patil", "Meena Shah") < 85);
    }
}
