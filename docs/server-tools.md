# Server tools

Which `mc.server.*` tools exist is in [protocol.md](protocol.md#mcserver), and the rule that they
always mean "this process" is [next to it](protocol.md#mcserver-on-a-client). This note is how the
two things that can be behind them —
a dedicated server, and a singleplayer client's integrated server — meet at one interface.

## Resolved per call, not held

A dedicated server's loader implements `MinecraftServerBridge` directly. On a client,
`IntegratedServerBridge` implements it over `MinecraftClientBridge.integratedServerBridge()`:
world-touching calls forward to whatever that returns, while identity (`loader()`,
`minecraftVersion()`, `gameDirectory()`) answers from the client bridge itself — that, and the
hardcoded `dedicated()` below, is what lets the wrapper answer at the title screen with no server
behind it.

That lookup happens **per call**, because the integrated server appears and disappears as the player
enters and leaves worlds. There is no cached delegate to go stale: `require()` asks the client bridge
every time and throws if there is nothing there.

`present()` is the non-throwing form, and it is what lets a client endpoint answer with data rather
than an exception. `mc.server.*` is registered on a client endpoint unconditionally, even at the title
screen — whether a world is open is runtime state, and a `tools/list` that changes underneath an agent
is worse than a tool that explains itself. With no integrated server the call returns a structured
`no_local_server` error.

`dedicated()` is how a tool tells the two apart. It defaults to `true` on the interface, the loaders
override it with `MinecraftServer#isDedicatedServer()`, and `IntegratedServerBridge` hardcodes `false`.

## Waiting for ticks that happened

`MinecraftBridge.waitTicks` sleeps `ticks * 50`ms. That only approximates a tick and says nothing
about whether the server advanced, and chaining `submit(...)` does not help either, because those
tasks are drained inside the current tick. So `MinecraftServerBridge` overrides `waitTicks` to go
through `ServerTickCounter`, which both loaders feed from their end-of-tick event — the one place in
the bridge hierarchy where a parent default is deliberately replaced rather than inherited (see the
[Minecraft bridges](minecraft-bridges.md) note).

Two consequences the tool result exposes:

- It reports ticks **observed**, not requested. `mc.server.ticks.wait` returns `requestedTicks`,
  `waitedTicks` and `complete`, so a caller sees a short wait instead of having its own request echoed
  back at it.
- The wait is bounded — a generous `max(1000, requested * 500)`ms ceiling — so a stalled or paused
  server times out rather than hanging the MCP call.

"Paused" is the case worth naming, because vanilla arranges for it. An empty server stops ticking
after `pause-when-empty-seconds`, 60 by default, and a paused server never feeds `ServerTickCounter`,
so `mc.server.ticks.wait` burns its whole ceiling and comes back with `waitedTicks: 0` — the same
result as a counter that was never wired up at all. A dev server that nothing is connected to needs
`pause-when-empty-seconds=0` in its `server.properties` for tick waits to mean anything.

`awaitTicks` calls `requireOffGameThread` first. Waiting on the server thread could only ever time out
at zero ticks observed, since the counter is fed from the very tick event that thread is blocking. The
[MCP worker threads](mcp-worker-threads.md) note has the general form of that guard.

## Capturing command output

`Commands.performPrefixedCommand` returns `void`, and a server's default command source writes to the
console, so running a command through MCP used to report nothing but `{"status":"executed"}` — no
output, no success flag. `CapturingCommandSource` collects both: wrapping the source stack with
`withSource(captured).withCallback(captured.callback())` brings back the output lines, the success
flag and the result value.

It also reports `completed`, which is false when the command never reached a callback at all — a parse
failure, say. That is a different outcome from a command that ran and failed, and worth telling apart.

## Shapes that were chosen

`ServerWorldTools` holds these as static methods over a `MinecraftServer` — the arrangement
`ServerSchematicTools` established, static helpers the loader bridges forward to with no logic of
their own. A few of its results look arbitrary and are not:

- `worldSnapshot` keeps the client `world` property's field names on purpose, so a condition
  expression like `world.dimension` ports between the two sides. The
  [condition property SPI](scenario-condition-property-spi.md) note covers that name sharing.
- `tickStats` reports `targetTickMs` as a flat `50.0` and sets `overloaded` when the average exceeds
  it: 20 ticks per second is the target, so anything slower is behind.
- `chunkState` has no per-chunk ticket detail. `TicketStorage` and `DistanceManager` expose it, but
  nothing public on `ServerChunkCache` hands either of them out, so it reports `hasActiveTickets` and
  stops there.

## The scenario batch without a GUI

The batch that `scenario.directory` starts at boot always prints its summary to stdout, because on a
dedicated server there is no GUI and the report would otherwise only live in memory until
`scenario.batchExit` killed the process. The `mc.scenario.batch.run` tool prints nothing: its report
goes back to the caller that asked for it.

When `batchExit` is set and the batch had failures, the process exits non-zero — a batch runner that
always exits 0 is useless in CI. `System.exit` runs the shutdown hooks, so the server still saves on
the way out.
