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
     ┌─────┴─────┐
     │            │
  CliChannel  WebChatChannel
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
| CLI | 终端命令行交互 |
| WebChat | 浏览器聊天界面（内置 HTTP 服务器） |

## 技术栈

- Java 25+（虚拟线程）
- Google ADK 0.5.0
- JDK 内置 HttpServer（WebChat，零额外依赖）
- SnakeYAML（配置解析）
- SLF4J（日志）
- Maven（构建）
- GraalVM native-image（可选，本地编译）

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
    - id: assistant
      model: gemini-2.5-flash
      instruction: |
        You are a helpful AI assistant.
      workspace: ~/.jclaw/workspace/assistant
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
