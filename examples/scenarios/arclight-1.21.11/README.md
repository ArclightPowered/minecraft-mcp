# Arclight 1.21.11 远程场景

这些是**静态场景定义与父 harness 契约**，不是已执行的 Arclight 结果。它们面向一个已在外部启动、安装合并 MCP 命名空间/配置/远程 API 的 Arclight 1.21.11 服务端。父 harness 替换 `__SERVER_ADDRESS__`；这些场景绝不通过 `mc.client.world.join` 创建或加入本地世界。

## 必要执行顺序

1. 执行 `connection/000_connect_external_server.json`，建立最小客户端连接并等待可游玩世界。
2. 执行 `entities/000_connect_remote_arclight.json`，证明插件消息远程 MCP 通道：`client.remoteAvailable == true`，随后 `mc.remote.call(mc.server.state)` 的 `dedicated == true`。
3. 按需执行 connection、inventory、entity、world 场景。库存夹具命令要求远程测试玩家有命令权限；容器操作本身仍是实际 `mc.client.container.click` / `mc.client.container.quick_move` 客户端交互。
4. `inventory/000..002` 要求已连接的被测玩家名为 `McpProbe`。每个场景先经 `mc.remote.call(mc.server.command.run)` `forceload` 目标 chunk、清空目标体积、铺 3×3 stone 平台并放置容器，**最后**才 `tp @a[name=McpProbe]` 到平台中心；每条 setup 命令都精确断言 `status=succeeded`、`success=true`、`completed=true`。TP 后先同步并由 `mc.client.block.state` 确认平台/容器，不能在客户端区块尚未加载时读取方块。物品使用 `item replace entity @a[name=McpProbe] hotbar.N` 固定槽位；furnace 通过远程 `mc.server.ticks.wait(220)` 等待真实服务端 tick。
5. bucket、末影珍珠、船场景必须由支持本 README 所述 JSONL 规则的**父 harness**单独执行；内建 `mc.scenario.batch.run` 不识别 `status: "PARENT_HARNESS_REQUIRED"`，也不能解析服务端 observer 日志。因此不要把它们混入普通批次并把普通 `passed` 当兼容性结论。

## 本目录使用的合并 MCP 契约

| 用途 | 已注册工具/属性 |
| --- | --- |
| 多人连接 | `mc.client.connection.connect` |
| 客户端等待/同步 | `mc.client.condition.wait`、`mc.client.connection.sync` |
| 从客户端远程调用服务端 | `mc.remote.call`，参数 `{ "tool", "arguments", "timeoutMs" }` |
| 远程可用性条件 | `client.remoteAvailable`（`ClientConditionProperties`） |
| 远程服务端状态/命令 | 嵌套 `mc.server.state`、`mc.server.command.run` |
| 服务端方块、玩家、实体观察 | 嵌套 `mc.server.block.state`、`mc.server.player.state`、`mc.server.player.inventory`、`mc.server.entity.query` |
| 服务端原理图 | 嵌套 `mc.server.schematic.export`、`mc.server.schematic.paste`；通用 `mc.schematic.info` |
| 实际库存动作 | `mc.client.container.click`、`mc.client.container.quick_move` |
| 实际持有物/方块使用 | `mc.client.hotbar.select` 后的 `mc.client.player.use_item`；对指定方块用 `mc.client.block.interact` |
| 客户端视觉/键盘输入 | `mc.client.player.look_at`、`mc.client.keyboard.press`、`mc.client.keyboard.hold` |

`mc.server.connect`、`mc.server.sync` 和 `mc.server.call` 是旧拆分命名空间，故意不存在。客户端动作一律使用 `mc.client.*`；服务端注册工具只能放在 `mc.remote.call.arguments` 内。

## 真实客户端 bucket / 珍珠 / 船场景

### 文件与范围

| 文件 | 真正客户端动作 | 取消/观察目标 |
| --- | --- | --- |
| `entities/020_bucket_or_pearl_contract_gap.json` | `mc.client.block.interact(...)` 对准水源 | `PlayerBucketFillEvent`（`bucketfill`）取消；水源与空桶保持不变 |
| `entities/021_ender_pearl_teleport_cancel.json` | `mc.client.player.use_item({"hand":"main"})` 投掷末影珍珠 | `PlayerTeleportEvent`（`teleport`）取消，且 `cause=ENDER_PEARL`；位置与 health 保持不变 |
| `entities/030_boat_interaction_contract_gap.json` | 对准船后 `mc.client.keyboard.press({"key":"key.mouse.right"})`，再 `keyboard.hold(W,20)` | 实际上船并保持乘坐；服务端 oak boat 位置确实移动 |

夹具 setup 可以使用 `mc.server.command.run`（建水源、清空体积、生成带 tag 的 boat 等），但它绝不是被测动作：

