# Headless support

Minecraft client still needs a graphics context. The supported CI path is virtual display rather
than true headless.

Recommended Linux command:

```bash
xvfb-run -a ./gradlew :fabric:runClient \
  -DminecraftMcp.scenario.directory="$PWD/examples/scenarios" \
  -DminecraftMcp.scenario.batchExit=true
```

**Use an absolute scenario directory.** The game process runs with its own run directory as the
working directory (`<loader>/run/client` for a client, `<loader>/run/server` for a dedicated
server), not the repository root, so a relative path like `examples/scenarios` does not resolve.
There is no implicit default either — with `batchExit` set, a blank scenario directory exits
non-zero at startup rather than silently scanning the wrong place; without it, blank simply means
no batch runs.

Environment/configuration:

- `MINECRAFT_MCP_ENDPOINT_HEADLESS=true`
- `MINECRAFT_MCP_ENDPOINT_BIND=127.0.0.1`
- `MINECRAFT_MCP_ENDPOINT_PORT=0`
- `MINECRAFT_MCP_SCENARIO_DIRECTORY=/absolute/path/to/examples/scenarios`
- `MINECRAFT_MCP_SCENARIO_BATCH_EXIT=true`

Every setting has three spellings derived from one path: `-DminecraftMcp.scenario.directory`,
`MINECRAFT_MCP_SCENARIO_DIRECTORY`, and `[scenario] directory` in the config file, in that order of
priority. The startup log says which one won for each, so a CI run that ignores your environment is
one `grep Picked` away from an answer. See [protocol.md](protocol.md#configuration) for the full
table.

If no OpenGL context can be created, only logic-level tests such as parser and JSON-RPC unit tests
are expected to pass.

## Dedicated server: no display needed

A dedicated server has no graphics context to create, so it runs in a plain container without
`xvfb-run`:

```bash
./gradlew :fabric:runServer \
  -DminecraftMcp.scenario.directory="$PWD/examples/scenarios/server" \
  -DminecraftMcp.scenario.batchExit=true
```

The batch prints its report and exits non-zero if any scenario failed, so it works as a CI gate
directly. Scenarios are gated with `requires.sides: ["server"]`, so a mixed directory skips the
client ones rather than failing them.

Such a server needs `pause-when-empty-seconds=0` in its `server.properties`, or every tick wait in a
server-side scenario times out at zero ticks once the vanilla pause (60 empty seconds by default)
kicks in — the [server tools](server-tools.md#waiting-for-ticks-that-happened) note has why. That
file and `eula.txt` are yours to put in the run directory; no Gradle task writes either for you.
The e2e script below is the one exception — it prepares both for its throwaway server and restores
them on exit.

## Two processes

`examples/plugin-channel-e2e.sh [fabric|neoforge]` starts a dedicated server and a client, connects
them, and checks that the two endpoints stay distinct and that `mc.remote.call` works in both
directions. It needs a display for the client half.
