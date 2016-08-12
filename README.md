# Rill/Wheel - Event generation using aggregates and commands

![Logo](logo.png)


> It may be hard for an egg to turn into a bird: it would be a jolly
> sight harder for it to learn to fly while remaining an egg.

-- C.S. Lewis

[![Build Status](https://travis-ci.org/rill-event-sourcing/wheel.svg?branch=master)](https://travis-ci.org/rill-event-sourcing/wheel)

## Usage

See [the manual](https://rill-event-sourcing.github.io/wheel/index.html)

## Changelog

### NEXT
  - (breaking) aggregate ids now include the aggregate type
  - body of `defevent` is now optional
  - (breaking) `defevent` creates additional `{name}-event` function

### v0.1.5
  Some breaking API updates.
  - defaggregate builds the aggregate constructor automatically
  - aggregate id is a map of identifying properties
  - repository protocol does `update` instead of
    `fetch`.

### v0.1.4
  - Finished `rill.wheel.wrap-stream-properties`.

### v0.1.3
  - Fixed `rill.wheel.testing/sub?` checking seqs with lists.

### v0.1.2

  - Added `rill.wheel.check` namespace for checking model consistency.
  - Enforcing use of `:rill.wheel.command/events` key on command
    definitions.

### v0.1.1

Initial release

## License

Copyright © 2016 Joost Diepenmaat, Zeekat Software Ontwikkeling

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
