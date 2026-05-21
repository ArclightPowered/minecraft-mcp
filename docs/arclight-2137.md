# Arclight issue 2137 scenarios

These scenarios are for reproducing and instrumenting Arclight issue #2137 BUG 2:
Sable/Create Aeronautics vehicle-seat swing packets cause Arclight's synthetic Bukkit
animation/interact bridge to call `CraftWorld.rayTrace`, which enters Sable's
`SubLevelInclusiveLevelEntityGetter` with an abnormally large AABB.

Issue: https://github.com/IzzelAliz/Arclight/issues/2137

## What this repository can automate

`minecraft-mcp` can now drive the client-side part of the reported action:

- create and join a test world;
- send normal client swing packets via `mc.player.swing`;
- observe world/player state;
- observe passenger/vehicle state via `mc.vehicle.state`.

`mc.player.swing` intentionally calls the vanilla client `LocalPlayer#swing(...)` path,
which sends `ServerboundSwingPacket` to the server. On Arclight this reaches
`ServerGamePacketListenerImpl#handleAnimate`, the same packet handler named in the
issue stack trace.

## Scenario files

- `001_world_ready.json`: create `minecraft_mcp_issue_2137` and wait until the client is in world.
- `010_swing_packet_baseline.json`: send one swing packet and verify the client remains in world.
- `011_repeated_swing_packets.json`: send several swing packets to make server logs easier to inspect.
- `012_spawn_and_mount_vanilla_boat.json`: send vanilla server commands to summon a
  vanilla boat and mount the player. This requires a connected server or local world
  where the player is allowed to run `/summon` and `/ride`; exclude
  `requires-command-permission` for baseline client-only batches, or include it for
  local test worlds created by `mc.world.join`, which enable cheats by
  default.
- `013_mounted_swing_packet_baseline.json`: while mounted, send a swing packet and
  verify the player remains a passenger. Requires `012` to have succeeded.
- `014_dismount_vehicle.json`: dismount and clean up the tagged vehicle.
- `020_sable_vehicle_observer.json`: manual-setup scenario for the real Sable vehicle condition.
- `090_leave_world.json`: leave the world after the run.

## Running the automated baseline

Against a normal Fabric/NeoForge client:

```bash
xvfb-run -a ./gradlew :neoforge:runClient -DminecraftMcp.port=0 -DminecraftMcp.batchExit=false
```

Run against the local dev client/integrated server:

```json
{
  "directory": "examples/scenarios/arclight-2137",
  "includeTags": ["arclight-2137"],
  "excludeTags": ["manual-setup", "external-server"]
}
```

Run against an external Arclight server:

1. Start the server separately.
2. Either call `mc.server.connect` directly, or copy `000_connect_external_server.json`
   and set its `address` to the target host:port.
3. Run the batch without assuming singleplayer-only tools. `mc.command.run` sends
   slash commands through the current client connection, so permissions are the
   connected server's permissions; it no longer calls the integrated server API.

The expected baseline result is no failed scenarios. This proves only that the MCP
client can generate swing packets, summon/mount a generic vehicle through vanilla
commands, and observe passenger state; it does not prove the Sable AABB abort without
an Arclight server and the reporter's full vehicle setup.

## Running the real Sable reproduction

For the real #2137 BUG 2 validation, connect this mod to an Arclight NeoForge 1.21.1
server with the reporter's Create Aeronautics/Sable stack, then perform the manual
setup before running the manual scenario:

1. Assemble or load a Create Aeronautics/Sable ship.
2. Sit the controlled player in the cockpit/typewriter seat.
3. Run `020_sable_vehicle_observer.json`, or run the whole directory without excluding
   `manual-setup`.
4. Inspect the Arclight server log for Sable lines like:

```text
Aborting entity get for abnormally large AABB
SubLevelInclusiveLevelEntityGetter.get
CraftWorld.rayTrace
ServerGamePacketListenerImpl...arclight$animateEvents
ServerGamePacketListenerImpl.handleAnimate
ServerboundSwingPacket.handle
```

Expected current-bug behavior on affected Arclight builds:

- server log prints the Sable abnormal AABB abort stack;
- the player may be ejected from the Sable vehicle seat;
- `mc.vehicle.state` changes from `isPassenger=true` to `isPassenger=false`, or
  the player otherwise leaves the controlled seat unexpectedly.

Expected fixed behavior:

- repeated `mc.player.swing` calls do not produce the Sable AABB abort stack through
  Arclight `arclight$animateEvents`;
- the player remains seated;
- normal animation events still occur.
