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

**Type `:act` property:** Entity types register their `:act` function in the type registry. The registry is the single source of truth for entity behavior — entities themselves never carry `:act`. Act functions are looked up from the registry via the entity's `:type` at runtime. This means save/load is trivial (no stripping/restoring needed).

**Tile display/behavior properties:** Tile types register `:char`, `:color`, `:walkable`, `:transparent` in the type registry. Tile instances on the map are minimal (`{:type :floor}`) — display and behavior properties are looked up from the registry at runtime. Instance properties override type properties. Use `register-default-tile-types` to register the standard five tile types.

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

Tile instances on the map are minimal (just `{:type :floor}`). Display and behavior properties (`:char`, `:color`, `:walkable`, `:transparent`) live in the type registry and are looked up at runtime. Individual tiles can override via instance properties.

- `make-tile [tile-type]` or `[tile-type properties]` - create tile (properties are instance overrides)
- Tile instances are just `{:type :floor}`, `{:type :wall}`, etc. — no predefined vars, use map literals directly
- `default-tile-types` - map of default tile type definitions with display/behavior properties
- `register-default-tile-types [registry]` - registers `:floor`, `:wall`, `:door-closed`, `:door-open`, `:water`, `:stairs-down`, `:stairs-up` types
- Stair tiles: `{:type :stairs-down}` (`>`, white, walkable, transparent), `{:type :stairs-up}` (`<`, white, walkable, transparent)
- `tile-char [registry tile]` / `tile-color [registry tile]` - display accessors (instance, then type registry)
- `walkable? [registry mover tile]` - checks if mover can traverse tile (instance, then registry, then abilities)
- `transparent? [registry tile]` - opacity accessor (instance, then type registry)

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

- `act-entity [map entity ctx]` - looks up act fn from `(:registry ctx)`, calls it with ctx, processes timing, returns action-result
- `process-actors [map ctx]` - processes all actors, returns action-result with accumulated flags
- `process-next-actor [map ctx]` - processes next actor, returns action-result
- `player-act [entity game-map ctx]` - named player act function; reads `:input-fn`, `:key-map`, `:registry`, `:on-look-move`, `:look-bounds-fn`, `:on-bump` from ctx

**Context (ctx):**
A plain map passed to all act functions. Game-specific keys can be added freely. Standard keys:
- `:registry` - type registry (required — used for act dispatch, look-at, etc.)
- `:input-fn` - blocking input function `(fn [] key)` (required for player)
- `:key-map` - maps input keys to actions (required for player)
- `:on-look-move` - `(fn [ctx game-map cx cy look-info])` callback during look mode
- `:look-bounds-fn` - `(fn [ctx game-map entity] [min-x min-y max-x max-y])` computes bounds at look-mode entry
- `:on-bump` - `(fn [mover game-map bumped-entity ctx])` callback when player bumps a blocking entity; returns action-result. Without `:on-bump`, bumps retry like walls.
- `:pass-through-actions` - set of action keywords that `player-act` returns as `{:action <keyword>}` for the game loop to handle (e.g. `#{:save}`)
- Game-specific: `:screen`, `:viewport`, `:explored`, etc.

**Action timing:**
- `entity-delay` - default ticks between actions (default 10). Lower = faster.
- `entity-next-action` - tick when entity can act next (default 0)
- After acting, `next-action` is incremented by `:time-cost` if present, otherwise by entity's `delay`
- Actions can return `:time-cost N` to specify custom time cost (e.g., quick attack = 5, heavy attack = 20)

**Key mappings (`yarf.core`):**
- `default-key-map` - vi-style: `hjkl` cardinal, `yubn` diagonal, `x` look
- `direction-deltas` - maps movement actions to `[dx dy]` vectors (e.g. `:move-up` -> `[0 -1]`)
- `execute-action [registry action entity map]` - executes movement actions via `direction-deltas`; returns action-result
- Custom key maps: `{\w :move-up \s :move-down ...}`

**Movement and terrain:**
- `try-move [registry map entity dx dy]` - safe movement with bounds, walkability, and entity-blocking checks; returns action-result
- Use `try-move` for all map-aware movement (players and NPCs)
- Entities cannot move off map edges or into unwalkable tiles
- Failed moves return `{:map game-map :no-time true :retry true}`
- Moving into a tile with a blocking entity returns `{:map game-map :no-time true :retry true :bumped-entity entity}` — callers that ignore `:bumped-entity` get wall-like behavior (backward compatible)
- `blocks-movement? [registry entity]` - checks if entity blocks movement (instance, then type registry, defaults to false). Follows the `contains?` pattern (instance overrides type, `false` is a valid value).
- Entity abilities affect terrain interaction:
  - `:can-swim true` - entity can traverse water tiles

