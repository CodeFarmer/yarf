(ns yarf.demo
  "Simple game loop demo to test the framework."
  (:require [yarf.core :as core]
            [yarf.basics :as basics]
            [yarf.display :as display]
            [yarf.display.curses :as curses]
            [lanterna.screen :as s])
  (:gen-class))

(def save-file "yarf-save.dat")

(def demo-key-map
  "Key bindings for demo: vi-style movement plus quit, Shift-S to save,
   > to descend stairs, < to ascend stairs, f to fire ranged attack."
  (merge core/default-key-map
         {\q :quit
          :escape :quit
          \S :save
          \> :descend
          \< :ascend
          \f :ranged-attack}))

(defn create-demo-registry
  "Creates a type registry for the demo with tile and entity descriptions."
  []
  (-> (core/create-type-registry)
      (basics/register-basic-tile-types)
      (core/define-entity-type :player {:name "Player" :description "That's you, the adventurer."
                                        :act core/player-act :blocks-movement true
                                        :max-hp 20 :attack "1d20+2" :defense 12
                                        :damage "1d6+1" :armor 0
                                        :ranged-attack "1d20+1" :ranged-damage "1d8" :range 8})
      (core/define-entity-type :goblin {:name "Goblin" :description "A small, green-skinned creature."
                                        :act (basics/make-chase-act basics/player-target-fn)
                                        :blocks-movement true
                                        :max-hp 5 :attack "1d20" :defense 8
                                        :damage "1d4" :armor 0})))

(defn render-game
  "Renders the game to the screen. fov is the set of currently visible coords,
   explored is the set of previously seen coords."
  [screen game-map viewport message fov explored registry]
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
              (s/put-string screen sx sy (str (core/tile-char registry tile))
                            {:fg (core/tile-color registry tile)})
              (explored [wx wy])
              (s/put-string screen sx sy (str (core/tile-char registry tile))
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
  [screen game-map viewport cx cy look-info fov explored registry]
  (let [{:keys [width height offset-x offset-y]} viewport
        sx (- cx offset-x)
        sy (- cy offset-y)]
    ;; Render normal game frame with look name as message
    (render-game screen game-map viewport (:name look-info) fov explored registry)
    ;; Position cursor on examined square
    (when (and (>= sx 0) (< sx width)
               (>= sy 0) (< sy height))
      (s/move-cursor screen sx sy))
    (s/redraw screen)))

(defn demo-on-look-move
  "Look-mode callback: renders the game with cursor at (cx, cy)."
  [ctx game-map cx cy look-info]
  (let [{:keys [screen viewport explored registry]} ctx
        player (core/get-player game-map)
        [px py] (core/entity-pos player)
        vp (-> viewport
               (display/center-viewport-on px py)
               (display/clamp-to-map game-map))
        fov (core/compute-entity-fov registry game-map player)]
    (render-look-frame screen game-map vp cx cy look-info fov explored registry)))

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
  "Creates a player for the demo with combat stats."
  [x y]
  (core/create-entity :player \@ :yellow x y {:hp 20}))

(defn create-goblin
  "Creates a goblin with combat stats."
  [x y]
  (core/create-entity :goblin \g :green x y {:hp 5}))

(def stairs-down-pos [22 7])
(def stairs-up-pos [8 18])

(defn create-demo-level-1
  "Creates level 1: test map with stairs-down, a door, player, and a goblin."
  []
  (let [[sx sy] stairs-down-pos]
    (-> (core/generate-test-map 60 40)
        (core/set-tile sx sy {:type :stairs-down})
        (core/set-tile 9 5 {:type :door-closed})
        (core/add-entity (create-demo-player 5 5))
        (core/add-entity (create-goblin 18 6)))))

(defn create-demo-level-2
  "Creates level 2: test map with stairs-up and a goblin. No player (enters via transition)."
  []
  (let [[sx sy] stairs-up-pos]
    (-> (core/generate-test-map 60 40)
        (core/set-tile sx sy {:type :stairs-up})
        (core/add-entity (create-goblin 8 20)))))

(defn create-demo-world
  "Creates the demo world with two levels connected by stairs."
  []
  (-> (core/create-world {:level-1 (create-demo-level-1)
                           :level-2 (create-demo-level-2)}
                          :level-1)
      (core/add-transition-pair :level-1 stairs-down-pos :level-2 stairs-up-pos)))

(defn center-viewport-on-player
  "Returns viewport centered on player."
  [game-map viewport]
  (if-let [player (core/get-player game-map)]
    (let [[x y] (core/entity-pos player)]
      (-> viewport
          (display/center-viewport-on x y)
          (display/clamp-to-map game-map)))
    viewport))

(defn- stair-tile-for-action
  "Returns the expected stair tile type for a transition action."
  [action]
  (case action
    :descend :stairs-down
    :ascend :stairs-up
    nil))

(defn- handle-transition
  "Handles a stair transition action. Returns [world message] or nil if no stairs here."
  [world game-map action]
  (let [player (core/get-player game-map)
        [px py] (core/entity-pos player)
        tile (core/get-tile game-map px py)
        expected-type (stair-tile-for-action action)]
    (if (= expected-type (:type tile))
      (if-let [transition (core/transition-at-entity world player)]
        (let [new-world (core/apply-transition world player transition)
              target-name (name (:target-map transition))]
          [new-world (str "You " (if (= action :descend) "descend" "ascend")
                          " to " target-name ".")])
        [nil "There are no stairs here."])
      [nil "There are no stairs here."])))

