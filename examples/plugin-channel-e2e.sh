#!/usr/bin/env bash
# A dedicated server and a client, in separate processes on this machine, each with its own MCP
# endpoint. Proves the two endpoints stay distinct and that mc.remote.call works in both
# directions over the plugin channel.
#
# Deliberately not a Gradle task: Gradle is a poor supervisor for long-running processes, and the
# server has to stay up while the client talks to it.
#
#   ./examples/plugin-channel-e2e.sh [fabric|neoforge]
#
# Needs a display for the client. Set DISPLAY/XAUTHORITY, or run under xvfb-run.
# Gradle runs --offline here, so the dependency caches must already be warm: build once online first.
set -uo pipefail

LOADER="${1:-fabric}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SERVER_DISCOVERY="$ROOT/$LOADER/run/server/mcp/server.json"
CLIENT_DISCOVERY="$ROOT/$LOADER/run/client/mcp/server.json"
SERVER_LOG="$(mktemp -t mcp-plugin-channel-server-XXXX.log)"
CLIENT_LOG="$(mktemp -t mcp-plugin-channel-client-XXXX.log)"
server_pid=""
client_pid=""
touched_files=()
failures=0

# This script has to be idempotent, and the run directories are persistent, so anything it changes
# in them has to be put back. Two separate leaks were found the hard way:
#
#   - the access policy stayed behind, and the next plain `gradlew runServer` silently inherited
#     disabledTools, which looks exactly like a broken tool;
#   - the last thing this script does is re-op the player, so ops.json kept an operator. The very
#     next run then failed "an unauthorised player is refused", because they already were one.
#     Fabric hid this for a long time: Loom randomises the dev player name every launch, so each run
#     opped a fresh identity. NeoForge's dev player is always "Dev", so it broke on the second run.
snapshot() {
  local file="$1"
  mkdir -p "$(dirname "$file")"
  # "absent" is a state worth restoring too, so record it rather than skipping the file.
  if [ -f "$file" ]; then cp "$file" "$file.e2e-backup"; else : > "$file.e2e-absent"; fi
  touched_files+=("$file")
}

write_config() {
  local file="$1" body="$2"
  snapshot "$file"
  printf '%s' "$body" > "$file"
}

restore_touched() {
  local file
  for file in "${touched_files[@]:-}"; do
    [ -n "$file" ] || continue
    if [ -f "$file.e2e-backup" ]; then
      mv "$file.e2e-backup" "$file"
    elif [ -f "$file.e2e-absent" ]; then
      rm -f "$file" "$file.e2e-absent"
    fi
  done
}

# Each half is started with setsid, so it leads its own process group and the game JVM Gradle forks
# is in it. Killing the gradlew pid alone left that JVM running -- still holding the port and still
# writing mcp/server.json -- long after this script exited.
cleanup() {
  for pid in "$client_pid" "$server_pid"; do
    [ -n "$pid" ] || continue
    kill -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null
  done
  wait 2>/dev/null
  # After the processes are gone, so the server cannot rewrite ops.json on its way out.
  restore_touched
}
trap cleanup EXIT

note() { printf '\n== %s\n' "$*"; }
check() {
  local label="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    printf '   ok   %s\n' "$label"
  else
    printf '   FAIL %s: expected %s, got %s\n' "$label" "$expected" "$actual"
    failures=$((failures + 1))
  fi
}

# Reads a field out of a discovery file.
discovery() { python3 -c "import json,sys;print(json.load(open(sys.argv[1]))[sys.argv[2]])" "$1" "$2"; }

# Calls a tool and prints the result JSON. Tool failures come back as isError content, so this
# returns the raw result and the caller decides.
call() {
  local file="$1" tool="$2" args="${3:-{\}}"
  local host port path token
  host=$(discovery "$file" host); port=$(discovery "$file" port)
  path=$(discovery "$file" path); token=$(discovery "$file" authToken)
  curl -s --max-time 60 -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"$tool\",\"arguments\":$args}}" \
    "http://$host:$port$path" \
    | python3 -c "
import json,sys
body = json.load(sys.stdin)
if 'error' in body:
    print(json.dumps({'_rpcError': body['error']})); raise SystemExit
result = body['result']
text = result['content'][0]['text']
try:
    inner = json.loads(text)
except json.JSONDecodeError:
    inner = {'_text': text}
if result.get('isError'):
    inner = {'_toolError': inner}
print(json.dumps(inner))
"
}

