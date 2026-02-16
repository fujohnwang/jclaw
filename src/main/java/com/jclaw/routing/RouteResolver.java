package com.jclaw.routing;

import com.jclaw.config.JClawConfig;
import com.jclaw.config.JClawConfig.BindingConfig;
import com.jclaw.config.JClawConfig.MatchCondition;

import java.util.List;

/**
 * Deterministic message routing — resolves which agent handles an incoming message.
 * Mirrors OpenClaw's resolveAgentRoute() with priority-based matching.
 */
public final class RouteResolver {

    private final List<BindingConfig> bindings;
    private final String defaultAgentId;

    public RouteResolver(JClawConfig config) {
        this.bindings = config.bindings() != null ? config.bindings() : List.of();
        this.defaultAgentId = config.agents().defaultAgent();
    }

    /**
     * Resolve the target agent for an incoming message.
     * Priority (high → low): peer exact → guild+roles → guild → team → accountId → channel → default.
     */
    public String resolve(MessageContext ctx) {
        // Pass 1: exact peer match
        for (var binding : bindings) {
            if (matchesPeer(binding.match(), ctx)) {
                return binding.agentId();
            }
        }
        // Pass 2: guild + roles (Discord-style)
        for (var binding : bindings) {
            if (matchesGuildRoles(binding.match(), ctx)) {
                return binding.agentId();
            }
        }
        // Pass 3: guild only
        for (var binding : bindings) {
            if (matchesGuildOnly(binding.match(), ctx)) {
                return binding.agentId();
            }
        }
        // Pass 4: team (Slack-style)
        for (var binding : bindings) {
            if (matchesTeam(binding.match(), ctx)) {
                return binding.agentId();
            }
        }
        // Pass 5: accountId
        for (var binding : bindings) {
            if (matchesAccount(binding.match(), ctx)) {
                return binding.agentId();
            }
        }
        // Pass 6: channel level
        for (var binding : bindings) {
            if (matchesChannel(binding.match(), ctx)) {
                return binding.agentId();
            }
        }
        return defaultAgentId;
    }

    private boolean matchesPeer(MatchCondition m, MessageContext ctx) {
        return m.peerId() != null
                && m.peerId().equals(ctx.peerId())
                && (m.peerKind() == null || m.peerKind().equals(ctx.peerKind()))
                && channelMatches(m, ctx);
    }

    private boolean matchesGuildRoles(MatchCondition m, MessageContext ctx) {
        return m.guildId() != null
                && m.guildId().equals(ctx.guildId())
                && m.roles() != null && !m.roles().isEmpty()
                && ctx.roles() != null
                && ctx.roles().stream().anyMatch(m.roles()::contains)
                && channelMatches(m, ctx);
    }

    private boolean matchesGuildOnly(MatchCondition m, MessageContext ctx) {
        return m.guildId() != null
                && m.guildId().equals(ctx.guildId())
                && (m.roles() == null || m.roles().isEmpty())
                && channelMatches(m, ctx);
    }

    private boolean matchesTeam(MatchCondition m, MessageContext ctx) {
        return m.teamId() != null
                && m.teamId().equals(ctx.teamId())
                && channelMatches(m, ctx);
    }

    private boolean matchesAccount(MatchCondition m, MessageContext ctx) {
        return m.accountId() != null
                && (m.accountId().equals("*") || m.accountId().equals(ctx.accountId()))
                && m.peerId() == null && m.guildId() == null && m.teamId() == null
                && channelMatches(m, ctx);
    }

    private boolean matchesChannel(MatchCondition m, MessageContext ctx) {
        return m.channel() != null
                && m.channel().equals(ctx.channel())
                && m.accountId() == null && m.peerId() == null
                && m.guildId() == null && m.teamId() == null;
    }

    private boolean channelMatches(MatchCondition m, MessageContext ctx) {
        return m.channel() == null || m.channel().equals(ctx.channel());
    }

    /**
     * Context of an incoming message used for routing.
     */
    public record MessageContext(
            String channel,
            String accountId,
            String peerId,
            String peerKind,
            String guildId,
            String teamId,
            List<String> roles,
            String senderId
    ) {
        /** Convenience constructor for simple CLI messages. */
        public static MessageContext cli(String senderId) {
            return new MessageContext("cli", "default", null, "direct", null, null, List.of(), senderId);
        }
    }
}
