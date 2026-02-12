(ns yarf.core)

(def default-tile {:type :floor})

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
