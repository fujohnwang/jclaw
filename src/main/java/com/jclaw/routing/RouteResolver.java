package com.jclaw.routing;

import com.jclaw.config.JClawConfig;
import com.jclaw.config.JClawConfig.BindingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Deterministic message routing — resolves which agent handles an incoming message.
 * Iterates bindings in order; first match on channel wins.
 * The optional {@code filter} field in BindingConfig is reserved for future
 * fine-grained routing (e.g. by peerId, teamId, roles) but not evaluated yet.
 */
public final class RouteResolver {

    private static final Logger log = LoggerFactory.getLogger(RouteResolver.class);

    private final List<BindingConfig> bindings;
    private final String defaultAgentId;

    public RouteResolver(JClawConfig config) {
        this.bindings = config.bindings() != null ? config.bindings() : List.of();
        this.defaultAgentId = config.agents().defaultAgent();
    }

    /**
     * Resolve the target agent for an incoming message.
     * Matches by channel name; first hit wins. Falls back to default agent.
     */
    public String resolve(String channel) {
        for (var binding : bindings) {
            if (binding.channel() != null && binding.channel().equals(channel)) {
                log.debug("Binding matched: id={}, channel={}, agentId={}",
                        binding.id(), channel, binding.agentId());
                return binding.agentId();
            }
        }
        log.debug("No binding matched for channel={}, using default agent={}", channel, defaultAgentId);
        return defaultAgentId;
    }
}
