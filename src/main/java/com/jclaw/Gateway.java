package com.jclaw;

import com.jclaw.agent.AgentRegistry;
import com.jclaw.agent.AgentRunner;
import com.jclaw.channel.Channel;
import com.jclaw.config.JClawConfig;
import com.jclaw.routing.RouteResolver;
import com.jclaw.session.SessionManager;
import com.jclaw.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * The Gateway — central orchestrator that wires channels, routing, sessions, and agents together.
 * This is the JClaw equivalent of OpenClaw's gateway daemon.
 */
public final class Gateway {

    private static final Logger log = LoggerFactory.getLogger(Gateway.class);

    private final JClawConfig config;
    private final RouteResolver router;
    private final SessionManager sessionManager;
    private final SkillRegistry skillRegistry;
    private final AgentRegistry agentRegistry;
    private final AgentRunner agentRunner;

    public Gateway(JClawConfig config) {
        this.config = config;
        this.router = new RouteResolver(config);
        this.sessionManager = new SessionManager(config.session());
        this.skillRegistry = new SkillRegistry(
                Path.of(System.getProperty("user.home"), ".jclaw", "skills"));
        this.agentRegistry = new AgentRegistry(config, skillRegistry);
        this.agentRunner = new AgentRunner(
                agentRegistry,
                sessionManager,
                config.agents().defaults().maxConcurrent(),
                config.gateway().agentTimeoutSeconds(),
                config.gateway().shutdownTimeoutSeconds()
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
     * Gracefully shut down all gateway resources.
     */
    public void shutdown() {
        log.info("Gateway shutting down...");
        agentRunner.shutdown();
        log.info("Gateway shut down complete");
    }

    /**
     * Message handling pipeline:
     * 1. Route to agent by channel
     * 2. Resolve session key
     * 3. Run agent turn (with concurrency control)
     */
    private String handleMessage(String channelId, String senderId, String text) {
        // 1. Resolve target agent by channel
        String agentId = router.resolve(channelId);
        if (!agentRegistry.hasAgent(agentId)) {
            log.warn("Routed to unknown agent '{}', falling back to default", agentId);
            agentId = config.agents().defaultAgent();
        }

        // 2. Resolve session key
        String sessionKey = sessionManager.resolveSessionKey(
                agentId, channelId, "direct", senderId
        );

        log.debug("Message: channel={}, sender={}, agent={}, session={}",
                channelId, senderId, agentId, sessionKey);

        // 3. Run agent turn (virtual thread handles blocking)
        return agentRunner.run(agentId, sessionKey, text);
    }
}
