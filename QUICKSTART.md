# JClaw Quick Start

## 环境要求

- JDK 25+
- Maven 3.9+
- (可选) GraalVM 24+ (用于编译本地可执行文件)
- Gemini API Key (设置环境变量 `GOOGLE_API_KEY`)

## 构建

```bash
# Fat JAR
mvn package -DskipTests

# GraalVM 本地可执行文件
mvn package -Pnative -DskipTests
```

## 配置

首次运行时，JClaw 会自动创建工作目录 `~/.jclaw/` 并生成默认配置文件 `~/.jclaw/jclaw-config.yaml`。

你可以直接编辑该文件来修改端口、Agent 模型、指令等。

### Agent 配置示例

```yaml
agents:
  default: assistant
  list:
    # Gemini 原生
    - id: assistant
      provider: gemini
      model: gemini-2.5-flash
      apiKeyEnvVar: GOOGLE_API_KEY
      instruction: |
        You are a helpful AI assistant.

    # Anthropic
    - id: claude-agent
      provider: anthropic
      model: claude-sonnet-4-20250514
      apiKeyEnvVar: ANTHROPIC_API_KEY
      baseUrl: https://api.anthropic.com
      instruction: |
        You are a coding assistant.

    # OpenRouter / OpenAI 兼容
    - id: openrouter-agent
      provider: openai
      model: gpt-4o
      apiKeyEnvVar: OPENROUTER_API_KEY
      baseUrl: https://openrouter.ai/api/v1
      instruction: |
        You are a reviewer.

    # 本地 Ollama
    - id: local-agent
      provider: ollama
      model: qwen3:1.7b
      baseUrl: http://localhost:11434
      instruction: |
        You are a local assistant.
```

`apiKeyEnvVar` 填的是环境变量名，不是 API Key 本身。运行前确保对应环境变量已设置：

```bash
export GOOGLE_API_KEY=your-gemini-key
export ANTHROPIC_API_KEY=your-anthropic-key
export OPENROUTER_API_KEY=your-openrouter-key
```

## 运行

```bash
# 默认启动 CLI + WebChat 双渠道
java -jar target/jclaw-0.1.0-SNAPSHOT.jar

# 仅启动 WebChat
java -jar target/jclaw-0.1.0-SNAPSHOT.jar --webchat-only

# 仅启动 CLI
java -jar target/jclaw-0.1.0-SNAPSHOT.jar --cli-only

# 指定配置文件
java -jar target/jclaw-0.1.0-SNAPSHOT.jar --config /path/to/config.yaml
```

如果编译了本地可执行文件：

```bash
./target/jclaw
./target/jclaw --webchat-only
```

## 使用

- CLI：启动后直接在终端输入消息，输入 `quit` 退出
- WebChat：浏览器打开 `http://localhost:8080`（端口取决于配置文件中 `gateway.port`）

## 目录结构

```
~/.jclaw/
├── jclaw-config.yaml    # 配置文件
├── sessions/            # 会话持久化
└── workspace/           # Agent 工作区
    └── assistant/
```
