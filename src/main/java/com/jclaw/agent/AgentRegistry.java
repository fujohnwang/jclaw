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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.jclaw.skill.SkillDef;
import com.jclaw.skill.SkillRegistry;

/**
 * Registry of configured agents. Each agent is built from config and equipped with tools.
 * Supports multiple LLM providers via LangChain4j integration.
 * Agents are lazily rebuilt when available skills change.
 */
public final class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);

    private final Map<String, BaseAgent> agents = new ConcurrentHashMap<>();
    private final Map<String, JClawConfig.AgentDef> agentDefs = new ConcurrentHashMap<>();
    private final Map<String, JClawConfig.ModelDef> modelDefs = new ConcurrentHashMap<>();
    private final SkillRegistry skillRegistry;
    private volatile long lastSkillVersion;

    public AgentRegistry(JClawConfig config, SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
        this.lastSkillVersion = skillRegistry.version();
        // Index models by id
        for (var m : config.models()) {
            modelDefs.put(m.id(), m);
        }
        for (var def : config.agents().list()) {
            var modelDef = resolveModelDef(def);
            validateConfig(def, modelDef);
            var agent = buildAgent(def, modelDef);
            agents.put(def.id(), agent);
            agentDefs.put(def.id(), def);
        }
        log.info("AgentRegistry initialized: {} agent(s), {} model(s) registered", agents.size(), modelDefs.size());
    }

    private JClawConfig.ModelDef resolveModelDef(JClawConfig.AgentDef def) {
        var modelDef = modelDefs.get(def.modelId());
        if (modelDef == null) {
            throw new IllegalStateException(
                    "Agent '%s': references unknown modelId '%s'".formatted(def.id(), def.modelId()));
        }
        return modelDef;
    }

    /**
     * Validate agent config at startup. Fail fast on misconfiguration.
     */
    private void validateConfig(JClawConfig.AgentDef def, JClawConfig.ModelDef modelDef) {
        if (def.id() == null || def.id().isBlank()) {
            throw new IllegalStateException("Agent config missing 'id'");
        }
        String provider = modelDef.provider() != null ? modelDef.provider() : "gemini";
        switch (provider) {
            case "gemini" -> {
                String gkey = System.getenv("GOOGLE_API_KEY");
                if (gkey == null || gkey.isBlank()) {
                    log.warn("Agent '{}': model '{}' (gemini) — env var GOOGLE_API_KEY is not set, ADK may fail at runtime",
                            def.id(), modelDef.id());
                }
            }
            case "openai", "anthropic" -> {
                if (modelDef.baseUrl() == null || modelDef.baseUrl().isBlank()) {
                    throw new IllegalStateException(
                            "Model '%s': provider '%s' requires 'baseUrl'".formatted(modelDef.id(), provider));
                }
                if (modelDef.apiKeyEnvVar() == null || modelDef.apiKeyEnvVar().isBlank()) {
                    throw new IllegalStateException(
                            "Model '%s': provider '%s' requires 'apiKeyEnvVar'".formatted(modelDef.id(), provider));
                }
                String apiKey = System.getenv(modelDef.apiKeyEnvVar());
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalStateException(
                            "Model '%s': env var '%s' is not set".formatted(modelDef.id(), modelDef.apiKeyEnvVar()));
                }
            }
            case "ollama" -> {
                if (modelDef.baseUrl() == null || modelDef.baseUrl().isBlank()) {
                    throw new IllegalStateException(
                            "Model '%s': provider 'ollama' requires 'baseUrl'".formatted(modelDef.id()));
                }
            }
            default -> throw new IllegalStateException(
                    "Model '%s': unknown provider '%s' (valid: gemini, openai, anthropic, ollama)".formatted(modelDef.id(), provider));
        }
        if (modelDef.model() == null || modelDef.model().isBlank()) {
            throw new IllegalStateException("Model '%s': missing 'model'".formatted(modelDef.id()));
        }
        log.debug("Agent '{}' config validated: modelId={}, provider={}, model={}",
                def.id(), modelDef.id(), provider, modelDef.model());
    }

    private BaseAgent buildAgent(JClawConfig.AgentDef def, JClawConfig.ModelDef modelDef) {
        String model = modelDef.model();
        BaseLlm resolvedLlm = resolveLlm(def, modelDef);

        String instruction = def.instruction() != null ? def.instruction() : "You are a helpful assistant.";
        instruction = injectSkillCatalog(instruction, def);

        var builder = LlmAgent.builder()
                .name(def.id())
                .description("JClaw agent: " + def.id())
                .instruction(instruction)
                .tools(ExecTool.create(), ReadFileTool.create(), WriteFileTool.create());

        if (resolvedLlm != null) {
            builder.model(resolvedLlm);
        } else {
            builder.model(model);
        }

        log.info("Built agent '{}' with modelId={}, model={}", def.id(), modelDef.id(), model);
        return builder.build();
    }

    private BaseLlm resolveLlm(JClawConfig.AgentDef def, JClawConfig.ModelDef modelDef) {
        String provider = modelDef.provider() != null ? modelDef.provider() : "gemini";
        String model = modelDef.model();
        String apiKey = resolveApiKey(modelDef);
        String baseUrl = modelDef.baseUrl();

        return switch (provider) {
            case "gemini" -> {
                log.info("Agent '{}': using Gemini (native), model={}", def.id(), model);
                yield null;
            }
            case "ollama" -> {
                log.info("Agent '{}': using Ollama at {}, model={}", def.id(), baseUrl, model);
                yield new LangChain4j(
                        OllamaChatModel.builder().modelName(model).baseUrl(baseUrl).build());
            }
            case "anthropic" -> {
                log.info("Agent '{}': using Anthropic at {}, model={}", def.id(), baseUrl, model);
                yield new LangChain4j(
                        AnthropicChatModel.builder().apiKey(apiKey).modelName(model).baseUrl(baseUrl).build(),
                        model);
            }
            case "openai" -> {
                log.info("Agent '{}': using OpenAI-compatible at {}, model={}", def.id(), baseUrl, model);
                yield new LangChain4j(
                        OpenAiChatModel.builder().apiKey(apiKey).modelName(model).baseUrl(baseUrl).build());
            }
            default -> throw new IllegalStateException(
                    "Model '%s': unknown provider '%s'".formatted(modelDef.id(), provider));
        };
    }

    private String resolveApiKey(JClawConfig.ModelDef modelDef) {
        if (modelDef.apiKeyEnvVar() == null || modelDef.apiKeyEnvVar().isBlank()) return null;
        return System.getenv(modelDef.apiKeyEnvVar());
    }

    public BaseAgent getAgent(String agentId) {
        checkSkillVersion();
        return agents.get(agentId);
    }

    public JClawConfig.AgentDef getAgentDef(String agentId) {
        return agentDefs.get(agentId);
    }

    public boolean hasAgent(String agentId) {
        return agents.containsKey(agentId);
    }

    // ── Skills integration ──────────────────────────────────────────────

    /**
     * If skills have changed since last build, rebuild all agents.
     */
    private void checkSkillVersion() {
        long current = skillRegistry.version();
        if (current != lastSkillVersion) {
            log.info("Skills changed (version {} → {}), rebuilding agents...", lastSkillVersion, current);
            lastSkillVersion = current;
            for (var entry : agentDefs.entrySet()) {
                var modelDef = resolveModelDef(entry.getValue());
                agents.put(entry.getKey(), buildAgent(entry.getValue(), modelDef));
            }
        }
    }

    /**
     * Append available skills catalog to the agent instruction.
     */
    private String injectSkillCatalog(String instruction, JClawConfig.AgentDef def) {
        List<SkillDef> available = skillRegistry.resolveSkills(def.skills());
        if (available.isEmpty()) return instruction;

        String catalog = available.stream()
                .map(s -> "- %s: %s".formatted(s.name(), s.description()))
                .collect(Collectors.joining("\n"));

        return instruction + "\n\n## Available Skills\n"
                + "You have access to the following skills. "
                + "When a task matches a skill, read its full instructions from the skill directory before proceeding.\n"
                + catalog;
    }
}
