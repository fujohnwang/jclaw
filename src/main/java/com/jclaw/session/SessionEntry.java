package com.jclaw.session;

import java.time.Instant;

/**
 * A single message entry in a session transcript.
 */
public record SessionEntry(
        String role,       // "user", "assistant", "system", "tool"
        String content,
        Instant timestamp,
        String toolCallId,
        String toolName
) {
    public static SessionEntry user(String content) {
        return new SessionEntry("user", content, Instant.now(), null, null);
    }

    public static SessionEntry assistant(String content) {
        return new SessionEntry("assistant", content, Instant.now(), null, null);
    }

    public static SessionEntry system(String content) {
        return new SessionEntry("system", content, Instant.now(), null, null);
    }
}
