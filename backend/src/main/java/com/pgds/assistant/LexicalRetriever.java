package com.pgds.assistant;

import java.util.*;

/**
 * Keyword retrieval (TF-IDF) over the knowledge-base passages. Needs no model or network, so the assistant works
 * out of the box and is the fallback when the vector store is unavailable.
 */
public class LexicalRetriever {

    private static final Set<String> STOP = Set.of("the", "and", "for", "are", "was", "what", "how", "does", "not", "with",
            "this", "that", "from", "have", "has", "can", "you", "your", "who", "why", "when", "which", "will", "into", "about",
            "any", "all", "its", "our", "their", "they", "them", "than", "then", "there", "been", "being", "also", "may", "much",
            "many", "should", "would", "could", "get", "got", "need", "want");

    private record Entry(KnowledgeBase.Chunk chunk, Map<String, Integer> tf, int length) {}

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Double> idf = new HashMap<>();

    public LexicalRetriever(List<KnowledgeBase.Chunk> chunks) {
        Map<String, Integer> df = new HashMap<>();
        for (KnowledgeBase.Chunk c : chunks) {
            List<String> toks = new ArrayList<>(tokens(c.text()));
            toks.addAll(tokens(c.title()));           // title words count twice
            toks.addAll(tokens(c.title()));
            Map<String, Integer> tf = new HashMap<>();
            toks.forEach(t -> tf.merge(t, 1, Integer::sum));
            tf.keySet().forEach(t -> df.merge(t, 1, Integer::sum));
            entries.add(new Entry(c, tf, Math.max(1, toks.size())));
        }
        int n = Math.max(1, chunks.size());
        df.forEach((t, d) -> idf.put(t, Math.log(1.0 + (double) n / (1 + d))));
    }

    public List<Passage> search(String query, int k) {
        Set<String> q = new LinkedHashSet<>(tokens(query));
        List<Passage> hits = new ArrayList<>();
        for (Entry e : entries) {
            double score = 0;
            for (String t : q) {
                Integer f = e.tf().get(t);
                if (f != null) score += (1 + Math.log(f)) * idf.getOrDefault(t, 0.0);
            }
            if (score > 0) hits.add(new Passage(e.chunk(), score / Math.sqrt(e.length())));
        }
        hits.sort(Comparator.comparingDouble(Passage::score).reversed());
        return hits.subList(0, Math.min(k, hits.size()));
    }

    static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() < 3 || STOP.contains(w)) continue;
            if (w.length() > 4 && w.endsWith("s") && !w.endsWith("ss")) w = w.substring(0, w.length() - 1);   // light plural stemming
            out.add(w);
        }
        return out;
    }
}
