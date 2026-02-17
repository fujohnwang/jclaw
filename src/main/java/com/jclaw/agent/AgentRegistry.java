package com.jclaw.agent;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.langchain4j.LangChain4j;
import com.jclaw.config.JClawConfig;
import com.jclaw.tool.ExecTool;
import com.jclaw.tool.ReadFileTool;
import com.jclaw.tool.WriteFileTool;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of configured agents. Each agent is built from config and equipped with tools.
 * Supports multiple LLM providers via LangChain4j integration.
 */
public final class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);

    private final Map<String, BaseAgent> agents = new ConcurrentHashMap<>();
    private final Map<String, JClawConfig.AgentDef> agentDefs = new ConcurrentHashMap<>();

    public AgentRegistry(JClawConfig config) {
        for (var def : config.agents().list()) {
            validateConfig(def);
            var agent = buildAgent(def);
            agents.put(def.id(), agent);
            agentDefs.put(def.id(), def);
        }
        log.info("AgentRegistry initialized: {} agent(s) registered", agents.size());
    }

    /**
     * Validate agent config at startup. Fail fast on misconfiguration.
     */
    private void validateConfig(JClawConfig.AgentDef def) {
        if (def.id() == null || def.id().isBlank()) {
            throw new IllegalStateException("Agent config missing 'id'");
        }
        String provider = def.provider() != null ? def.provider() : "gemini";
        switch (provider) {
            case "gemini" -> {
                // Gemini reads GOOGLE_API_KEY internally via ADK, just warn if not set
                String gkey = System.getenv("GOOGLE_API_KEY");
                if (gkey == null || gkey.isBlank()) {
                    log.warn("Agent '{}': provider 'gemini' — env var GOOGLE_API_KEY is not set, ADK may fail at runtime", def.id());
                }
            }
            case "openai", "anthropic" -> {
                if (def.baseUrl() == null || def.baseUrl().isBlank()) {
                    throw new IllegalStateException(
                            "Agent '%s': provider '%s' requires 'baseUrl'".formatted(def.id(), provider));
                }
                if (def.apiKeyEnvVar() == null || def.apiKeyEnvVar().isBlank()) {
                    throw new IllegalStateException(
                            "Agent '%s': provider '%s' requires 'apiKeyEnvVar'".formatted(def.id(), provider));
                }
                String apiKey = System.getenv(def.apiKeyEnvVar());
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalStateException(
                            "Agent '%s': env var '%s' is not set".formatted(def.id(), def.apiKeyEnvVar()));
                }
            }
            case "ollama" -> {
                if (def.baseUrl() == null || def.baseUrl().isBlank()) {
                    throw new IllegalStateException(
                            "Agent '%s': provider 'ollama' requires 'baseUrl'".formatted(def.id()));
                }
            }
            default -> throw new IllegalStateException(
                    "Agent '%s': unknown provider '%s' (valid: gemini, openai, anthropic, ollama)".formatted(def.id(), provider));
        }
        if (def.model() == null || def.model().isBlank()) {
            throw new IllegalStateException("Agent '%s': missing 'model'".formatted(def.id()));
        }
        log.debug("Agent '{}' config validated: provider={}, model={}", def.id(), provider, def.model());
    }

    private BaseAgent buildAgent(JClawConfig.AgentDef def) {
        String model = def.model() != null ? def.model() : "gemini-2.5-flash";
        BaseLlm resolvedLlm = resolveLlm(def, model);

        var builder = LlmAgent.builder()
                .name(def.id())
                .description("JClaw agent: " + def.id())
                .instruction(def.instruction() != null ? def.instruction() : "You are a helpful assistant.")
                .tools(ExecTool.create(), ReadFileTool.create(), WriteFileTool.create());

        if (resolvedLlm != null) {
            builder.model(resolvedLlm);
        } else {
            builder.model(model);
        }

        log.info("Built agent '{}' with model: {}", def.id(), model);
        return builder.build();
    }

    /**
     * Resolve the LLM based on provider config.
     * - gemini → Gemini native (return null, use model string)
     * - ollama → Ollama client via LangChain4j
     * - anthropic → Anthropic native API via LangChain4j
     * - openai (default for baseUrl) → OpenAI-compatible via LangChain4j
     */
    private BaseLlm resolveLlm(JClawConfig.AgentDef def, String model) {
        String provider = def.provider() != null ? def.provider() : "gemini";
        String apiKey = resolveApiKey(def);
        String baseUrl = def.baseUrl();

        return switch (provider) {
            case "gemini" -> {
                log.info("Agent '{}': using Gemini (native), model={}", def.id(), model);
                yield null;
            }
            case "ollama" -> {
                if (baseUrl == null || baseUrl.isBlank()) {
                    throw new IllegalStateException(
                            "Agent '%s': provider 'ollama' requires baseUrl".formatted(def.id()));
                }
                log.info("Agent '{}': using Ollama at {}, model={}", def.id(), baseUrl, model);
                yield new LangChain4j(
                        OllamaChatModel.builder().modelName(model).baseUrl(baseUrl).build());
            }
            case "anthropic" -> {
                requireApiKey(def, apiKey, model);
                if (baseUrl == null || baseUrl.isBlank()) {
                    throw new IllegalStateException(
                            "Agent '%s': provider 'anthropic' requires baseUrl".formatted(def.id()));
                }
                log.info("Agent '{}': using Anthropic at {}, model={}", def.id(), baseUrl, model);
                yield new LangChain4j(
                        AnthropicChatModel.builder().apiKey(apiKey).modelName(model).baseUrl(baseUrl).build(),
                        model);
            }
            case "openai" -> {
                requireApiKey(def, apiKey, model);
                if (baseUrl == null || baseUrl.isBlank()) {
                    throw new IllegalStateException(
                            "Agent '%s': provider 'openai' requires baseUrl".formatted(def.id()));
                }
                log.info("Agent '{}': using OpenAI-compatible at {}, model={}", def.id(), baseUrl, model);
                yield new LangChain4j(
                        OpenAiChatModel.builder().apiKey(apiKey).modelName(model).baseUrl(baseUrl).build());
            }
            default -> throw new IllegalStateException(
                    "Agent '%s': unknown provider '%s'".formatted(def.id(), provider));
        };
    }

    private String resolveApiKey(JClawConfig.AgentDef def) {
        if (def.apiKeyEnvVar() == null || def.apiKeyEnvVar().isBlank()) return null;
        return System.getenv(def.apiKeyEnvVar());
    }

    private void requireApiKey(JClawConfig.AgentDef def, String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Agent '%s' uses model '%s' but API key is not set (env var: %s)"
                            .formatted(def.id(), model, def.apiKeyEnvVar()));
        }
    }

    public BaseAgent getAgent(String agentId) {
        return agents.get(agentId);
    }

    public JClawConfig.AgentDef getAgentDef(String agentId) {
        return agentDefs.get(agentId);
    }

    public boolean hasAgent(String agentId) {
        return agents.containsKey(agentId);
    }
}
