# Protocol

The first implementation uses MCP-compatible JSON-RPC 2.0 over localhost HTTP POST `/mcp`.

Supported methods:

- `initialize`
- `tools/list`
- `tools/call`

Implemented tools:

- `mc.client.world.join` supports normal world creation by default and accepts `preset`/`generator: "flat"` or `"superflat"` for superflat test worlds.

- `mc.client.state` returns both `inWorld` and `rawInWorld`. `rawInWorld` means the underlying client level/player exists; `inWorld` means the client is in a playable world state with no loading/GUI screen blocking normal controls.

- `mc.client.state`
- `mc.client.state`
- `mc.client.player.swing`
- `mc.client.player.look`
- `mc.client.player.look_at`
- `mc.client.player.use_item`
- `mc.client.player.attack.block`
- `mc.client.player.destroy.block`
- `mc.client.player.drop`
- `mc.client.player.jump`
- `mc.client.inventory.state`
- `mc.client.inventory.find`
- `mc.client.inventory.count`
- `mc.client.inventory.selected`
- `mc.client.container.state`
- `mc.client.container.click`
- `mc.client.container.quick_move`
- `mc.client.container.drop`
- `mc.client.container.close`
- `mc.client.hotbar.select`
- `mc.client.command.run`
- `mc.client.command.suggest`
- `mc.client.connection.sync`
- `mc.client.screen.current`
- `mc.client.ticks.wait`
- `mc.client.keyboard.press`
- `mc.client.keyboard.hold`
- `mc.client.screenshot.take`
- `mc.client.packet.recording.start`
- `mc.client.packet.recording.stop`
- `mc.client.packet.recording.clear`
- `mc.client.packet.recording.status`
- `mc.client.packet.dump`
- `mc.client.packet.wait`
- `mc.debug.capabilities`
- `mc.scenario.batch.run`
- `mc.scenario.report`
