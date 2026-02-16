package com.jclaw.channel;

import java.util.Scanner;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * CLI-based channel — reads from stdin, writes to stdout.
 * The simplest possible message surface for testing.
 */
public final class CliChannel implements Channel {

    private static final String SENDER_ID = "cli-user";
    private volatile boolean running = true;

    @Override
    public String id() {
        return "cli";
    }

    @Override
    public void start(MessageHandler handler) {
        System.out.println("JClaw CLI — type your message (or 'quit' to exit)");
        System.out.println("─".repeat(50));

        try (Scanner scanner = new Scanner(System.in, UTF_8)) {
            while (running && scanner.hasNextLine()) {
                System.out.print("\nYou > ");
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) continue;
                if ("quit".equalsIgnoreCase(input) || "exit".equalsIgnoreCase(input)) {
                    System.out.println("Bye!");
                    break;
                }

                String reply = handler.onMessage(SENDER_ID, input);
                System.out.println("\nAgent > " + reply);
            }
        }
    }

    @Override
    public void send(String to, String message) {
        System.out.println("\nAgent > " + message);
    }

    @Override
    public void stop() {
        running = false;
    }
}
