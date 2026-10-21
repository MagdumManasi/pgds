package com.pgds.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AssistantDtos {
    private AssistantDtos() {}

    public record AskRequest(@NotBlank @Size(min = 3, max = 500) String question) {}

    public record Source(int n, String title, String source, String snippet) {}

    /** mode: LLM (Spring AI chat model) or LOCAL (extractive answer from policy notes and live data). */
    public record AskResponse(String answer, String mode, List<Source> sources, List<String> liveData, String notice) {}

    public record Status(String configuredMode, String retrieval, boolean vectorStoreReady, String vectorStoreError, int passages) {}
}
