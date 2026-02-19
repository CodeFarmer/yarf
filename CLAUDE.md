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

- `create-entity [type char color x y]` or `[type char color x y props]` — auto-assigns an incrementing integer `:id`. Callers can supply `:id` in properties to override (e.g. loaded saves preserve their original IDs).
- `entity-type`, `entity-char`, `entity-color` - accessors
- `entity-pos` - returns `[x y]` position vector
- `move-entity [entity x y]` / `move-entity-by [entity dx dy]` - low-level movement (no bounds checking)

**Map entity management:**
- `add-entity` / `remove-entity` - add/remove from map. `remove-entity` matches by `:id` when available, falling back to structural equality for backward compatibility.
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
- `player-act [entity game-map ctx]` - named player act function; reads `:input-fn`, `:key-map`, `:registry`, `:on-look-move`, `:look-bounds-fn`, `:on-bump`, `:on-bump-tile`, `:on-ranged-attack` from ctx

**Context (ctx):**
A plain map passed to all act functions. Game-specific keys can be added freely. Standard keys:
- `:registry` - type registry (required — used for act dispatch, look-at, etc.)
- `:input-fn` - blocking input function `(fn [] key)` (required for player)
- `:key-map` - maps input keys to actions (required for player)
- `:on-look-move` - `(fn [ctx game-map cx cy look-info])` callback during look mode
- `:look-bounds-fn` - `(fn [ctx game-map entity] [min-x min-y max-x max-y])` computes bounds at look-mode entry
- `:on-bump` - `(fn [mover game-map bumped-entity ctx])` callback when player bumps a blocking entity; returns action-result. Without `:on-bump`, bumps retry like walls.
- `:on-bump-tile` - `(fn [mover game-map [x y] ctx])` callback when player bumps an in-bounds tile; returns action-result. Called for non-walkable tiles during movement and for ANY in-bounds tile during bump-without-move. If result has `:retry`, retries input. Without `:on-bump-tile`, bumps retry. Entity bumps (`:on-bump`) take priority over tile bumps.
- `:on-ranged-attack` - `(fn [attacker game-map [tx ty] ctx])` callback when player confirms a ranged attack target; returns action-result. Without `:on-ranged-attack`, ranged attack falls back to look-mode message. If result has `:retry`, retries input.
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
- `bump-deltas` - maps bump actions to `[dx dy]` vectors (e.g. `:bump-up` -> `[0 -1]`). Bump actions interact with a direction without moving.
- `execute-action [registry action entity map]` - executes movement actions via `direction-deltas`; returns action-result
- Custom key maps: `{\w :move-up \s :move-down ...}`

**Movement and terrain:**
- `try-move [registry map entity dx dy]` - safe movement with bounds, walkability, and entity-blocking checks; returns action-result
- `try-bump [registry map entity dx dy]` - checks position at (dx, dy) from entity without moving. Returns `:bumped-entity` for blocking entities, `:bumped-pos` for ANY in-bounds tile (including walkable), or plain retry for out-of-bounds. Never moves the entity.
- Use `try-move` for all map-aware movement (players and NPCs)
- Entities cannot move off map edges or into unwalkable tiles
- Failed moves into non-walkable in-bounds tiles return `{:map game-map :no-time true :retry true :bumped-pos [x y]}` — `:bumped-pos` tells callers which tile was bumped
- Failed moves out of bounds return `{:map game-map :no-time true :retry true}` (no `:bumped-pos`)
- Moving into a tile with a blocking entity returns `{:map game-map :no-time true :retry true :bumped-entity entity}` — callers that ignore `:bumped-entity` get wall-like behavior (backward compatible)
- `blocks-movement? [registry entity]` - checks if entity blocks movement (instance, then type registry, defaults to false). Follows the `contains?` pattern (instance overrides type, `false` is a valid value).
- Entity abilities affect terrain interaction:
  - `:can-swim true` - entity can traverse water tiles

