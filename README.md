# Minecraft MCP

Java-only Minecraft mod exposing a local MCP-style JSON-RPC endpoint for AI agents, on both
the client and a dedicated server.

Supported, each as a Fabric and a NeoForge jar built from the same sources:

| Minecraft | NeoForge | Fabric API | Java |
|---|---|---|---|
| 1.21.1 | 21.1 | 0.116.1 | 21 |
| 1.21.4 | 21.4 | 0.119.4 | 21 |
| 1.21.8 | 21.8 | 0.136.1 | 21 |
| 1.21.11 | 21.11 | 0.141.6 | 21 |
| 26.1.2 | 26.1 | 0.155.2 | 25 |
| 26.2 | 26.2 | 0.161.0 | 25 |
| 26.3 | 26.3 | 0.164.0 | 25 |

## Building

`./gradlew build` builds every version for both loaders (Gradle 9.8, JDK 21 and 25 installed or
downloaded by the toolchain resolver). Jars land in `<loader>/versions/<minecraft>/build/libs/`, named
`minecraft_mcp-<loader>-<version>+<minecraft>.jar`; `./gradlew :neoforge:1.21.8:build` builds one.

The versions are [Stonecutter](https://stonecutter.kikugie.dev) nodes: `common/`, `fabric/` and
`neoforge/` are branches, and the sources in git are written for 1.21.11 (`stonecutter.gradle`). What
differs between Minecraft versions lives in `common/.../compat/MinecraftCompat` and `ClientCompat`,
whose bodies switch per version with `//? if` blocks; per-version dependency versions are in
`versions/<minecraft>.properties`. A pushed tag builds all jars and publishes them as a release
(`.github/workflows/build.yml`).

## Tools for agents

Every tool lists its arguments with types, defaults and descriptions in `tools/list`; an unknown or
malformed argument is refused with the list of valid ones. Some that save an agent round trips:

- `mc.client.world.join` / `world.leave` / `connection.connect` with `wait: true` return once the client
  is in the world (or out of it, or disconnected) instead of needing a polling loop.
- `mc.client.chat.read` (with a `since` cursor) and `mc.client.chat.wait` (by `contains` or `regex`)
  return chat and system messages, which is where command feedback arrives.
- `mc.client.screenshot.take` with `inline: true` returns the picture as MCP image content.
- `mc.client.state` reports the screen title, which also names the screen on Fabric (its class names
  are intermediary there).
- `mc.client.entities.nearby`, `mc.client.target` (what the crosshair hits) and `mc.client.blocks.find`
  describe what the client sees, on any server.

Default transport is local HTTP JSON-RPC at `/mcp`. The mod writes discovery data to
`mcp/server.json` under the game directory. It binds to `127.0.0.1` by default and requires a
bearer token.

## Sides

Each process serves the tools it can actually back, and nothing else — a dedicated server has no
`mc.client.*` in `tools/list` at all.

| Prefix | Meaning |
|---|---|
| *(none)* | Process-level: capabilities, schematic metadata, scenario batches |
| `mc.client.*` | This process has a Minecraft client |
| `mc.server.*` | This process has a `MinecraftServer` (a singleplayer integrated server counts) |
| `mc.remote.*` | Reach the *other* process over the plugin channel |

`mcp/server.json` carries `side`, `loader`, `minecraftVersion` and `capabilities`, so an agent can
tell endpoints apart without probing. See [docs/protocol.md](docs/protocol.md) for the full list.

## Access control

The local HTTP endpoint is guarded by its bearer token. The plugin channel — how a client and server
drive each other's tools — refuses by default instead. Each side has one question, and asks it of
every message that arrives, a call and its reply alike:

- A **server** asks whether the player holds the `minecraft_mcp` remote-call permission node
  (`minecraft_mcp.remote.call` on NeoForge, `minecraft_mcp:remote.call` on Fabric). It defaults to
  owner level; grant it with a permission mod.
- A **client** asks whether the server's address appears in its `trustedServers`.
- A singleplayer world's own integrated server is always trusted.

Because a reply is authorised like a call, both cover **both directions**: driving player `P`'s client
from a server needs the node on `P`, and `mc.remote.call` from a client needs that server listed. A
peer whose calls you would refuse does not get to answer you either — and calling one fails
immediately rather than timing out.

`disabledTools` takes a tool out of a process entirely, the local endpoint included. Details in
[docs/protocol.md](docs/protocol.md#access-control).

## Configuration

Every setting resolves through one chain — system property, environment variable, config file,
built-in default:

```bash
-DminecraftMcp.endpoint.port=25580     # or
MINECRAFT_MCP_ENDPOINT_PORT=25580      # or [endpoint] port in the config file
```

The config file is `config/minecraft-mcp.toml` (NeoForge) or `config/minecraft-mcp.json` (Fabric),
written with documented defaults on first start. `access.*` can be edited while the game runs;
everything else needs a restart, which NeoForge enforces rather than merely documents. The startup
log reports which layer won for each setting. Full table in
[docs/protocol.md](docs/protocol.md#configuration).

A dedicated server needs no graphics context, so server-side scenarios run in a plain container —
unlike client runs, which still need a virtual display. See [docs/headless.md](docs/headless.md).

Build:

```bash
./gradlew build
```

Run a dedicated server and its scenario batch:

```bash
./gradlew :fabric:runServer \
  -DminecraftMcp.scenario.directory="$PWD/examples/scenarios/server" \
  -DminecraftMcp.scenario.batchExit=true
```

The batch prints its report and exits non-zero if any scenario failed.

Example MCP call:

```bash
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' \
  http://127.0.0.1:$PORT/mcp
```
