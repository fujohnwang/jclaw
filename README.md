# JClaw

Java 实现的多渠道 AI Agent 网关，灵感来自 [OpenClaw](https://github.com/anthropics/openclaw)。

## 概述

JClaw 是一个轻量级的 AI Agent 网关框架，负责将来自不同渠道（CLI、Web 等）的用户消息路由到对应的 AI Agent，并管理会话上下文。核心基于 [Google ADK (Agent Development Kit)](https://google.github.io/adk-java/) 构建。

## 架构

```
用户 ──→ Channel ──→ Gateway ──→ RouteResolver ──→ AgentRunner ──→ LLM
           │                                            │
           │                                      SessionManager
           │
     WebChatChannel
```

- **Channel** — 消息接入层，定义用户交互界面（CLI 终端、Web 聊天页面等）
- **Gateway** — 中央编排器，串联渠道、路由、会话和 Agent
- **RouteResolver** — 基于 binding 配置的确定性消息路由，支持按渠道、peer、guild、team 等维度匹配
- **SessionManager** — 会话管理，支持多种 scope（main / per-channel-peer / group）
- **AgentRunner** — Agent 执行器，基于虚拟线程的并发控制（session 内串行，session 间并行）
- **AgentRegistry** — Agent 注册中心，根据配置创建和管理 Agent 实例

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
- SLF4J（日志）
- Maven（构建）
- GraalVM native-image（可选，本地编译）

## 模型 Provider 选择逻辑

Agent 配置中通过 `provider` 字段显式指定 LLM Provider：

| provider | 说明 | 必需字段 |
|----------|------|----------|
| `gemini` (默认) | Google Gemini 原生，通过 ADK 直接调用 | 无（ADK 自动读取 `GOOGLE_API_KEY` 环境变量） |
| `anthropic` | Anthropic 原生 API | `apiKeyEnvVar`, `baseUrl` |
| `openai` | OpenAI 兼容协议（适用于 OpenAI、OpenRouter、vLLM 等） | `apiKeyEnvVar`, `baseUrl` |
| `ollama` | 本地 Ollama | `baseUrl` |

`apiKeyEnvVar` 配置的是环境变量名（而非 API Key 本身），运行时从环境变量读取实际值。

## 快速开始

详见 [QUICKSTART.md](QUICKSTART.md)

## 配置

默认工作目录为 `~/.jclaw/`，首次运行自动创建并生成默认配置文件 `~/.jclaw/jclaw-config.yaml`。

配置示例：

```yaml
gateway:
  port: 8080

agents:
  default: assistant
  list:
    # Gemini 原生
    - id: assistant
      provider: gemini
      model: gemini-2.5-flash
      # apiKeyEnvVar: GOOGLE_API_KEY  # Gemini 通过 ADK 自动读取 GOOGLE_API_KEY，无需显式配置
      instruction: |
        You are a helpful AI assistant.
      workspace: ~/.jclaw/workspace/assistant

    # Anthropic
    - id: coder
      provider: anthropic
      model: claude-sonnet-4-20250514
      apiKeyEnvVar: ANTHROPIC_API_KEY
      baseUrl: https://api.anthropic.com
      instruction: |
        You are a coding assistant.

    # OpenAI / OpenRouter（OpenAI 兼容协议）
    - id: reviewer
      provider: openai
      model: gpt-4o
      apiKeyEnvVar: OPENROUTER_API_KEY
      baseUrl: https://openrouter.ai/api/v1
      instruction: |
        You are a code reviewer.

    # 本地 Ollama
    - id: local
      provider: ollama
      model: qwen3:1.7b
      baseUrl: http://localhost:11434
      instruction: |
        You are a local assistant.

  defaults:
    maxConcurrent: 4

bindings:
  - match:
      channel: cli
    agentId: assistant
  - match:
      channel: webchat
    agentId: assistant

session:
  store: ~/.jclaw/sessions
  dmScope: main
```

## License

MIT
