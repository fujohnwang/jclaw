package com.jclaw.session;

import com.jclaw.config.JClawConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages session keys and conversation history.
 * Mirrors OpenClaw's session key generation logic with configurable dmScope.
 */
public final class SessionManager {

    private final JClawConfig.SessionConfig config;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SessionEntry>> sessions = new ConcurrentHashMap<>();

    public SessionManager(JClawConfig.SessionConfig config) {
        this.config = config;
    }

    /**
     * Build a session key following OpenClaw conventions:
     * - dmScope "main": agent:{agentId}:main
     * - dmScope "per-channel-peer": agent:{agentId}:{channel}:direct:{peerId}
     * - group: agent:{agentId}:{channel}:group:{groupId}
     */
    public String resolveSessionKey(String agentId, String channel, String peerKind, String peerId) {
        if ("group".equals(peerKind) && peerId != null) {
            return "agent:" + agentId + ":" + channel + ":group:" + peerId;
        }
        if ("per-channel-peer".equals(config.dmScope()) && peerId != null) {
            return "agent:" + agentId + ":" + channel + ":direct:" + peerId;
        }
        // default: main session
        return "agent:" + agentId + ":main";
    }

    public void append(String sessionKey, SessionEntry entry) {
        sessions.computeIfAbsent(sessionKey, _ -> new CopyOnWriteArrayList<>()).add(entry);
    }

    public List<SessionEntry> getHistory(String sessionKey) {
        return sessions.getOrDefault(sessionKey, new CopyOnWriteArrayList<>());
    }

    public void clear(String sessionKey) {
        sessions.remove(sessionKey);
    }

    /**
     * Persist session to JSONL file (simple implementation).
     */
    public void persist(String sessionKey) throws IOException {
        String storePath = config.store().replace("~", System.getProperty("user.home"));
        Path dir = Path.of(storePath);
        Files.createDirectories(dir);

        String safeKey = sessionKey.replace(":", "_");
        Path file = dir.resolve(safeKey + ".jsonl");

        var history = getHistory(sessionKey);
        var lines = history.stream()
                .map(e -> "{\"role\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\"}"
                        .formatted(e.role(), escapeJson(e.content()), e.timestamp()))
                .toList();
        Files.write(file, lines);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
