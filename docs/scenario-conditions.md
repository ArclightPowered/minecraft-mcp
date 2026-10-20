# Scenario condition expression syntax

`mc.client.condition.wait` and `mc.server.condition.wait` conditions use a small boolean expression
language. The syntax is identical on both; only the properties in scope differ.

## Context object

Conditions are evaluated against a context object. Property access uses normal dot syntax:

```text
client.inWorld
connection.disconnected
screen.children.0.message
```

The language also has a built-in root named `$`, whose value is the whole context object. When both
spellings are valid, they resolve to the same value:

```text
$.client.inWorld
client.inWorld
```

They differ in how unknown names are treated (see below): the shorthand form is validated, the `$.`
form is dynamic.

Resolution rule:

1. `client.inWorld` first looks for a global variable named `client`.
2. Otherwise `client` must be a registered context property; it is read off the `$` root.
3. A chain-start name that is neither is an **unknown property**: the expression is rejected up
   front, before any waiting or recording starts. All unknown names in the expression are reported
   together, along with the names that do exist.
4. `$.client.inWorld` explicitly starts from the built-in `$` root. Names read off `$` are
   **dynamic**: an unknown one is `null`, never an error. Use this to probe for properties that may
   not exist on the current side, e.g. `exists($.vehicle)`.

The BNF below describes property access generically and does not reserve `client`, `connection`,
`screen`, or any other context names. They are ordinary properties resolved at evaluation time.

### Three kinds of errors

| Kind | When it is reported | Example |
| --- | --- | --- |
| Syntax error | Parsing the expression string | `client.inWorld ==` |
| Static error: unknown property or function, wrong argument count, invalid literal regex | Binding the expression to a side's registry, before the first evaluation | `sceen.title != null`, `exsits(client)`, `matches(x, "[")` |
| Evaluation error | At runtime, depending on data | `summary.hp > 3` when `hp` is absent |

Reads are lenient, comparisons are strict: a missing member anywhere below a valid chain start is
`null`, and `null` keeps propagating through deeper accesses (`world.nosuchfield.deeper` is `null`).
`==`, `!=`, `exists` and `missing` handle `null` fine; the ordering operators `>` `>=` `<` `<=`
refuse it with an evaluation error. That error is what surfaces a misspelled *member* name, which
up-front validation cannot see.

For the condition wait tools (`mc.client.condition.wait`, `mc.server.condition.wait`) these map to
three outcomes:

- static error (unknown property or function, wrong argument count, invalid literal regex): the tool
  call fails immediately, listing everything that is wrong with the expression;
- the condition never evaluated successfully before the timeout (its state stayed unreadable): the
  tool call fails with the number of attempts and the last error;
- the condition evaluated fine but stayed false: the tool returns `matched: false`. Waiting on a
  dynamic `$.` probe that never appears also ends here, by design.

The second and third outcomes are distinguished so that a wait whose state was never readable cannot
be mistaken for a wait that read fine and saw `false`. The polling loop behind them — where the
expression is validated, how the poll interval is clamped so it cannot stretch the timeout, and why
the deadline uses a monotonic clock — is described in the [Minecraft
bridges](minecraft-bridges.md#the-waituntil-polling-loop) note.

## Properties by side

There is one registry per side, so the same name can mean the side-appropriate thing. `world` is
deliberately shared, with a compatible shape, so an expression like `world.dimension` ports between
`mc.client.condition.wait` and `mc.server.condition.wait`.

Client endpoints (`mc.client.condition.wait`):

```text
client       lightweight client snapshot: running, inWorld, screen, playerName, position, rotation
             plus integratedServer (this process has a MinecraftServer) and remoteAvailable
             (a server is reachable over the plugin channel)
connection   multiplayer/disconnect state: disconnected, screen, title, message
screen       current GUI screen state: hasScreen, screen, title, narration, children
vehicle      player vehicle state: inWorld, isPassenger, vehicle, x, y, z
world        world snapshot
inventory    inventory snapshot
packet       client packet-recording status: recording, count, serverbound, clientbound,
             nextSequence, filter, last
```

Server endpoints (`mc.server.condition.wait`), including a singleplayer client's integrated server:

```text
server       server state: running, dedicated, motd, players, maxPlayers, version, overworldTime
world        overworld snapshot: inWorld, dimension, gameTime, difficulty, players, loadedChunks,
             entityCount
players      online players: count, names, players
tick         tick statistics: tickCount, averageTickMs, smoothedTickMs, overloaded
packet       reserved, not implemented -- nothing records server-side packets yet, so this always
             reports an idle recorder. Do not write conditions against it.
```

`screen`, `connection`, `vehicle` and `inventory` have no server-side meaning and are absent there;
naming one in a server condition is an error rather than a silent false.

## BNF

```bnf
condition     ::= orExpr EOF
orExpr        ::= andExpr ( "||" andExpr )*
andExpr       ::= unaryExpr ( "&&" unaryExpr )*
unaryExpr     ::= "!" unaryExpr | comparison
comparison    ::= primary ( compOp primary )?
compOp        ::= "==" | "!=" | ">=" | "<=" | ">" | "<"
primary       ::= literal
                | path
                | functionCall
                | "(" condition ")"
functionCall  ::= identifier "(" argumentList? ")"
argumentList  ::= condition ( "," condition )*
path          ::= propertyAccess
propertyAccess ::= property ( "." property )*
property      ::= identifier | unsignedInteger
literal       ::= booleanLiteral | nullLiteral | numberLiteral | stringLiteral
booleanLiteral ::= "true" | "false"
nullLiteral   ::= "null"
numberLiteral ::= "-"? digit+ ( "." digit+ )?
stringLiteral ::= doubleQuotedString | singleQuotedString
identifier    ::= identifierStart identifierPart*
```

Notes:

- A bare identifier is parsed as a shorthand path, not as an unquoted string.
- Function names are reserved only when followed by `(`.
- Direct truthiness is strict: only Boolean `true` is true. Strings and numbers do not auto-coerce
  to booleans.

## Functions

```text
exists(value)
missing(value)
contains(value, needle)
startsWith(value, prefix)
endsWith(value, suffix)
matches(value, regex)
size(value)
```

Function names, argument counts, and `matches` regexes written as string literals are all fixed
once the expression is parsed, so they are checked when the expression is bound, together with
property names. A regex that arrives as data (`matches(packetClass, summary.pattern)`) can only
be compiled at evaluation time; its failures are evaluation errors.

## Examples

```text
client.inWorld == true
client.inWorld && client.screen == null
client.inWorld && client.screen == null
screen.hasScreen && contains(screen.title, "Title")
connection.disconnected == false
!vehicle.isPassenger
size(screen.children) > 0
missing(client.screen)
```
