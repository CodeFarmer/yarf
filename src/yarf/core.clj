(ns yarf.core)

;; Tile properties and constructors

(defn make-tile
  "Creates a tile with the given type and properties."
  [tile-type properties]
  (merge {:type tile-type} properties))

(def floor-tile
  (make-tile :floor {:walkable true :transparent true}))

(def wall-tile
  (make-tile :wall {:walkable false :transparent false}))

(def door-closed-tile
  (make-tile :door-closed {:walkable false :transparent false}))

(def door-open-tile
  (make-tile :door-open {:walkable true :transparent true}))

(def water-tile
  (make-tile :water {:walkable false :transparent true}))

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
   :tiles (vec (repeat (* width height) default-tile))})

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
