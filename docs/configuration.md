# Configuration internals

What the settings are, where they resolve from and which ones need a restart is in
[protocol.md](protocol.md#configuration). This note is the other half: why the config code is shaped
the way it is, which the code itself no longer says.

## Layers, not a snapshot

`McpConfig` holds the *layers* rather than a snapshot of resolved values, so a getter reflects what
the layers answer right now. That is what makes `access.*` live on NeoForge with no reload plumbing
here: `ModConfigSpec.ConfigValue.get()` caches, but FML clears that cache on every config reload,
so the next `get()` reads the new file and `McpConfig` has nothing to invalidate and nothing to
swap. A loader installs one config and never replaces it. Fabric's file layer is the contrast: the
file is parsed once at startup, so on that loader "live" extends only to the property and
environment layers.

The settings that must *not* follow the file are frozen by the platform rather than by copying them
into a record — a `gameRestart()` value is exactly one whose cache the reload leaves alone, so its
`get()` keeps answering the old value. So "restart to change" is a property of the spec, not a
convention the getters have to remember.

Reading config before a loader installed one throws. That is loud on purpose: it is a startup-order
bug, and a silent all-defaults fallback would mean quietly trusting nobody or disabling nothing,
which is very hard to diagnose from the outside.

## `DefaultOptions` is the register of what exists

It is the bottom of the chain, and because it always answers it is what makes resolution total — no
getter has to describe "unset". It doubles as the list of which options exist: the startup report
walks its paths, and NeoForge checks its spec against the same list at startup.

So adding an option means adding it to `DefaultOptions` plus a getter on `McpConfig`, and nowhere
else in `common`. Insertion order is report order, which is why related options are kept together.

NeoForge's coverage check runs at startup rather than in a test because that module has no test
source set, and standing one up would mean bootstrapping the game just to touch a spec. Without it
the failure is quiet: a path the spec never defined falls through to the built-in default, so the
option simply cannot be set from the config file on that loader while still working from a system
property.

## Why the report is a separate call

`McpConfigs.report()` is not folded into `install()` because the two happen at different times on
NeoForge. The config object can be installed during mod construction, but FML has not read the file
until `CommonModLoader.begin()` — so reporting there would claim every setting came from its default.
NeoForge defers it to `ModConfigEvent.Loading`; Fabric has already parsed its file by install time and
reports straight away.

`Reloading` is also listened to, and reruns the report: FML has already refreshed the spec's caches
by then, so there are no values for this side to refresh — what a reload changes is where a value
resolves from, and rerunning the report is what makes that visible. Rerunning it also re-validates,
so an edit that leaves the config invalid throws from the `Reloading` handler rather than surfacing
later.

## A layer does not name itself

`McpOptions` is one layer, looked up by option path. It returns empty for "nothing to say", and the
order and the labels belong to `McpConfig` — the only thing that knows what the chain looks like.
That is what keeps the two loaders from drifting apart on precedence: a loader supplies exactly one
layer, and has no say in where it sits.

Lists get their own method rather than being flattened through the scalar path, because TOML and JSON
both have a native list type; inventing a separator would only serve the two string-based layers,
which split on commas themselves.

## The JSON document

`McpConfigDocument` lives in `common` rather than in the Fabric module so its parsing rules can be
covered by plain unit tests. Only Fabric reads this format; NeoForge goes through `ModConfigSpec`.

`Json` has no comment syntax, so the shipped default carries its documentation in `_comment` arrays —
real data, which survives a parse untouched and is ignored on the way in.

What a malformed file *means* is the caller's decision, not the parser's, because the two `access`
settings want opposite answers. `read` throws, and `FabricMcpConfig` falls back to an empty layer —
which fails closed on `trustedServers` and open on `disabledTools`. There is no "previous layer" to
keep, because the file is read once at startup.

Two smaller parsing choices: a scalar is stringified rather than type-checked, since `Json` returns
`Integer`/`Boolean` for TOML-equivalent literals and `McpConfig` converts from the string form anyway;
and a bare string where a list is expected is accepted as a one-element list, so a single entry can be
written without brackets.

## Where the two access lists differ

Both trim their entries and drop blanks. The property and environment layers already trim what they
split on commas, but a config file's own list does not — so `["mc.server.log.tail "]` used to disable
nothing.

They differ in exactly one respect, and it is deliberate: `trustedServers` folds case,
`disabledTools` does not. A hostname is case-insensitive by definition, while a tool name is an
identifier — so `mc.Server.State` genuinely is not a tool, and folding it would be wrong rather than
lenient.

## Permission node names are strings

`McpPermissions` keeps them as plain strings because the two loaders' permission APIs are unrelated
and neither is reachable from `common`. Both spellings are named there — NeoForge joins with a dot,
Fabric goes through `Identifier` and joins with a colon — so a refusal can quote the one that works
on the server the player is actually on. [protocol.md](protocol.md#the-remote-call-permission-node)
has what the node grants.