**Input retry behavior:**
- `player-act` loops until an action that affects the world is performed
- Failed moves and unknown keys are retried immediately without consuming time
- Valid actions (successful moves, quit) exit the loop
- Bumping a blocking entity: if `:on-bump` is in ctx, calls it and returns its result; otherwise retries like a wall
- Bumping a non-walkable tile: if `:on-bump-tile` is in ctx, calls it; if its result has `:retry`, retries input; otherwise returns the result. Without `:on-bump-tile`, retries like before.
- Bump-without-move (`:bump-*` actions): uses `try-bump` to check the target direction without moving. Dispatches to `:on-bump` / `:on-bump-tile` callbacks just like movement bumps. Key difference: `try-bump` returns `:bumped-pos` for ALL in-bounds tiles (including walkable ones like open doors), enabling interactions that normal movement wouldn't trigger (e.g. closing an open door). Without callbacks, bump actions retry.
- `:look`, `:ranged-attack`, and `:quit` are handled in `player-act`, not in `execute-action`
- `:ranged-attack` enters look-mode for targeting; on Enter calls `:on-ranged-attack` callback with target position; on Escape retries input
- Without `:registry` in ctx, pressing look or ranged-attack key is treated as unknown input (retried)

**Look mode (`yarf.core`):**
- `look-mode [ctx game-map start-x start-y]` - self-contained cursor movement loop
- `look-mode [ctx game-map start-x start-y bounds]` - with optional bounds
- Reads `:registry`, `:input-fn`, `:key-map`, `:on-look-move` from ctx
- Cursor starts at `(start-x, start-y)`, moves with directional keys (same key-map as player)
- `:on-look-move` callback: `(fn [ctx game-map cx cy look-info])` - called at initial position and each cursor move
- `look-info` is the result of `(look-at registry game-map cx cy)`
- `bounds`: optional `[min-x min-y max-x max-y]` to constrain cursor movement (intersected with map bounds); nil = map bounds only
- Enter: returns `{:map game-map :no-time true :message description :target-pos [cx cy] :look-info info}` (falls back to "You see {name}.")
- Escape: returns `{:map game-map :no-time true}` (no message, no `:target-pos`, no `:look-info`)
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
- `:effect` - an effect map to play (propagated by `process-actors`, game loop calls `play-effect`)
- `:bumped-entity` - the blocking entity at the destination (set by `try-move` when movement is blocked by an entity, not by terrain)
- `:bumped-pos` - `[x y]` of the non-walkable tile bumped (set by `try-move` for in-bounds terrain blocks; not set for out-of-bounds or entity blocks)
- `:target-pos` - `[x y]` of the position selected in look-mode (set on Enter, absent on Escape)
- `:look-info` - `{:name :description :category :target}` for the selected position (set on Enter, absent on Escape)

### Dice Notation (`yarf.core`)

Parse and roll dice using standard notation (e.g. `"2d6+3"`).

**Notation format:**
- `NdS` — N dice with S sides (e.g. `"2d6"`)
- `NdS+M` / `NdS-M` — with modifier (e.g. `"3d8+2"`, `"1d6-1"`)
- `dS` — shorthand for 1dS (e.g. `"d20"`)
- `M` — constant (e.g. `"5"`, `"-3"`, `"+7"`)

**Functions:**
- `parse-dice [notation]` — parses string into `{:count N :sides S :modifier M}`. Throws on invalid format.
- `roll [dice]` — returns integer total. `dice` can be a string or a parsed map.
- `roll-detail [dice]` — returns `{:rolls [3 5] :modifier 2 :total 10}`. Same input flexibility.

**Data structure:**
```clojure
{:count 2 :sides 6 :modifier 3}  ;; 2d6+3
{:count 1 :sides 20 :modifier 0} ;; d20
{:count 0 :sides 0 :modifier 5}  ;; 5 (constant)
```

**Notes:**
- Uses `rand-int` (not seeded); seeded RNG is a separate TODO
- Constants: `(roll "5")` always returns 5; `(roll-detail "5")` returns `{:rolls [] :modifier 5 :total 5}`
- Zero dice: `(roll "0d6")` returns 0

