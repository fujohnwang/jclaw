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
     * Resolve the LLM based on config.
     * - No baseUrl → Gemini native (return null, use model string)
     * - baseUrl + ollama=true → Ollama client
     * - baseUrl + model starts with "anthropic/" → Anthropic native API
     * - baseUrl + apiKey → OpenAI-compatible (OpenRouter, OpenAI, vLLM, etc.)
     * Model names follow openrouter convention: "anthropic/claude-opus-4.6", "openai/gpt-4o", etc.
     */
    private BaseLlm resolveLlm(JClawConfig.AgentDef def, String model) {
        String apiKey = resolveApiKey(def);
        String baseUrl = def.baseUrl();

        // No baseUrl → Gemini native
        if (baseUrl == null || baseUrl.isBlank()) {
            log.info("Agent '{}': using Gemini (native), model={}", def.id(), model);
            return null;
        }

        // Ollama
        if (def.ollama()) {
            log.info("Agent '{}': using Ollama at {}, model={}", def.id(), baseUrl, model);
            return new LangChain4j(
                    OllamaChatModel.builder().modelName(model).baseUrl(baseUrl).build());
        }

        requireApiKey(def, apiKey, model);

        // Anthropic: model starts with "anthropic/"
        if (model.startsWith("anthropic/")) {
            String anthropicModel = model.substring("anthropic/".length());
            log.info("Agent '{}': using Anthropic API at {}, model={}", def.id(), baseUrl, anthropicModel);
            var builder = AnthropicChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(anthropicModel)
                    .baseUrl(baseUrl);
            return new LangChain4j(builder.build(), anthropicModel);
        }

        // Everything else: OpenAI-compatible
        log.info("Agent '{}': using OpenAI-compatible at {}, model={}", def.id(), baseUrl, model);
        return new LangChain4j(
                OpenAiChatModel.builder().apiKey(apiKey).modelName(model).baseUrl(baseUrl).build());
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
