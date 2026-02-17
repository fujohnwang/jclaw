package com.jclaw.agent;

import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.jclaw.session.SessionEntry;
import com.jclaw.session.SessionManager;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Orchestrates agent runs with concurrency control.
 * Uses virtual threads + per-session semaphore (serial within session)
 * and a global semaphore (bounded total concurrency).
 */
public final class AgentRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentRunner.class);

    private final AgentRegistry registry;
    private final SessionManager sessionManager;
    private final long agentTimeoutSeconds;
    private final long shutdownTimeoutSeconds;
    private final Semaphore globalConcurrency;
    private final Map<String, Semaphore> sessionLocks = new ConcurrentHashMap<>();
    private final Map<String, InMemoryRunner> runners = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AgentRunner(AgentRegistry registry, SessionManager sessionManager,
                       int maxConcurrent, int agentTimeoutSeconds, int shutdownTimeoutSeconds) {
        this.registry = registry;
        this.sessionManager = sessionManager;
        this.agentTimeoutSeconds = agentTimeoutSeconds;
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
        this.globalConcurrency = new Semaphore(maxConcurrent);
    }

    /**
     * Run an agent turn for the given message. Blocks the calling virtual thread.
     * Same session is serialized; different sessions run concurrently up to maxConcurrent.
     */
    public String run(String agentId, String sessionKey, String userMessage) {
        var agent = registry.getAgent(agentId);
        if (agent == null) {
            return "[error] Unknown agent: " + agentId;
        }

        // Per-session lock ensures serial execution within a session
        var sessionLock = sessionLocks.computeIfAbsent(sessionKey, _ -> new Semaphore(1));

        sessionLock.acquireUninterruptibly();
        try {
            globalConcurrency.acquireUninterruptibly();
            try {
                Future<String> future = executor.submit(() -> executeAgentTurn(agentId, sessionKey, userMessage));
                try {
                    return future.get(agentTimeoutSeconds, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    log.error("Agent timeout after {}s: agent={}, session={}", agentTimeoutSeconds, agentId, sessionKey);
                    throw new RuntimeException("Agent '%s' timed out after %ds".formatted(agentId, agentTimeoutSeconds));
                } catch (ExecutionException e) {
                    throw new RuntimeException(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Agent execution interrupted", e);
                }
            } finally {
                globalConcurrency.release();
            }
        } finally {
            sessionLock.release();
        }
    }

    private String executeAgentTurn(String agentId, String sessionKey, String userMessage) {
        try {
            // Record user message
            sessionManager.append(sessionKey, SessionEntry.user(userMessage));

            var runner = runners.computeIfAbsent(agentId, id -> {
                var agent = registry.getAgent(id);
                return new InMemoryRunner(agent);
            });

            // Create or reuse ADK session (keyed by sessionKey as userId for simplicity)
            Session session = runner.sessionService()
                    .createSession(runner.appName(), sessionKey)
                    .blockingGet();

            Content userMsg = Content.fromParts(Part.fromText(userMessage));
            RunConfig runConfig = RunConfig.builder().build();

            Flowable<Event> events = runner.runAsync(session.userId(), session.id(), userMsg, runConfig);

            // Collect final response
            var responseBuilder = new StringBuilder();
            events.blockingForEach(event -> {
                if (event.finalResponse()) {
                    String text = event.stringifyContent();
                    if (text != null && !text.isBlank()) {
                        responseBuilder.append(text);
                    }
                }
            });

            String response = responseBuilder.toString().trim();
            if (response.isEmpty()) {
                response = "[no response from agent]";
            }

            // Record assistant response
            sessionManager.append(sessionKey, SessionEntry.assistant(response));

            log.debug("Agent turn complete: agent={}, session={}, responseLen={}",
                    agentId, sessionKey, response.length());

            return response;
        } catch (Exception e) {
            log.error("Agent execution failed: agent={}, session={}, error={}", agentId, sessionKey, e.getMessage(), e);
            throw new RuntimeException("Agent '%s' failed: %s".formatted(agentId, e.getMessage()), e);
        }
    }

    /**
     * Gracefully shut down the executor, waiting for in-flight tasks.
     */
    public void shutdown() {
        log.info("AgentRunner shutting down...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                log.warn("AgentRunner forced shutdown after {}s timeout", shutdownTimeoutSeconds);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("AgentRunner shut down complete");
    }
}
