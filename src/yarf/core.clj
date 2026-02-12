(ns yarf.core)

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
  "Returns true if the tile can be walked through."
  [tile]
  (:walkable tile false))

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

;; Entities

(defn create-entity
  "Creates an entity with type, display char, color, and position.
   Optional properties map can be provided."
  ([entity-type char color x y]
   (create-entity entity-type char color x y {}))
  ([entity-type char color x y properties]
   (merge {:type entity-type :char char :color color :x x :y y} properties)))

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

(defn entity-x
  "Returns the x coordinate of an entity."
  [entity]
  (:x entity))

(defn entity-y
  "Returns the y coordinate of an entity."
  [entity]
  (:y entity))

(defn move-entity
  "Moves an entity to a new position."
  [entity x y]
  (assoc entity :x x :y y))

(defn move-entity-by
  "Moves an entity by a relative offset."
  [entity dx dy]
  (move-entity entity (+ (entity-x entity) dx) (+ (entity-y entity) dy)))

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
  (filter #(and (= x (entity-x %)) (= y (entity-y %)))
          (get-entities tile-map)))

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

(defn act-entity
  "Calls the entity's act function if it has one.
   The act function receives (entity, game-map) and returns updated map."
  [tile-map entity]
  (if-let [act-fn (:act entity)]
    (act-fn entity tile-map)
    tile-map))

(defn process-actors
  "Processes all entities that have act functions."
  [tile-map]
  (reduce (fn [m entity]
            (if (can-act? entity)
              ;; Re-fetch entity from map in case it was modified
              (if-let [current (first (filter #(= entity %) (get-entities m)))]
                (act-entity m current)
                m)
              m))
          tile-map
          (get-entities tile-map)))

;; Player input handling

(defn make-player-act
  "Creates a player act function that calls input-fn to get input."
  [input-fn]
  (fn [entity game-map]
    (let [input (input-fn)]
      (case input
        :up (update-entity game-map entity move-entity-by 0 -1)
        :down (update-entity game-map entity move-entity-by 0 1)
        :left (update-entity game-map entity move-entity-by -1 0)
        :right (update-entity game-map entity move-entity-by 1 0)
        game-map))))
