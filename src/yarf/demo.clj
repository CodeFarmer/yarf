(ns yarf.demo
  "Simple game loop demo to test the framework."
  (:require [yarf.core :as core]
            [yarf.display :as display]
            [lanterna.screen :as s])
  (:gen-class))

(def demo-key-map
  "Key bindings for demo: vi-style movement plus quit."
  (merge core/default-key-map
         {\q :quit
          :escape :quit}))

(defn make-demo-player-act
  "Creates a player act function with bump feedback.
   Wraps the base player behavior to add 'bump!' message on blocked movement."
  [input-fn key-map]
  (fn [entity game-map]
    (let [input (input-fn)
          action (get key-map input)
          move-action? (#{:move-up :move-down :move-left :move-right
                          :move-up-left :move-up-right :move-down-left :move-down-right} action)]
      (if action
        (let [result (core/execute-action action entity game-map)
              player-after (core/get-player result)
              blocked? (and move-action?
                            player-after
                            (= (core/entity-x entity) (core/entity-x player-after))
                            (= (core/entity-y entity) (core/entity-y player-after)))]
          (if blocked?
            (assoc result :message "bump!")
            result))
        game-map))))

(defn create-demo-player
  "Creates a player for the demo with vi-style movement, quit, and bump feedback."
  [x y screen]
  (core/create-entity :player \@ :yellow x y
                      {:act (make-demo-player-act #(s/get-key-blocking screen) demo-key-map)}))

(defn create-wandering-goblin
  "Creates a goblin that wanders randomly."
  [x y]
  (core/create-entity :goblin \g :green x y
                      {:act (fn [e m]
                              (let [dx (- (rand-int 3) 1)
                                    dy (- (rand-int 3) 1)]
                                (core/update-entity m e core/move-entity-by dx dy)))}))

(defn create-demo-game
  "Creates a demo game state."
  [screen]
  (-> (core/generate-test-map 60 40)
      (core/add-entity (create-demo-player 5 5 screen))
      (core/add-entity (create-wandering-goblin 18 6))
      (core/add-entity (create-wandering-goblin 8 18))))

(defn render-game
  "Renders the game to the screen."
  [screen game-map viewport message]
  (s/clear screen)
  (let [{:keys [width height offset-x offset-y]} viewport]
    ;; Render tiles
    (doseq [sy (range height)
            sx (range width)]
      (let [wx (+ sx offset-x)
            wy (+ sy offset-y)]
        (when (core/in-bounds? game-map wx wy)
          (let [tile (core/get-tile game-map wx wy)]
            (s/put-string screen sx sy (str (core/tile-char tile))
                          {:fg (core/tile-color tile)})))))
    ;; Render entities
    (doseq [entity (core/get-entities game-map)]
      (let [wx (core/entity-x entity)
            wy (core/entity-y entity)
            sx (- wx offset-x)
            sy (- wy offset-y)]
        (when (and (>= sx 0) (< sx width)
                   (>= sy 0) (< sy height))
          (s/put-string screen sx sy (str (core/entity-char entity))
                        {:fg (core/entity-color entity)}))))
    ;; Render message bar
    (when message
      (s/put-string screen 0 height message {:fg :white})))
  (s/redraw screen))

(defn center-viewport-on-player
  "Returns viewport centered on player."
  [game-map viewport]
  (if-let [player (core/get-player game-map)]
    (-> viewport
        (display/center-viewport-on (core/entity-x player) (core/entity-y player))
        (display/clamp-to-map game-map))
    viewport))

(defn game-loop
  "Main game loop."
  [screen initial-map viewport]
  (loop [game-map initial-map
         message nil]
    (let [vp (center-viewport-on-player game-map viewport)]
      (render-game screen game-map vp message)
      (let [new-map (core/process-actors game-map)
            new-message (:message new-map)
            clean-map (dissoc new-map :message)]
        (if (:quit clean-map)
          :quit
          (recur clean-map new-message))))))

(defn run-demo
  "Runs the demo game."
  []
  (println "Starting YARF demo...")
  (println "Movement: hjkl (vi-style), yubn (diagonals)")
  (println "Quit: q or ESC")
  (println "Press Enter to start...")
  (read-line)
  (let [screen (s/get-screen :swing)
        viewport (display/create-viewport 50 25)]
    (s/start screen)
    (try
      (let [game-map (create-demo-game screen)]
        (game-loop screen game-map viewport))
      (finally
        (s/stop screen))))
  (println "Demo ended."))

(defn -main
  "Main entry point."
  [& args]
  (run-demo)
  (System/exit 0))
