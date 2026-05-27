# Protocol

The first implementation uses MCP-compatible JSON-RPC 2.0 over localhost HTTP POST `/mcp`.

Supported methods:

- `initialize`
- `tools/list`
- `tools/call`

Implemented tools:

- `mc.world.join` supports normal world creation by default and accepts `preset`/`generator: "flat"` or `"superflat"` for superflat test worlds.

- `mc.client.state` returns both `inWorld` and `rawInWorld`. `rawInWorld` means the underlying client level/player exists; `inWorld` means the client is in a playable world state with no loading/GUI screen blocking normal controls.

- `mc.client.state`
- `mc.player.state`
- `mc.player.swing`
- `mc.player.look`
- `mc.player.look_at`
- `mc.player.use_item`
- `mc.player.attack.block`
- `mc.player.destroy.block`
- `mc.player.drop`
- `mc.player.jump`
- `mc.inventory.state`
- `mc.inventory.find`
- `mc.inventory.count`
- `mc.inventory.selected`
- `mc.container.state`
- `mc.container.click`
- `mc.container.quick_move`
- `mc.container.drop`
- `mc.container.close`
- `mc.hotbar.select`
- `mc.command.run`
- `mc.command.suggest`
- `mc.server.sync`
- `mc.screen.current`
- `mc.ticks.wait`
- `mc.keyboard.press`
- `mc.keyboard.hold`
- `mc.screenshot.take`
- `mc.packet.recording.start`
- `mc.packet.recording.stop`
- `mc.packet.recording.clear`
- `mc.packet.recording.status`
- `mc.packet.dump`
- `mc.packet.wait`
- `mc.debug.capabilities`
- `mc.scenario.batch.run`
- `mc.scenario.report`
