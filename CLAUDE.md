# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

YARF (Yet Another Roguelike Framework) is a Clojure library for building roguelike games. It supports both Clojure and ClojureScript, with graphical (tiled) interfaces or text-based display using clj-lanterna and Curses.

This project is an experiment in using Claude Code to build a reusable component rather than an application.

## Build Commands

This is a Leiningen-based Clojure project.

```bash
lein repl          # Start REPL (enters yarf.core namespace)
lein test          # Run all tests
lein run           # Run demo game
lein uberjar       # Build executable JAR
```

## Project Structure

- `src/yarf/` - Main source code
- `test/yarf/` - Test suite (mirrors src structure)
- `doc/` - Documentation
- `project.clj` - Leiningen configuration

## Architecture

### Tile Maps (`yarf.core`)

Maps use a flat vector for tile storage with coordinate-to-index conversion.

- `create-tile-map [width height]` - creates map filled with floor tiles
- `get-tile / set-tile` - coordinate-based access (immutable updates)
- `in-bounds?` - boundary checking

### Tiles

Tiles are maps with `:type`, `:char`, `:color`, and properties (`:walkable`, `:transparent`).

- `make-tile [type char color properties]` - create custom tiles
- Predefined: `floor-tile`, `wall-tile`, `door-closed-tile`, `door-open-tile`, `water-tile`
- `tile-char` / `tile-color` - display accessors (char and color are core game data)
- `walkable?` / `transparent?` - property accessors

### Entities (`yarf.core`)

Entities are game objects (players, monsters, items) with position and display properties.

- `create-entity [type char color x y]` or `[type char color x y props]`
- `entity-type`, `entity-char`, `entity-color`, `entity-x`, `entity-y` - accessors
- `move-entity [entity x y]` / `move-entity-by [entity dx dy]` - movement

**Map entity management:**
- `add-entity` / `remove-entity` - add/remove from map
- `get-entities` / `get-entities-at [map x y]` - query entities
- `update-entity [map entity f & args]` - update entity in place

**Player:**
- `create-player [x y]` - creates player (`@`, yellow, no input)
- `get-player [map]` - retrieves player from map

**Entity actions:**
- `can-act? [entity]` - true if entity has `:act` function
- `act-entity [map entity]` - calls entity's act fn `(fn [entity map] -> map)`
- `process-actors [map]` - processes all entities with act functions
- `make-player-act [input-fn]` - creates act fn that gets input from input-fn

### Map Generation (`yarf.core`)

- `fill-rect [map x y w h tile]` - fill rectangular region
- `make-room [map x y w h]` - room with wall border and floor interior
- `make-corridor [map x1 y1 x2 y2]` - L-shaped corridor
- `generate-test-map [width height]` - sample map for testing

### Display (`yarf.display`)

**Display protocol:**
- `get-input` - get input (blocking)
- `render-tile [this x y tile]` / `render-entity [this x y entity]`
- `clear-screen` / `refresh-screen` - screen management
- `start-display` / `stop-display` - lifecycle

**CursesDisplay** - lanterna implementation:
- `create-curses-display [viewport]` or `[viewport screen-type]`
- `render-map-to-display` / `render-entities-to-display` - render using protocol

**Player with display:**
- `create-player-with-display [x y display]` - player that gets input from display

**Viewport:**
- `create-viewport [w h]` - create viewport
- `center-viewport-on` / `clamp-to-map` - position viewport
- `world-to-screen` / `screen-to-world` - coordinate conversion

**Legacy screen functions** (low-level lanterna access):
- `create-screen`, `start-screen`, `stop-screen`
- `render-map`, `render-entities`, `render-char`
- `get-key`, `get-key-non-blocking`

### Demo (`yarf.demo`)

Simple game loop demonstrating the framework. Run with `lein run`.

- Arrow keys to move player, `q` or ESC to quit
- Player and two wandering goblins
- Viewport follows player

## Development Notes

- Clojure version: 1.10.3
- License: EPL-2.0 OR GPL-2.0-or-later
- Uses TDD: write failing tests first, then implement
