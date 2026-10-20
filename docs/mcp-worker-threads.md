# MCP worker threads

Tool bodies run on `McpWorkers` — never on a game thread, and never on a Netty event loop. The code
shows only the result, so this note records the reasoning.

## Why not a game thread

Every loader hands play payloads to the main thread before a handler sees them:

- NeoForge's `PayloadRegistrar` defaults to `HandlerThread.MAIN`.
- Fabric's networking addon always schedules onto the receiving side's main thread —
  `MinecraftServer#execute` for serverbound payloads, the client's main thread for clientbound.

A tool body inlined there would wait on the very thread that has to satisfy it, and many tools block
for as long as the caller asks:

- `mc.client.ticks.wait` sleeps `ticks * 50`ms, up to the 24,000-tick cap — twenty minutes.
- `mc.server.ticks.wait` waits for real ticks through `ServerTickCounter`, which a server thread
  running the wait could only ever fail: the counter is fed from the tick event it is blocking.
- `mc.client.condition.wait`, `mc.server.condition.wait`, `mc.client.packet.wait` and
  `mc.client.connection.wait_disconnect` poll until their timeout, 30s by default.
- `mc.client.movement.waypoints` steps the player over many ticks, sleeping between each.
- `mc.client.command.suggest` waits for the server's reply, and `mc.client.screenshot.take` for a
  later frame.
- `mc.scenario.batch.run` blocks without a bound.
- `mc.remote.call` blocks on the peer's reply, so a handler running tool bodies inline on the main
  thread would turn a nested call into a deadlock.

## Why not a Netty event loop

The event loop group is process-wide and shared by every connection, so blocking one thread there
stalls other players past the 15s keep-alive limit. NeoForge's `HandlerThread.NETWORK` would move
the problem there rather than solve it, and Fabric's API offers no knob at all.

## Why `McpWorkers` gets its own package

Both transports need the workers — the local HTTP endpoint and the plugin channel — and neither
should own them: putting the type in `mcp` or `serverlink` would make one transport's package a
dependency of the other's. Callers take a plain `Executor`. In main sources `MinecraftMcpBootstrap`
is the only class that names `McpWorkers`, because it is the only one that creates and closes a set.

## Ownership and shutdown

- `MinecraftMcpBootstrap.McpEndpoint` owns the workers, not `LocalHttpMcpServer`. The server is
  handed a plain `Executor` and deliberately does not stop it. A loader wiring up the plugin channel
  passes the same `McpEndpoint.workers()` to its `McpPluginMessageHandler`, so both transports run
  tool bodies on one pool.
- `McpEndpoint.close()` stops serving first, so no request can be accepted after the threads it would
  need are gone.
- `McpWorkers.close()` uses `shutdownNow()` rather than `shutdown()`: a task may be parked in a 30s
  wait, and nothing is waiting on its result once the endpoint is gone. The threads are daemons.
- The pool is unbounded on purpose. A bounded pool would let one blocking tool — a 30s
  `mc.client.condition.wait` is ordinary — starve every other call, and both transports are already
  gated (HTTP by the bearer token; the plugin channel by the permission node for players and by
  `trustedServers` for servers).

`McpWorkers.pooled` currently hands out named daemon platform threads, grown on demand and reused
while idle. The implementation is not part of the contract: on the Java 25 toolchain this project
targets, swapping it for `Executors.newVirtualThreadPerTaskExecutor()` is a one-line change, and JEP
491 means the `synchronized`/`wait()` in `ServerTickCounter` would no longer pin a carrier.

`McpWorkers.direct()` runs each task on the calling thread. It is for tests that assert on what a
handler produced without polling for another thread to finish. Never use it to back a real endpoint —
that is exactly the bug this type exists to prevent. `McpPluginMessageHandler` takes its executor as
a required constructor argument for the same reason: a handler that ran tool bodies on the caller's
thread is the bug the parameter exists to prevent, so every call site has to name the threads it
means.

## What still runs inline

The plugin-channel handler's trust check, and on the response path the lookup of which proxy the
reply belongs to — both on the game thread the payload arrived on, and neither reads the payload
nor blocks. Why the hand-off is drawn there rather than earlier or later is in
[protocol.md](protocol.md#threads); how the handler survives what it then parses is in the
[plugin channel](plugin-channel.md) note.

## The guard

`MinecraftBridge.requireOffGameThread(String)` refuses to let a blocking operation run on the thread
it is waiting for. Once the workers are wired up correctly it never fires. It exists because the
failure it guards against is silent and slow rather than loud: a wait on the game thread cannot
observe the ticks it is itself preventing, so it burns its whole timeout, returns a plausible-looking
"nothing happened", and on a dedicated server hands the vanilla watchdog a 60-second tick. The thrown
`IllegalStateException` names the side and the operation at the point it went wrong.

`waitTicks` and `waitUntil` call it, as does the server bridge's `awaitTicks`. The client bridges
call it directly from the five operations that block on later work: `takeScreenshot`,
`destroyBlock`, `commandSuggest` and `moveWaypoints` wait on later ticks; `serverMcpCall` waits on
the peer's reply.

## Testing

`FakeGameThread` backs `isOnClientThread()` / `isOnServerThread()` and `execute(Runnable)` in every
fake bridge. A unit test has no game thread, so a fake has to invent one, and the invention has to be
self-consistent because `submit` branches on it and `requireOffGameThread` refuses on it. Hardcoding
the answer works in neither direction: `execute` means "run this on the game thread", so a fake that
runs the task inline and also reports "I am not the game thread" contradicts itself, while one that
reports "I am" makes every blocking tool under test look like a deadlock.

The model `FakeGameThread` implements needs no extra threads — the test thread is an MCP worker, and
you are on the game thread exactly while you are inside `run(Runnable)`. The flag is a `ThreadLocal`,
so a test that hands work to a second thread gets a separate answer there rather than inheriting this
one, and `run` is reentrant because game-thread work is allowed to schedule more of it.

`GameThreadGuardTest` covers the contract from both sides: blocking on a worker is allowed, blocking
inside the game loop is refused by name, and `submit` still short-circuits when it is already on the
game thread.
