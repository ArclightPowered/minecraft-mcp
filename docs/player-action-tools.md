# Player action tools

Player action tools perform actions through the client player/game mode path. They do not add separate state-query tools; use `mc.player.state` for current player state.

Implemented actions:

- `mc.player.look`
- `mc.player.look_at`
- `mc.player.use_item`
- `mc.player.attack.block`
- `mc.player.destroy.block`
- `mc.player.drop`
- `mc.player.jump`
- existing `mc.player.swing`

## `mc.player.look`

```json
{
  "yaw": 90,
  "pitch": 10
}
```

Returns:

```json
{
  "status": "looked",
  "yaw": 90.0,
  "pitch": 10.0
}
```

Pitch is clamped to `[-90, 90]`.

## `mc.player.look_at`

```json
{
  "x": 1,
  "y": 65,
  "z": -2
}
```

Rotates the player toward the world coordinate and returns the target plus computed yaw/pitch.

## `mc.player.use_item`

```json
{
  "hand": "main"
}
```

`hand` accepts `main`, `mainhand`, `off`, `off_hand`, or `offhand`. The loader bridge calls the normal client game mode item-use path and swings when the interaction consumes action.

## `mc.player.attack.block`

```json
{
  "x": 1,
  "y": 64,
  "z": 0,
  "face": "up"
}
```

Starts destroying/attacking the target block through the client game mode path and swings the main hand. `face` accepts vanilla directions: `up`, `down`, `north`, `south`, `west`, `east`.

## `mc.player.destroy.block`

```json
{
  "x": 1,
  "y": 64,
  "z": 0,
  "face": "up",
  "timeoutMs": 30000
}
```

Keeps calling the normal client block destroy path until the target block becomes air or `timeoutMs` expires. Returns `status: "destroyed"` on success or `status: "timeout"` with `attempts` and `elapsedMs` when it does not finish in time.

## `mc.player.drop`

```json
{
  "all": false
}
```

Drops one selected item when `all=false`, or the selected stack when `all=true`.

## `mc.player.jump`

```json
{}
```

Calls the player jump action once.

##

Sneak and sprint are not standalone player action tools. Use the movement tool parameters instead:

```json
{
  "tool": "mc.movement.waypoints",
  "args": {
    "waypoints": [{"x": 0, "y": 64, "z": 4}],
    "sprint": true,
    "sneak": false
  }
}
```
