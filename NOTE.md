# JClaw 开发备忘

## CLI 已移除

CLI 渠道已从主项目移除。JClaw 定位为服务端程序，CLI 如有需要应作为独立项目实现。

## Bindings 路由设计

bindings 采用扁平结构 `(id, channel, agentId, filter)`：

```yaml
bindings:
  - id: webchat-assistant
    channel: webchat
    agentId: assistant

  - id: webchat-coder
    channel: webchat
    filter:
      peerId: "user-abc"
    agentId: coder
```

- 同一个 channel 类型可以绑定不同的 agent，通过 `filter` 区分
- `filter` 是可选的 `Map<String, String>`，当前版本只按 `channel` 匹配（第一个命中的 binding 生效）
- 将来实现 filter 匹配逻辑时，需在 `RouteResolver.resolve()` 中增加对 filter 字段的评估，匹配维度包括但不限于：peerId、teamId、roles 等
- binding 的 `id` 用于日志追踪和管理标识

## 待办

- 模型参数配置（temperature、maxTokens、topP 等）
- 启动时模型可达性校验
- Bindings filter 细粒度路由匹配实现
