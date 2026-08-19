# Scenario condition property SPI

Condition expressions resolve property paths at runtime. The parser does not know that names such as `client`, `screen`, or `world` are special; they are ordinary properties supplied by a runtime registry.

## Resolution model

The runtime has global variables and context properties, both supplied by a per-side registry. `$` is not registered anywhere: it is a built-in of the expression language itself, a lazy view over the context properties. Registering anything under the name `$` is rejected.

For a path such as:

```text
client.inWorld
```

resolution is:

1. Try a global variable named `client`.
2. If no global `client` exists, fallback to property `client` of global `$`.
3. Continue reading `.inWorld` from the resolved value.

For:

```text
$.client.inWorld
```

resolution starts explicitly from the global `$` object.

## Sides

One registry is built per side (`client` and `server`); in singleplayer both live in the same JVM. A provider declares which sides it contributes to via `ConditionPropertyProvider.sides()` — the default is both. It is instantiated and asked to `register` once per side it opts into, so providers should be stateless. Providers that read client-only state must narrow `sides()` to `"client"`, otherwise they end up in the server registry and fail at evaluation time.

## Built-in context properties

Built-in client-side context properties:

```text
client       bridge.snapshot().toMap()
connection   bridge.disconnectState()
screen       bridge.screenState()
vehicle      bridge.vehicleState()
world        bridge.worldSnapshot()
inventory    bridge.inventorySnapshot()
```

They are lazy and cached only for one condition evaluation.

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

Most extensions should register context properties. Register a global only when it intentionally must shadow fallback lookup:

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
