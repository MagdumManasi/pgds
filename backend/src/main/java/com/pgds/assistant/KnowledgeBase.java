package com.pgds.assistant;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Policy notes from src/main/resources/kb/*.md, split into passages at each "## " heading. */
@Component
public class KnowledgeBase {

    public record Chunk(String id, String source, String title, String text) {}

    private static final int MAX_CHARS = 900;
    private final List<Chunk> chunks;

    public KnowledgeBase() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:kb/*.md");
        Arrays.sort(files, Comparator.comparing(Resource::getFilename));
        List<Chunk> all = new ArrayList<>();
        for (Resource r : files)
            all.addAll(split(r.getFilename(), new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));
        this.chunks = List.copyOf(all);
    }

    public List<Chunk> chunks() { return chunks; }

    static List<Chunk> split(String source, String markdown) {
        List<Chunk> out = new ArrayList<>();
        String docTitle = source;
        String heading = null;
        StringBuilder body = new StringBuilder();
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("# ")) { docTitle = line.substring(2).trim(); continue; }
            if (line.startsWith("## ")) {
                flush(out, source, docTitle, heading, body.toString());
                heading = line.substring(3).trim();
                body.setLength(0);
            } else body.append(line).append('\n');
        }
        flush(out, source, docTitle, heading, body.toString());
        return out;
    }

    private static void flush(List<Chunk> out, String source, String docTitle, String heading, String body) {
        String text = body.trim();
        if (heading == null || text.isEmpty()) return;
        String title = docTitle + " - " + heading;
        StringBuilder part = new StringBuilder();
        for (String para : text.split("\\n\\s*\\n")) {
            if (part.length() > 0 && part.length() + para.length() > MAX_CHARS) {
                out.add(new Chunk(source + "#" + out.size(), source, title, part.toString().trim()));
                part.setLength(0);
            }
            part.append(para).append("\n\n");
        }
        if (part.length() > 0) out.add(new Chunk(source + "#" + out.size(), source, title, part.toString().trim()));
    }
}
