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

### Type Registry (`yarf.core`)

Types define shared immutable properties (description, lore, etc.) for entities and tiles. Game authors can add custom properties (species, family, generation frequency).

- `create-type-registry` - creates empty registry
- `define-entity-type [registry type-key properties]` - register entity type
- `define-tile-type [registry type-key properties]` - register tile type
- `get-type-property [registry category type-key property]` - get property (follows inheritance)
- `get-instance-type-property [registry instance property]` - get type property from instance
- `get-property [registry instance property]` - instance value, falling back to type

**Type inheritance:** Types can have `:parent` for single inheritance. Property lookup walks up the chain until found.

```clojure
(-> (create-type-registry)
    (define-entity-type :creature {:mortal true})
    (define-entity-type :humanoid {:parent :creature :has-hands true})
    (define-entity-type :goblin {:parent :humanoid :base-hp 5}))
;; goblin inherits :mortal and :has-hands from ancestors
```

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
- `walkable? [mover tile]` - checks if mover can traverse tile (considers entity abilities)
- `transparent?` - property accessor

### Entities (`yarf.core`)

Entities are game objects (players, monsters, items) with position and display properties. Position is stored as `:pos [x y]`.

- `create-entity [type char color x y]` or `[type char color x y props]`
- `entity-type`, `entity-char`, `entity-color` - accessors
- `entity-pos` - returns `[x y]` position vector
- `move-entity [entity x y]` / `move-entity-by [entity dx dy]` - low-level movement (no bounds checking)

**Map entity management:**
- `add-entity` / `remove-entity` - add/remove from map
- `get-entities` / `get-entities-at [map x y]` - query entities
- `update-entity [map entity f & args]` - update entity in place

**Player:**
- `create-player [x y]` - creates player (`@`, yellow, no input)
- `get-player [map]` - retrieves player from map

**Entity actions and action-results:**

Act functions receive `(entity, game-map)` and return an **action-result** map:
```clojure
{:map     updated-game-map   ;; REQUIRED - clean game state
 ;; Optional:
 :time-cost  10              ;; ticks this action costs (default: entity's :delay)
 :no-time    true            ;; action costs zero time
 :retry      true            ;; action had no effect, retry input
 :quit       true            ;; game should exit
 :message    "text"          ;; display to player
}
```

- `can-act? [entity]` - true if entity has `:act` function
- `act-entity [map entity]` - calls entity's act fn, processes timing, returns action-result
- `process-actors [map]` - processes all actors, returns action-result with accumulated flags
- `process-next-actor [map]` - processes next actor, returns action-result
- `make-player-act [input-fn]` or `[input-fn key-map]` or `[input-fn key-map opts]` - creates player act fn (returns action-result)

**Action timing:**
- `entity-delay` - default ticks between actions (default 10). Lower = faster.
- `entity-next-action` - tick when entity can act next (default 0)
- `get-next-actor [map]` - returns entity with lowest next-action
- After acting, `next-action` is incremented by `:time-cost` if present, otherwise by entity's `delay`
- Actions can return `:time-cost N` to specify custom time cost (e.g., quick attack = 5, heavy attack = 20)

**Key mappings (`yarf.core`):**
- `default-key-map` - vi-style: `hjkl` cardinal, `yubn` diagonal, `x` look
- `direction-deltas` - maps movement actions to `[dx dy]` vectors (e.g. `:move-up` -> `[0 -1]`)
- `execute-action [action entity map]` - executes movement actions via `direction-deltas`; returns action-result
- Custom key maps: `{\w :move-up \s :move-down ...}`

**Movement and terrain:**
- `try-move [map entity dx dy]` - safe movement with bounds and walkability checks; returns action-result
- Use `try-move` for all map-aware movement (players and NPCs)
- Entities cannot move off map edges or into unwalkable tiles
- Failed moves return `{:map game-map :no-time true :retry true}`
- Entity abilities affect terrain interaction:
  - `:can-swim true` - entity can traverse water tiles

**Input retry behavior:**
- `make-player-act` loops until an action that affects the world is performed
- Failed moves and unknown keys are retried immediately without consuming time
- Valid actions (successful moves, quit) exit the loop
- `:look` and `:quit` are handled in `make-player-act`, not in `execute-action`

**`make-player-act` opts:**
- `:registry` - type registry for `look-at` (enables look mode)
- `:on-look-move` - `(fn [game-map cx cy look-info])` callback called at initial cursor position and each move during look mode
- Without `:registry`, pressing look key is treated as unknown input (retried)

**Look mode (`yarf.core`):**
- `look-mode [registry game-map start-x start-y input-fn key-map on-move]` - self-contained cursor movement loop
- Cursor starts at `(start-x, start-y)`, moves with directional keys (same key-map as player)
- `on-move` callback: `(fn [game-map cx cy look-info])` - called at initial position and each cursor move
- `look-info` is the result of `(look-at registry game-map cx cy)`
- Enter: returns `{:map game-map :no-time true :message description}` (falls back to "You see {name}.")
- Escape: returns `{:map game-map :no-time true}` (no message)
- Cursor stays within map bounds; unknown keys are ignored

**Looking at objects (`yarf.core`):**
- `get-name [registry instance]` - returns name (instance, type, or type-key as fallback)
- `get-description [registry instance]` - returns description from instance or type
- `look-at [registry map x y]` - returns `{:name :description :category :target}` for position

**Action-result flags:**
Action-results are returned by act functions, `try-move`, `execute-action`, `act-entity`, `process-actors`, etc.
The `:map` key always contains the game map (clean, no flags embedded).
- `:message` - displayed in message bar by game loop
- `:no-time` - action doesn't increment `next-action`
- `:time-cost N` - action takes N ticks instead of entity's delay
- `:retry` - action had no effect, poll for new input (used with `:no-time`)
- `:quit` - signals game should exit

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
- `display-message [this message]` - show message in message bar

**CursesDisplay** - lanterna implementation:
- `create-curses-display [viewport]` or `[viewport screen-type]`
- `render-map-to-display` / `render-entities-to-display` - render using protocol

**Player with display:**
- `create-player-with-display [x y display]` or `[x y display key-map]`

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

- `hjkl` to move, `yubn` for diagonals, `q` or ESC to quit
- `x` to enter look mode: move cursor, Enter to inspect, Escape to cancel
- Player and two wandering goblins
- Type registry with tile/entity names and descriptions
- Viewport follows player
- Terminal cursor tracks the player; in look mode it tracks the examined square
- Swing screen sized to fit viewport + message bar
- Invalid inputs (unknown keys, blocked moves) are retried immediately

## Style

- **No trivial getter functions.** Entities and tiles are plain maps — access fields directly with keywords (e.g. `(:view-radius entity)`) rather than writing wrapper functions that just retrieve a single field. Only create accessor functions when they provide real value (default values, computed results, or polymorphic behavior).

## Development Notes

- Clojure version: 1.10.3
- License: EPL-2.0 OR GPL-2.0-or-later
- Uses TDD: write failing tests first, then implement