(defn game-loop
  "Main game loop. Uses a world structure with per-map explored state."
  [screen initial-world base-ctx initial-explored]
  (let [registry (:registry base-ctx)]
    (loop [world initial-world
           message nil
           explored (or initial-explored {})]
      (let [map-id (core/current-map-id world)
            game-map (core/current-map world)
            player (core/get-player game-map)]
        (if-not player
          ;; Player died
          :dead
          (let [fov (core/compute-entity-fov registry game-map player)
                map-explored (into (get explored map-id #{}) fov)
                explored (assoc explored map-id map-explored)
                vp (center-viewport-on-player game-map (:viewport base-ctx))
                level-msg (when message (str "[" (name map-id) "] " message))]
            (render-game screen game-map vp level-msg fov map-explored registry)
            (let [ctx (assoc base-ctx :explored map-explored)
                  result (core/process-actors game-map ctx)
                  {:keys [map quit message action effect]} result]
              (when effect
                (curses/play-effect screen effect vp
                                     {:render-base-fn (fn [scr]
                                                        (render-game scr map vp nil fov map-explored registry))}))
              (cond
                quit :quit

                ;; Check if player died during this turn
                (nil? (core/get-player map))
                (do
                  (render-game screen map vp
                               (str "[" (name map-id) "] " (or message "You die..."))
                               fov map-explored registry)
                  :dead)

                (= :save action)
                (let [updated-world (core/set-current-map world map)]
                  (core/save-world save-file updated-world
                                   {:explored explored
                                    :viewport (:viewport base-ctx)})
                  :saved)

                (#{:descend :ascend} action)
                (let [[new-world msg] (handle-transition world map action)]
                  (if new-world
                    (recur new-world msg explored)
                    (recur world msg explored)))

                :else
                (recur (core/set-current-map world map) message explored)))))))))

(defn- prompt-screen
  "Displays a message on the screen's message bar and waits for a key."
  [screen viewport message]
  (s/clear screen)
  (let [{:keys [width height]} viewport]
    (s/put-string screen 0 height message {:fg :white})
    (s/redraw screen)
    (s/get-key-blocking screen)))

(defn- load-saved-game
  "Attempts to load a saved game. Returns {:world w :explored e} or nil.
   Handles both v1 (single-map) and v2 (world) save formats."
  [screen viewport registry]
  (when (.exists (java.io.File. save-file))
    (let [key (prompt-screen screen viewport "Save file found. Load it? (y/n)")]
      (if (= \y key)
        (try
          (let [restored (core/load-game save-file)]
            (prompt-screen screen viewport "Game loaded. Press any key...")
            (if (= 2 (:version restored))
              ;; v2: world format
              {:world (:world restored)
               :explored (:explored restored)}
              ;; v1: wrap single map in a world for backward compat
              (let [game-map (:game-map restored)
                    world (core/create-world {:level-1 game-map} :level-1)]
                {:world world
                 :explored {:level-1 (or (:explored restored) #{})}})))
          (catch Exception e
            (prompt-screen screen viewport (str "Failed to load: " (.getMessage e)))
            nil))
        nil))))

(defn run-demo
  "Runs the demo game."
  []
  (println "Starting YARF demo...")
  (println "Movement: hjkl (vi-style), yubn (diagonals)")
  (println "Look: x (move cursor, Enter to inspect, Escape to cancel)")
  (println "Fire: f (select target, Enter to fire, Escape to cancel)")
  (println "Stairs: > to descend, < to ascend")
  (println "Save: Shift-S | Quit: q or ESC")
  (let [registry (create-demo-registry)
        viewport (display/create-viewport 50 25)
        screen (s/get-screen :swing {:cols (:width viewport)
                                     :rows (inc (:height viewport))})]
    (s/start screen)
    (let [result (try
                   (let [loaded (load-saved-game screen viewport registry)
                         _ (when-not loaded
                             (prompt-screen screen viewport "Press any key to start..."))
                         world (or (:world loaded) (create-demo-world))
                         explored (or (:explored loaded) {})
                         base-ctx {:input-fn #(s/get-key-blocking screen)
                                   :key-map demo-key-map
                                   :registry registry
                                   :viewport viewport
                                   :screen screen
                                   :on-look-move demo-on-look-move
                                   :look-bounds-fn demo-look-bounds-fn
                                   :on-bump basics/combat-on-bump
                                   :on-bump-tile basics/door-on-bump-tile
                                   :on-ranged-attack basics/ranged-on-target
                                   :pass-through-actions #{:save :descend :ascend}}]
                     (game-loop screen world base-ctx explored))
                   (finally
                     (s/stop screen)))]
      (case result
        :saved (println "Game saved.")
        :dead (println "You died! Game over.")
        (println "Demo ended.")))))

(defn -main
  "Main entry point."
  [& args]
  (run-demo)
  (System/exit 0))