field() { python3 -c "import json,sys;print(json.loads(sys.argv[1]).get(sys.argv[2], '<missing>'))" "$1" "$2"; }

wait_for_file() {
  local file="$1" label="$2" deadline=$((SECONDS + ${3:-300}))
  while [ $SECONDS -lt $deadline ]; do
    [ -f "$file" ] && { printf '   %s is up\n' "$label"; return 0; }
    sleep 2
  done
  printf '   FAIL %s never wrote %s\n' "$label" "$file"
  return 1
}

# The dedicated server needs its run directory prepared before it will start at all: vanilla writes
# an unaccepted eula.txt and exits, which from here looks like a server that never came up. Written
# by this script rather than by a Gradle task hooked onto runServer: accepting the eula is a local
# act, and this file is restored on exit like everything else here, so a plain `gradlew runServer`
# neither inherits an acceptance it did not make nor these throwaway-server properties.
#
# max-tick-time is left at the vanilla default. Tool bodies run on McpWorkers, so nothing should hold
# the server thread for a whole tick, and an armed watchdog turns that regression into a failure here.
note "preparing the dedicated server run directory"
write_config "$ROOT/$LOADER/run/server/eula.txt" 'eula=true
'
write_config "$ROOT/$LOADER/run/server/server.properties" 'online-mode=false
level-type=minecraft:flat
spawn-protection=0
view-distance=4
simulation-distance=4
sync-chunk-writes=false
enable-command-block=true
pause-when-empty-seconds=0
'

# Both endpoints refuse the plugin channel by default, so the policy has to be on disk before either
# process starts -- neither loader re-reads it on its own except NeoForge, and relying on that would
# make the two halves of this script asymmetric.
#
# The client is told to trust this machine's dedicated server. The server is NOT given a player list:
# a player is authorised through the minecraft_mcp remote-call permission node, which this script
# grants further down with /op once it knows the player's actual name.
note "writing the access policy for both processes"
# Restored on exit, so a later `gradlew runServer` does not silently inherit disabledTools.
if [ "$LOADER" = "neoforge" ]; then
  write_config "$ROOT/$LOADER/run/server/config/minecraft-mcp.toml" '[access]
	disabledTools = ["mc.server.log.tail"]
	trustedServers = []
'
  write_config "$ROOT/$LOADER/run/client/config/minecraft-mcp.toml" '[access]
	disabledTools = []
	trustedServers = ["127.0.0.1:25565"]
'
else
  write_config "$ROOT/$LOADER/run/server/config/minecraft-mcp.json" \
    '{"access":{"disabledTools":["mc.server.log.tail"],"trustedServers":[]}}
'
  write_config "$ROOT/$LOADER/run/client/config/minecraft-mcp.json" \
    '{"access":{"disabledTools":[],"trustedServers":["127.0.0.1:25565"]}}
'
fi

# The permission check below only means anything if the player starts out unopped, and this script
# opts them in as it goes. Restored on exit like the config files.
snapshot "$ROOT/$LOADER/run/server/ops.json"
rm -f "$ROOT/$LOADER/run/server/ops.json"

note "starting $LOADER dedicated server"
# Removed before launching, not inside wait_for_file: the server can win the race and write it
# before the wait starts, and deleting it then would hang the wait for its full timeout.
rm -f "$SERVER_DISCOVERY" "$CLIENT_DISCOVERY"
setsid ./gradlew ":$LOADER:runServer" --offline -DminecraftMcp.endpoint.port=0 > "$SERVER_LOG" 2>&1 &
server_pid=$!
wait_for_file "$SERVER_DISCOVERY" "server" || { tail -30 "$SERVER_LOG"; exit 1; }

note "starting $LOADER client"
setsid ./gradlew ":$LOADER:runClient" --offline -DminecraftMcp.endpoint.port=0 > "$CLIENT_LOG" 2>&1 &
client_pid=$!
wait_for_file "$CLIENT_DISCOVERY" "client" || { tail -30 "$CLIENT_LOG"; exit 1; }

