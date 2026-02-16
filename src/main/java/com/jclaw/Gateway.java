package com.jclaw;

import com.jclaw.agent.AgentRegistry;
import com.jclaw.agent.AgentRunner;
import com.jclaw.channel.Channel;
import com.jclaw.config.JClawConfig;
import com.jclaw.routing.RouteResolver;
import com.jclaw.routing.RouteResolver.MessageContext;
import com.jclaw.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Gateway — central orchestrator that wires channels, routing, sessions, and agents together.
 * This is the JClaw equivalent of OpenClaw's gateway daemon.
 */
public final class Gateway {

    private static final Logger log = LoggerFactory.getLogger(Gateway.class);

    private final JClawConfig config;
    private final RouteResolver router;
    private final SessionManager sessionManager;
    private final AgentRegistry agentRegistry;
    private final AgentRunner agentRunner;

    public Gateway(JClawConfig config) {
        this.config = config;
        this.router = new RouteResolver(config);
        this.sessionManager = new SessionManager(config.session());
        this.agentRegistry = new AgentRegistry(config);
        this.agentRunner = new AgentRunner(
                agentRegistry,
                sessionManager,
                config.agents().defaults().maxConcurrent()
        );
    }

    /**
     * Start the gateway with the given channel.
     * The channel's message handler routes messages through the full pipeline:
     * channel → routing → session → agent → response.
     */
    public void start(Channel channel) {
        log.info("JClaw Gateway starting on channel: {}", channel.id());
        log.info("Default agent: {}", config.agents().defaultAgent());
        log.info("Configured agents: {}",
                config.agents().list().stream().map(JClawConfig.AgentDef::id).toList());

        channel.start((senderId, text) -> handleMessage(channel.id(), senderId, text));
    }

    /**
     * Full message handling pipeline:
     * 1. Build message context
     * 2. Route to agent via bindings
     * 3. Resolve session key
     * 4. Run agent turn (with concurrency control)
     * 5. Return response
     */
    private String handleMessage(String channelId, String senderId, String text) {
        // 1. Build routing context
        var ctx = new MessageContext(
                channelId, "default", null, "direct",
                null, null, java.util.List.of(), senderId
        );

        // 2. Resolve target agent
        String agentId = router.resolve(ctx);
        if (!agentRegistry.hasAgent(agentId)) {
            log.warn("Routed to unknown agent '{}', falling back to default", agentId);
            agentId = config.agents().defaultAgent();
        }

        // 3. Resolve session key
        String sessionKey = sessionManager.resolveSessionKey(
                agentId, channelId, ctx.peerKind(), senderId
        );

        log.debug("Message: channel={}, sender={}, agent={}, session={}",
                channelId, senderId, agentId, sessionKey);

        // 4. Run agent turn (virtual thread handles blocking)
        return agentRunner.run(agentId, sessionKey, text);
    }
}
