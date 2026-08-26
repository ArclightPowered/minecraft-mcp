# Plugin channel internals

What is authorised, by which setting, and in what order is in
[protocol.md](protocol.md#access-control). This note is how the channel code is arranged to make that
true, which the code itself no longer says.

## Three questions, three owners

Conflating any two of these caused a real bug, so the code keeps them apart:

| Question | Answered by | Where |
|---|---|---|
| Is the peer **reachable**? | Minecraft's channel negotiation | the loader (`canSend` / `hasChannel`) |
| Is the peer **trusted**? | the config or the permission API | `McpPluginMessageHandler`, before parsing |
| Which call is this a reply to? | the pending-request map | `RemoteMcpProxy` |

`RemoteMcpProxy.pending` is protocol bookkeeping, not a gate. Matching an id says which call a reply
belongs to, never whether the peer was allowed to send it — the gate ran before the payload got here.
The proxy used to also answer the other two, and both were wrong there: caching an
application-level hello meant the reachability answer could outlive the connection it described, and
treating an unmatched id as a refusal confused "unsolicited" with "unauthorised".

The proxy registers a pending future *before* sending, which is what makes a reply that arrives
immediately safe. `failPending()` fails everything in flight when the link goes away; without it a
pending call sat for its full 30s against a peer that was never going to answer.

Reachability answers "did the peer register the channel", and on NeoForge that answer can be yes from
a process that will never reply. `registerPayloads` runs on the mod bus whatever the dist —
`playToServer` has to be registered on both so the codecs match, the same constraint the clientbound
routes carry below — so every NeoForge instance declares `minecraft_mcp:server_request`, a client
included. Answering on it needs `pluginHandler`, which is built only when a *dedicated* server starts.
A client that opens its world to LAN is a server without one: it declares the channel, `hasChannel`
tells the joining client it is reachable, and the request dies at the null check, so the caller waits
out its full timeout. Fabric cannot reach that state — its whole serverbound half is a
`DedicatedServerModInitializer`, so a LAN host never registers the channel, `canSend` is false and
`mc.remote.call` refuses before sending. Matching that on NeoForge would mean making the registration
conditional and giving up the codec parity it exists to hold, so this one is written down rather than
fixed.

## `ChannelCaller` carries the identity

It is sealed, and `McpConfig.trusts` switches over it exhaustively, so adding a fourth kind of caller
later cannot silently default to trusted. Three variants: `Local` (this process's own integrated
server, over an in-memory connection), `Player` (a serverbound sender, carrying the verdict the
loader's permission API already resolved) and `Server` (identified by the address as typed into the
multiplayer screen).

It names no Minecraft types at all. The same abstraction has to describe "the player who sent this
request" and "the server this client is connected to", and it has to be constructible from `common`,
which may not touch `net.minecraft.client`.

The two variants carry different things for a reason. A `Player`'s permission check needs a
`ServerPlayer` and a loader-specific API, so the loader does the check and reports a boolean. A
`Server` carries only an identity, because matching it against `trustedServers` is something
`McpConfig` can do on its own.

The refusal wording lives next to the decision, in `McpConfig.refusal`, because both directions need
the same sentence: an inbound request is refused with it, and an outbound `mc.remote.call` fails with
it before sending.

## Resolving which server a payload came from

That is client-only work — every type involved is `net.minecraft.client` — so it is duplicated per
loader as `FabricClientTrust` and `NeoForgeClientTrust` rather than shared from `common`.

Both resolve per request rather than caching. A stale "trusted" answer is the one failure mode worth
avoiding here, and a reconnect would invalidate a cache anyway. An in-memory connection is checked
first, because in singleplayer the "current server" lookups return null. An unidentifiable caller is
refused rather than guessed at.

NeoForge's has two entry points, but they differ in less than it looks: the inbound one only uses
the payload's own connection to spot the in-memory case, then resolves a `Server` identity from the
connection this client currently holds — the same lookup the outbound entry point, which has no
payload context at all, uses for everything. Fabric's reads the
current connection for both: its receivers run on the client thread, where the current connection is
the one the payload arrived on — or already gone, which resolves to an unidentifiable caller and is
refused.

On NeoForge the clientbound handlers go through `ClientPayloadRouter`. `playToClient` has to be
registered on both dists so the codecs match, but neither handler body can live in the dist-agnostic
mod class, because both have to ask whether the sending server is trusted and answering that means
reading client state. Both directions need the routing, not just the request one — a reply is
authorised exactly like a request, and that verdict needs the same client-only lookup. On a dedicated
server the routes stay unset and never fire.

## `RemoteMcpProxies` is asymmetric

A client has exactly one peer, so its proxy is a singleton. A server has one per connected player,
keyed by UUID, and those must be dropped on disconnect or the map grows for the life of the server.

Entries are created from the loaders' join event, plus a catch-up sweep over players already online
when the endpoint starts. A client used to be able to announce itself over a hello channel, and
that channel is gone — routing every creation through `rememberClientName` is what keeps the proxy
map and the player-name map in step.

It lives in `common`, deliberately not on a loader's client bridge class: payload handlers have to be
registered on both dists for the codecs to match, so the registration site cannot reference
client-only types.

`clientSummaries` takes the reachability predicate as an argument rather than answering it, since that
needs the loader's networking API and the player list. Every joined player gets an entry regardless of
whether it is reachable, because an operator diagnosing `mc.remote.call` wants to see the player that
*cannot* be reached just as much as the ones that can.

## Threads and the two errors that are caught

Both receive methods are called on a game thread; the handler resolves only the trust verdict —
and, for a response, which proxy the reply belongs to — inline, and hands everything else, parsing
included, to `McpWorkers`. The reasoning is in
[protocol.md](protocol.md#threads) and the [MCP worker threads](mcp-worker-threads.md) note.

The trust verdict is resolved on the game thread rather than on the worker for a reason of its own: a
`ChannelCaller.Server` is only meaningful next to the client state that thread owns, and a verdict
taken later could disagree with the one the caller was told about. It needs nothing from the payload,
which is what makes that possible.

A `Sender` handed to the handler must be safe to call from a worker, so it has to be bound to the
connection the request arrived on rather than resolving "the current connection" when it fires. Both
loaders pass their payload context's own reply path, which ends in `Connection#send`.

Two failures are caught explicitly instead of being left to escape a worker:

- **`RejectedExecutionException`** means the endpoint is shutting down and there is no worker left to
  hand the payload to. A request is still answered rather than dropped silently, but without an id —
  reading one would mean parsing on the game thread, which is exactly what the handoff avoids.
  `RemoteMcpProxy` matches replies by id, so an answer carrying none matches nothing and the peer
  discards it. That answer is therefore not what spares the peer its timeout; the disconnect is.
  Shutting an endpoint down drops the connection, and the peer's `failPending()` fails everything in
  flight. Sending the idless answer still costs nothing on a wire that is closing, so it stays. A
  response is simply dropped, since there is nothing it could be an answer to.
- **`StackOverflowError`** is caught alongside `RuntimeException` on both parse paths. It is not a
  nesting limit — a peer we let in is one we do try to read — but `Json` is recursive descent and the
  depth is the peer's choice, so a deep payload can exhaust the worker's stack. Letting it escape would
  take the worker with it; catching it costs one message. Recovery is sound because the stack has
  already unwound by the time the handler runs. The call it belonged to then falls back to its own
  timeout.

The handler is constructed with its executor as a required argument, with no default. A handler that
ran tool bodies on the caller's thread is exactly the bug that parameter exists to prevent, so every
call site has to name the threads it means. The same applies to the caller identity on
`receiveRequest`: there is no convenience overload, because every receiver is a global payload handler
and failing to compile is better than silently skipping the check.

## Channel ids

```text
minecraft_mcp:server_request    client asks the server to run a tool
minecraft_mcp:server_response   server answers a client request
minecraft_mcp:client_request    server asks a client to run a tool
minecraft_mcp:client_response   client answers a server request
```

A channel id is inherently one-directional, so reaching a client from the server needs its own pair
rather than reusing the serverbound set. Splitting requests from responses is also what makes
authorisation-before-parsing possible: the kind of message is known from the id, which is what lets a
refused request be answered while a refused response is dropped.
