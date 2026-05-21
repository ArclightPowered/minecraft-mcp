# Scenario condition expression syntax

`mc.condition.wait` conditions use a small boolean expression language.

## Context object

Conditions are evaluated against a context object. Property access uses normal dot syntax:

```text
client.inWorld
connection.disconnected
screen.children.0.message
```

The runtime also provides a global variable named `$`, whose value is the same context object. Therefore these two forms resolve to the same value:

```text
$.client.inWorld
client.inWorld
```

Resolution rule:

1. `client.inWorld` first looks for a global variable named `client`.
2. If no such global variable exists, it falls back to property `client` of the global `$` object.
3. `$.client.inWorld` explicitly starts from the global `$` object.

The BNF below describes property access generically and does not reserve `client`, `connection`, `screen`, or any other context names. They are ordinary properties resolved at evaluation time.

Common top-level properties currently provided by the evaluator:

```text
client       lightweight client snapshot: running, inWorld, screen, playerName, position, rotation
connection   multiplayer/disconnect state: disconnected, screen, title, message
screen       current GUI screen state: hasScreen, screen, title, narration, children
vehicle      player vehicle state: isPassenger, vehicle, passengers, ...
world        world snapshot
inventory    inventory snapshot
```

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
- Direct truthiness is strict: only Boolean `true` is true. Strings and numbers do not auto-coerce to booleans.

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
