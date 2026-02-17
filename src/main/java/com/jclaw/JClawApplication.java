package com.jclaw;

import com.jclaw.channel.WebChatChannel;
import com.jclaw.config.ConfigLoader;
import com.jclaw.config.JClawConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * JClaw — Java port of OpenClaw core.
 * Entry point: loads config from ~/.jclaw/, creates gateway, starts WebChat server.
 *
 * Usage:
 *   java -jar jclaw.jar                  → starts WebChat server
 *   java -jar jclaw.jar --config path    → use custom config file
 */
public final class JClawApplication {

    private static final Logger log = LoggerFactory.getLogger(JClawApplication.class);

    public static void main(String[] args) {
        String configPath = null;

        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                configPath = args[++i];
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
        int port = config.gateway().port();
        String adminToken = config.gateway().adminToken();

        final WebChatChannel[] holder = new WebChatChannel[1];
        var channel = new WebChatChannel(port, adminToken, () -> {
            log.info("Initiating graceful shutdown...");
            holder[0].stop();        // 1. Stop accepting new HTTP requests
            gateway.shutdown();      // 2. Drain in-flight agent tasks, close executor
            log.info("All resources released. Exiting.");
            System.exit(0);
        });
        holder[0] = channel;

        gateway.start(channel);
    }
}
