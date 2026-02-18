(ns yarf.display
  (:require [yarf.core :as core]))

;; Display protocol

(defprotocol Display
  "Protocol for game display implementations."
  (get-input [this] "Gets input from the display (blocking).")
  (render-tile [this x y tile registry] "Renders a tile at screen coordinates using registry for display properties.")
  (render-entity [this x y entity] "Renders an entity at screen coordinates.")
  (clear-screen [this] "Clears the display.")
  (refresh-screen [this] "Refreshes the display to show rendered content.")
  (start-display [this] "Starts the display.")
  (stop-display [this] "Stops the display and cleans up.")
  (display-message [this message] "Displays a message in the message bar."))

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

;; Render helpers (used by both curses and canvas display implementations)

(defn render-map-to-display
  "Renders the visible portion of the map to a Display."
  [display tile-map registry]
  (let [{:keys [width height offset-x offset-y]} (:viewport display)]
    (doseq [sy (range height)
            sx (range width)]
      (let [wx (+ sx offset-x)
            wy (+ sy offset-y)]
        (when (core/in-bounds? tile-map wx wy)
          (render-tile display sx sy (core/get-tile tile-map wx wy) registry))))))

(defn render-entities-to-display
  "Renders all visible entities to a Display."
  [display tile-map]
  (let [{:keys [width height]} (:viewport display)]
    (doseq [entity (core/get-entities tile-map)]
      (let [[wx wy] (core/entity-pos entity)
            [sx sy] (world-to-screen (:viewport display) wx wy)]
        (when (and (>= sx 0) (< sx width)
                   (>= sy 0) (< sy height))
          (render-entity display sx sy entity))))))