### Spatial Utilities (`yarf.core`)

Distance, line drawing, line-of-sight, entity spatial queries, and pathfinding. All position arguments are `[x y]` vectors matching `entity-pos` return type.

**Distance functions:**
- `chebyshev-distance [pos1 pos2]` — max(|dx|, |dy|). Matches 8-directional movement/FOV.
- `manhattan-distance [pos1 pos2]` — |dx| + |dy|
- `euclidean-distance [pos1 pos2]` — sqrt(dx²+dy²), returns double

**Line drawing:**
- `line [from to]` — Bresenham's line algorithm. Returns vector of `[x y]` points, inclusive of both endpoints. Useful for projectile paths, blast patterns, LOS checks.

**Line of sight:**
- `line-of-sight? [registry game-map from to]` — true if all intermediate tiles on the line are transparent. Endpoints excluded from checks (you can see FROM a wall and TO a wall). Same position or adjacent = always true.

**Entity spatial queries:**
- `entities-in-radius [game-map pos radius]` — entities within Chebyshev distance. No registry needed.
- `entities-in-radius [game-map pos radius distance-fn]` — with custom distance function
- `nearest-entity [game-map pos]` — nearest entity by Chebyshev distance, or nil
- `nearest-entity [game-map pos pred]` — nearest entity matching predicate, or nil

**Pathfinding:**
- `find-path [registry game-map mover from to]` — A* pathfinding with 8-directional movement
- `find-path [registry game-map mover from to opts]` — with options: `{:max-distance N}`

**Pathfinding details:**
- Heuristic: Chebyshev distance. Movement cost: 1 for all directions.
- Uses `walkable?` for terrain — respects mover abilities (e.g. `:can-swim`)
- Does NOT check blocking entities (terrain-only paths)
- Returns vector of `[x y]` start-to-goal inclusive, or nil if no path
- Both start and goal must be walkable and in-bounds; otherwise nil
- `:max-distance` limits g-score depth (nil = no limit)

### Effects (`yarf.core` + `yarf.display`)

An extension point for ASCII special effects (hit flashes, explosions, projectiles). Core provides low-level data constructors; display provides the playback engine. Game authors build their own effects by composing these helpers with existing spatial utilities (`line`, `chebyshev-distance`, etc.).

**Data model:**
```clojure
;; Cell: single character overlay at a world position
{:pos [x y] :char \* :color :red}

;; Frame: cells displayed simultaneously
[{:pos [3 5] :char \* :color :yellow} {:pos [4 5] :char \* :color :red}]

;; Effect: frames played in sequence
{:frames [[...] [...]] :frame-ms 80}  ;; :frame-ms optional, default 50ms
```

**Core helpers (`yarf.core`):**
- `make-effect-cell [pos ch color]` — creates `{:pos pos :char ch :color color}`
- `make-effect-frame [cells]` — wraps cells in a vector
- `make-effect [frames]` / `[frames frame-ms]` — creates effect map
- `concat-effects [& effects]` — concatenates `:frames` from multiple effects

**Playback (`yarf.display`):**
- `play-effect [screen effect viewport]` / `[screen effect viewport opts]`
- For each frame: optionally calls `render-base-fn`, overlays cells via `render-char`, refreshes, sleeps
- `opts`: `{:frame-ms N, :render-base-fn (fn [screen] ...)}`
- Uses existing low-level `render-char` and `refresh`; no Display protocol extension

**Action-result integration:**
- Act functions (e.g. `on-bump`) can return `:effect` in their action-result
- `process-actors` propagates `:effect` alongside `:quit`, `:message`, `:action`
- Game loop checks `:effect` in result and calls `play-effect`

### Basics (`yarf.basics`)

Ready-to-use building blocks: named tiles, door mechanics, d20-style combat, and basic AI. Games can use these directly or as reference implementations.

