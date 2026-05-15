# Scenario condition property SPI

Condition expressions resolve property paths at runtime. The parser does not know that names such as `client`, `screen`, or `world` are special; they are ordinary properties supplied by a runtime registry.

## Resolution model

The runtime has global variables. The built-in global variable `$` is a lazy context object.

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

## Built-in context properties

Built-in properties registered under `$`:

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
