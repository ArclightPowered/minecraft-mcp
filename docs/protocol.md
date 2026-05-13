# Protocol

The first implementation uses MCP-compatible JSON-RPC 2.0 over localhost HTTP POST `/mcp`.

Supported methods:

- `initialize`
- `tools/list`
- `tools/call`

Implemented tools:

- `mc.get_client_state`
- `mc.get_player_state`
- `mc.get_current_screen`
- `mc.wait_ticks`
- `mc.key_press` (MVP placeholder returning unsupported status)
- `mc.debug.capabilities`
- `mc.scenario.run_batch`
- `mc.scenario.report`