**Input retry behavior:**
- `player-act` loops until an action that affects the world is performed
- Failed moves and unknown keys are retried immediately without consuming time
- Valid actions (successful moves, quit) exit the loop
- Bumping a blocking entity: if `:on-bump` is in ctx, calls it and returns its result; otherwise retries like a wall
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
- `:action` - pass-through action keyword (returned when action is in `:pass-through-actions`)
- `:bumped-entity` - the blocking entity at the destination (set by `try-move` when movement is blocked by an entity, not by terrain)

### Map Generation (`yarf.core`)

- `fill-rect [map x y w h tile]` - fill rectangular region
- `make-room [map x y w h]` - room with wall border and floor interior
- `make-corridor [map x1 y1 x2 y2]` - L-shaped corridor
- `generate-test-map [width height]` - sample map for testing

### Field of View (`yarf.core`)

Uses **recursive shadow casting** across 8 octants. Opaque tiles (walls, closed doors) are visible but block tiles behind them. Uses `transparent?` for opacity and **Chebyshev distance** for radius (square-shaped FOV).

- `compute-fov [registry map ox oy]` or `[registry map ox oy radius]` - returns `#{[x y] ...}` set of visible coordinates from origin; nil radius = unlimited (bounded by map size)
- `compute-entity-fov [registry map entity]` - uses entity's `:pos` and `:view-radius` (nil = unlimited)

### Save/Load (`yarf.core`)

Saves game state as gzipped EDN. Since entities don't carry `:act` functions (behavior lives in the registry), save/load is straightforward — no stripping or restoring needed.

**Single-map save (v1):**
- `prepare-save-data [game-map save-state]` - adds `:version 1`, merges save-state (e.g. `:explored`, `:viewport`)
- `save-game [file-path game-map save-state]` - writes gzipped EDN to file

**World save (v2):**
- `prepare-world-save-data [world save-state]` - adds `:version 2`, merges save-state
- `save-world [file-path world save-state]` - writes gzipped EDN to file

**Common:**
- `restore-save-data [save-data]` - validates version (1 or 2); throws on unsupported version
- `load-game [file-path]` - reads gzipped EDN, validates version, returns raw data map. Caller checks `:version` to determine format.

**Save v1 format (single-map):**
```clojure
{:version 1
 :game-map <game-map>
 :explored #{[x y] ...}    ;; game-specific
 :viewport {...}}           ;; game-specific
```

**Save v2 format (world):**
```clojure
{:version 2
 :world {:maps {:level-1 <game-map> ...}
         :current-map-id :level-1
         :transitions {[:level-1 22 7] {:target-map :level-2 :target-pos [5 18]} ...}}
 :explored {:level-1 #{[x y] ...} :level-2 #{...}}  ;; per-map explored
 :viewport {...}}
```

### World Structure (`yarf.core`)

An optional layer on top of single maps. Holds multiple named maps, transitions between them, and tracks the current active map. All existing single-map functions are unchanged — the world just wraps them.

```clojure
{:maps {:level-1 <game-map> :level-2 <game-map>}
 :current-map-id :level-1
 :transitions {[:level-1 22 7] {:target-map :level-2 :target-pos [5 18]}
               [:level-2 5 18] {:target-map :level-1 :target-pos [22 7]}}}
```

**World CRUD:**
- `create-world [maps current-map-id]` - creates world with empty transitions
- `current-map-id [world]` - returns `:current-map-id`
- `current-map [world]` - returns the active game-map
- `set-current-map [world game-map]` - replaces the current map
- `get-world-map [world map-id]` - returns a specific map

**Transitions:**
- `add-transition [world from-map x y target-map target-pos]` - one-way transition
- `add-transition-pair [world map-a [xa ya] map-b [xb yb]]` - bidirectional pair
- `get-transition [world map-id x y]` - look up transition at position
- `transition-at-entity [world entity]` - look up transition at entity's pos on current map
- `apply-transition [world entity transition]` - move entity between maps: removes from source, adds to target at target-pos, switches `current-map-id`

