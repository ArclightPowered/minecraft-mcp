# Protocol

The first implementation uses MCP-compatible JSON-RPC 2.0 over localhost HTTP POST `/mcp`.

Supported methods:

- `initialize`
- `tools/list`
- `tools/call`

Implemented tools:

- `mc.client.state`
- `mc.player.state`
- `mc.screen.current`
- `mc.ticks.wait`
- `mc.keyboard.press` (MVP placeholder returning unsupported status)
- `mc.debug.capabilities`
- `mc.scenario.batch.run`
- `mc.scenario.report`