note "disabledTools applies to the local HTTP endpoint too"
check "a disabled tool is absent from tools/list" "False" \
  "$(curl -s --max-time 30 \
      -H "Authorization: Bearer $(discovery "$SERVER_DISCOVERY" authToken)" \
      -H 'Content-Type: application/json' \
      -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' \
      "http://$(discovery "$SERVER_DISCOVERY" host):$(discovery "$SERVER_DISCOVERY" port)$(discovery "$SERVER_DISCOVERY" path)" \
      | python3 -c "import json,sys;print(any(t['name']=='mc.server.log.tail' for t in json.load(sys.stdin)['result']['tools']))")"
check "calling it is a protocol error, not a tool error" "True" \
  "$(call "$SERVER_DISCOVERY" mc.server.log.tail | python3 -c "import json,sys;print('_rpcError' in json.load(sys.stdin))")"

note "endpoints are distinct"
check "server side"  "server" "$(discovery "$SERVER_DISCOVERY" side)"
check "client side"  "client" "$(discovery "$CLIENT_DISCOVERY" side)"
server_port=$(discovery "$SERVER_DISCOVERY" port)
client_port=$(discovery "$CLIENT_DISCOVERY" port)
[ "$server_port" != "$client_port" ] \
  && printf '   ok   ports differ (%s vs %s)\n' "$server_port" "$client_port" \
  || { printf '   FAIL ports collide: %s\n' "$server_port"; failures=$((failures + 1)); }
[ "$(discovery "$SERVER_DISCOVERY" authToken)" != "$(discovery "$CLIENT_DISCOVERY" authToken)" ] \
  && printf '   ok   tokens differ\n' \
  || { printf '   FAIL tokens are identical\n'; failures=$((failures + 1)); }

note "tool sets are side-appropriate"
check "server has no client tools" "True" \
  "$(call "$SERVER_DISCOVERY" mc.client.screenshot.take | python3 -c "import json,sys;print('_rpcError' in json.load(sys.stdin))")"
check "client serves mc.client.state" "True" \
  "$(call "$CLIENT_DISCOVERY" mc.client.state | python3 -c "import json,sys;print('running' in json.load(sys.stdin))")"

note "connecting the client to the server"
call "$CLIENT_DISCOVERY" mc.client.connection.connect '{"address":"127.0.0.1:25565"}' > /dev/null
sleep 5
matched=$(call "$CLIENT_DISCOVERY" mc.client.condition.wait \
  '{"condition":"client.remoteAvailable == true","timeoutMs":180000,"intervalTicks":10}')
check "client sees the server over the plugin channel" "True" "$(field "$matched" matched)"

note "an unauthorised player is refused"
# The permission node defaults to owner level and nobody is opped yet, so this must fail even though
# the plugin channel itself is up. Regression for the old behaviour, where any connected player could
# drive the whole server tool set.
denied=$(call "$CLIENT_DISCOVERY" mc.remote.call '{"tool":"mc.server.state","arguments":{},"timeoutMs":30000}')
check "call without the permission is refused" "True" \
  "$(python3 -c "import json,sys;print('_toolError' in json.loads(sys.argv[1]))" "$denied")"
check "refusal names the permission node" "True" \
  "$(python3 -c "import json,sys;print('remote.call' in json.dumps(json.loads(sys.argv[1])))" "$denied")"