**Design notes:**
- Entities on non-current maps are frozen (never passed to `process-actors`)
- Act function signature unchanged: still `(entity game-map ctx)` → `{:map updated-map}`
- Transition triggering uses existing pass-through action pattern (e.g. `:descend`, `:ascend`)
- The game loop handles transition actions by checking tile type + looking up transition + calling `apply-transition`

### Display (`yarf.display`)

**Display protocol:**
- `get-input` - get input (blocking)
- `render-tile [this x y tile registry]` / `render-entity [this x y entity]`
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

Two-level dungeon demo demonstrating the framework. Run with `lein run`.

- `hjkl` to move, `yubn` for diagonals, `q` or ESC to quit
- `x` to enter look mode: move cursor, Enter to inspect, Escape to cancel
- `>` to descend stairs, `<` to ascend stairs
- `Shift-S` to save game (writes `yarf-save.dat` in working directory)
- On startup, prompts to load if a save file exists (handles both v1 and v2 saves)
- Two dungeon levels connected by stairs (level-1 has `>` at [22 7], level-2 has `<` at [8 18])
- Player starts on level-1; goblins wander on each level independently
- Bump-attack: walking into a goblin kills it instantly (via `demo-on-bump` callback on `:on-bump` in ctx)
- Type registry with tile/entity names, descriptions, and `:act` functions (including stair tile descriptions)
- Viewport follows player
- Terminal cursor tracks the player; in look mode it tracks the examined square
- Swing screen sized to fit viewport + message bar
- Invalid inputs (unknown keys, blocked moves) are retried immediately
- FOV/fog of war: visible tiles in color, explored tiles in blue, unexplored black; entities hidden outside FOV
- Explored state is a per-map map (`{:level-1 #{...} :level-2 #{...}}`) threaded through the game loop via ctx. The `game-loop` updates the current map's explored set each turn; look-mode callbacks read it from ctx.
- Uses `:pass-through-actions #{:save :descend :ascend}` in ctx so `player-act` returns these actions for the game loop to handle.
- Message bar shows current level prefix: `[level-1] message`
- Save uses world format (v2); load handles v1 backward compat by wrapping in a single-map world.

## Style

- **No trivial getter functions.** Entities and tiles are plain maps — access fields directly with keywords (e.g. `(:view-radius entity)`) rather than writing wrapper functions that just retrieve a single field. Only create accessor functions when they provide real value (default values, computed results, or polymorphic behavior).
- **Prefer refactoring signatures over inserting workaround state.** When a function needs access to data it doesn't currently receive, refactor the call chain to pass it through (e.g. add a ctx parameter) rather than introducing shared mutable state (atoms, globals) or closures that capture ambient dependencies. The ctx refactor was a large retroactive fix for exactly this — design function signatures to be extensible from the start.

## TODO

- Fix green character artifacts when Swing window is resized larger than viewport. `render-game` only writes within viewport bounds; lanterna/Swing repeats buffer content to fill extra pixel area. Need a proper fix (e.g. clearing the full terminal buffer, or handling resize events).
- Fix FOV shadow casting artifacts near walls and in corridors — some tiles that should be visible are left unseen. Likely an issue in `compute-fov` octant scanning (e.g. wall-adjacent tiles missed at octant boundaries).
- Extend bump actions beyond attack: open closed doors on bump, push objects, etc. The `:on-bump` callback can inspect the bumped entity/tile and choose the appropriate action.
- Consider later: move game-map into the context as well, simplifying the act signature to `(entity, ctx)` where ctx contains both the map and metadata.
- Consider later: have entities and tiles hold a direct reference to their type at runtime, to avoid registry lookups on every access (deferred to see if it's needed).
- Consider later: whether tiles in the map vector can be bare type keywords (e.g. `:floor` instead of `{:type :floor}`), saving even more space. Would need tiles with instance overrides to remain maps.
- Add save file migration: when save version changes, implement migration from older versions in `restore-save-data` (v1 -> v2 is handled by demo's `load-saved-game`, but core could offer a general migration hook).
- Support more transition types beyond stairs: portals, zone boundaries, etc. The transition structure is generic (position-to-position mapping); game loops can define their own triggering logic.

## Development Notes

- Clojure version: 1.10.3
- License: EPL-2.0 OR GPL-2.0-or-later
- Uses TDD: write failing tests first, then implement
- **Commits:** Do not add AI/co-authored-by lines to commit messages
