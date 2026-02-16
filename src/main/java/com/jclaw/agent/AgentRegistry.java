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
            var agent = buildAgent(def);
            agents.put(def.id(), agent);
            agentDefs.put(def.id(), def);
        }
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