**Tiles:**
- `basic-tile-descriptions` — map of names/descriptions for all 7 standard tile types
- `register-basic-tile-types [registry]` — calls `core/register-default-tile-types` then merges names and descriptions. Replaces the manual `update-in` pattern.

**Doors:**
- `open-door [game-map x y]` — swaps `:door-closed` to `:door-open`, preserving instance properties
- `close-door [game-map x y]` — swaps `:door-open` to `:door-closed`, preserving instance properties
- `door-on-bump-tile [mover game-map [x y] ctx]` — `:on-bump-tile` callback: opens closed doors, closes open doors, returns `{:retry true}` for non-doors

**Combat:**

Melee type properties: `:max-hp`, `:attack` (dice string), `:defense` (number), `:damage` (dice string), `:armor` (number). Instance property: `:hp` (set at entity creation).

Ranged type properties: `:ranged-attack` (dice string), `:ranged-damage` (dice string), `:range` (number, default 6).

Resolution (same for melee and ranged): `roll(attack-dice) >= defense` → hit → `max(0, roll(damage-dice) - armor)` → HP ≤ 0 → entity removed from map.

- `alive? [entity]` — `(:hp entity) > 0`
- `apply-damage [game-map entity damage]` — reduces HP, removes at 0. Returns `{:map updated-map :removed true/nil}`
- `melee-attack [attacker game-map defender ctx]` — full melee attack resolution. Returns action-result with `:message`. Uses `core/get-property` for instance-then-type lookup. Also returns `:hit true` on hit (stripped by `combat-on-bump`).
- `combat-on-bump [mover game-map bumped ctx]` — `:on-bump` callback. Calls `melee-attack`, adds `:effect` (hit flash or miss dash).
- `ranged-attack [attacker game-map defender ctx]` — ranged attack resolution using `:ranged-attack` and `:ranged-damage` properties. Same return pattern as `melee-attack` (`:hit true` on hit).
- `projectile-effect [from to]` / `[from to ch color]` — multi-frame projectile along Bresenham line (default `*` cyan, 30ms). Skips start position.
- `ranged-on-target [attacker game-map [tx ty] ctx]` — `:on-ranged-attack` callback. Checks: self-target (retry), LOS ("You can't see there."), range ("Out of range."), no entity ("Nothing to shoot at."). On valid target: resolves `ranged-attack`, composes projectile + hit/miss effect.
- `hit-effect [pos]` — 2-frame red/yellow flash
- `miss-effect [pos]` — 1-frame white dash

Messages are player-aware:
- `"You hit the goblin for 4 damage."` / `"The goblin hits you for 3 damage."`
- `"You miss the goblin."` / `"The goblin misses you."`
- Kill: `"...killing it!"` / `"...killing you!"`

Tests use `with-redefs` on `core/roll` for determinism.

**AI:**
- `wander [entity game-map ctx]` — random walk via `try-move`. Stands still if blocked.
- `player-target-fn [entity game-map ctx]` — returns `(core/get-player game-map)`. Common target function.
- `make-chase-act [target-fn]` — returns act function: if adjacent to target → `melee-attack`; if path exists (max-distance 20) → follow `find-path`; else → `wander`. `target-fn` signature: `(fn [entity game-map ctx] → entity or nil)`.

`make-chase-act` is a closure factory (legitimate parameterization — different entity types can chase different targets). `find-path` is terrain-only; AI wastes a turn if another entity blocks the path.

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
- `prepare-save-data [game-map save-state]` - adds `:version 1` and `:next-entity-id`, merges save-state (e.g. `:explored`, `:viewport`)
- `save-game [file-path game-map save-state]` - writes gzipped EDN to file

**World save (v2):**
- `prepare-world-save-data [world save-state]` - adds `:version 2` and `:next-entity-id`, merges save-state
- `save-world [file-path world save-state]` - writes gzipped EDN to file

