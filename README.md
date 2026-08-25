# Minecraft MCP

Java-only Minecraft 26.1 mod exposing a local MCP-style JSON-RPC endpoint for AI agents, on both
the client and a dedicated server.

Supported loaders:

- Fabric 26.1
- NeoForge 26.1

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
