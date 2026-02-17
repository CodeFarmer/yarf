(ns yarf.core
  (:require [clojure.edn :as edn])
  (:import [java.io PushbackReader InputStreamReader OutputStreamWriter
            BufferedReader BufferedWriter FileInputStream FileOutputStream]
           [java.util.zip GZIPInputStream GZIPOutputStream]))

;; Type registry
;; Types define shared immutable properties for entities and tiles (description, lore, etc.)
;; Types can have a :parent for single inheritance.

(defn create-type-registry
  "Creates an empty type registry for storing entity and tile type definitions."
  []
  {:entity {}
   :tile {}})

(defn define-tile-type
  "Defines a tile type with the given properties.
   Properties typically include :description, :lore, and game-specific attributes.
   Use :parent to inherit from another tile type."
  [registry type-key properties]
  (assoc-in registry [:tile type-key] properties))

(defn define-entity-type
  "Defines an entity type with the given properties.
   Properties typically include :description, :lore, and game-specific attributes.
   Use :parent to inherit from another entity type."
  [registry type-key properties]
  (assoc-in registry [:entity type-key] properties))

(defn get-type-property
  "Gets a property from a type definition, following the inheritance chain.
   category is :entity or :tile, type-key is the type identifier."
  [registry category type-key property]
  (loop [current-type type-key]
    (when current-type
      (let [type-def (get-in registry [category current-type])
            value (get type-def property)]
        (if (and (nil? value) (contains? type-def :parent))
          (recur (:parent type-def))
          value)))))

(defn get-instance-type-property
  "Gets a type property from an entity or tile instance.
   Looks up the instance's :type in the registry and returns the property.
   Follows the inheritance chain if the type has a :parent."
  [registry instance property]
  (let [type-key (:type instance)
        ;; Try entity first, then tile
        entity-prop (get-type-property registry :entity type-key property)
        tile-prop (get-type-property registry :tile type-key property)]
    (or entity-prop tile-prop)))

(defn- determine-category
  "Determines if an instance is an entity or tile based on registry contents."
  [registry instance]
  (let [type-key (:type instance)]
    (cond
      (get-in registry [:entity type-key]) :entity
      (get-in registry [:tile type-key]) :tile
      ;; Default guess based on presence of position (entities have :pos)
      (contains? instance :pos) :entity
      :else :tile)))

(defn get-property
  "Gets a property from an instance, falling back to its type definition.
   Checks the instance first, then the type (following inheritance chain)."
  [registry instance property]
  (let [instance-value (get instance property)]
    (if (some? instance-value)
      instance-value
      (let [category (determine-category registry instance)]
        (get-type-property registry category (:type instance) property)))))

(defn get-name
  "Gets the name of an instance. Checks instance :name, then type :name,
   then falls back to the type key as a string."
  [registry instance]
  (or (get-property registry instance :name)
      (name (:type instance))))

(defn get-description
  "Gets the description of an instance. Checks instance, then type."
  [registry instance]
  (get-property registry instance :description))

;; Tile properties and constructors

(defn make-tile
  "Creates a tile with the given type and optional instance-level properties.
   Display/behavior properties (:char, :color, :walkable, :transparent) are normally
   defined in the type registry; use instance properties only for overrides."
  ([tile-type] {:type tile-type})
  ([tile-type properties] (merge {:type tile-type} properties)))

