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
- `create-player [x y]` - creates player (`@`, yellow)
- `get-player [map]` - retrieves player from map

### Map Generation (`yarf.core`)

- `fill-rect [map x y w h tile]` - fill rectangular region
- `make-room [map x y w h]` - room with wall border and floor interior
- `make-corridor [map x1 y1 x2 y2]` - L-shaped corridor
- `generate-test-map [width height]` - sample map for testing

### Display (`yarf.display`)

Curses display using clojure-lanterna.

**Viewport:**
- `create-viewport [w h]` - create viewport
- `center-viewport-on` / `clamp-to-map` - position viewport
- `world-to-screen` / `screen-to-world` - coordinate conversion

**Screen:**
- `create-screen` - create lanterna screen (`:text`, `:swing`, `:auto`)
- `start-screen` / `stop-screen` - lifecycle
- `render-map [screen map viewport]` - draw visible map with colors
- `render-entities [screen map viewport]` - draw visible entities
- `render-char [screen x y ch]` or `[screen x y ch color]` - draw character
- `get-key` / `get-key-non-blocking` - input handling

## Development Notes

- Clojure version: 1.10.3
- License: EPL-2.0 OR GPL-2.0-or-later
- Uses TDD: write failing tests first, then implement
