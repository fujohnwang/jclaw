package com.jclaw;

import com.jclaw.channel.CliChannel;
import com.jclaw.channel.WebChatChannel;
import com.jclaw.config.ConfigLoader;
import com.jclaw.config.JClawConfig;

import java.nio.file.Path;

/**
 * JClaw — Java port of OpenClaw core.
 * Entry point: loads config from ~/.jclaw/, creates gateway, starts channels.
 *
 * Usage:
 *   jclaw                        → starts both CLI + WebChat (default)
 *   jclaw --cli-only             → starts CLI channel only
 *   jclaw --webchat-only         → starts WebChat channel only
 *   jclaw --config path          → use custom config file
 */
public final class JClawApplication {

    public static void main(String[] args) {
        boolean cli = true;
        boolean webchat = true;
        String configPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--cli-only" -> { cli = true; webchat = false; }
                case "--webchat-only" -> { cli = false; webchat = true; }
                case "--config" -> {
                    if (i + 1 < args.length) configPath = args[++i];
                }
            }
        }

        JClawConfig config;
        try {
            if (configPath != null) {
                ConfigLoader.ensureDefaults();
                config = ConfigLoader.load(Path.of(configPath));
                System.out.println("Loaded config from: " + configPath);
            } else {
                config = ConfigLoader.loadDefault();
                System.out.println("Loaded config from: " + ConfigLoader.DEFAULT_CONFIG_PATH);
            }
        } catch (Exception e) {
            System.err.println("Failed to load config: " + e.getMessage());
            return;
        }

        var gateway = new Gateway(config);

        // Start WebChat in a background virtual thread (non-blocking)
        if (webchat) {
            int port = config.gateway().port();
            Thread.startVirtualThread(() -> gateway.start(new WebChatChannel(port)));
        }

        // Start CLI on the main thread (blocks until quit)
        if (cli) {
            gateway.start(new CliChannel());
        } else {
            // If no CLI, block main thread to keep the process alive
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
