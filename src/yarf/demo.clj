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

(defn create-demo-registry
  "Creates a type registry for the demo with tile and entity descriptions."
  []
  (-> (core/create-type-registry)
      (core/define-entity-type :player {:name "Player" :description "That's you, the adventurer."})
      (core/define-entity-type :goblin {:name "Goblin" :description "A small, green-skinned creature."})
      (core/define-tile-type :floor {:name "Stone Floor" :description "Cold grey stone."})
      (core/define-tile-type :wall {:name "Stone Wall" :description "A solid wall of rough-hewn stone."})
      (core/define-tile-type :water {:name "Water" :description "Dark, still water."})))

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
      (let [[wx wy] (core/entity-pos entity)
            sx (- wx offset-x)
            sy (- wy offset-y)]
        (when (and (>= sx 0) (< sx width)
                   (>= sy 0) (< sy height))
          (s/put-string screen sx sy (str (core/entity-char entity))
                        {:fg (core/entity-color entity)}))))
    ;; Render message bar
    (s/put-string screen 0 height (apply str (repeat width \space)) {:fg :white})
    (when message
      (s/put-string screen 0 height message {:fg :white}))
    ;; Position cursor on player
    (when-let [player (core/get-player game-map)]
      (let [[wx wy] (core/entity-pos player)
            px (- wx offset-x)
            py (- wy offset-y)]
        (when (and (>= px 0) (< px width) (>= py 0) (< py height))
          (s/move-cursor screen px py)))))
  (s/redraw screen))

(defn render-look-frame
  "Renders the game with a highlighted cursor at (cx, cy) and look info in message bar."
  [screen game-map viewport cx cy look-info]
  (let [{:keys [width height offset-x offset-y]} viewport
        sx (- cx offset-x)
        sy (- cy offset-y)]
    ;; Render normal game frame with look name as message
    (render-game screen game-map viewport (:name look-info))
    ;; Position cursor on examined square
    (when (and (>= sx 0) (< sx width)
               (>= sy 0) (< sy height))
      (s/move-cursor screen sx sy))
    (s/redraw screen)))

(defn create-demo-player
  "Creates a player for the demo with vi-style movement, quit, look mode."
  [x y screen registry base-viewport]
  (let [input-fn #(s/get-key-blocking screen)
        on-look-move (fn [game-map cx cy look-info]
                       (let [vp (-> base-viewport
                                    (display/center-viewport-on
                                     (first (core/entity-pos (core/get-player game-map)))
                                     (second (core/entity-pos (core/get-player game-map))))
                                    (display/clamp-to-map game-map))]
                         (render-look-frame screen game-map vp cx cy look-info)))]
    (core/create-entity :player \@ :yellow x y
                        {:act (core/make-player-act input-fn demo-key-map
                                                    {:registry registry
                                                     :on-look-move on-look-move})})))

(defn create-wandering-goblin
  "Creates a goblin that wanders randomly."
  [x y]
  (core/create-entity :goblin \g :green x y
                      {:act (fn [e m]
                              (let [dx (- (rand-int 3) 1)
                                    dy (- (rand-int 3) 1)]
                                (core/try-move m e dx dy)))}))

(defn create-demo-game
  "Creates a demo game state."
  [screen registry viewport]
  (-> (core/generate-test-map 60 40)
      (core/add-entity (create-demo-player 5 5 screen registry viewport))
      (core/add-entity (create-wandering-goblin 18 6))
      (core/add-entity (create-wandering-goblin 8 18))))

(defn center-viewport-on-player
  "Returns viewport centered on player."
  [game-map viewport]
  (if-let [player (core/get-player game-map)]
    (let [[x y] (core/entity-pos player)]
      (-> viewport
          (display/center-viewport-on x y)
          (display/clamp-to-map game-map)))
    viewport))

(defn game-loop
  "Main game loop."
  [screen initial-map viewport]
  (loop [game-map initial-map
         message nil]
    (let [vp (center-viewport-on-player game-map viewport)]
      (render-game screen game-map vp message)
      (let [result (core/process-actors game-map)
            {:keys [map quit message]} result]
        (if quit
          :quit
          (recur map message))))))

(defn run-demo
  "Runs the demo game."
  []
  (println "Starting YARF demo...")
  (println "Movement: hjkl (vi-style), yubn (diagonals)")
  (println "Look: x (move cursor, Enter to inspect, Escape to cancel)")
  (println "Quit: q or ESC")
  (println "Press Enter to start...")
  (read-line)
  (let [viewport (display/create-viewport 50 25)
        screen (s/get-screen :swing {:cols (:width viewport)
                                     :rows (inc (:height viewport))})
        registry (create-demo-registry)]
    (s/start screen)
    (try
      (let [game-map (create-demo-game screen registry viewport)]
        (game-loop screen game-map viewport))
      (finally
        (s/stop screen))))
  (println "Demo ended."))

(defn -main
  "Main entry point."
  [& args]
  (run-demo)
  (System/exit 0))
