(ns yarf.display.curses
  "Lanterna/curses-based display implementation."
  (:require [lanterna.screen :as s]
            [yarf.core :as core]
            [yarf.display :as display]))

;; Legacy screen functions (low-level lanterna access)

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
            [sx sy] (display/world-to-screen viewport wx wy)]
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
  display/Display
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

;; Effect playback

(defn play-effect
  "Plays a visual effect on screen. For each frame, optionally re-renders the base
   scene, overlays effect cells via render-char, refreshes, and sleeps.
   opts: {:frame-ms N, :render-base-fn (fn [screen] ...)}"
  ([screen effect viewport] (play-effect screen effect viewport nil))
  ([screen effect viewport opts]
   (let [frame-ms (or (:frame-ms opts) (:frame-ms effect) 50)
         render-base-fn (:render-base-fn opts)
         {:keys [width height]} viewport]
     (doseq [frame (:frames effect)]
       (when render-base-fn
         (render-base-fn screen))
       (doseq [cell frame]
         (let [[wx wy] (:pos cell)
               [sx sy] (display/world-to-screen viewport wx wy)]
           (when (and (>= sx 0) (< sx width)
                      (>= sy 0) (< sy height))
             (render-char screen sx sy (:char cell) (:color cell)))))
       (refresh screen)
       (when (pos? frame-ms)
         (Thread/sleep frame-ms))))))
