package com.pgds.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** With SPRING_PROFILES_ACTIVE=prod the application refuses to start with insecure settings. */
@Component
@Profile("prod")
public class ProdSecurityCheck {

    @Value("${pgds.jwt.secret}") private String secret;
    @Value("${pgds.seed-demo-data}") private boolean seedDemo;
    @Value("${pgds.ai.demo-endpoint-enabled}") private boolean demoEndpoint;

    @PostConstruct
    void verify() {
        if (secret == null || secret.length() < 32 || secret.startsWith("change-me") || secret.startsWith("replace-this"))
            throw new IllegalStateException("JWT_SECRET must be a random value of at least 32 characters in the prod profile");
        if (seedDemo) throw new IllegalStateException("Demo data seeding must be disabled in the prod profile");
        if (demoEndpoint) throw new IllegalStateException("The AI demo-data endpoint must be disabled in the prod profile");
    }
}
