# Minecraft bridges

`MinecraftBridge` is what a client bridge and a dedicated-server bridge have in common; the two
sub-interfaces `MinecraftClientBridge` and `MinecraftServerBridge` add the side-specific surface. The
code shows only the result, so this note records the boundary and the contracts that are easy to
break by accident.

## What lives in the parent

Three things, and nothing else:

- Identity: `loader()`, `side()`, `minecraftVersion()`, `gameDirectory()`, `capabilities()`.
- Thread scheduling: `isOnGameThread()`, `execute(Runnable)`, and the `submit(Supplier)` and
  `requireOffGameThread(String)` that build on them.
- Operations whose meaning is identical on both sides: `waitUntil` and `schematicInfo`.
  `schematicInfo` qualifies because it is pure disk I/O — it resolves a path under the game directory
  and reads Sponge v3 metadata, never touching a world.

Everything that needs a level, a player, a screen or a connection belongs to a sub-interface.

`waitTicks` sits in the parent as a fallback rather than as a shared meaning. The parent sleeps
`ticks * 50`ms, which only approximates a tick and says nothing about whether the game advanced;
`MinecraftServerBridge` overrides it to wait on `ServerTickCounter` for ticks that actually
happened. The override stays `void` like the parent; the count of observed ticks comes from the
`awaitTicks` it delegates to, which `mc.server.ticks.wait` calls directly. A client has no
equivalent counter, so it keeps the sleep. Only
`side()`, `isOnGameThread()`, `capabilities()` and that `waitTicks` are overridden at all — the rest
of the parent is used as written.

## No client classes in common

`common` has to stay loadable on a dedicated server, so no main source in the module may reference
`net.minecraft.client` — not `MinecraftBridge`, and not anything it reaches. That is why
`MinecraftClientBridge` can still live in `common`: the interface only names types the server jar
also has (`Vec3`, `Util`, plain maps), and the client-only work happens in the loader modules that
implement it.

This is a convention, not a build-time check. There is deliberately no classpath scan and no
server-only Minecraft jar wired into `common/build.gradle`; the intended long-term fix is a split
client source set, so the compiler enforces it instead of a verification task.

## `side()` is the dispatch key

`side()` returns `"client"` or `"server"`, supplied as a `default` by each sub-interface rather than
by implementations. One process-wide lookup is keyed on it, and a second split follows the same
line:

- the condition property registry — `ConditionPropertyProviders.registryFor(side())`, used by
  `waitUntil` to validate and evaluate an expression against the properties that side can answer;
- the packet recorder — not a lookup but the same split: each sub-interface's `packetRecorder()`
  default hands back its own of `PacketRecorders.CLIENT` and `PacketRecorders.SERVER`.

`PacketRecorders` is a holder of its own so that neither side has to reach through the other. The
client recorder used to be a `static` field on `MinecraftClientBridge`, which meant a server bridge
could not have one without dragging the client interface in with it.

## `isOnGameThread()` is a forwarder

`MinecraftClientBridge` forwards it to the pre-existing `isOnClientThread()` and
`MinecraftServerBridge` to `isOnServerThread()`, both as `default` methods. Introducing the common
thread question therefore required no change to any loader's implementation class. New code should
ask `isOnGameThread()`; the two side-specific spellings stay because the implementations already had
them.

Two `default` methods on the parent read it. `submit(Supplier)` branches on it: already on the game
thread it runs the supplier inline, otherwise it hands the work to `execute` and completes the future
from there. Either way the caller gets a `CompletableFuture`, and a supplier that threw becomes a
failed future rather than a thrown exception, so callers never need to know which branch they took.
`requireOffGameThread(String)` refuses on it instead; why that guard exists, and how a unit test
invents a game thread consistent with both, is in the [MCP worker threads](mcp-worker-threads.md)
note.

## The `waitUntil` polling loop

`waitUntil(condition, timeoutMs, intervalTicks)` backs both `mc.client.condition.wait` and
`mc.server.condition.wait`. It is a `default` on the parent, so each side gets the same loop against
its own registry. Its contract:

- It refuses to run on a game thread at all. `requireOffGameThread` is the first statement, ahead of
  even parsing, because a wait on the thread that has to satisfy it can only burn its whole timeout.
  `waitTicks` opens the same way.
- The expression is parsed and validated against the side's registry with
  `ConditionValidator.validate` once, before the loop. A static error surfaces before the first
  sleep, not after the timeout.
- The deadline is monotonic: it comes from `Util.getNanos()`, never from
  `System.currentTimeMillis()`, so a wall-clock adjustment cannot shorten or extend a wait.
- Each iteration sleeps `min(intervalMs, remainingMs)`, so a large `intervalTicks` cannot stretch the
  timeout past what the caller asked for, and the loop always gets one more evaluation after its last
  sleep.
- A failed evaluation is assumed transient. State can be briefly unreadable while a world loads, and
  the caller asked to wait, so a `RuntimeException` from one attempt is remembered as `lastError`
  rather than thrown. Only the most recent failure is kept.
- Never evaluated is not the same as evaluated false. If no attempt ever completed, the timeout
  throws `ConditionEvaluationException` with the attempt count and the last error, instead of
  returning a plausible-looking `matched: false`. If evaluation did succeed at some point but the
  condition stayed false, the call returns `false` and any trailing error goes to stderr.

The three outcomes this produces for callers are described from the tool's side in the
[scenario condition expression](scenario-conditions.md) note.
