# JClaw TODO

## 功能待实现

### 模型参数配置

支持在 agent 配置中设置模型参数：

```yaml
agents:
  list:
    - id: assistant
      provider: gemini
      model: gemini-2.5-flash
      parameters:
        temperature: 0.7
        maxTokens: 4096
        topP: 0.9
```

**涉及修改**:
- `JClawConfig.AgentDef` - 添加 `parameters` 字段
- `ConfigLoader` - 解析参数配置
- `AgentRegistry` - 将参数传递给各 provider 的 model builder

**优先级**: 低
