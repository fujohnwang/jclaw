# JClaw

Java 实现的多渠道 AI Agent 网关，灵感来自 [OpenClaw](https://github.com/anthropics/openclaw)。

## 概述

JClaw 是一个轻量级的 AI Agent 网关框架，负责将来自不同渠道的用户消息路由到对应的 AI Agent，并管理会话上下文。核心基于 [Google ADK (Agent Development Kit)](https://google.github.io/adk-java/) 构建。

## 架构

```
用户 ──→ Channel ──→ Gateway ──→ RouteResolver ──→ AgentRunner ──→ LLM
           │                                            │
           │                                      SessionManager
           │
     WebChatChannel
```

- **Channel** — 消息接入层，定义用户交互界面
- **Gateway** — 中央编排器，串联渠道、路由、会话和 Agent
- **RouteResolver** — 基于 binding 配置的确定性消息路由
- **SessionManager** — 会话管理，支持多种 scope（main / per-channel-peer / group）
- **AgentRunner** — Agent 执行器，基于虚拟线程的并发控制（session 内串行，session 间并行）
- **AgentRegistry** — Agent 注册中心，根据配置创建和管理 Agent 实例
- **SkillRegistry** — Agent Skills 注册中心，动态监控 `~/.jclaw/skills/` 目录变化

## 支持的渠道

| 渠道 | 说明 |
|------|------|
| WebChat | 浏览器聊天界面（内置 HTTP 服务器） |

## 技术栈

- Java 25+（虚拟线程）
- Google ADK 0.5.0
- LangChain4j（多 LLM Provider 支持）
- JDK 内置 HttpServer（WebChat，零额外依赖）
- SnakeYAML（配置解析）
- SLF4J + Logback（日志）
- Maven（构建）
- GraalVM native-image（可选，本地编译）

## 模型与 Agent 配置

模型定义和 Agent 配置分离，模型可被多个 Agent 复用。

### 模型定义

通过 `provider` 字段显式指定 LLM Provider：

| provider | 说明 | 必需字段 |
|----------|------|----------|
| `gemini` (默认) | Google Gemini 原生，通过 ADK 直接调用 | 无（ADK 自动读取 `GOOGLE_API_KEY` 环境变量） |
| `anthropic` | Anthropic 原生 API | `apiKeyEnvVar`, `baseUrl` |
| `openai` | OpenAI 兼容协议（适用于 OpenAI、OpenRouter、vLLM 等） | `apiKeyEnvVar`, `baseUrl` |
| `ollama` | 本地 Ollama | `baseUrl` |

`apiKeyEnvVar` 配置的是环境变量名（而非 API Key 本身），运行时从环境变量读取实际值。

### 配置示例

```yaml
gateway:
  port: 8080
  adminToken: jclaw-admin
  agentTimeoutSeconds: 60
  shutdownTimeoutSeconds: 10

models:
  - id: gemini-flash
    provider: gemini
    model: gemini-2.5-flash

  - id: claude
    provider: anthropic
    model: claude-sonnet-4-20250514
    apiKeyEnvVar: ANTHROPIC_API_KEY
    baseUrl: https://api.anthropic.com

  - id: gpt4o
    provider: openai
    model: gpt-4o
    apiKeyEnvVar: OPENROUTER_API_KEY
    baseUrl: https://openrouter.ai/api/v1

  - id: local-qwen
    provider: ollama
    model: qwen3:1.7b
    baseUrl: http://localhost:11434

agents:
  default: assistant
  list:
    - id: assistant
      modelId: gemini-flash
      instruction: |
        You are a helpful AI assistant.
      workspace: ~/.jclaw/workspace/assistant

    - id: coder
      modelId: claude
      instruction: |
        You are a coding assistant.

    - id: reviewer
      modelId: gpt4o
      instruction: |
        You are a code reviewer.

  defaults:
    maxConcurrent: 4

bindings:
  - id: webchat-assistant
    channel: webchat
    agentId: assistant

session:
  store: ~/.jclaw/sessions
  dmScope: main
```

## Agent Skills

JClaw 支持 [Agent Skills](https://agentskills.io) 规范。将 skill 目录放入 `~/.jclaw/skills/` 即可，运行时自动发现并注入到 Agent 上下文。

Agent 配置中通过 `skills` 字段控制可用范围：
- 不配置或为空：不加载任何 skill
- `[all]`：加载全部可用 skill
- `[skill-a, skill-b]`：只加载指定 skill

## 快速开始

详见 [QUICKSTART.md](QUICKSTART.md)

## License

MIT
