package com.jclaw.config;

import java.util.List;
import java.util.Map;

/**
 * Root configuration model, mapped from jclaw-config.yaml.
 */
public record JClawConfig(
        GatewayConfig gateway,
        AgentsConfig agents,
        List<BindingConfig> bindings,
        SessionConfig session
) {
    public record GatewayConfig(int port, String adminToken, int agentTimeoutSeconds, int shutdownTimeoutSeconds) {
        public GatewayConfig() { this(8080, "jclaw-admin", 60, 10); }
    }

    public record AgentsConfig(
            String defaultAgent,
            List<AgentDef> list,
            AgentDefaults defaults
    ) {
        public AgentsConfig() { this("assistant", List.of(), new AgentDefaults()); }

        // Custom key mapping: "default" in YAML → defaultAgent field
    }

    public record AgentDef(
            String id,
            String provider,
            String model,
            String apiKeyEnvVar,
            String baseUrl,
            String instruction,
            String workspace,
            List<String> skills
    ) {}

    public record AgentDefaults(int maxConcurrent) {
        public AgentDefaults() { this(4); }
    }

    public record BindingConfig(
            String id,
            String channel,
            String agentId,
            Map<String, String> filter
    ) {}

    public record SessionConfig(
            String store,
            String dmScope
    ) {
        public SessionConfig() { this("~/.jclaw/sessions", "main"); }
    }
}
