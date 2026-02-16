(ns yarf.display
  (:require [lanterna.screen :as s]
            [yarf.core :as core]))

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
  [screen tile-map viewport registry]
  (let [{:keys [width height offset-x offset-y]} viewport]
    (doseq [sy (range height)
            sx (range width)]
      (let [wx (+ sx offset-x)
            wy (+ sy offset-y)]
        (when (core/in-bounds? tile-map wx wy)
          (let [tile (core/get-tile tile-map wx wy)
                ch (core/tile-char registry tile)
                color (core/tile-color registry tile)]
            (s/put-string screen sx sy (str ch) {:fg color})))))))

(defn render-char
  "Renders a single character at screen coordinates with optional color."
  ([screen x y ch]
   (s/put-string screen x y (str ch)))
  ([screen x y ch color]
   (s/put-string screen x y (str ch) {:fg color})))

(defn render-entities
  "Renders all visible entities to the screen."
  [screen tile-map viewport]
  (let [{:keys [width height offset-x offset-y]} viewport]
    (doseq [entity (core/get-entities tile-map)]
      (let [[wx wy] (core/entity-pos entity)
            [sx sy] (world-to-screen viewport wx wy)]
        (when (and (>= sx 0) (< sx width)
                   (>= sy 0) (< sy height))
          (let [ch (core/entity-char entity)
                color (core/entity-color entity)]
            (s/put-string screen sx sy (str ch) {:fg color})))))))

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

;; Curses display implementation

(defrecord CursesDisplay [screen viewport]
  Display
  (get-input [this]
    (s/get-key-blocking screen))
  (render-tile [this x y tile registry]
    (s/put-string screen x y (str (core/tile-char registry tile)) {:fg (core/tile-color registry tile)}))
  (render-entity [this x y entity]
    (s/put-string screen x y (str (core/entity-char entity)) {:fg (core/entity-color entity)}))
  (clear-screen [this]
    (s/clear screen))
  (refresh-screen [this]
    (s/redraw screen))
  (start-display [this]
    (s/start screen)
    this)
  (stop-display [this]
    (s/stop screen))
  (display-message [this message]
    (let [status-line (:height viewport)]
      (s/put-string screen 0 status-line (apply str (repeat (:width viewport) \space)))
      (s/put-string screen 0 status-line message {:fg :white}))))

(defn create-curses-display
  "Creates a curses display with the given viewport.
   screen-type can be :text, :swing, or :auto."
  ([viewport] (create-curses-display viewport :auto))
  ([viewport screen-type]
   (->CursesDisplay (s/get-screen screen-type) viewport)))

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

