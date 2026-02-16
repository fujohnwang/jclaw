# JClaw 开发备忘

## CLI quit 行为

当前 CLI 模式下输入 `quit` 会退出整个 JVM 进程，包括同时运行的 WebChat 渠道。

后续可考虑：
- `quit` 仅退出 CLI，WebChat 继续服务
- 增加 `shutdown` 命令用于关闭整个进程
- 或通过 ShutdownHook 优雅关闭所有 channel
