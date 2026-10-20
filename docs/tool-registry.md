# Tool registry

Which tools an endpoint serves, and what each prefix means, is in
[protocol.md](protocol.md#tool-namespaces). This note is how the registry behind it is put together.

## Composed from side-specific sets

Four registration sets, combined per endpoint:

- `BuiltinCommonTools` — the unprefixed process-level tools.
- `BuiltinClientTools` — `mc.client.*`.
- `BuiltinServerTools` — `mc.server.*`.
- `BuiltinRemoteTools` — `mc.remote.*`, with separate `registerClient` and `registerServer` entry
  points, because the same two names mean the opposite direction on each side.

`register` throws on a duplicate name. That matters now that tools come from several sets rather than
one: a silent overwrite would be a very quiet bug, since whichever set registered last would win, by
call order.

A client endpoint additionally registers `mc.server.*` backed by its integrated server. Those copies
come out of a throwaway registry via `allTools()`, not `listTools()` — the throwaway has no policy
attached, and it is the copies that `disabledTools` should filter. Otherwise whether a name was
enabled would depend on which registry it came in through. The [server tools](server-tools.md) note
covers the bridge behind them.

## Policy is consulted, not snapshotted

`ToolRegistry` takes a `Supplier<McpConfig>` and asks it on every lookup. The registry is populated
once at startup and never rebuilt, while `disabledTools` is editable at runtime — so holding a
snapshot here would freeze the only part that changes. The no-argument constructor supplies a null
policy that disables nothing, for tests and for that throwaway registry.

`find(name)` returns empty for a disabled tool, which is what makes a disabled tool
indistinguishable from one that was never registered. `allTools()` is the registration-time view that
includes disabled ones; anything serving a request wants `listTools()` or `find`.

`UnknownToolException` extends `IllegalArgumentException` so callers that already caught that keep
working, while letting the JSON-RPC layer tell "no such tool" (`-32602`) apart from "the tool ran and
failed" (`isError: true`).

## One reader for numeric arguments

`McpTools.longArg` accepts the JSON number a well-behaved client sends and the quoted string a
hand-written one often does. The registry's call-level timeout and the `mc.remote.*` tools read
through it; most other tools still cast `(Number)` inline and throw `ClassCastException` on the
quoted form — so whether `"timeoutMs": "5000"` works still depends on which tool you happened to
call. The reader exists so converging on one behaviour is a call-site change, not a design one.

`McpTools.simple` is the factory for the small anonymous tools the builtin sets are made of, with an
unconstrained object schema unless one is passed.

## Testing

`ToolNamespaceTest` pins the split from both ends. It asserts that the intersection of a client
registry and a server registry is exactly the four unprefixed tools — the test's server registry
deliberately leaves out the loader-registered remote set, since `mc.remote.call` and
`mc.remote.state` are the same names on both sides by design — so a new tool cannot quietly become
"available everywhere". And it asserts that every `mc.*`-prefixed name appearing in a scenario file
under `examples/` is registered somewhere, which is what catches a rename the scenarios were not
updated for; scenarios whose top-level `expected` is `"fail"` are exempt, so one may name a missing
tool on purpose.
