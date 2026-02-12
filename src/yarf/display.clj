(ns yarf.display
  (:require [lanterna.screen :as s]
            [yarf.core :as core]))

;; Viewport management

(defn create-viewport
  "Creates a viewport with the given dimensions."
  [width height]
  {:width width
   :height height
   :offset-x 0
   :offset-y 0})

(defn center-viewport-on
  "Centers the viewport on the given world coordinates."
  [viewport x y]
  (assoc viewport
         :offset-x (- x (quot (:width viewport) 2))
         :offset-y (- y (quot (:height viewport) 2))))

(defn clamp-to-map
  "Clamps viewport offset to stay within map bounds."
  [viewport tile-map]
  (let [max-x (- (core/map-width tile-map) (:width viewport))
        max-y (- (core/map-height tile-map) (:height viewport))]
    (assoc viewport
           :offset-x (max 0 (min (:offset-x viewport) max-x))
           :offset-y (max 0 (min (:offset-y viewport) max-y)))))

(defn world-to-screen
  "Converts world coordinates to screen coordinates."
  [viewport world-x world-y]
  [(- world-x (:offset-x viewport))
   (- world-y (:offset-y viewport))])

(defn screen-to-world
  "Converts screen coordinates to world coordinates."
  [viewport screen-x screen-y]
  [(+ screen-x (:offset-x viewport))
   (+ screen-y (:offset-y viewport))])

;; Screen rendering

(defn create-screen
  "Creates a lanterna screen. Type can be :text, :swing, or :auto."
  ([] (create-screen :auto))
  ([screen-type]
   (s/get-screen screen-type)))

(defn start-screen
  "Starts the screen for rendering."
  [screen]
  (s/start screen)
  screen)

(defn stop-screen
  "Stops the screen and restores terminal."
  [screen]
  (s/stop screen))

(defn render-map
  "Renders the visible portion of the map to the screen."
  [screen tile-map viewport]
  (let [{:keys [width height offset-x offset-y]} viewport]
    (doseq [sy (range height)
            sx (range width)]
      (let [wx (+ sx offset-x)
            wy (+ sy offset-y)]
        (when (core/in-bounds? tile-map wx wy)
          (let [tile (core/get-tile tile-map wx wy)
                ch (core/tile-char tile)
                color (core/tile-color tile)]
            (s/put-string screen sx sy (str ch) {:fg color})))))))

(defn render-char
  "Renders a single character at screen coordinates with optional color."
  ([screen x y ch]
   (s/put-string screen x y (str ch)))
  ([screen x y ch color]
   (s/put-string screen x y (str ch) {:fg color})))

(defn refresh
  "Refreshes the screen to show rendered content."
  [screen]
  (s/redraw screen))

(defn get-key
  "Blocks until a key is pressed, returns the key."
  [screen]
  (s/get-key-blocking screen))

(defn get-key-non-blocking
  "Returns the key if one is pressed, nil otherwise."
  [screen]
  (s/get-key screen))
