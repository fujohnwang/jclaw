package com.jclaw.agent;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.FunctionTool;
import com.jclaw.config.JClawConfig;
import com.jclaw.tool.ExecTool;
import com.jclaw.tool.ReadFileTool;
import com.jclaw.tool.WriteFileTool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of configured agents. Each agent is built from config and equipped with tools.
 */
public final class AgentRegistry {

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
        return LlmAgent.builder()
                .name(def.id())
                .description("JClaw agent: " + def.id())
                .instruction(def.instruction() != null ? def.instruction() : "You are a helpful assistant.")
                .model(def.model() != null ? def.model() : "gemini-2.5-flash")
                .tools(
                        ExecTool.create(),
                        ReadFileTool.create(),
                        WriteFileTool.create()
                )
                .build();
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
