(ns yarf.demo-web
  "Browser-based demo game using canvas display and core.async."
  (:require [yarf.core :as core]
            [yarf.basics :as basics]
            [yarf.display :as display]
            [yarf.display.canvas :as canvas]
            [yarf.io :as io]
            [cljs.core.async :as async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def save-key "yarf-save")

(def demo-key-map
  "Key bindings for demo."
  (merge core/default-key-map
         {\q :quit
          :escape :quit
          \S :save
          \> :descend
          \< :ascend
          \f :ranged-attack
          ;; Ctrl+direction: bump without moving
          :ctrl-h :bump-left
          :ctrl-j :bump-down
          :ctrl-k :bump-up
          :ctrl-l :bump-right
          :ctrl-y :bump-up-left
          :ctrl-u :bump-up-right
          :ctrl-b :bump-down-left
          :ctrl-n :bump-down-right}))

(defn create-demo-registry []
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

(defn create-demo-player [x y]
  (core/create-entity :player \@ :yellow x y {:hp 20}))

(defn create-goblin [x y]
  (core/create-entity :goblin \g :green x y {:hp 5}))

(def stairs-down-pos [22 7])
(def stairs-up-pos [8 18])

(defn create-demo-level-1 []
  (let [[sx sy] stairs-down-pos]
    (-> (core/generate-test-map 60 40)
        (core/set-tile sx sy {:type :stairs-down})
        (core/set-tile 9 5 {:type :door-closed})
        (core/add-entity (create-demo-player 5 5))
        (core/add-entity (create-goblin 18 6)))))

(defn create-demo-level-2 []
  (let [[sx sy] stairs-up-pos]
    (-> (core/generate-test-map 60 40)
        (core/set-tile sx sy {:type :stairs-up})
        (core/add-entity (create-goblin 8 20)))))

(defn create-demo-world []
  (-> (core/create-world {:level-1 (create-demo-level-1)
                           :level-2 (create-demo-level-2)}
                          :level-1)
      (core/add-transition-pair :level-1 stairs-down-pos :level-2 stairs-up-pos)))

(defn render-game
  "Renders the game to the canvas display."
  [canvas-disp game-map viewport message fov explored registry]
  (display/clear-screen canvas-disp)
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
              (display/render-tile canvas-disp sx sy tile registry)
              (explored [wx wy])
              ;; Explored but not visible: render in blue
              (let [ch (core/tile-char registry tile)]
                (canvas/render-char canvas-disp sx sy ch :blue)))))))
    ;; Render entities (only if in FOV)
    (doseq [entity (core/get-entities game-map)]
      (let [[wx wy] (core/entity-pos entity)
            sx (- wx offset-x)
            sy (- wy offset-y)]
        (when (and (fov [wx wy])
                   (>= sx 0) (< sx width)
                   (>= sy 0) (< sy height))
          (display/render-entity canvas-disp sx sy entity))))
    ;; Message bar
    (display/display-message canvas-disp message)
    (display/refresh-screen canvas-disp)))

(defn render-look-frame
  "Renders the game with a highlighted cursor at (cx, cy) and look info in message bar."
  [canvas-disp game-map viewport cx cy look-info fov explored registry]
  (render-game canvas-disp game-map viewport (:name look-info) fov explored registry)
  (let [{:keys [width height offset-x offset-y]} viewport
        sx (- cx offset-x)
        sy (- cy offset-y)]
    (when (and (>= sx 0) (< sx width)
               (>= sy 0) (< sy height))
      ;; Draw cursor: white background with black character
      (let [tile (when (core/in-bounds? game-map cx cy) (core/get-tile game-map cx cy))
            entity (first (core/get-entities-at game-map cx cy))
            ch (cond entity (str (core/entity-char entity))
                     tile (str (core/tile-char registry tile))
                     :else " ")
            ctx-2d (:ctx-2d canvas-disp)
            px (* sx canvas/cell-width)
            py (* sy canvas/cell-height)]
        (set! (.-fillStyle ctx-2d) "#ffffff")
        (.fillRect ctx-2d px py canvas/cell-width canvas/cell-height)
        (set! (.-fillStyle ctx-2d) "#000000")
        (set! (.-font ctx-2d) canvas/font-str)
        (set! (.-textBaseline ctx-2d) "top")
        (.fillText ctx-2d ch px py)))
    (display/refresh-screen canvas-disp)))

(defn center-viewport-on-player [game-map viewport]
  (if-let [player (core/get-player game-map)]
    (let [[x y] (core/entity-pos player)]
      (-> viewport
          (display/center-viewport-on x y)
          (display/clamp-to-map game-map)))
    viewport))

