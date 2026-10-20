# Scenario condition property SPI

Condition expressions resolve property paths at runtime. The parser does not know that names such as
`client`, `screen`, or `world` are special; they are ordinary properties supplied by a runtime
registry.

## Resolution model

The runtime has global variables and context properties, both supplied by a per-side registry. `$`
is not registered anywhere: it is a built-in of the expression language itself, a lazy view over the
context properties. Registering anything under the name `$` is rejected.

For a path such as:

```text
client.inWorld
```

resolution is:

1. Try a global variable named `client`.
2. Otherwise `client` must be a registered context property.
3. If it is neither, the expression is rejected when it is bound to the registry (before any wait
   loop or packet recording starts), listing every unknown name it contains together with the known
   ones.
4. Continue reading `.inWorld` from the resolved value; members below the chain start are dynamic
   and resolve to `null` when absent.

For:

```text
$.client.inWorld
```

resolution starts explicitly from the built-in `$` root. Names read off `$` are never validated: an
unknown one is `null`. This is the escape hatch for expressions that must stay portable across sides
(`exists($.vehicle)`).

## Sides

One registry is built per side (`client` and `server`); in singleplayer both live in the same JVM. A
provider declares which sides it contributes to via `ConditionPropertyProvider.sides()` — the
default is both. It is instantiated and asked to `register` once per side it opts into, so providers
should be stateless. Providers that read client-only state must narrow `sides()` to `"client"`,
otherwise they end up in the server registry and fail at evaluation time.

The two registries have to stay separate, and not only because the sides expose different things.
Duplicate names are rejected (see below), and in singleplayer a client and its integrated server
share a JVM — so one shared registry would blow up the moment both sides wanted to expose `world`.
Keeping them apart is also what lets a name be reused *deliberately*: `world` answers on both sides
with a compatible shape, so an expression can port between the two condition tools. Which properties
each side actually has is listed in the [scenario condition
expression](scenario-conditions.md#properties-by-side) note; the registry is selected by
`MinecraftBridge.side()`, so an expression is always validated and evaluated against the side of the
bridge it runs on.

## Built-in context properties

Client-side, from `ClientConditionProperties`:

```text
client       bridge.snapshot().toMap(), plus integratedServer and remoteAvailable
connection   bridge.disconnectState()
screen       bridge.screenState()
vehicle      bridge.vehicleState()
world        bridge.worldSnapshot()
inventory    bridge.inventorySnapshot()
packet       bridge.packetRecordingStatus()
```

Server-side, from `ServerConditionProperties`:

```text
server       bridge.serverState()
world        bridge.worldSnapshot("minecraft:overworld")
players      bridge.players()
tick         bridge.tickStats()
packet       bridge.packetRecorder().status(), a reserved shape that never fills
```

That set is deliberately not a copy of the client's: `screen`, `connection`, `vehicle` and a
single-player `inventory` have no meaning on a dedicated server, so they are absent rather than
empty, and naming one in a server condition is a static error instead of a silent false.

They are lazy and cached only for one condition evaluation.

Each of the six client snapshots, and all four real server ones, is one read of the bridge hopped
onto the game thread: `bridge.submit(() -> ...).get(10, SECONDS)`. Condition evaluation runs on an
MCP worker, never on a game thread, so a provider that reads live game state has to make the same
hop — reading it from the worker directly is a data race. A provider that only assembles data it can
already reach safely needs no hop; `packet` is the built-in example, since it reads a `synchronized`
recorder rather than the game.

## Contexts without a bridge

Not every evaluation has a bridge behind it. `ConditionContext.overValues(map)` builds a context
whose properties are exactly that map's entries, with no bridge and no registry — packet filters
build one per matched packet. `ConditionPropertyContext.bridge()` throws `IllegalStateException` in
such a context, which is safe because it is only ever used for expressions validated against a fixed
name set, never against a side's registry. Registry-backed providers therefore never see one.

## SPI interfaces

Implement:

```java
import io.izzel.minecraftmcp.condition.property.ConditionPropertyProvider;
import io.izzel.minecraftmcp.condition.property.ConditionPropertyRegistry;

public final class MyConditionProperties implements ConditionPropertyProvider {
    @Override
    public void register(ConditionPropertyRegistry registry) {
        registry.registerContextProperty("mod", ctx -> Map.of(
                "loaded", true,
                "count", 3
        ));
    }
}
```

Service file:

```text
META-INF/services/io.izzel.minecraftmcp.condition.property.ConditionPropertyProvider
```

Content:

```text
com.example.MyConditionProperties
```

Then scenarios may use:

```text
mod.loaded == true && mod.count > 0
$.mod.loaded == true
```

## Global variables

Most extensions should register context properties. Register a global only when it intentionally
must shadow the context property of the same name in shorthand position:

```java
registry.registerGlobal("custom", ctx -> Map.of("value", 2));
```

With both of these:

```java
registry.registerContextProperty("custom", ctx -> Map.of("value", 1));
registry.registerGlobal("custom", ctx -> Map.of("value", 2));
```

resolution is:

```text
custom.value   == 2
$.custom.value == 1
```

## Duplicate policy

Duplicate context property names or global names are rejected. This keeps scenario semantics deterministic.

## Testing

`ConditionPropertyRegistryTest` guards the side split from both directions: the client registry has
to carry the builtins, and the client-only namespaces (`client`, `screen`, `connection`, `vehicle`,
`inventory`) must not appear in the server registry. Naming one of them in a server-side expression
has to hit the unknown-property error rather than resolve to fabricated data, which is why the same
test also asserts that an unregistered chain start throws instead of returning `null`.