- **bucket**：禁止 `/give` 之后用命令填桶、`/setblock air` 伪造结果，或 `callEvent`。
- **末影珍珠**：禁止 `/tp`、`/teleport`、`/spreadplayers`、插件 API teleport，以及以命令输出替代 `ENDER_PEARL` 事件。
- **船**：禁止 `/ride`、`/mount` 或服务端直接设置 passenger；禁止以 `mc.client.movement.waypoints` 代替船的真实前进键输入。

### 工具能力边界

`BuiltinClientTools` 已注册 `mc.client.player.use_item`（只接收 `hand`，主/副手）和 `mc.client.vehicle.state`，并且 Fabric/NeoForge bridge 实际调用客户端 `gameMode.useItem(...)`。现有工具**没有** `mc.client.entity.interact` 或 `mc.client.entity.mount`；所以船的 mount 使用原生绑定 USE 键 `key.mouse.right`，而不是编造一个不存在的实体交互工具。

如果测试客户端的 USE 键不是右键、该绑定不可通过 `mc.client.keyboard.press` 触发，或目标端实际 tools/list 缺少本场景依赖的工具，父 harness 必须返回明确的：

```text
NOT_IMPLEMENTED: no registered entity-interact/mount tool and USE binding is unavailable
```

不得用 `/ride` 兜底，更不得把“发出了键”认定为 mount 成功。场景后续的 `mc.client.vehicle.state` 和 `mc.server.entity.query` 才是 mount/move 的状态证据。

## McpObserve JSONL：取消断言是父 harness 的职责

服务端需安装已有的只读观察插件：

```text
/home/openclaw/.hermes/profiles/saki/workspace/arclight-1.21.11-artifacts/mcp-validation/observer-plugin
```

插件只提供：

```text
/mcpobserve arm <scenario> <normal|cancel|rewrite> <eventKind>
/mcpobserve state
/mcpobserve clear
```

并以 `MCPOBSERVE ` 前缀输出 JSONL。插件不调用 `PluginManager#callEvent`；`arm` 只登记由同一在线 OP 玩家触发的策略。可用 eventKind 包含 `bucketfill`、`bucketempty` 与 `teleport`；没有 boat 专用 Bukkit 事件，船场景以真实客户端乘坐状态 + 服务端实体位置为观察依据。

父 harness 对每个取消场景必须：

1. 在发 `/mcpobserve arm ...` 前保存服务端日志字节/行偏移，并只解析之后至 `clear` 之后的记录；剥离 `MCPOBSERVE ` 之前的普通日志前缀再按 JSONL 解析。
2. 通过 `mc.client.chat.send` 的正常聊天/命令路径，由**被测玩家本身**执行 arm；随后执行 JSON 文件中的真实客户端动作一次。
3. 按 `scenario`、同一 `playerId`、`eventKind` 过滤，要求精确一条 `record="event"`，而且 `count=1`、`mutation="cancelled"`、`cancel=true`、`prepost.afterPolicyCancelled=true`、`prepost.postCancelled=true`。bucket 还要求 `event.bucket="BUCKET"`、`event.block.type="WATER"`；珍珠还要求 `event.cause="ENDER_PEARL"`。
4. 读取 `state` 和远端服务端真值：bucket 断言水仍在且选中槽仍是空桶；珍珠比较 use 前/后 `mc.server.player.state.position` 与 health（误差分别最多 1.0 格、0.001），并与 observer `state.health` 一致。
5. 即使失败也在 `finally` 用真实客户端聊天运行 `/mcpobserve state` 和 `/mcpobserve clear`。只有符合的 `cleared` 记录/命令完成可证明清理尝试，不可反向证明事件发生。

以下都**不是**事件成功证据：`chat status=sent`、`armed`、`state`、`clear`、命令回执、库存客户端预测、投射物瞬态查询，或旧 run 中的 JSONL。普通 ScenarioEngine 只会执行每步工具和 `expect`；它不支持跨步骤变量、浮点/坐标容差、JSONL 偏移窗口或 `parentAssertions`，因此父 harness 必须额外实现这些检查。

## 包捕获与 Fabric 映射

`connection/001_sync_swing_packet_screenshot.json` 触发真实 `mc.client.player.swing`、记录传输，并等待服务端包。它**不**声称能稳定匹配语义 `Swing` 类名：生产 Fabric 捕获中的 `packetClass` / `packetSimpleName` 可能是运行时/intermediary 名称。保留原始记录并先完成映射审计，不能为了命中过滤器而把 intermediary 标识重写成 named 类名。

## 解释边界

JSON 可解析、静态工具契约检查，或普通步骤的 `passed` 只说明定义能走到对应 MCP 调用；它们不证明 Fabric/NeoForge 已运行、包语义、Arclight 兼容性或取消事件。只有外部 endpoint 上的真实 harness 执行，连同该次 JSONL 窗口、服务端权威状态和指定后果断言，才能报告 Arclight PASS/FAIL。
