# Minecraft MCP

Java-only Minecraft 26.1 client mod exposing a local MCP-style JSON-RPC endpoint for AI agents.

Supported loaders:

- Fabric 26.1
- NeoForge 26.1

Default transport is local HTTP JSON-RPC at `/mcp`. The mod writes discovery data to `mcp/server.json` under the game directory. It binds to `127.0.0.1` by default and requires a bearer token.

Build:

```bash
./gradlew build
```

Example MCP call:

```bash
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' \
  http://127.0.0.1:$PORT/mcp
```