**Common:**
- `restore-save-data [save-data]` - validates version (1 or 2), restores entity id counter; throws on unsupported version. If `:next-entity-id` is present, resets the counter to that value. If absent (old saves), scans entities for the highest `:id` and uses that.
- `load-game [file-path]` - reads gzipped EDN, validates version, returns raw data map. Caller checks `:version` to determine format.

**Save v1 format (single-map):**
```clojure
{:version 1
 :game-map <game-map>
 :next-entity-id 42          ;; entity id counter
 :explored #{[x y] ...}    ;; game-specific
 :viewport {...}}           ;; game-specific
```

**Save v2 format (world):**
```clojure
{:version 2
 :world {:maps {:level-1 <game-map> ...}
         :current-map-id :level-1
         :transitions {[:level-1 22 7] {:target-map :level-2 :target-pos [5 18]} ...}}
 :next-entity-id 42          ;; entity id counter
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

Two-level dungeon demo demonstrating the framework. Uses `yarf.basics` for combat, AI, and doors. Run with `lein run`.

- `hjkl` to move, `yubn` for diagonals, `q` or ESC to quit
- `Ctrl+hjkl` / `Ctrl+yubn` to bump without moving (interact with adjacent tile/entity)
- `x` to enter look mode: move cursor, Enter to inspect, Escape to cancel
- `f` to fire ranged attack: move cursor to target, Enter to fire, Escape to cancel
- `>` to descend stairs, `<` to ascend stairs
- `Shift-S` to save game (writes `yarf-save.dat` in working directory)
- On startup, prompts to load if a save file exists (handles both v1 and v2 saves)
- Two dungeon levels connected by stairs (level-1 has `>` at [22 7], level-2 has `<` at [8 18])
- Player starts on level-1; goblins chase and attack the player (via `basics/make-chase-act`)
- d20-style combat: player (20 HP, 1d20+2 melee attack, 12 defense, 1d6+1 melee damage, 1d20+1 ranged attack, 1d8 ranged damage, range 8) vs goblins (5 HP, 1d20 attack, 8 defense, 1d4 damage). Hit/miss effects on bump. Uses `basics/combat-on-bump` as `:on-bump` callback, `basics/ranged-on-target` as `:on-ranged-attack` callback.
- Doors: level-1 has a closed door at [9 5]; walking into it opens it via `basics/door-on-bump-tile` as `:on-bump-tile` callback. Ctrl+direction toward an open door closes it (bump-without-move triggers `on-bump-tile` on walkable tiles).
- Player death: game loop checks if player was killed after `process-actors`, ends game with death message
- Type registry uses `basics/register-basic-tile-types` for named tiles with descriptions
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
- Extend bump actions: push objects, etc. The `:on-bump` and `:on-bump-tile` callbacks can inspect the bumped entity/tile and choose the appropriate action. Door open/close is provided by `basics/door-on-bump-tile`. Bump-without-move (Ctrl+direction) enables interactions with walkable tiles.
- Consider later: move game-map into the context as well, simplifying the act signature to `(entity, ctx)` where ctx contains both the map and metadata.
- Consider later: have entities and tiles hold a direct reference to their type at runtime, to avoid registry lookups on every access (deferred to see if it's needed).
- Consider later: whether tiles in the map vector can be bare type keywords (e.g. `:floor` instead of `{:type :floor}`), saving even more space. Would need tiles with instance overrides to remain maps.
- Add save file migration: when save version changes, implement migration from older versions in `restore-save-data` (v1 -> v2 is handled by demo's `load-saved-game`, but core could offer a general migration hook).
- Support more transition types beyond stairs: portals, zone boundaries, etc. The transition structure is generic (position-to-position mapping); game loops can define their own triggering logic.
- Seeded random number generation: deterministic RNG threaded through ctx for reproducible map generation, combat, AI, etc. (would also make dice rolls deterministic via an optional rng parameter)

## Development Notes

- Clojure version: 1.10.3
- License: EPL-2.0 OR GPL-2.0-or-later
- Uses TDD: write failing tests first, then implement
- **Commits:** Do not add AI/co-authored-by lines to commit messages