(defn- stair-tile-for-action [action]
  (case action
    :descend :stairs-down
    :ascend :stairs-up
    nil))

(defn- handle-transition [world game-map action]
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

(defn demo-on-look-move
  "Look-mode callback: renders the game with cursor info."
  [ctx game-map cx cy look-info]
  (let [{:keys [canvas-display viewport explored registry]} ctx
        player (core/get-player game-map)
        [px py] (core/entity-pos player)
        vp (-> viewport
               (display/center-viewport-on px py)
               (display/clamp-to-map game-map))
        fov (core/compute-entity-fov registry game-map player)]
    (render-look-frame canvas-display game-map vp cx cy look-info fov explored registry)))

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

(defn game-loop
  "Main game loop using core.async. Returns a channel."
  [canvas-disp initial-world base-ctx initial-explored]
  (let [registry (:registry base-ctx)]
    (go (loop [world initial-world
               message nil
               explored (or initial-explored {})]
          (let [map-id (core/current-map-id world)
                game-map (core/current-map world)
                player (core/get-player game-map)]
            (if-not player
              :dead
              (let [fov (core/compute-entity-fov registry game-map player)
                    map-explored (into (get explored map-id #{}) fov)
                    explored (assoc explored map-id map-explored)
                    vp (center-viewport-on-player game-map (:viewport base-ctx))
                    level-msg (when message (str "[" (name map-id) "] " message))]
                (render-game canvas-disp game-map vp level-msg fov map-explored registry)
                (let [ctx (assoc base-ctx :explored map-explored)
                      result (<! (core/process-actors game-map ctx))
                      {:keys [map quit message action effect]} result]
                  (when effect
                    (<! (canvas/play-effect-async canvas-disp effect vp
                          {:render-base-fn (fn [disp]
                                             (render-game disp map vp nil fov map-explored registry))})))
                  (cond
                    quit :quit

                    (nil? (core/get-player map))
                    (do
                      (render-game canvas-disp map vp
                                   (str "[" (name map-id) "] " (or message "You die..."))
                                   fov map-explored registry)
                      :dead)

                    (= :save action)
                    (let [updated-world (core/set-current-map world map)]
                      (io/save-world save-key updated-world
                                     {:explored explored
                                      :viewport (:viewport base-ctx)})
                      :saved)

                    (#{:descend :ascend} action)
                    (let [[new-world msg] (handle-transition world map action)]
                      (if new-world
                        (recur new-world msg explored)
                        (recur world msg explored)))

                    :else
                    (recur (core/set-current-map world map) message explored))))))))))

(defn- load-saved-game []
  (when (io/save-exists? save-key)
    (try
      (let [restored (io/load-save save-key)]
        (if (= 2 (:version restored))
          {:world (:world restored)
           :explored (:explored restored)}
          (let [game-map (:game-map restored)
                world (core/create-world {:level-1 game-map} :level-1)]
            {:world world
             :explored {:level-1 (or (:explored restored) #{})}})))
      (catch :default e
        (js/console.error "Failed to load save:" e)
        nil))))

(defn- start-game
  "Starts a game session. Returns a channel that completes when the session ends."
  [canvas-disp input-fn base-ctx]
  (let [loaded (load-saved-game)
        world (or (:world loaded) (create-demo-world))
        explored (or (:explored loaded) {})]
    (go (let [result (<! (game-loop canvas-disp world base-ctx explored))
              msg (case result
                    :saved "Game saved. Press any key to continue..."
                    :dead "You died! Game over. Press any key to restart..."
                    "Demo ended. Press any key to restart...")]
          (when (not= :saved result)
            (io/delete-save save-key))
          (display/display-message canvas-disp msg)
          (<! (input-fn))
          result))))

(defn ^:export init!
  "Entry point for the browser demo."
  []
  (let [canvas-el (.getElementById js/document "game-canvas")]
    (when canvas-el
      (let [registry (create-demo-registry)
            viewport (display/create-viewport 50 25)
            {:keys [display input-fn]} (canvas/create-canvas-display viewport canvas-el)
            base-ctx {:input-fn input-fn
                      :key-map demo-key-map
                      :registry registry
                      :viewport viewport
                      :canvas-display display
                      :on-look-move demo-on-look-move
                      :look-bounds-fn demo-look-bounds-fn
                      :on-bump basics/combat-on-bump
                      :on-bump-tile basics/door-on-bump-tile
                      :on-ranged-attack basics/ranged-on-target
                      :pass-through-actions #{:save :descend :ascend}}]
        (go (loop []
              (<! (start-game display input-fn base-ctx))
              (recur)))))))

(defn on-reload
  "Called by shadow-cljs after hot reload."
  []
  (js/console.log "YARF reloaded"))
