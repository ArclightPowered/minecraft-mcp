# Inventory and container tools

`minecraft-mcp` exposes inventory/container tools for client-side automation. Read tools return structured snapshots; container click tools use Minecraft's normal client interaction path instead of directly mutating item stacks.

## `mc.inventory.state`

Returns the player inventory snapshot.

Important fields:

```json
{
  "inWorld": true,
  "selected": 0,
  "carried": { "item": "minecraft:air", "count": 0, "empty": true },
  "hotbar": [],
  "main": [],
  "armor": [],
  "offhand": [],
  "slots": []
}
```

Slot summaries include:

```text
section, index, slot, playerInventoryIndex, item, count, empty, maxStackSize, displayName
```

## `mc.inventory.find`

Arguments:

```json
{
  "item": "minecraft:diamond",
  "section": "all",
  "limit": 10
}
```

`section` may be `all`, `hotbar`, `main`, `armor`, or `offhand`.

Returns:

```json
{
  "found": true,
  "item": "minecraft:diamond",
  "totalCount": 3,
  "matches": []
}
```

## `mc.inventory.count`

Arguments:

```json
{ "item": "minecraft:diamond" }
```

Returns:

```json
{ "item": "minecraft:diamond", "count": 3 }
```

## `mc.inventory.selected`

Returns the selected hotbar slot item:

```json
{
  "inWorld": true,
  "selected": 0,
  "slot": 0,
  "item": "minecraft:diamond",
  "count": 1
}
```

## `mc.container.state`

Returns the currently open container/menu state.

If no container screen is open:

```json
{
  "inWorld": true,
  "hasContainer": false,
  "hasScreen": false,
  "reason": "no container screen",
  "slots": []
}
```

When a container is open:

```json
{
  "inWorld": true,
  "hasContainer": true,
  "hasScreen": true,
  "screen": "net.minecraft.client.gui.screens.inventory.InventoryScreen",
  "title": "Crafting",
  "containerId": 0,
  "menuClass": "net.minecraft.world.inventory.InventoryMenu",
  "menuType": "net.minecraft.world.inventory.InventoryMenu",
  "carried": { "item": "minecraft:air", "count": 0 },
  "slots": []
}
```

Container slot summaries include:

```text
slot, containerSlot, index, x, y, hasItem, mayPickup, mayPlace, active, item, count, empty, maxStackSize, displayName
```

Use the `slot` field from this result when calling click tools.

## `mc.container.click`

Arguments:

```json
{
  "slot": 36,
  "button": 0,
  "clickType": "PICKUP"
}
```

Supported `clickType` values in the first implementation:

```text
PICKUP
QUICK_MOVE
THROW
```

Returns:

```json
{
  "status": "clicked",
  "slot": 36,
  "button": 0,
  "clickType": "PICKUP",
  "containerId": 0
}
```

## `mc.container.quick_move`

Convenience wrapper for shift-click / quick-move:

```json
{ "slot": 36 }
```

Equivalent to:

```text
clickType=QUICK_MOVE, button=0
```

## `mc.container.drop`

Arguments:

```json
{
  "slot": 36,
  "all": false
}
```

Equivalent to `clickType=THROW`, with `button=0` for one item and `button=1` for the full stack.

## `mc.container.close`

Closes the current container using the normal player close path.

Returns:

```json
{ "status": "closed" }
```

## Runtime notes

- Wait for `client.inWorld == true` before inventory/container interaction after `mc.world.join`.
- Container click tools require an open container/menu and use `Minecraft#gameMode.handleInventoryMouseClick(...)`.
- Creative inventory and survival inventory have different slot layouts; scenarios that click fixed slot numbers should put the player into a known game mode first.
- The example scenarios live under `examples/scenarios/inventory-container/`.
