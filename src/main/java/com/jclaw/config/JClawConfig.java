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
    public record GatewayConfig(int port) {
        public GatewayConfig() { this(8080); }
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
            String model,
            String instruction,
            String workspace
    ) {}

    public record AgentDefaults(int maxConcurrent) {
        public AgentDefaults() { this(4); }
    }

    public record BindingConfig(
            MatchCondition match,
            String agentId
    ) {}

    public record MatchCondition(
            String channel,
            String accountId,
            String peerId,
            String peerKind,
            String guildId,
            String teamId,
            List<String> roles
    ) {}

    public record SessionConfig(
            String store,
            String dmScope
    ) {
        public SessionConfig() { this("~/.jclaw/sessions", "main"); }
    }
}
