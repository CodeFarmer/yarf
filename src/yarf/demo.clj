(ns yarf.demo
  "Simple game loop demo to test the framework."
  (:require [yarf.core :as core]
            [yarf.display :as display]
            [lanterna.screen :as s])
  (:gen-class))

(def save-file "yarf-save.dat")

(def demo-key-map
  "Key bindings for demo: vi-style movement plus quit, Shift-S to save."
  (merge core/default-key-map
         {\q :quit
          :escape :quit
          \S :save}))

(defn goblin-wander
  "Act function for goblins: wander randomly."
  [entity game-map _ctx]
  (let [dx (- (rand-int 3) 1)
        dy (- (rand-int 3) 1)]
    (core/try-move game-map entity dx dy)))

(defn create-demo-registry
  "Creates a type registry for the demo with tile and entity descriptions."
  []
  (-> (core/create-type-registry)
      (core/define-entity-type :player {:name "Player" :description "That's you, the adventurer."
                                        :act core/player-act})
      (core/define-entity-type :goblin {:name "Goblin" :description "A small, green-skinned creature."
                                        :act goblin-wander})
      (core/define-tile-type :floor {:name "Stone Floor" :description "Cold grey stone."})
      (core/define-tile-type :wall {:name "Stone Wall" :description "A solid wall of rough-hewn stone."})
      (core/define-tile-type :water {:name "Water" :description "Dark, still water."})))

(defn render-game
  "Renders the game to the screen. fov is the set of currently visible coords,
   explored is the set of previously seen coords."
  [screen game-map viewport message fov explored]
  (s/clear screen)
  (let [{:keys [width height offset-x offset-y]} viewport]
    ;; Render tiles
    (doseq [sy (range height)
            sx (range width)]
      (let [wx (+ sx offset-x)
            wy (+ sy offset-y)]
        (when (core/in-bounds? game-map wx wy)
          (let [tile (core/get-tile game-map wx wy)]
            (cond
              (fov [wx wy])
              (s/put-string screen sx sy (str (core/tile-char tile))
                            {:fg (core/tile-color tile)})
              (explored [wx wy])
              (s/put-string screen sx sy (str (core/tile-char tile))
                            {:fg :blue}))))))
    ;; Render entities (only if in FOV)
    (doseq [entity (core/get-entities game-map)]
      (let [[wx wy] (core/entity-pos entity)
            sx (- wx offset-x)
            sy (- wy offset-y)]
        (when (and (fov [wx wy])
                   (>= sx 0) (< sx width)
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
  [screen game-map viewport cx cy look-info fov explored]
  (let [{:keys [width height offset-x offset-y]} viewport
        sx (- cx offset-x)
        sy (- cy offset-y)]
    ;; Render normal game frame with look name as message
    (render-game screen game-map viewport (:name look-info) fov explored)
    ;; Position cursor on examined square
    (when (and (>= sx 0) (< sx width)
               (>= sy 0) (< sy height))
      (s/move-cursor screen sx sy))
    (s/redraw screen)))

(defn demo-on-look-move
  "Look-mode callback: renders the game with cursor at (cx, cy)."
  [ctx game-map cx cy look-info]
  (let [{:keys [screen viewport explored]} ctx
        player (core/get-player game-map)
        [px py] (core/entity-pos player)
        vp (-> viewport
               (display/center-viewport-on px py)
               (display/clamp-to-map game-map))
        fov (core/compute-entity-fov game-map player)]
    (render-look-frame screen game-map vp cx cy look-info fov explored)))

(defn demo-look-bounds-fn
  "Look-mode bounds callback: constrains cursor to viewport."
  [ctx game-map entity]
  (let [{:keys [viewport]} ctx
        [px py] (core/entity-pos entity)
        vp (-> viewport
               (display/center-viewport-on px py)
               (display/clamp-to-map game-map))
        {:keys [offset-x offset-y width height]} vp]
    [offset-x offset-y
     (+ offset-x (dec width))
     (+ offset-y (dec height))]))

(defn create-demo-player
  "Creates a player for the demo."
  [x y]
  (core/create-entity :player \@ :yellow x y))

(defn create-wandering-goblin
  "Creates a goblin that wanders randomly."
  [x y]
  (core/create-entity :goblin \g :green x y))

(defn create-demo-game
  "Creates a demo game state."
  []
  (-> (core/generate-test-map 60 40)
      (core/add-entity (create-demo-player 5 5))
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
  "Main game loop. Threads explored state as a plain set through ctx."
  [screen initial-map base-ctx initial-explored]
  (loop [game-map initial-map
         message nil
         explored (or initial-explored #{})]
    (let [player (core/get-player game-map)
          fov (core/compute-entity-fov game-map player)
          explored (into explored fov)
          vp (center-viewport-on-player game-map (:viewport base-ctx))]
      (render-game screen game-map vp message fov explored)
      (let [ctx (assoc base-ctx :explored explored)
            result (core/process-actors game-map ctx)
            {:keys [map quit message action]} result]
        (cond
          quit :quit
          (= :save action)
          (do (core/save-game save-file map {:explored explored
                                             :viewport (:viewport base-ctx)})
              (recur map "Game saved." explored))
          :else
          (recur map message explored))))))

(defn- load-saved-game
  "Attempts to load a saved game. Returns {:game-map m :explored e} or nil."
  []
  (when (.exists (java.io.File. save-file))
    (println "Save file found. Load it? (y/n)")
    (let [answer (read-line)]
      (when (= "y" (.toLowerCase (.trim answer)))
        (try
          (let [restored (core/load-game save-file)]
            (println "Game loaded.")
            {:game-map (:game-map restored)
             :explored (:explored restored)})
          (catch Exception e
            (println (str "Failed to load save: " (.getMessage e)))
            nil))))))

(defn run-demo
  "Runs the demo game."
  []
  (println "Starting YARF demo...")
  (println "Movement: hjkl (vi-style), yubn (diagonals)")
  (println "Look: x (move cursor, Enter to inspect, Escape to cancel)")
  (println "Save: Shift-S")
  (println "Quit: q or ESC")
  (let [registry (create-demo-registry)
        loaded (load-saved-game)
        _ (when-not loaded
            (println "Press Enter to start...")
            (read-line))
        viewport (display/create-viewport 50 25)
        screen (s/get-screen :swing {:cols (:width viewport)
                                     :rows (inc (:height viewport))})
        base-ctx {:input-fn #(s/get-key-blocking screen)
                  :key-map demo-key-map
                  :registry registry
                  :viewport viewport
                  :screen screen
                  :on-look-move demo-on-look-move
                  :look-bounds-fn demo-look-bounds-fn
                  :pass-through-actions #{:save}}]
    (s/start screen)
    (try
      (let [game-map (or (:game-map loaded) (create-demo-game))
            explored (or (:explored loaded) #{})]
        (game-loop screen game-map base-ctx explored))
      (finally
        (s/stop screen))))
  (println "Demo ended."))

(defn -main
  "Main entry point."
  [& args]
  (run-demo)
  (System/exit 0))
