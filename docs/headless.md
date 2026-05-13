# Headless support

Minecraft client still needs a graphics context. The supported CI path is virtual display rather than true headless.

Recommended Linux command:

```bash
xvfb-run -a ./gradlew :fabric:runClient -DminecraftMcp.scenarioDir=examples/scenarios -DminecraftMcp.batchExit=true
```

Environment/configuration:

- `MINECRAFT_MCP_HEADLESS=true`
- `MINECRAFT_MCP_BIND=127.0.0.1`
- `MINECRAFT_MCP_PORT=0`
- `MINECRAFT_MCP_SCENARIO_DIR=examples/scenarios`
- `MINECRAFT_MCP_BATCH_EXIT=true`

If no OpenGL context can be created, only logic-level tests such as parser and JSON-RPC unit tests are expected to pass.
