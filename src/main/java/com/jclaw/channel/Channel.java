package com.jclaw.channel;

/**
 * A message channel — the surface through which users interact with agents.
 */
public interface Channel {

    /** Channel identifier (e.g. "cli", "telegram", "discord"). */
    String id();

    /** Start listening for messages. Blocks until shutdown. */
    void start(MessageHandler handler);

    /** Send a reply back through this channel. */
    void send(String to, String message);

    /** Graceful shutdown. */
    void stop();

    @FunctionalInterface
    interface MessageHandler {
        /**
         * Called when a message arrives.
         * @param senderId who sent it
         * @param text     message content
         * @return agent's reply text
         */
        String onMessage(String senderId, String text);
    }
}
