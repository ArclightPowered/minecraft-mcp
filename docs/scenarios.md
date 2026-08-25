# Scenarios

Scenarios are JSON files containing a name, optional metadata, and ordered steps. The runner scans scenario directories recursively, so scenarios may be grouped under `smoke/`, `input/`, `assert/`, and `metadata/`.

Minimal example:

```json
{
  "name": "client_ready",
  "tags": ["smoke", "client"],
  "steps": [
    {
      "id": "state",
      "tool": "mc.client.state",
      "args": {},
      "expect": {
        "result.running": true
      }
    }
  ]
}
```

Step-level `expect` supports dot-path equality and small `contains` / `startsWith` matchers:

```json
{
  "expect": {
    "result.running": true,
    "result.screen": {"contains": "Screen"},
    "result.minecraftVersion": {"startsWith": "26.1"}
  }
}
```

Supported scenario metadata:

```json
{
  "tags": ["input", "smoke"],
  "requires": {
    "loaders": ["fabric", "neoforge"],
    "sides": ["client"]
  },
  "expected": "pass"
}
```

- `tags`: used by `includeTags` / `excludeTags` in `mc.scenario.batch.run`.
- `requires.loaders`: skips scenarios that do not match the active loader. Always `fabric` or
  `neoforge` — a dedicated server reports the same loader as its client, and the side is a separate
  axis.
- `requires.sides`: `client` and/or `server`; skips scenarios that do not apply to the endpoint
  running the batch. A dedicated-server endpoint registers no `mc.client.*` tools at all, so a
  client scenario would otherwise fail rather than skip.
- `expected: "fail"`: expected failures are reported as `expected_failed` and do not increase `failed`.

Preconditions beyond the side (is a remote server involved? has the world been created yet?) are
expressed with `tags` plus `excludeTags`, as `arclight-2137/` already does with `external-server`
and `manual-setup`.

Run all scenarios through MCP:

```json
{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"mc.scenario.batch.run","arguments":{"directory":"/absolute/path/to/examples/scenarios"}}}
```

Run only input scenarios:

```json
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"mc.scenario.batch.run","arguments":{"directory":"/absolute/path/to/examples/scenarios","includeTags":["input"]}}}
```

Current scenario groups:

- `smoke/`: client readiness, capabilities, current screen, pre-world player state.
- `input/`: key press / key hold coverage.
- `assert/`: examples using `expect` assertions.
- `metadata/`: loader requirements, tags, and expected-failure behavior.
- `world/`: full-client scenarios that create/join a singleplayer test world, inspect player/world/inventory/block state, exercise input in-world, and leave back to title.
- `arclight-2137/`: manual-setup reproduction of [Arclight issue #2137](https://github.com/IzzelAliz/Arclight/issues/2137) against an external server on `127.0.0.1:25565`. The issue was reported on Arclight 1.21.1; the scenarios here use 26.1 entity ids (`minecraft:oak_boat`), so they require an Arclight 26.1 server. To reproduce in the original 1.21.1 environment, use these scenarios from the `main` branch with the 1.21.1 client.

World scenarios require a real client run and are intended for `includeTags:["world"]` or `includeTags:["full-client"]`. They are not part of quick smoke-only validation.