note "granting the permission through the HTTP endpoint"
# The local endpoint runs commands as console, so it is unaffected by the plugin-channel gate --
# this is the intended admin path. Reading the name back also avoids depending on the dev username,
# which Loom randomises to Player### on Fabric.
players=$(call "$SERVER_DISCOVERY" mc.server.players)
player=$(python3 -c "
import json,sys
names = json.loads(sys.argv[1]).get('names', [])
print(names[0] if names else '')
" "$players")
if [ -z "$player" ]; then
  printf '   FAIL no player is connected to the server\n'
  failures=$((failures + 1))
else
  printf '   connected player: %s\n' "$player"
  call "$SERVER_DISCOVERY" mc.server.command.run "{\"command\":\"op $player\"}" > /dev/null
fi

note "client -> server over the plugin channel"
remote=$(call "$CLIENT_DISCOVERY" mc.remote.call '{"tool":"mc.server.state","arguments":{},"timeoutMs":30000}')
check "server reports dedicated" "True" "$(field "$remote" dedicated)"

note "a blocking tool over the plugin channel does not stall the server thread"
# Regression for the thread model: the payload handler runs on the server thread, so a tool body
# inlined there could never observe the ticks it was itself preventing -- this returned 0 after a
# 20s freeze, and a larger tick count handed the (now re-armed) watchdog a fatal tick. Tool bodies
# run on McpWorkers, so the server keeps ticking while this waits.
ticked=$(call "$CLIENT_DISCOVERY" mc.remote.call \
  '{"tool":"mc.server.ticks.wait","arguments":{"ticks":40},"timeoutMs":30000}')
check "the server ticked while the call waited" "True" \
  "$(python3 -c "import json,sys;print(json.loads(sys.argv[1]).get('waitedTicks', 0) >= 40)" "$ticked")"

note "disabledTools also applies to the plugin channel"
tailed=$(call "$CLIENT_DISCOVERY" mc.remote.call '{"tool":"mc.server.log.tail","arguments":{},"timeoutMs":30000}')
check "an authorised player still cannot reach a disabled tool" "True" \
  "$(python3 -c "import json,sys;print('_toolError' in json.loads(sys.argv[1]))" "$tailed")"

note "revoking the permission takes effect without a restart"
if [ -n "$player" ]; then
  call "$SERVER_DISCOVERY" mc.server.command.run "{\"command\":\"deop $player\"}" > /dev/null
  revoked=$(call "$CLIENT_DISCOVERY" mc.remote.call '{"tool":"mc.server.state","arguments":{},"timeoutMs":10000}')
  check "deop revokes remote access immediately" "True" \
    "$(python3 -c "import json,sys;print('_toolError' in json.loads(sys.argv[1]))" "$revoked")"
  call "$SERVER_DISCOVERY" mc.server.command.run "{\"command\":\"op $player\"}" > /dev/null
fi

note "server -> client over the plugin channel"
clients=$(call "$SERVER_DISCOVERY" mc.remote.state)
printf '   clients known to the server: %s\n' "$clients"
player=$(python3 -c "
import json,sys
entries = json.loads(sys.argv[1]).get('clients', [])
available = [c for c in entries if c.get('available')]
print(available[0]['player'] if available else '')
" "$clients")
if [ -z "$player" ]; then
  printf '   FAIL the server sees no client with the MCP channel registered\n'
  failures=$((failures + 1))
else
  back=$(call "$SERVER_DISCOVERY" mc.remote.call \
    "{\"player\":\"$player\",\"tool\":\"mc.client.state\",\"arguments\":{},\"timeoutMs\":30000}")
  check "server drove the client" "True" "$(field "$back" running)"

  # The same regression from the client side. A screenshot is delivered by a later frame, so
  # inlining it on the render thread deadlocked until the 30s timeout -- it could never succeed.
  shot=$(call "$SERVER_DISCOVERY" mc.remote.call \
    "{\"player\":\"$player\",\"tool\":\"mc.client.screenshot.take\",\"arguments\":{\"name\":\"plugin-channel-e2e.png\"},\"timeoutMs\":60000}")
  check "a frame-bound client tool completes over the channel" "saved" "$(field "$shot" status)"
fi

note "disconnect clears reachability"
# Reachability is the negotiated channel set, so leaving the world drops it by itself -- there is no
# cached flag to go stale. This used to be a handshake the client remembered, and nothing cleared it:
# a client that had seen one hello treated the server as reachable forever, so later calls sent into
# the void and timed out. The fast-fail below is the other half: failPending unblocks whatever was
# already in flight rather than leaving it to time out.
call "$CLIENT_DISCOVERY" mc.client.world.leave > /dev/null
gone=$(call "$CLIENT_DISCOVERY" mc.client.condition.wait \
  '{"condition":"client.remoteAvailable == false","timeoutMs":60000,"intervalTicks":5}')
check "client stops claiming the server is reachable" "True" "$(field "$gone" matched)"
after=$(call "$CLIENT_DISCOVERY" mc.remote.call '{"tool":"mc.server.state","arguments":{},"timeoutMs":5000}')
check "a call after disconnect fails fast" "True" \
  "$(python3 -c "import json,sys;print('_toolError' in json.loads(sys.argv[1]))" "$after")"

note "result"
if [ "$failures" -eq 0 ]; then
  printf 'plugin channel e2e passed\n'
else
  printf 'plugin channel e2e failed: %s check(s)\n\nserver log: %s\nclient log: %s\n' \
    "$failures" "$SERVER_LOG" "$CLIENT_LOG"
fi
exit "$failures"