(def default-tile-types
  "Default tile type definitions with display and behavior properties."
  {:floor {:char \. :color :white :walkable true :transparent true}
   :wall {:char \# :color :white :walkable false :transparent false}
   :door-closed {:char \+ :color :yellow :walkable false :transparent false}
   :door-open {:char \/ :color :yellow :walkable true :transparent true}
   :water {:char \~ :color :blue :walkable false :transparent true}
   :stairs-down {:char \> :color :white :walkable true :transparent true}
   :stairs-up {:char \< :color :white :walkable true :transparent true}})

(defn register-default-tile-types
  "Registers the default tile types (:floor, :wall, :door-closed, :door-open, :water)
   into the given registry."
  [registry]
  (reduce-kv (fn [reg k v] (define-tile-type reg k v))
             registry default-tile-types))

(defn tile-char
  "Returns the display character for a tile. Checks instance, then type registry."
  [registry tile]
  (or (:char tile) (get-type-property registry :tile (:type tile) :char) \?))

(defn tile-color
  "Returns the display color for a tile. Checks instance, then type registry."
  [registry tile]
  (or (:color tile) (get-type-property registry :tile (:type tile) :color) :white))

(defn walkable?
  "Returns true if the mover can walk through the tile.
   Checks instance :walkable first, then type registry, then mover's special abilities."
  [registry mover tile]
  (let [base-walkable (if (contains? tile :walkable)
                        (:walkable tile)
                        (get-type-property registry :tile (:type tile) :walkable))]
    (if base-walkable
      true
      (cond
        (and (:can-swim mover) (= :water (:type tile))) true
        :else false))))

(defn transparent?
  "Returns true if the tile can be seen through. Checks instance, then type registry."
  [registry tile]
  (if (contains? tile :transparent)
    (:transparent tile)
    (or (get-type-property registry :tile (:type tile) :transparent) false)))

(defn blocks-movement?
  "Returns true if the entity blocks other entities from moving into its tile.
   Checks instance :blocks-movement first, then type registry. Defaults to false."
  [registry entity]
  (if (contains? entity :blocks-movement)
    (:blocks-movement entity)
    (or (get-type-property registry :entity (:type entity) :blocks-movement) false)))

(defn create-tile-map
  "Creates a tile map with the given width and height, filled with default tiles."
  [width height]
  {:width width
   :height height
   :tiles (vec (repeat (* width height) {:type :floor}))
   :entities []})

(defn map-width
  "Returns the width of the tile map."
  [tile-map]
  (:width tile-map))

(defn map-height
  "Returns the height of the tile map."
  [tile-map]
  (:height tile-map))

(defn in-bounds?
  "Returns true if the coordinates are within the map bounds."
  [tile-map x y]
  (and (>= x 0)
       (>= y 0)
       (< x (:width tile-map))
       (< y (:height tile-map))))

(defn- coords->index
  "Converts x,y coordinates to a flat array index."
  [tile-map x y]
  (+ x (* y (:width tile-map))))

(defn get-tile
  "Returns the tile at the given coordinates."
  [tile-map x y]
  (get (:tiles tile-map) (coords->index tile-map x y)))

(defn set-tile
  "Returns a new tile map with the tile at coordinates set to the given value."
  [tile-map x y tile]
  (assoc-in tile-map [:tiles (coords->index tile-map x y)] tile))

;; Map generation

(defn fill-rect
  "Fills a rectangular region with the specified tile.
   x, y are top-left corner; w, h are width and height."
  [tile-map x y w h tile]
  (reduce (fn [m [cx cy]]
            (set-tile m cx cy tile))
          tile-map
          (for [cy (range y (+ y h))
                cx (range x (+ x w))]
            [cx cy])))

(defn make-room
  "Creates a room with floor interior and wall border.
   x, y are top-left corner; w, h are outer dimensions."
  [tile-map x y w h]
  (-> tile-map
      (fill-rect x y w h {:type :wall})
      (fill-rect (inc x) (inc y) (- w 2) (- h 2) {:type :floor})))

(defn make-corridor
  "Creates an L-shaped corridor from (x1,y1) to (x2,y2).
   Goes horizontal first, then vertical."
  [tile-map x1 y1 x2 y2]
  (let [min-x (min x1 x2)
        max-x (max x1 x2)
        min-y (min y1 y2)
        max-y (max y1 y2)]
    (-> tile-map
        ;; horizontal segment at y1
        (fill-rect min-x y1 (inc (- max-x min-x)) 1 {:type :floor})
        ;; vertical segment at x2
        (fill-rect x2 min-y 1 (inc (- max-y min-y)) {:type :floor}))))

(defn generate-test-map
  "Generates a simple test map with a few rooms connected by corridors."
  [width height]
  (-> (create-tile-map width height)
      (fill-rect 0 0 width height {:type :wall})
      (make-room 2 2 8 6)
      (make-room 15 3 10 8)
      (make-room 5 15 12 10)
      (make-corridor 9 5 15 7)
      (make-corridor 10 14 10 20)))

;; Field of View (shadow casting)

(defn- scan-octant
  "Scans one octant for shadow casting FOV.
   transform-fn maps (row, col) to (dx, dy) relative to origin.
   Adds visible [x y] coords to the visible set atom."
  [registry game-map ox oy radius transform-fn visible]
  (let [max-dist (or radius (max (map-width game-map) (map-height game-map)))]
    (loop [row 1
           shadows []]
      (when (<= row max-dist)
        (let [done (volatile! false)
              any-in-bounds (volatile! false)
              new-shadows (volatile! shadows)]
          (loop [col 0]
            (when (and (not @done) (<= col row))
              (let [[dx dy] (transform-fn row col)
                    x (+ ox dx)
                    y (+ oy dy)]
                (if (not (in-bounds? game-map x y))
                  (recur (inc col))
                  (let [start-slope (/ (- col 0.5) row)
                        center-slope (/ (double col) row)
                        end-slope (/ (+ col 0.5) row)
                        in-shadow (some (fn [[s e]] (and (>= center-slope s) (<= center-slope e)))
                                        @new-shadows)
                        opaque (not (transparent? registry (get-tile game-map x y)))]
                    (vreset! any-in-bounds true)
                    (when-not in-shadow
                      (swap! visible conj [x y]))
                    (when (and opaque (not in-shadow))
                      (let [new-shadow [start-slope end-slope]
                            merged (reduce (fn [acc [ss se :as s]]
                                             (let [prev (peek acc)]
                                               (if (and prev (<= (first prev) ss) (>= (second prev) ss))
                                                 (conj (pop acc) [(first prev) (max (second prev) se)])
                                                 (conj acc s))))
                                           []
                                           (sort-by first (conj @new-shadows new-shadow)))]
                        (vreset! new-shadows merged)
                        (when (and (seq merged)
                                   (<= (first (first merged)) 0.0)
                                   (>= (second (last merged)) 1.0))
                          (vreset! done true))))
                    (recur (inc col)))))))
          (when (and (not @done) @any-in-bounds)
            (recur (inc row) @new-shadows)))))))

(def ^:private octant-transforms
  "Transform functions for 8 octants. Each maps (row, col) to (dx, dy)."
  [(fn [row col] [row (- col)])
   (fn [row col] [col (- row)])
   (fn [row col] [(- col) (- row)])
   (fn [row col] [(- row) (- col)])
   (fn [row col] [(- row) col])
   (fn [row col] [(- col) row])
   (fn [row col] [col row])
   (fn [row col] [row col])])

(defn compute-fov
  "Computes field of view from origin (ox, oy) using shadow casting.
   Optional radius limits visible distance (Chebyshev distance).
   Returns a set of visible [x y] coordinate pairs."
  ([registry game-map ox oy] (compute-fov registry game-map ox oy nil))
  ([registry game-map ox oy radius]
   (let [visible (atom #{[ox oy]})]
     (doseq [transform octant-transforms]
       (scan-octant registry game-map ox oy radius transform visible))
     @visible)))

(defn compute-entity-fov
  "Computes field of view for an entity using its position and view-radius.
   If entity has no view-radius, computes unlimited FOV."
  [registry game-map entity]
  (let [[x y] (:pos entity)
        r (:view-radius entity)]
    (if r
      (compute-fov registry game-map x y r)
      (compute-fov registry game-map x y))))

;; Entities

(defn create-entity
  "Creates an entity with type, display char, color, and position.
   Position is stored as :pos [x y]. Optional properties map can be provided."
  ([entity-type char color x y]
   (create-entity entity-type char color x y {}))
  ([entity-type char color x y properties]
   (merge {:type entity-type :char char :color color :pos [x y]} properties)))

(defn entity-type
  "Returns the type of an entity."
  [entity]
  (:type entity))

(defn entity-char
  "Returns the display character for an entity."
  [entity]
  (:char entity \?))

(defn entity-color
  "Returns the display color for an entity."
  [entity]
  (:color entity :white))

(defn entity-pos
  "Returns the position of an entity as [x y]."
  [entity]
  (:pos entity))

(defn entity-delay
  "Returns the delay (ticks between actions) for an entity. Defaults to 10."
  [entity]
  (:delay entity 10))

(defn entity-next-action
  "Returns the next-action tick for an entity. Defaults to 0."
  [entity]
  (:next-action entity 0))

(defn move-entity
  "Moves an entity to a new position."
  [entity x y]
  (assoc entity :pos [x y]))

(defn move-entity-by
  "Moves an entity by a relative offset."
  [entity dx dy]
  (let [[x y] (entity-pos entity)]
    (move-entity entity (+ x dx) (+ y dy))))

;; Map entity management

(defn get-entities
  "Returns all entities in the map."
  [tile-map]
  (:entities tile-map []))

(defn add-entity
  "Adds an entity to the map."
  [tile-map entity]
  (update tile-map :entities conj entity))

(defn remove-entity
  "Removes an entity from the map."
  [tile-map entity]
  (update tile-map :entities #(vec (remove #{entity} %))))

(defn get-entities-at
  "Returns all entities at the specified position."
  [tile-map x y]
  (filter #(= [x y] (entity-pos %))
          (get-entities tile-map)))

(defn look-at
  "Returns information about what's at the given position.
   Returns a map with :name, :description, :category (:entity or :tile), and :target.
   Requires a type registry for name/description lookup."
  [registry tile-map x y]
  (let [entities (get-entities-at tile-map x y)
        tile (get-tile tile-map x y)]
    (if (seq entities)
      ;; Return info about topmost entity (last added)
      (let [entity (last entities)]
        {:name (get-name registry entity)
         :description (get-description registry entity)
         :category :entity
         :target entity})
      ;; Return info about tile
      {:name (get-name registry tile)
       :description (get-description registry tile)
       :category :tile
       :target tile})))

(defn update-entity
  "Updates an entity in the map by applying f to it."
  [tile-map entity f & args]
  (let [updated (apply f entity args)]
    (-> tile-map
        (remove-entity entity)
        (add-entity updated))))

;; Player

(defn create-player
  "Creates a player entity at the given position."
  [x y]
  (create-entity :player \@ :yellow x y))

(defn get-player
  "Returns the player entity from the map, or nil if not found."
  [tile-map]
  (first (filter #(= :player (entity-type %)) (get-entities tile-map))))

;; Entity actions

(defn- can-act?
  "Returns true if the entity's type has an :act function in the registry."
  [registry entity]
  (some? (get-type-property registry :entity (:type entity) :act)))

(defn- increment-next-action
  "Increments an entity's next-action by the given time, or its delay if not specified."
  ([entity] (increment-next-action entity (entity-delay entity)))
  ([entity time]
   (assoc entity :next-action (+ (entity-next-action entity) time))))

(defn- find-acted-entity
  "Finds the entity that acted by matching type."
  [entities original-entity]
  (first (filter #(= (:type %) (:type original-entity))
                 entities)))

(defn act-entity
  "Calls the entity's act function if it has one (looked up from registry in ctx).
   The act function receives (entity, game-map, ctx) and returns an action-result map
   {:map updated-map, optional :time-cost, :no-time, :retry, :quit, :message}.
   After acting, the entity's next-action is incremented by :time-cost
   if present, otherwise by its delay. If :no-time is set, no increment occurs."
  [tile-map entity ctx]
  (if-let [act-fn (get-type-property (:registry ctx) :entity (:type entity) :act)]
    (let [result (act-fn entity tile-map ctx)
          {:keys [map no-time time-cost]} result
          acted-entity (find-acted-entity (get-entities map) entity)
          updated-map (if (and acted-entity (not no-time))
                        (if time-cost
                          (update-entity map acted-entity increment-next-action time-cost)
                          (update-entity map acted-entity increment-next-action))
                        map)]
      (assoc result :map updated-map))
    {:map tile-map}))

(defn- get-next-actor
  "Returns the entity with the lowest next-action value that can act, or nil if none."
  [tile-map registry]
  (->> (get-entities tile-map)
       (filter #(can-act? registry %))
       (sort-by entity-next-action)
       first))

(defn process-next-actor
  "Processes only the entity with the lowest next-action value.
   Returns an action-result map."
  [tile-map ctx]
  (if-let [actor (get-next-actor tile-map (:registry ctx))]
    (act-entity tile-map actor ctx)
    {:map tile-map}))

(defn process-actors
  "Processes all entities that have act functions.
   Returns an action-result map with :map, and accumulated :quit/:message flags."
  [tile-map ctx]
  (let [registry (:registry ctx)]
    (reduce (fn [accum entity]
              (let [current-map (:map accum)]
                (if (can-act? registry entity)
                  ;; Re-fetch entity from map in case it was modified
                  (if-let [current (first (filter #(= entity %) (get-entities current-map)))]
                    (let [result (act-entity current-map current ctx)]
                      (cond-> {:map (:map result)}
                        (or (:quit accum) (:quit result))       (assoc :quit true)
                        (or (:message accum) (:message result)) (assoc :message (or (:message result) (:message accum)))
                        (or (:action accum) (:action result))   (assoc :action (or (:action result) (:action accum)))))
                    accum)
                  accum)))
            {:map tile-map}
            (get-entities tile-map))))

;; Player input handling

(def default-key-map
  "Default key bindings using vi-style hjklyubn for movement."
  {\h :move-left
   \j :move-down
   \k :move-up
   \l :move-right
   \y :move-up-left
   \u :move-up-right
   \b :move-down-left
   \n :move-down-right
   \x :look})

(defn try-move
  "Attempts to move entity by dx,dy. Only moves if new position is in bounds and walkable.
   Returns an action-result: {:map updated-map} on success,
   {:map game-map :no-time true :retry true} on failure."
  [registry game-map entity dx dy]
  (let [[x y] (entity-pos entity)
        new-x (+ x dx)
        new-y (+ y dy)
        in-map (in-bounds? game-map new-x new-y)
        can-walk (and in-map (walkable? registry entity (get-tile game-map new-x new-y)))]
    (if can-walk
      (let [blockers (filter #(blocks-movement? registry %)
                             (get-entities-at game-map new-x new-y))
            blocker (first blockers)]
        (if blocker
          {:map game-map :no-time true :retry true :bumped-entity blocker}
          {:map (update-entity game-map entity move-entity-by dx dy)}))
      {:map game-map :no-time true :retry true})))

(def direction-deltas
  "Maps movement actions to [dx dy] vectors."
  {:move-up [0 -1] :move-down [0 1] :move-left [-1 0] :move-right [1 0]
   :move-up-left [-1 -1] :move-up-right [1 -1]
   :move-down-left [-1 1] :move-down-right [1 1]})

(defn execute-action
  "Executes a world action (movement), returning an action-result map.
   Player-only actions (:look, :quit) are handled in player-act."
  [registry action entity game-map]
  (if-let [[dx dy] (direction-deltas action)]
    (try-move registry game-map entity dx dy)
    {:map game-map}))

(defn- in-look-bounds?
  "Returns true if (x, y) is within map bounds and optional look bounds."
  [game-map bounds x y]
  (and (in-bounds? game-map x y)
       (if bounds
         (let [[min-x min-y max-x max-y] bounds]
           (and (>= x min-x) (<= x max-x)
                (>= y min-y) (<= y max-y)))
         true)))

(defn look-mode
  "Enters look mode: cursor starts at (start-x, start-y), moves with directional keys.
   Reads from ctx: :registry, :input-fn, :key-map, :on-look-move.
   on-look-move: (fn [ctx game-map cx cy look-info]) called at initial position and each move
   bounds: optional [min-x min-y max-x max-y] to constrain cursor (nil = map bounds only)
   Returns action-result:
     Enter  -> {:map game-map :no-time true :message description}
     Escape -> {:map game-map :no-time true}"
  ([ctx game-map start-x start-y]
   (look-mode ctx game-map start-x start-y nil))
  ([ctx game-map start-x start-y bounds]
   (let [{:keys [registry input-fn key-map on-look-move]} ctx]
     (loop [cx start-x
            cy start-y]
       (let [look-info (look-at registry game-map cx cy)]
         (when on-look-move
           (on-look-move ctx game-map cx cy look-info))
         (let [input (input-fn)
               action (get key-map input)]
           (cond
             ;; Enter: return description
             (= input :enter)
             {:map game-map :no-time true
              :message (or (:description look-info)
                           (str "You see " (:name look-info) "."))}

             ;; Escape: cancel
             (= input :escape)
             {:map game-map :no-time true}

             ;; Movement: move cursor if in bounds
             (direction-deltas action)
             (let [[dx dy] (direction-deltas action)
                   nx (+ cx dx)
                   ny (+ cy dy)]
               (if (in-look-bounds? game-map bounds nx ny)
                 (recur nx ny)
                 (recur cx cy)))

             ;; Unknown key: ignore
             :else
             (recur cx cy))))))))

(defn player-act
  "Player act function. Reads :input-fn, :key-map, :registry, :look-bounds-fn,
   :pass-through-actions from ctx. Loops until an action that affects the world
   is performed. Failed moves and unknown keys are retried immediately.
   Returns an action-result map."
  [entity game-map ctx]
  (let [{:keys [input-fn key-map registry look-bounds-fn pass-through-actions]} ctx]
    (loop []
      (let [input (input-fn)
            action (get key-map input)]
        (cond
          ;; Quit
          (= action :quit)
          {:map game-map :quit true}

          ;; Look mode with registry
          (and (= action :look) registry)
          (let [[px py] (entity-pos entity)
                bounds (when look-bounds-fn
                         (look-bounds-fn ctx game-map entity))
                result (look-mode ctx game-map px py bounds)]
            (if (:message result)
              result
              (recur)))

          ;; Look without registry - retry
          (= action :look)
          (recur)

          ;; Pass-through actions (game-loop handles these)
          (and pass-through-actions (contains? pass-through-actions action))
          {:map game-map :no-time true :action action}

          ;; World action (movement etc.)
          action
          (let [result (execute-action registry action entity game-map)]
            (if-let [bumped (:bumped-entity result)]
              (if-let [on-bump (:on-bump ctx)]
                (on-bump entity game-map bumped ctx)
                (recur))
              (if (:retry result)
                (recur)
                result)))

          ;; Unknown key
          :else
          (recur))))))

;; Save/Load

(defn prepare-save-data
  "Prepares game state for saving. Adds version.
   save-state is a map of additional keys to include (e.g. :explored, :viewport)."
  [game-map save-state]
  (merge {:version 1 :game-map game-map} save-state))

(defn restore-save-data
  "Restores save data. Accepts version 1 or 2. Throws on unsupported version."
  [save-data]
  (when-not (#{1 2} (:version save-data))
    (throw (ex-info (str "Unsupported save version: " (:version save-data))
                    {:version (:version save-data)})))
  save-data)

(defn save-game
  "Saves game state to a gzipped EDN file."
  [file-path game-map save-state]
  (let [save-data (prepare-save-data game-map save-state)]
    (with-open [out (-> (FileOutputStream. ^String file-path)
                        (GZIPOutputStream.)
                        (OutputStreamWriter. "UTF-8")
                        (BufferedWriter.))]
      (.write out (pr-str save-data)))))

(defn load-game
  "Loads game state from a gzipped EDN file."
  [file-path]
  (let [save-data (with-open [in (-> (FileInputStream. ^String file-path)
                                     (GZIPInputStream.)
                                     (InputStreamReader. "UTF-8")
                                     (BufferedReader.)
                                     (PushbackReader.))]
                    (edn/read in))]
    (restore-save-data save-data)))

;; World structure (multi-map support)

(defn create-world
  "Creates a world with named maps and empty transitions.
   maps is a map of map-id -> game-map. current-map-id is the active map."
  [maps current-map-id]
  {:maps maps
   :current-map-id current-map-id
   :transitions {}})

(defn current-map-id
  "Returns the id of the current map in the world."
  [world]
  (:current-map-id world))

(defn current-map
  "Returns the active game-map from the world."
  [world]
  (get-in world [:maps (:current-map-id world)]))

(defn set-current-map
  "Replaces the current map in the world."
  [world game-map]
  (assoc-in world [:maps (:current-map-id world)] game-map))

(defn get-world-map
  "Returns a specific map from the world by id."
  [world map-id]
  (get-in world [:maps map-id]))

;; Transitions

(defn add-transition
  "Adds a one-way transition from (x, y) on from-map to target-pos on target-map."
  [world from-map x y target-map target-pos]
  (assoc-in world [:transitions [from-map x y]]
            {:target-map target-map :target-pos target-pos}))

(defn add-transition-pair
  "Adds bidirectional transitions between two positions on different maps."
  [world map-a [xa ya] map-b [xb yb]]
  (-> world
      (add-transition map-a xa ya map-b [xb yb])
      (add-transition map-b xb yb map-a [xa ya])))

(defn get-transition
  "Looks up a transition at the given position on the given map."
  [world map-id x y]
  (get-in world [:transitions [map-id x y]]))

(defn transition-at-entity
  "Looks up a transition at the entity's position on the current map."
  [world entity]
  (let [[x y] (entity-pos entity)]
    (get-transition world (:current-map-id world) x y)))

(defn apply-transition
  "Moves an entity from the current map to the target map via a transition.
   Removes entity from current map, adds to target at target-pos, switches current-map-id."
  [world entity transition]
  (let [{:keys [target-map target-pos]} transition
        [tx ty] target-pos
        current-id (:current-map-id world)
        source-map (get-in world [:maps current-id])
        dest-map (get-in world [:maps target-map])
        updated-entity (move-entity entity tx ty)]
    (-> world
        (assoc-in [:maps current-id] (remove-entity source-map entity))
        (assoc-in [:maps target-map] (add-entity dest-map updated-entity))
        (assoc :current-map-id target-map))))

;; World Save/Load

(defn prepare-world-save-data
  "Prepares world state for saving as version 2.
   save-state is a map of additional keys to include (e.g. :explored, :viewport)."
  [world save-state]
  (merge {:version 2 :world world} save-state))

(defn save-world
  "Saves world state to a gzipped EDN file."
  [file-path world save-state]
  (let [save-data (prepare-world-save-data world save-state)]
    (with-open [out (-> (FileOutputStream. ^String file-path)
                        (GZIPOutputStream.)
                        (OutputStreamWriter. "UTF-8")
                        (BufferedWriter.))]
      (.write out (pr-str save-data)))))
