(ns yarf.core)

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
  "Creates a tile with the given type, display character, color, and properties."
  [tile-type char color properties]
  (merge {:type tile-type :char char :color color} properties))

(def floor-tile
  (make-tile :floor \. :white {:walkable true :transparent true}))

(def wall-tile
  (make-tile :wall \# :white {:walkable false :transparent false}))

(def door-closed-tile
  (make-tile :door-closed \+ :yellow {:walkable false :transparent false}))

(def door-open-tile
  (make-tile :door-open \/ :yellow {:walkable true :transparent true}))

(def water-tile
  (make-tile :water \~ :blue {:walkable false :transparent true}))

(defn tile-char
  "Returns the display character for a tile."
  [tile]
  (:char tile \?))

(defn tile-color
  "Returns the display color for a tile."
  [tile]
  (:color tile :white))

(defn walkable?
  "Returns true if the mover can walk through the tile.
   Checks the tile's :walkable property first, then the mover's special abilities.
   For example, a mover with :can-swim can walk on water tiles."
  [mover tile]
  (let [base-walkable (:walkable tile false)]
    (if base-walkable
      true
      ;; Check for special abilities that might allow movement
      (cond
        ;; Entities with :can-swim can traverse water
        (and (:can-swim mover) (= :water (:type tile))) true
        ;; Default to not walkable
        :else false))))

(defn transparent?
  "Returns true if the tile can be seen through."
  [tile]
  (:transparent tile false))

(def default-tile floor-tile)

(defn create-tile-map
  "Creates a tile map with the given width and height, filled with default tiles."
  [width height]
  {:width width
   :height height
   :tiles (vec (repeat (* width height) default-tile))
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
      (fill-rect x y w h wall-tile)
      (fill-rect (inc x) (inc y) (- w 2) (- h 2) floor-tile)))

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
        (fill-rect min-x y1 (inc (- max-x min-x)) 1 floor-tile)
        ;; vertical segment at x2
        (fill-rect x2 min-y 1 (inc (- max-y min-y)) floor-tile))))

(defn generate-test-map
  "Generates a simple test map with a few rooms connected by corridors."
  [width height]
  (-> (create-tile-map width height)
      (fill-rect 0 0 width height wall-tile)
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
  [game-map ox oy radius transform-fn visible]
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
                        opaque (not (transparent? (get-tile game-map x y)))]
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
  ([game-map ox oy] (compute-fov game-map ox oy nil))
  ([game-map ox oy radius]
   (let [visible (atom #{[ox oy]})]
     (doseq [transform octant-transforms]
       (scan-octant game-map ox oy radius transform visible))
     @visible)))

(defn compute-entity-fov
  "Computes field of view for an entity using its position and view-radius.
   If entity has no view-radius, computes unlimited FOV."
  [game-map entity]
  (let [[x y] (:pos entity)
        r (:view-radius entity)]
    (if r
      (compute-fov game-map x y r)
      (compute-fov game-map x y))))

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

(defn can-act?
  "Returns true if the entity has an act function."
  [entity]
  (fn? (:act entity)))

(defn- increment-next-action
  "Increments an entity's next-action by the given time, or its delay if not specified."
  ([entity] (increment-next-action entity (entity-delay entity)))
  ([entity time]
   (assoc entity :next-action (+ (entity-next-action entity) time))))

(defn- find-acted-entity
  "Finds the entity that acted by matching type and act function."
  [entities original-entity]
  (first (filter #(and (= (:type %) (:type original-entity))
                       (= (:act %) (:act original-entity)))
                 entities)))

(defn act-entity
  "Calls the entity's act function if it has one.
   The act function receives (entity, game-map, ctx) and returns an action-result map
   {:map updated-map, optional :time-cost, :no-time, :retry, :quit, :message}.
   After acting, the entity's next-action is incremented by :time-cost
   if present, otherwise by its delay. If :no-time is set, no increment occurs."
  [tile-map entity ctx]
  (if-let [act-fn (:act entity)]
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

(defn get-next-actor
  "Returns the entity with the lowest next-action value that can act, or nil if none."
  [tile-map]
  (->> (get-entities tile-map)
       (filter can-act?)
       (sort-by entity-next-action)
       first))

(defn process-next-actor
  "Processes only the entity with the lowest next-action value.
   Returns an action-result map."
  [tile-map ctx]
  (if-let [actor (get-next-actor tile-map)]
    (act-entity tile-map actor ctx)
    {:map tile-map}))

(defn process-actors
  "Processes all entities that have act functions.
   Returns an action-result map with :map, and accumulated :quit/:message flags."
  [tile-map ctx]
  (reduce (fn [accum entity]
            (let [current-map (:map accum)]
              (if (can-act? entity)
                ;; Re-fetch entity from map in case it was modified
                (if-let [current (first (filter #(= entity %) (get-entities current-map)))]
                  (let [result (act-entity current-map current ctx)]
                    (cond-> {:map (:map result)}
                      (or (:quit accum) (:quit result))       (assoc :quit true)
                      (or (:message accum) (:message result)) (assoc :message (or (:message result) (:message accum)))))
                  accum)
                accum)))
          {:map tile-map}
          (get-entities tile-map)))

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
  [game-map entity dx dy]
  (let [[x y] (entity-pos entity)
        new-x (+ x dx)
        new-y (+ y dy)
        in-map (in-bounds? game-map new-x new-y)
        can-walk (and in-map (walkable? entity (get-tile game-map new-x new-y)))]
    (if can-walk
      {:map (update-entity game-map entity move-entity-by dx dy)}
      {:map game-map :no-time true :retry true})))

(def direction-deltas
  "Maps movement actions to [dx dy] vectors."
  {:move-up [0 -1] :move-down [0 1] :move-left [-1 0] :move-right [1 0]
   :move-up-left [-1 -1] :move-up-right [1 -1]
   :move-down-left [-1 1] :move-down-right [1 1]})

(defn execute-action
  "Executes a world action (movement), returning an action-result map.
   Player-only actions (:look, :quit) are handled in make-player-act."
  [action entity game-map]
  (if-let [[dx dy] (direction-deltas action)]
    (try-move game-map entity dx dy)
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
  "Player act function. Reads :input-fn, :key-map, :registry, :look-bounds-fn from ctx.
   Loops until an action that affects the world is performed.
   Failed moves and unknown keys are retried immediately.
   Returns an action-result map."
  [entity game-map ctx]
  (let [{:keys [input-fn key-map registry look-bounds-fn]} ctx]
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

          ;; World action (movement etc.)
          action
          (let [result (execute-action action entity game-map)]
            (if (:retry result)
              (recur)
              result))

          ;; Unknown key
          :else
          (recur))))))
