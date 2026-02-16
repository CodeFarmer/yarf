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

Act functions receive `(entity, game-map, ctx)` and return an **action-result** map:
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
- `act-entity [map entity ctx]` - calls entity's act fn with ctx, processes timing, returns action-result
- `process-actors [map ctx]` - processes all actors, returns action-result with accumulated flags
- `process-next-actor [map ctx]` - processes next actor, returns action-result
- `player-act [entity game-map ctx]` - named player act function; reads `:input-fn`, `:key-map`, `:registry`, `:on-look-move`, `:look-bounds-fn` from ctx

**Context (ctx):**
A plain map passed to all act functions. Game-specific keys can be added freely. Standard keys used by `player-act` and `look-mode`:
- `:input-fn` - blocking input function `(fn [] key)` (required for player)
- `:key-map` - maps input keys to actions (required for player)
- `:registry` - type registry for look-at (enables look mode)
- `:on-look-move` - `(fn [ctx game-map cx cy look-info])` callback during look mode
- `:look-bounds-fn` - `(fn [ctx game-map entity] [min-x min-y max-x max-y])` computes bounds at look-mode entry
- Game-specific: `:screen`, `:viewport`, `:explored`, etc.

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
- `player-act` loops until an action that affects the world is performed
- Failed moves and unknown keys are retried immediately without consuming time
- Valid actions (successful moves, quit) exit the loop
- `:look` and `:quit` are handled in `player-act`, not in `execute-action`
- Without `:registry` in ctx, pressing look key is treated as unknown input (retried)

**Look mode (`yarf.core`):**
- `look-mode [ctx game-map start-x start-y]` - self-contained cursor movement loop
- `look-mode [ctx game-map start-x start-y bounds]` - with optional bounds
- Reads `:registry`, `:input-fn`, `:key-map`, `:on-look-move` from ctx
- Cursor starts at `(start-x, start-y)`, moves with directional keys (same key-map as player)
- `:on-look-move` callback: `(fn [ctx game-map cx cy look-info])` - called at initial position and each cursor move
- `look-info` is the result of `(look-at registry game-map cx cy)`
- `bounds`: optional `[min-x min-y max-x max-y]` to constrain cursor movement (intersected with map bounds); nil = map bounds only
- Enter: returns `{:map game-map :no-time true :message description}` (falls back to "You see {name}.")
- Escape: returns `{:map game-map :no-time true}` (no message)
- Cursor stays within bounds (and map bounds); unknown keys are ignored

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

### Field of View (`yarf.core`)

Uses **recursive shadow casting** across 8 octants. Opaque tiles (walls, closed doors) are visible but block tiles behind them. Uses `transparent?` for opacity and **Chebyshev distance** for radius (square-shaped FOV).

- `compute-fov [map ox oy]` or `[map ox oy radius]` - returns `#{[x y] ...}` set of visible coordinates from origin; nil radius = unlimited (bounded by map size)
- `compute-entity-fov [map entity]` - uses entity's `:pos` and `:view-radius` (nil = unlimited)

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
- FOV/fog of war: visible tiles in color, explored tiles in blue, unexplored black; entities hidden outside FOV
- Explored state is a plain set threaded through the game loop via ctx. The `game-loop` updates `:explored` in ctx each turn; look-mode callbacks read it from ctx.

## Style

- **No trivial getter functions.** Entities and tiles are plain maps — access fields directly with keywords (e.g. `(:view-radius entity)`) rather than writing wrapper functions that just retrieve a single field. Only create accessor functions when they provide real value (default values, computed results, or polymorphic behavior).
- **Prefer refactoring signatures over inserting workaround state.** When a function needs access to data it doesn't currently receive, refactor the call chain to pass it through (e.g. add a ctx parameter) rather than introducing shared mutable state (atoms, globals) or closures that capture ambient dependencies. The ctx refactor was a large retroactive fix for exactly this — design function signatures to be extensible from the start.

## TODO

- Fix green character artifacts when Swing window is resized larger than viewport. `render-game` only writes within viewport bounds; lanterna/Swing repeats buffer content to fill extra pixel area. Need a proper fix (e.g. clearing the full terminal buffer, or handling resize events).
- Fix FOV shadow casting artifacts near walls and in corridors — some tiles that should be visible are left unseen. Likely an issue in `compute-fov` octant scanning (e.g. wall-adjacent tiles missed at octant boundaries).
- Add a context-sensitive default action for directional keypresses: inspect the target square and choose the appropriate action (move into empty floor, attack an entity, open a closed door, etc.) instead of always attempting movement.
- Implement save/load using EDN + gzip. With act functions no longer closures (they're named functions like `player-act`), save just needs to persist the game map and context data; load reconstructs act functions by looking up entity `:type`.
- Consider later: move game-map into the context as well, simplifying the act signature to `(entity, ctx)` where ctx contains both the map and metadata.
- Support multiple maps in the world (e.g. dungeon levels, overworld, buildings). Needs a world structure that holds named maps, transitions between them (stairs, portals), and per-map explored state.

## Development Notes

- Clojure version: 1.10.3
- License: EPL-2.0 OR GPL-2.0-or-later
- Uses TDD: write failing tests first, then implement
- **Commits:** Do not add AI/co-authored-by lines to commit messages
