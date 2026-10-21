package com.pgds.assistant;

import com.pgds.assistant.AssistantDtos.*;
import com.pgds.domain.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService service;
    private final VectorIndex vectors;

    /** Any signed-in user. What the answer may contain depends on the caller's role (see DataContextService). */
    @PostMapping("/api/assistant/ask")
    public AskResponse ask(Authentication a, @Valid @RequestBody AskRequest r) {
        String authority = a.getAuthorities().iterator().next().getAuthority();     // ROLE_xxx
        return service.ask(a.getName(), Role.valueOf(authority.substring(5)), r.question());
    }

    @GetMapping("/api/assistant/status")
    public Status status() { return service.status(); }

    /** SUPER_ADMIN only (/api/admin/**): re-embed the knowledge base after editing the policy notes. */
    @PostMapping("/api/admin/assistant/reindex")
    public Status reindex() {
        vectors.rebuild();
        return service.status();
    }
}
