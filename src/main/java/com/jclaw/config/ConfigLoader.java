package com.jclaw.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads JClaw configuration from YAML.
 * Default working directory: ~/.jclaw/
 * Default config file: ~/.jclaw/jclaw-config.yaml
 */
public final class ConfigLoader {

    public static final Path JCLAW_HOME = Path.of(System.getProperty("user.home"), ".jclaw");
    public static final Path DEFAULT_CONFIG_PATH = JCLAW_HOME.resolve("jclaw-config.yaml");

    private ConfigLoader() {}

    /**
     * Ensure ~/.jclaw/ directory exists and default config is present.
     * Called at startup before loading config.
     */
    public static void ensureDefaults() throws IOException {
        if (!Files.exists(JCLAW_HOME)) {
            Files.createDirectories(JCLAW_HOME);
        }
        // Also ensure sessions subdirectory
        Path sessionsDir = JCLAW_HOME.resolve("sessions");
        if (!Files.exists(sessionsDir)) {
            Files.createDirectories(sessionsDir);
        }
        // Ensure skills directory
        Path skillsDir = JCLAW_HOME.resolve("skills");
        if (!Files.exists(skillsDir)) {
            Files.createDirectories(skillsDir);
        }
        // Write default config if not present
        if (!Files.exists(DEFAULT_CONFIG_PATH)) {
            Files.writeString(DEFAULT_CONFIG_PATH, DEFAULT_CONFIG_YAML);
        }
    }

    public static JClawConfig load(Path configPath) throws IOException {
        try (InputStream in = Files.newInputStream(configPath)) {
            return parse(in);
        }
    }

    /**
     * Load from the default location: ~/.jclaw/jclaw-config.yaml
     */
    public static JClawConfig loadDefault() throws IOException {
        ensureDefaults();
        return load(DEFAULT_CONFIG_PATH);
    }

    @SuppressWarnings("unchecked")
    private static JClawConfig parse(InputStream in) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(in);

        var gatewayMap = getMap(root, "gateway");
        var gateway = new JClawConfig.GatewayConfig(
                getInt(gatewayMap, "port", 8080),
                getString(gatewayMap, "adminToken", "jclaw-admin"),
                getInt(gatewayMap, "agentTimeoutSeconds", 60),
                getInt(gatewayMap, "shutdownTimeoutSeconds", 10)
        );

        var agentsMap = getMap(root, "agents");
        String defaultAgent = getString(agentsMap, "default", "assistant");
        var agentList = new ArrayList<JClawConfig.AgentDef>();
        var rawList = (List<Map<String, Object>>) agentsMap.getOrDefault("list", List.of());
        for (var entry : rawList) {
            agentList.add(new JClawConfig.AgentDef(
                    getString(entry, "id", ""),
                    getString(entry, "provider", "gemini"),
                    getString(entry, "model", "gemini-2.5-flash"),
                    getString(entry, "apiKeyEnvVar", null),
                    getString(entry, "baseUrl", null),
                    getString(entry, "instruction", ""),
                    getString(entry, "workspace", ""),
                    getStringList(entry, "skills")
            ));
        }
        var defaultsMap = getMap(agentsMap, "defaults");
        var defaults = new JClawConfig.AgentDefaults(
                getInt(defaultsMap, "maxConcurrent", 4)
        );
        var agents = new JClawConfig.AgentsConfig(defaultAgent, agentList, defaults);

        var bindingsList = new ArrayList<JClawConfig.BindingConfig>();
        var rawBindings = (List<Map<String, Object>>) root.getOrDefault("bindings", List.of());
        for (var b : rawBindings) {
            var filterMap = getMap(b, "filter");
            var filter = new java.util.HashMap<String, String>();
            for (var entry : filterMap.entrySet()) {
                filter.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
            }
            bindingsList.add(new JClawConfig.BindingConfig(
                    getString(b, "id", ""),
                    getString(b, "channel", ""),
                    getString(b, "agentId", ""),
                    filter.isEmpty() ? null : filter
            ));
        }

        var sessionMap = getMap(root, "session");
        var session = new JClawConfig.SessionConfig(
                getString(sessionMap, "store", JCLAW_HOME.resolve("sessions").toString()),
                getString(sessionMap, "dmScope", "main")
        );

        return new JClawConfig(gateway, agents, bindingsList, session);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> parent, String key) {
        Object val = parent.get(key);
        if (val instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static String getString(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    private static int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return defaultVal;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getStringList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    static final String DEFAULT_CONFIG_YAML = """
            # JClaw Gateway Configuration

            gateway:
              port: 8080
              adminToken: jclaw-admin
              agentTimeoutSeconds: 60
              shutdownTimeoutSeconds: 10

            agents:
              default: assistant
              list:
                - id: assistant
                  provider: gemini
                  model: gemini-2.5-flash
                  # apiKeyEnvVar: GOOGLE_API_KEY  # Gemini 通过 ADK 自动读取 GOOGLE_API_KEY 环境变量，无需显式配置
                  instruction: |
                    You are a helpful AI assistant. You can read and write files,
                    and execute shell commands when needed.
                  workspace: ~/.jclaw/workspace/assistant
                  # skills: [all]  # 可用 skills 列表，默认为空（不加载任何 skill），设为 [all] 加载全部

              defaults:
                maxConcurrent: 4

            bindings:
              - id: webchat-assistant
                channel: webchat
                agentId: assistant

            session:
              store: ~/.jclaw/sessions
              dmScope: main
            """;
}
