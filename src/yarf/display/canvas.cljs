(ns yarf.display.canvas
  "Canvas-based browser display implementation."
  (:require [yarf.core :as core]
            [yarf.display :as display]
            [cljs.core.async :as async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Color mapping: keyword -> CSS color string

(def color-map
  {:black "#000000"
   :red "#ff0000"
   :green "#00cc00"
   :yellow "#ffff00"
   :blue "#4444ff"
   :magenta "#ff00ff"
   :cyan "#00ffff"
   :white "#cccccc"
   :default "#cccccc"})

(defn- css-color [kw]
  (get color-map kw "#cccccc"))

;; Key translation

(defn- translate-browser-key
  "Maps a KeyboardEvent.key string to YARF key values."
  [key-str]
  (case key-str
    "Enter" :enter
    "Escape" :escape
    "ArrowUp" :up
    "ArrowDown" :down
    "ArrowLeft" :left
    "ArrowRight" :right
    "Backspace" :backspace
    "Tab" :tab
    ;; Single character keys
    (when (= 1 (count key-str))
      (first key-str))))

;; Canvas display

(def cell-width 12)
(def cell-height 18)
(def font-str "16px monospace")

(defrecord CanvasDisplay [canvas ctx-2d viewport input-ch]
  display/Display
  (get-input [this]
    ;; In CLJS, callers use the input-ch channel directly
    (throw (js/Error. "Use input-ch channel for CLJS input")))
  (render-tile [this x y tile registry]
    (let [px (* x cell-width)
          py (* y cell-height)
          ch (str (core/tile-char registry tile))
          color (css-color (core/tile-color registry tile))]
      (set! (.-fillStyle ctx-2d) "#000000")
      (.fillRect ctx-2d px py cell-width cell-height)
      (set! (.-fillStyle ctx-2d) color)
      (set! (.-font ctx-2d) font-str)
      (set! (.-textBaseline ctx-2d) "top")
      (.fillText ctx-2d ch px py)))
  (render-entity [this x y entity]
    (let [px (* x cell-width)
          py (* y cell-height)
          ch (str (core/entity-char entity))
          color (css-color (core/entity-color entity))]
      (set! (.-fillStyle ctx-2d) "#000000")
      (.fillRect ctx-2d px py cell-width cell-height)
      (set! (.-fillStyle ctx-2d) color)
      (set! (.-font ctx-2d) font-str)
      (set! (.-textBaseline ctx-2d) "top")
      (.fillText ctx-2d ch px py)))
  (clear-screen [this]
    (set! (.-fillStyle ctx-2d) "#000000")
    (.fillRect ctx-2d 0 0 (.-width canvas) (.-height canvas)))
  (refresh-screen [this]
    ;; Canvas renders immediately, no-op
    nil)
  (start-display [this] this)
  (stop-display [this] nil)
  (display-message [this message]
    (let [msg-y (* (:height viewport) cell-height)
          msg-w (* (:width viewport) cell-width)]
      ;; Clear message bar
      (set! (.-fillStyle ctx-2d) "#000000")
      (.fillRect ctx-2d 0 msg-y msg-w cell-height)
      ;; Render message
      (when message
        (set! (.-fillStyle ctx-2d) (css-color :white))
        (set! (.-font ctx-2d) font-str)
        (set! (.-textBaseline ctx-2d) "top")
        (.fillText ctx-2d message 0 msg-y)))))

(defn render-char
  "Renders a single character at screen coordinates with color on a canvas display."
  [canvas-display x y ch color]
  (let [ctx-2d (:ctx-2d canvas-display)
        px (* x cell-width)
        py (* y cell-height)]
    (set! (.-fillStyle ctx-2d) "#000000")
    (.fillRect ctx-2d px py cell-width cell-height)
    (set! (.-fillStyle ctx-2d) (css-color color))
    (set! (.-font ctx-2d) font-str)
    (set! (.-textBaseline ctx-2d) "top")
    (.fillText ctx-2d (str ch) px py)))

(defn play-effect-async
  "Plays a visual effect asynchronously using core.async timeout.
   Returns a channel that closes when playback is complete.
   opts: {:frame-ms N, :render-base-fn (fn [canvas-display] ...)}"
  ([canvas-display effect viewport] (play-effect-async canvas-display effect viewport nil))
  ([canvas-display effect viewport opts]
   (let [frame-ms (or (:frame-ms opts) (:frame-ms effect) 50)
         render-base-fn (:render-base-fn opts)
         {:keys [width height]} viewport]
     (go (doseq [frame (:frames effect)]
           (when render-base-fn
             (render-base-fn canvas-display))
           (doseq [cell frame]
             (let [[wx wy] (:pos cell)
                   [sx sy] (display/world-to-screen viewport wx wy)]
               (when (and (>= sx 0) (< sx width)
                          (>= sy 0) (< sy height))
                 (render-char canvas-display sx sy (:char cell) (:color cell)))))
           (when (pos? frame-ms)
             (<! (async/timeout frame-ms))))))))

(defn create-canvas-display
  "Creates a canvas display. Sets canvas size and attaches keydown listener.
   Returns {:display CanvasDisplay :input-fn (fn [] ch)}.
   input-fn returns the input channel (for use with core.async <!)."
  [viewport canvas-element]
  (let [w (* (:width viewport) cell-width)
        h (* (inc (:height viewport)) cell-height) ;; +1 for message bar
        input-ch (async/chan 32)
        ctx-2d (.getContext canvas-element "2d")]
    ;; Set canvas size
    (set! (.-width canvas-element) w)
    (set! (.-height canvas-element) h)
    ;; Set up keydown handler
    (.addEventListener js/document "keydown"
      (fn [e]
        (when-let [key (translate-browser-key (.-key e))]
          (.preventDefault e)
          (async/put! input-ch key))))
    ;; Return display and input function
    (let [display (->CanvasDisplay canvas-element ctx-2d viewport input-ch)
          input-fn (fn [] input-ch)]
      {:display display
       :input-fn input-fn})))
