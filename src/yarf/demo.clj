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

;; Type registry for demo
(def demo-registry
  "Type definitions for demo entities and tiles."
  (-> (core/create-type-registry)
      (core/define-tile-type :floor
        {:name "Stone Floor"
         :description "Cold grey flagstones, worn smooth by countless feet."})
      (core/define-tile-type :wall
        {:name "Stone Wall"
         :description "A solid wall of ancient stonework. It looks impenetrable."})
      (core/define-tile-type :water
        {:name "Water"
         :description "Dark water of unknown depth. You'd rather not swim in it."})
      (core/define-tile-type :door-open
        {:name "Open Door"
         :description "A wooden door, hanging open on rusty hinges."})
      (core/define-tile-type :door-closed
        {:name "Closed Door"
         :description "A sturdy wooden door, firmly shut."})
      (core/define-entity-type :player
        {:name "Yourself"
         :description "A brave adventurer, perhaps foolhardy, delving into the unknown."})
      (core/define-entity-type :goblin
        {:name "Goblin"
         :description "A small, green-skinned creature with beady eyes and sharp teeth. It moves erratically."})))

(defn create-demo-player
  "Creates a player for the demo with vi-style movement and quit."
  [x y screen]
  (core/create-entity :player \@ :yellow x y
                      {:act (core/make-player-act #(s/get-key-blocking screen) demo-key-map)}))

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
  ([screen game-map viewport]
   (render-game screen game-map viewport nil nil))
  ([screen game-map viewport cursor-x cursor-y]
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
     ;; Render cursor if in look mode
     (when (and cursor-x cursor-y)
       (let [sx (- cursor-x offset-x)
             sy (- cursor-y offset-y)]
         (when (and (>= sx 0) (< sx width)
                    (>= sy 0) (< sy height))
           ;; Draw cursor as inverted colors
           (let [entities (core/get-entities-at game-map cursor-x cursor-y)
                 tile (core/get-tile game-map cursor-x cursor-y)
                 ch (if (seq entities)
                      (core/entity-char (last entities))
                      (core/tile-char tile))]
             (s/put-string screen sx sy (str ch) {:fg :black :bg :white}))))))))

(defn render-status-line
  "Renders a status line at the bottom of the viewport."
  [screen viewport message]
  (let [{:keys [width height]} viewport]
    (s/put-string screen 0 height (apply str (repeat width \space)))
    (s/put-string screen 0 height message {:fg :white})))

(defn show-description
  "Shows a full description, waiting for a key press to dismiss."
  [screen viewport description]
  (let [{:keys [width height]} viewport
        ;; Word wrap the description
        words (clojure.string/split (or description "No description available.") #"\s+")
        lines (loop [words words
                     current-line ""
                     lines []]
                (if (empty? words)
                  (if (empty? current-line) lines (conj lines current-line))
                  (let [word (first words)
                        test-line (if (empty? current-line) word (str current-line " " word))]
                    (if (> (count test-line) (- width 2))
                      (recur (rest words) word (conj lines current-line))
                      (recur (rest words) test-line lines)))))]
    ;; Clear and show description
    (doseq [y (range (min (count lines) 5))]
      (s/put-string screen 0 (+ height y 1) (apply str (repeat width \space)))
      (s/put-string screen 0 (+ height y 1) (nth lines y "") {:fg :cyan}))
    (s/put-string screen 0 (+ height (min (count lines) 5) 1) "[Press any key]" {:fg :yellow})
    (s/redraw screen)
    (s/get-key-blocking screen)))

(def look-key-map
  "Key bindings for look mode cursor movement."
  {\h [-1 0] \j [0 1] \k [0 -1] \l [1 0]
   \y [-1 -1] \u [1 -1] \b [-1 1] \n [1 1]
   :left [-1 0] :right [1 0] :up [0 -1] :down [0 1]})

(defn look-mode
  "Enters look mode, allowing cursor movement to examine objects.
   Returns when user presses Escape, 'x', or 'q'."
  [screen game-map viewport start-x start-y]
  (loop [cx start-x cy start-y]
    (let [info (core/look-at demo-registry game-map cx cy)
          name-str (:name info)]
      ;; Render game with cursor
      (render-game screen game-map viewport cx cy)
      (render-status-line screen viewport (str "Look: " name-str " [Enter for details, Esc to exit]"))
      (s/redraw screen)
      ;; Get input
      (let [key (s/get-key-blocking screen)]
        (cond
          ;; Exit look mode
          (or (= key :escape) (= key \x) (= key \q))
          nil

          ;; Show description
          (= key :enter)
          (do
            (show-description screen viewport (:description info))
            (recur cx cy))

          ;; Move cursor
          (contains? look-key-map key)
          (let [[dx dy] (look-key-map key)
                new-x (+ cx dx)
                new-y (+ cy dy)]
            (if (core/in-bounds? game-map new-x new-y)
              (recur new-x new-y)
              (recur cx cy)))

          ;; Unknown key, stay in look mode
          :else
          (recur cx cy))))))

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
  (loop [game-map initial-map]
    (let [vp (center-viewport-on-player game-map viewport)]
      (render-game screen game-map vp)
      (let [new-map (core/process-actors game-map)]
        (cond
          (:quit new-map)
          :quit

          (:look-mode new-map)
          (let [player (core/get-player new-map)
                clean-map (dissoc new-map :look-mode)]
            (when player
              (look-mode screen clean-map vp
                         (core/entity-x player)
                         (core/entity-y player)))
            (recur clean-map))

          :else
          (recur new-map))))))

(defn run-demo
  "Runs the demo game."
  []
  (println "Starting YARF demo...")
  (println "Movement: hjkl (vi-style), yubn (diagonals)")
  (println "Look: x (move cursor with hjkl, Enter for details, Esc to exit)")
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
