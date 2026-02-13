(ns yarf.core-test
  (:require [clojure.test :refer :all]
            [yarf.core :refer :all]
            [yarf.display :as display]))

(defn mock-display
  "Creates a mock display that returns the given input."
  [input-atom]
  (reify display/Display
    (get-input [_] @input-atom)
    (render-tile [_ _ _ _] nil)
    (render-entity [_ _ _ _] nil)
    (clear-screen [_] nil)
    (refresh-screen [_] nil)
    (start-display [this] this)
    (stop-display [_] nil)))

(deftest create-tile-map-test
  (testing "creates a tile map with specified dimensions"
    (let [m (create-tile-map 10 8)]
      (is (= 10 (map-width m)))
      (is (= 8 (map-height m))))))

(deftest get-tile-test
  (testing "returns default tile for empty map"
    (let [m (create-tile-map 5 5)]
      (is (= :floor (:type (get-tile m 0 0))))
      (is (= :floor (:type (get-tile m 4 4)))))))

(deftest set-tile-test
  (testing "sets a tile at coordinates"
    (let [m (create-tile-map 5 5)
          m2 (set-tile m 2 3 {:type :wall})]
      (is (= :wall (:type (get-tile m2 2 3))))
      (is (= :floor (:type (get-tile m2 0 0)))))))

(deftest bounds-check-test
  (testing "in-bounds? returns true for valid coordinates"
    (let [m (create-tile-map 10 10)]
      (is (in-bounds? m 0 0))
      (is (in-bounds? m 9 9))
      (is (in-bounds? m 5 5))))
  (testing "in-bounds? returns false for invalid coordinates"
    (let [m (create-tile-map 10 10)]
      (is (not (in-bounds? m -1 0)))
      (is (not (in-bounds? m 0 -1)))
      (is (not (in-bounds? m 10 0)))
      (is (not (in-bounds? m 0 10))))))

(deftest tile-types-test
  (testing "predefined tile types have correct properties"
    (is (= :floor (:type floor-tile)))
    (is (= :wall (:type wall-tile)))
    (is (= :door-closed (:type door-closed-tile)))
    (is (= :door-open (:type door-open-tile)))
    (is (= :water (:type water-tile)))))

(deftest tile-char-test
  (testing "predefined tiles have display characters"
    (is (= \. (tile-char floor-tile)))
    (is (= \# (tile-char wall-tile)))
    (is (= \+ (tile-char door-closed-tile)))
    (is (= \/ (tile-char door-open-tile)))
    (is (= \~ (tile-char water-tile))))
  (testing "custom tiles can specify display character"
    (let [t (make-tile :lava \* :red {:walkable false})]
      (is (= \* (tile-char t)))))
  (testing "returns ? for tiles without char"
    (is (= \? (tile-char {:type :unknown})))))

(deftest tile-color-test
  (testing "predefined tiles have display colors"
    (is (= :white (tile-color floor-tile)))
    (is (= :white (tile-color wall-tile)))
    (is (= :yellow (tile-color door-closed-tile)))
    (is (= :yellow (tile-color door-open-tile)))
    (is (= :blue (tile-color water-tile))))
  (testing "custom tiles can specify display color"
    (let [t (make-tile :lava \* :red {:walkable false})]
      (is (= :red (tile-color t)))))
  (testing "returns :white for tiles without color"
    (is (= :white (tile-color {:type :unknown})))))

(deftest tile-walkable-test
  (testing "walkable? returns correct values for tile types"
    (let [e (create-entity :player \@ :white 5 5)]
      (is (walkable? e floor-tile))
      (is (not (walkable? e wall-tile)))
      (is (not (walkable? e door-closed-tile)))
      (is (walkable? e door-open-tile))
      (is (not (walkable? e water-tile)))))
  (testing "entities with :can-swim can walk on water"
    (let [fish (create-entity :fish \f :blue 5 5 {:can-swim true})
          player (create-entity :player \@ :white 5 5)]
      (is (walkable? fish water-tile))
      (is (not (walkable? player water-tile)))))
  (testing "special abilities don't override unwalkable solid tiles"
    (let [fish (create-entity :fish \f :blue 5 5 {:can-swim true})]
      (is (not (walkable? fish wall-tile))))))

(deftest tile-transparent-test
  (testing "transparent? returns correct values for tile types"
    (is (transparent? floor-tile))
    (is (not (transparent? wall-tile)))
    (is (not (transparent? door-closed-tile)))
    (is (transparent? door-open-tile))
    (is (transparent? water-tile))))

(deftest make-tile-test
  (testing "make-tile creates tile with specified properties"
    (let [t (make-tile :lava \* :red {:walkable false :transparent true :damage 10})
          e (create-entity :player \@ :white 5 5)]
      (is (= :lava (:type t)))
      (is (= \* (tile-char t)))
      (is (= :red (tile-color t)))
      (is (not (walkable? e t)))
      (is (transparent? t))
      (is (= 10 (:damage t))))))

;; Type definition tests

(deftest define-tile-type-test
  (testing "can define a tile type with description and lore"
    (let [registry (-> (create-type-registry)
                       (define-tile-type :wall
                         {:description "A solid stone wall"
                          :lore "These ancient walls have stood for centuries."}))]
      (is (= "A solid stone wall" (get-type-property registry :tile :wall :description)))
      (is (= "These ancient walls have stood for centuries." (get-type-property registry :tile :wall :lore)))))
  (testing "tile type can have custom properties"
    (let [registry (-> (create-type-registry)
                       (define-tile-type :lava
                         {:description "Molten rock"
                          :damage-per-turn 5
                          :element :fire}))]
      (is (= 5 (get-type-property registry :tile :lava :damage-per-turn)))
      (is (= :fire (get-type-property registry :tile :lava :element))))))

(deftest define-entity-type-test
  (testing "can define an entity type with description and lore"
    (let [registry (-> (create-type-registry)
                       (define-entity-type :goblin
                         {:description "A small, green-skinned creature"
                          :lore "Goblins are known for their cunning and cowardice."}))]
      (is (= "A small, green-skinned creature" (get-type-property registry :entity :goblin :description)))
      (is (= "Goblins are known for their cunning and cowardice." (get-type-property registry :entity :goblin :lore)))))
  (testing "entity type can have custom properties"
    (let [registry (-> (create-type-registry)
                       (define-entity-type :dragon
                         {:description "A fearsome winged beast"
                          :base-hp 100
                          :species :reptile
                          :family :draconic}))]
      (is (= 100 (get-type-property registry :entity :dragon :base-hp)))
      (is (= :reptile (get-type-property registry :entity :dragon :species)))
      (is (= :draconic (get-type-property registry :entity :dragon :family))))))

(deftest get-instance-type-test
  (testing "can get type properties from a tile instance"
    (let [registry (-> (create-type-registry)
                       (define-tile-type :floor
                         {:description "A stone floor"
                          :lore "Worn smooth by countless footsteps."}))
          tile floor-tile]
      (is (= "A stone floor" (get-instance-type-property registry tile :description)))))
  (testing "can get type properties from an entity instance"
    (let [registry (-> (create-type-registry)
                       (define-entity-type :player
                         {:description "The hero of our story"
                          :lore "A brave adventurer seeking fortune and glory."}))
          player (create-player 5 5)]
      (is (= "The hero of our story" (get-instance-type-property registry player :description)))))
  (testing "returns nil for undefined type properties"
    (let [registry (create-type-registry)
          player (create-player 5 5)]
      (is (nil? (get-instance-type-property registry player :description))))))

(deftest instance-property-fallback-test
  (testing "instance properties override type properties"
    (let [registry (-> (create-type-registry)
                       (define-entity-type :goblin
                         {:description "A small goblin"
                          :base-hp 10}))
          ;; This goblin has a custom description
          special-goblin (create-entity :goblin \g :green 0 0 {:description "A goblin chieftain"})]
      (is (= "A goblin chieftain" (get-property registry special-goblin :description)))
      (is (= 10 (get-property registry special-goblin :base-hp)))))
  (testing "falls back to type when instance lacks property"
    (let [registry (-> (create-type-registry)
                       (define-entity-type :orc
                         {:description "A brutish orc"
                          :base-hp 20}))
          orc (create-entity :orc \o :green 0 0)]
      (is (= "A brutish orc" (get-property registry orc :description)))
      (is (= 20 (get-property registry orc :base-hp)))))
  (testing "works for tiles too"
    (let [registry (-> (create-type-registry)
                       (define-tile-type :floor
                         {:description "Stone floor"
                          :material :stone}))
          custom-floor (make-tile :floor \. :white {:walkable true :description "Marble floor"})]
      (is (= "Marble floor" (get-property registry custom-floor :description)))
      (is (= :stone (get-property registry custom-floor :material))))))

(deftest type-inheritance-test
  (testing "types can have a parent type"
    (let [registry (-> (create-type-registry)
                       (define-entity-type :creature
                         {:category :living
                          :can-die true})
                       (define-entity-type :humanoid
                         {:parent :creature
                          :has-hands true
                          :base-hp 10})
                       (define-entity-type :goblin
                         {:parent :humanoid
                          :description "A small goblin"
                          :base-hp 5}))]
      ;; Direct property
      (is (= "A small goblin" (get-type-property registry :entity :goblin :description)))
      ;; Overridden from parent
      (is (= 5 (get-type-property registry :entity :goblin :base-hp)))
      ;; Inherited from parent
      (is (= true (get-type-property registry :entity :goblin :has-hands)))
      ;; Inherited from grandparent
      (is (= :living (get-type-property registry :entity :goblin :category)))
      (is (= true (get-type-property registry :entity :goblin :can-die)))))
  (testing "inheritance works for tiles"
    (let [registry (-> (create-type-registry)
                       (define-tile-type :door
                         {:description "A door"
                          :openable true})
                       (define-tile-type :door-closed
                         {:parent :door
                          :walkable false})
                       (define-tile-type :door-open
                         {:parent :door
                          :walkable true}))]
      (is (= true (get-type-property registry :tile :door-closed :openable)))
      (is (= true (get-type-property registry :tile :door-open :openable)))
      (is (= false (get-type-property registry :tile :door-closed :walkable)))
      (is (= true (get-type-property registry :tile :door-open :walkable)))))
  (testing "get-property uses full inheritance chain"
    (let [registry (-> (create-type-registry)
                       (define-entity-type :creature
                         {:mortal true})
                       (define-entity-type :goblin
                         {:parent :creature
                          :base-hp 5}))
          goblin (create-entity :goblin \g :green 0 0)]
      (is (= true (get-property registry goblin :mortal)))
      (is (= 5 (get-property registry goblin :base-hp))))))

;; Map generation tests

(deftest fill-rect-test
  (testing "fills rectangular region with specified tile"
    (let [m (-> (create-tile-map 10 10)
                (fill-rect 2 2 5 4 wall-tile))]
      (is (= :wall (:type (get-tile m 2 2))))
      (is (= :wall (:type (get-tile m 6 5))))
      (is (= :floor (:type (get-tile m 1 1))))
      (is (= :floor (:type (get-tile m 7 6)))))))

(deftest make-room-test
  (testing "creates a room with floor interior and wall border"
    (let [m (-> (create-tile-map 20 20)
                (fill-rect 0 0 20 20 wall-tile)
                (make-room 5 5 6 4))]
      ;; walls on border
      (is (= :wall (:type (get-tile m 5 5))))
      (is (= :wall (:type (get-tile m 10 8))))
      ;; floor inside
      (is (= :floor (:type (get-tile m 6 6))))
      (is (= :floor (:type (get-tile m 9 7)))))))

(deftest make-corridor-test
  (testing "creates horizontal then vertical corridor between points"
    (let [m (-> (create-tile-map 20 20)
                (fill-rect 0 0 20 20 wall-tile)
                (make-corridor 2 2 8 6))]
      ;; horizontal segment
      (is (= :floor (:type (get-tile m 2 2))))
      (is (= :floor (:type (get-tile m 5 2))))
      (is (= :floor (:type (get-tile m 8 2))))
      ;; vertical segment
      (is (= :floor (:type (get-tile m 8 4))))
      (is (= :floor (:type (get-tile m 8 6)))))))

(deftest generate-test-map-test
  (testing "generates a simple test map with rooms and corridors"
    (let [m (generate-test-map 40 30)]
      (is (= 40 (map-width m)))
      (is (= 30 (map-height m)))
      ;; should have some floor tiles (rooms/corridors)
      (is (some #(= :floor (:type %)) (:tiles m)))
      ;; should have some wall tiles
      (is (some #(= :wall (:type %)) (:tiles m))))))

;; Entity tests

(deftest create-entity-test
  (testing "creates an entity with id, char, color, and position"
    (let [e (create-entity :player \@ :white 5 10)]
      (is (= :player (entity-type e)))
      (is (= \@ (entity-char e)))
      (is (= :white (entity-color e)))
      (is (= 5 (first (entity-pos e))))
      (is (= 10 (second (entity-pos e))))))
  (testing "creates entity with additional properties"
    (let [e (create-entity :goblin \g :green 3 4 {:hp 10 :attack 2})]
      (is (= :goblin (entity-type e)))
      (is (= 10 (:hp e)))
      (is (= 2 (:attack e))))))

(deftest move-entity-test
  (testing "moves entity to new position"
    (let [e (-> (create-entity :player \@ :white 5 5)
                (move-entity 10 15))]
      (is (= 10 (first (entity-pos e))))
      (is (= 15 (second (entity-pos e))))))
  (testing "move-entity-by adds to current position"
    (let [e (-> (create-entity :player \@ :white 5 5)
                (move-entity-by 2 -1))]
      (is (= 7 (first (entity-pos e))))
      (is (= 4 (second (entity-pos e)))))))

(deftest boundary-check-test
  (testing "entities cannot move off left edge"
    (let [e (create-entity :player \@ :white 0 5)
          m (-> (create-tile-map 10 10)
                (add-entity e))
          m2 (:map (execute-action :move-left e m))]
      (is (= 0 (first (entity-pos (first (get-entities m2))))))))
  (testing "entities cannot move off right edge"
    (let [e (create-entity :player \@ :white 9 5)
          m (-> (create-tile-map 10 10)
                (add-entity e))
          m2 (:map (execute-action :move-right e m))]
      (is (= 9 (first (entity-pos (first (get-entities m2))))))))
  (testing "entities cannot move off top edge"
    (let [e (create-entity :player \@ :white 5 0)
          m (-> (create-tile-map 10 10)
                (add-entity e))
          m2 (:map (execute-action :move-up e m))]
      (is (= 0 (second (entity-pos (first (get-entities m2))))))))
  (testing "entities cannot move off bottom edge"
    (let [e (create-entity :player \@ :white 5 9)
          m (-> (create-tile-map 10 10)
                (add-entity e))
          m2 (:map (execute-action :move-down e m))]
      (is (= 9 (second (entity-pos (first (get-entities m2))))))))
  (testing "diagonal movement respects boundaries"
    (let [e (create-entity :player \@ :white 0 0)
          m (-> (create-tile-map 10 10)
                (add-entity e))
          m2 (:map (execute-action :move-up-left e m))]
      (is (= 0 (first (entity-pos (first (get-entities m2))))))
      (is (= 0 (second (entity-pos (first (get-entities m2)))))))))

(deftest solid-tile-collision-test
  (testing "entities cannot move into wall tiles"
    (let [e (create-entity :player \@ :white 5 5)
          m (-> (create-tile-map 10 10)
                (set-tile 5 4 wall-tile)
                (add-entity e))
          m2 (:map (execute-action :move-up e m))]
      (is (= 5 (first (entity-pos (first (get-entities m2))))))
      (is (= 5 (second (entity-pos (first (get-entities m2))))))))
  (testing "entities can move into floor tiles"
    (let [e (create-entity :player \@ :white 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity e))
          m2 (:map (execute-action :move-up e m))]
      (is (= 5 (first (entity-pos (first (get-entities m2))))))
      (is (= 4 (second (entity-pos (first (get-entities m2))))))))
  (testing "entities cannot move diagonally into walls"
    (let [e (create-entity :player \@ :white 5 5)
          m (-> (create-tile-map 10 10)
                (set-tile 4 4 wall-tile)
                (add-entity e))
          m2 (:map (execute-action :move-up-left e m))]
      (is (= 5 (first (entity-pos (first (get-entities m2))))))
      (is (= 5 (second (entity-pos (first (get-entities m2))))))))
  (testing "entities can move into open doors"
    (let [e (create-entity :player \@ :white 5 5)
          m (-> (create-tile-map 10 10)
                (set-tile 5 4 door-open-tile)
                (add-entity e))
          m2 (:map (execute-action :move-up e m))]
      (is (= 4 (second (entity-pos (first (get-entities m2))))))))
  (testing "entities cannot move into closed doors"
    (let [e (create-entity :player \@ :white 5 5)
          m (-> (create-tile-map 10 10)
                (set-tile 5 4 door-closed-tile)
                (add-entity e))
          m2 (:map (execute-action :move-up e m))]
      (is (= 5 (second (entity-pos (first (get-entities m2))))))))
  (testing "entities with :can-swim can move into water"
    (let [fish (create-entity :fish \f :blue 5 5 {:can-swim true})
          m (-> (create-tile-map 10 10)
                (set-tile 5 4 water-tile)
                (add-entity fish))
          result (try-move m fish 0 -1)]
      (is (= 4 (second (entity-pos (first (get-entities (:map result)))))))))
  (testing "entities without :can-swim cannot move into water"
    (let [player (create-entity :player \@ :white 5 5)
          m (-> (create-tile-map 10 10)
                (set-tile 5 4 water-tile)
                (add-entity player))
          result (try-move m player 0 -1)]
      (is (= 5 (second (entity-pos (first (get-entities (:map result)))))))))
  (testing "failed moves set :no-time and :retry flags"
    (let [player (create-entity :player \@ :white 5 5)
          m (-> (create-tile-map 10 10)
                (set-tile 5 4 wall-tile)
                (add-entity player))
          result (try-move m player 0 -1)]
      (is (:no-time result))
      (is (:retry result))))
  (testing "successful moves don't set retry flag"
    (let [player (create-entity :player \@ :white 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity player))
          result (try-move m player 0 -1)]
      (is (nil? (:retry result))))))

(deftest map-entities-test
  (testing "new map has no entities"
    (let [m (create-tile-map 10 10)]
      (is (empty? (get-entities m)))))
  (testing "add-entity adds entity to map"
    (let [e (create-entity :player \@ :white 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity e))]
      (is (= 1 (count (get-entities m))))
      (is (= e (first (get-entities m))))))
  (testing "remove-entity removes entity from map"
    (let [e (create-entity :player \@ :white 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity e)
                (remove-entity e))]
      (is (empty? (get-entities m))))))

(deftest get-entities-at-test
  (testing "returns entities at specified position"
    (let [player (create-entity :player \@ :white 5 5)
          goblin (create-entity :goblin \g :green 5 5)
          orc (create-entity :orc \o :green 3 3)
          m (-> (create-tile-map 10 10)
                (add-entity player)
                (add-entity goblin)
                (add-entity orc))]
      (is (= 2 (count (get-entities-at m 5 5))))
      (is (= 1 (count (get-entities-at m 3 3))))
      (is (empty? (get-entities-at m 0 0))))))

(deftest update-entity-test
  (testing "updates entity in map"
    (let [player (create-entity :player \@ :white 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity player))
          m2 (update-entity m player move-entity 10 10)
          updated (first (get-entities-at m2 10 10))]
      (is (= 10 (first (entity-pos updated))))
      (is (= 10 (second (entity-pos updated))))
      (is (empty? (get-entities-at m2 5 5))))))

;; Player entity tests

(deftest create-player-test
  (testing "creates player entity with standard properties"
    (let [p (create-player 5 10)]
      (is (= :player (entity-type p)))
      (is (= \@ (entity-char p)))
      (is (= :yellow (entity-color p)))
      (is (= 5 (first (entity-pos p))))
      (is (= 10 (second (entity-pos p))))))
  (testing "player can be added to map"
    (let [p (create-player 3 3)
          m (-> (create-tile-map 10 10)
                (add-entity p))]
      (is (= 1 (count (get-entities m))))
      (is (= p (first (get-entities-at m 3 3)))))))

(deftest get-player-test
  (testing "retrieves player from map"
    (let [p (create-player 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity p))]
      (is (= p (get-player m)))))
  (testing "returns nil if no player in map"
    (let [m (create-tile-map 10 10)]
      (is (nil? (get-player m))))))

;; Entity act function tests

(deftest entity-act-test
  (testing "entities can have an act function"
    (let [wander-fn (fn [entity game-map]
                      {:map (update-entity game-map entity move-entity-by 1 0)})
          e (create-entity :goblin \g :green 5 5 {:act wander-fn})
          m (-> (create-tile-map 10 10)
                (add-entity e))]
      (is (can-act? e))
      (is (not (can-act? (create-player 0 0))))))
  (testing "act-entity calls entity's act function"
    (let [wander-fn (fn [entity game-map]
                      {:map (update-entity game-map entity move-entity-by 1 0)})
          e (create-entity :goblin \g :green 5 5 {:act wander-fn})
          m (-> (create-tile-map 10 10)
                (add-entity e))
          result (act-entity m e)]
      (is (= 6 (first (entity-pos (first (get-entities (:map result)))))))))
  (testing "act-entity returns unchanged map for entities without act"
    (let [p (create-player 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity p))
          result (act-entity m p)]
      (is (= m (:map result))))))

(deftest process-actors-test
  (testing "processes all entities with act functions"
    (let [move-right (fn [entity game-map]
                       {:map (update-entity game-map entity move-entity-by 1 0)})
          e1 (create-entity :goblin \g :green 3 3 {:act move-right})
          e2 (create-entity :orc \o :green 7 7 {:act move-right})
          m (-> (create-tile-map 10 10)
                (add-entity e1)
                (add-entity e2))
          result (process-actors m)]
      (is (= 1 (count (get-entities-at (:map result) 4 3))))
      (is (= 1 (count (get-entities-at (:map result) 8 7)))))))

;; Action timing tests

(deftest entity-delay-test
  (testing "entities can have a delay property"
    (let [fast (create-entity :rat \r :white 0 0 {:delay 5})
          slow (create-entity :turtle \t :green 0 0 {:delay 20})]
      (is (= 5 (entity-delay fast)))
      (is (= 20 (entity-delay slow)))))
  (testing "entities without delay default to 10"
    (let [e (create-entity :goblin \g :green 0 0)]
      (is (= 10 (entity-delay e))))))

(deftest entity-next-action-test
  (testing "entities default to next-action of 0"
    (let [e (create-entity :goblin \g :green 0 0)]
      (is (= 0 (entity-next-action e)))))
  (testing "entities can have custom next-action"
    (let [e (create-entity :goblin \g :green 0 0 {:next-action 50})]
      (is (= 50 (entity-next-action e))))))

(deftest act-increments-next-action-test
  (testing "acting increments next-action by delay"
    (let [act-fn (fn [entity game-map]
                   {:map (update-entity game-map entity move-entity-by 1 0)})
          e (create-entity :goblin \g :green 5 5 {:act act-fn :delay 15})
          m (-> (create-tile-map 10 10)
                (add-entity e))
          result (act-entity m e)
          updated (first (get-entities (:map result)))]
      (is (= 15 (entity-next-action updated)))))
  (testing "acting uses default delay when not specified"
    (let [act-fn (fn [entity game-map]
                   {:map (update-entity game-map entity move-entity-by 1 0)})
          e (create-entity :goblin \g :green 5 5 {:act act-fn})
          m (-> (create-tile-map 10 10)
                (add-entity e))
          result (act-entity m e)
          updated (first (get-entities (:map result)))]
      (is (= 10 (entity-next-action updated)))))
  (testing "action can specify custom :time-cost"
    (let [act-fn (fn [entity game-map]
                   {:map (update-entity game-map entity move-entity-by 1 0)
                    :time-cost 5})
          e (create-entity :goblin \g :green 5 5 {:act act-fn :delay 15})
          m (-> (create-tile-map 10 10)
                (add-entity e))
          result (act-entity m e)
          updated (first (get-entities (:map result)))]
      (is (= 5 (entity-next-action updated)))))
  (testing ":time-cost overrides entity delay"
    (let [slow-action (fn [entity game-map]
                        {:map (update-entity game-map entity move-entity-by 1 0)
                         :time-cost 50})
          fast-entity (create-entity :rat \r :white 5 5 {:act slow-action :delay 2})
          m (-> (create-tile-map 10 10)
                (add-entity fast-entity))
          result (act-entity m fast-entity)
          updated (first (get-entities (:map result)))]
      ;; Even though entity has delay 2, action took 50
      (is (= 50 (entity-next-action updated))))))

(deftest next-actor-test
  (testing "get-next-actor returns entity with lowest next-action"
    (let [e1 (create-entity :goblin \g :green 0 0 {:act identity :next-action 20})
          e2 (create-entity :orc \o :green 1 1 {:act identity :next-action 5})
          e3 (create-entity :troll \T :green 2 2 {:act identity :next-action 15})
          m (-> (create-tile-map 10 10)
                (add-entity e1)
                (add-entity e2)
                (add-entity e3))]
      (is (= :orc (entity-type (get-next-actor m))))))
  (testing "get-next-actor returns nil when no actors"
    (let [e (create-entity :rock \* :gray 0 0)  ;; no act function
          m (-> (create-tile-map 10 10)
                (add-entity e))]
      (is (nil? (get-next-actor m)))))
  (testing "get-next-actor only considers entities with act functions"
    (let [rock (create-entity :rock \* :gray 0 0 {:next-action 0})  ;; lowest but can't act
          goblin (create-entity :goblin \g :green 1 1 {:act identity :next-action 10})
          m (-> (create-tile-map 10 10)
                (add-entity rock)
                (add-entity goblin))]
      (is (= :goblin (entity-type (get-next-actor m)))))))

(deftest process-next-actor-test
  (testing "process-next-actor processes only the entity with lowest next-action"
    (let [move-right (fn [entity game-map]
                       {:map (update-entity game-map entity move-entity-by 1 0)})
          fast (create-entity :rat \r :white 3 3 {:act move-right :delay 5 :next-action 0})
          slow (create-entity :turtle \t :green 7 7 {:act move-right :delay 20 :next-action 10})
          m (-> (create-tile-map 10 10)
                (add-entity fast)
                (add-entity slow))
          result (process-next-actor m)
          m2 (:map result)]
      ;; Only fast moved (it had lower next-action)
      (is (= 1 (count (get-entities-at m2 4 3))))  ;; rat moved
      (is (= 1 (count (get-entities-at m2 7 7))))  ;; turtle stayed
      ;; Fast's next-action was incremented
      (let [rat (first (filter #(= :rat (entity-type %)) (get-entities m2)))]
        (is (= 5 (entity-next-action rat)))))))

;; Player input tests

(deftest player-with-display-test
  (testing "player gets input from display"
    (let [input-atom (atom \l)
          d (mock-display input-atom)
          p (display/create-player-with-display 5 5 d)
          m (-> (create-tile-map 10 10)
                (add-entity p))
          result (act-entity m p)]
      (is (= 6 (first (entity-pos (get-player (:map result))))))))
  (testing "player responds to vi-style hjkl inputs"
    (let [input-atom (atom \k)
          d (mock-display input-atom)
          p (display/create-player-with-display 5 5 d)
          m (-> (create-tile-map 10 10)
                (add-entity p))]
      ;; k = up
      (reset! input-atom \k)
      (let [result (act-entity m p)]
        (is (= 4 (second (entity-pos (get-player (:map result)))))))
      ;; j = down
      (reset! input-atom \j)
      (let [result (act-entity m p)]
        (is (= 6 (second (entity-pos (get-player (:map result)))))))
      ;; h = left
      (reset! input-atom \h)
      (let [result (act-entity m p)]
        (is (= 4 (first (entity-pos (get-player (:map result)))))))
      ;; l = right
      (reset! input-atom \l)
      (let [result (act-entity m p)]
        (is (= 6 (first (entity-pos (get-player (:map result)))))))))
  (testing "player responds to diagonal yubn inputs"
    (let [input-atom (atom \y)
          d (mock-display input-atom)
          p (display/create-player-with-display 5 5 d)
          m (-> (create-tile-map 10 10)
                (add-entity p))]
      ;; y = up-left
      (reset! input-atom \y)
      (let [result (act-entity m p)
            player (get-player (:map result))]
        (is (= 4 (first (entity-pos player))))
        (is (= 4 (second (entity-pos player)))))
      ;; u = up-right
      (reset! input-atom \u)
      (let [result (act-entity m p)
            player (get-player (:map result))]
        (is (= 6 (first (entity-pos player))))
        (is (= 4 (second (entity-pos player)))))
      ;; b = down-left
      (reset! input-atom \b)
      (let [result (act-entity m p)
            player (get-player (:map result))]
        (is (= 4 (first (entity-pos player))))
        (is (= 6 (second (entity-pos player)))))
      ;; n = down-right
      (reset! input-atom \n)
      (let [result (act-entity m p)
            player (get-player (:map result))]
        (is (= 6 (first (entity-pos player))))
        (is (= 6 (second (entity-pos player)))))))
  (testing "player retries on unknown input until valid input"
    (let [inputs (atom [:unknown :invalid \l])  ;; two invalid, then valid
          input-fn #(let [i (first @inputs)]
                      (swap! inputs rest)
                      i)
          p (create-entity :player \@ :yellow 5 5
                           {:act (make-player-act input-fn default-key-map)})
          m (-> (create-tile-map 10 10)
                (add-entity p))
          result (act-entity m p)]
      ;; Should have moved right (the \l input) after skipping invalid inputs
      (is (= 6 (first (entity-pos (get-player (:map result))))))
      (is (= 5 (second (entity-pos (get-player (:map result))))))))
  (testing "player retries on blocked movement until valid move"
    (let [inputs (atom [\k \k \l])  ;; two blocked moves (into wall), then valid
          input-fn #(let [i (first @inputs)]
                      (swap! inputs rest)
                      i)
          p (create-entity :player \@ :yellow 5 5
                           {:act (make-player-act input-fn default-key-map)})
          m (-> (create-tile-map 10 10)
                (set-tile 5 4 wall-tile)  ;; wall above player
                (add-entity p))
          result (act-entity m p)]
      ;; Should have moved right after failing to move up twice
      (is (= 6 (first (entity-pos (get-player (:map result))))))
      (is (= 5 (second (entity-pos (get-player (:map result))))))))
  (testing "process-actors includes player with display"
    (let [input-atom (atom \l)
          d (mock-display input-atom)
          p (display/create-player-with-display 5 5 d)
          move-right (fn [entity game-map]
                       {:map (update-entity game-map entity move-entity-by 1 0)})
          goblin (create-entity :goblin \g :green 3 3 {:act move-right})
          m (-> (create-tile-map 10 10)
                (add-entity p)
                (add-entity goblin))
          result (process-actors m)]
      ;; both moved
      (is (= 6 (first (entity-pos (get-player (:map result))))))
      (is (= 1 (count (get-entities-at (:map result) 4 3)))))))

;; Custom key mapping tests

(deftest default-key-map-test
  (testing "default-key-map has vi-style hjkl bindings"
    (is (= :move-left (default-key-map \h)))
    (is (= :move-down (default-key-map \j)))
    (is (= :move-up (default-key-map \k)))
    (is (= :move-right (default-key-map \l))))
  (testing "default-key-map has diagonal yubn bindings"
    (is (= :move-up-left (default-key-map \y)))
    (is (= :move-up-right (default-key-map \u)))
    (is (= :move-down-left (default-key-map \b)))
    (is (= :move-down-right (default-key-map \n)))))

(deftest custom-key-map-test
  (testing "player can use custom key mappings"
    (let [wasd-keys {\w :move-up
                     \s :move-down
                     \a :move-left
                     \d :move-right}
          input-atom (atom \w)
          d (mock-display input-atom)
          p (display/create-player-with-display 5 5 d wasd-keys)
          m (-> (create-tile-map 10 10)
                (add-entity p))]
      ;; w = up
      (let [result (act-entity m p)]
        (is (= 4 (second (entity-pos (get-player (:map result)))))))
      ;; d = right
      (reset! input-atom \d)
      (let [result (act-entity m p)]
        (is (= 6 (first (entity-pos (get-player (:map result)))))))))
  (testing "custom key map can include quit action"
    (let [custom-keys {\q :quit
                       :escape :quit
                       :up :move-up}
          input-atom (atom \q)
          d (mock-display input-atom)
          p (display/create-player-with-display 5 5 d custom-keys)
          m (-> (create-tile-map 10 10)
                (add-entity p))
          result (act-entity m p)]
      (is (:quit result)))))

;; Look action tests

(deftest default-key-map-look-test
  (testing "default-key-map has x for look"
    (is (= :look (default-key-map \x)))))

(deftest get-name-test
  (testing "get-name returns name from type registry"
    (let [registry (-> (create-type-registry)
                       (define-entity-type :goblin {:name "Goblin"}))
          goblin (create-entity :goblin \g :green 0 0)]
      (is (= "Goblin" (get-name registry goblin)))))
  (testing "get-name returns instance name if present"
    (let [registry (-> (create-type-registry)
                       (define-entity-type :goblin {:name "Goblin"}))
          named-goblin (create-entity :goblin \g :green 0 0 {:name "Grishnak"})]
      (is (= "Grishnak" (get-name registry named-goblin)))))
  (testing "get-name falls back to type key if no name defined"
    (let [registry (create-type-registry)
          goblin (create-entity :goblin \g :green 0 0)]
      (is (= "goblin" (get-name registry goblin)))))
  (testing "get-name works for tiles"
    (let [registry (-> (create-type-registry)
                       (define-tile-type :floor {:name "Stone Floor"}))]
      (is (= "Stone Floor" (get-name registry floor-tile))))))

(deftest get-description-test
  (testing "get-description returns description from type registry"
    (let [registry (-> (create-type-registry)
                       (define-entity-type :goblin {:description "A small, vicious creature."}))
          goblin (create-entity :goblin \g :green 0 0)]
      (is (= "A small, vicious creature." (get-description registry goblin)))))
  (testing "get-description returns instance description if present"
    (let [registry (-> (create-type-registry)
                       (define-entity-type :goblin {:description "A small goblin."}))
          special (create-entity :goblin \g :green 0 0 {:description "The goblin chieftain, scarred and fierce."})]
      (is (= "The goblin chieftain, scarred and fierce." (get-description registry special)))))
  (testing "get-description returns nil if not defined"
    (let [registry (create-type-registry)
          goblin (create-entity :goblin \g :green 0 0)]
      (is (nil? (get-description registry goblin))))))

(deftest look-at-test
  (testing "look-at returns entity info when entity is present"
    (let [registry (-> (create-type-registry)
                       (define-entity-type :goblin {:name "Goblin" :description "A small goblin."})
                       (define-tile-type :floor {:name "Floor"}))
          goblin (create-entity :goblin \g :green 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity goblin))
          info (look-at registry m 5 5)]
      (is (= "Goblin" (:name info)))
      (is (= "A small goblin." (:description info)))
      (is (= :entity (:category info)))))
  (testing "look-at returns tile info when no entity present"
    (let [registry (-> (create-type-registry)
                       (define-tile-type :floor {:name "Stone Floor" :description "Cold grey stone."}))
          m (create-tile-map 10 10)
          info (look-at registry m 5 5)]
      (is (= "Stone Floor" (:name info)))
      (is (= "Cold grey stone." (:description info)))
      (is (= :tile (:category info)))))
  (testing "look-at returns topmost entity when multiple present"
    (let [registry (-> (create-type-registry)
                       (define-entity-type :goblin {:name "Goblin"})
                       (define-entity-type :item {:name "Gold Coin"}))
          goblin (create-entity :goblin \g :green 5 5)
          coin (create-entity :item \$ :yellow 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity coin)
                (add-entity goblin))  ;; goblin added last, on top
          info (look-at registry m 5 5)]
      (is (= "Goblin" (:name info))))))

(deftest look-action-no-time-test
  (testing "look action does not increment next-action"
    (let [player (create-entity :player \@ :yellow 5 5 {:next-action 0 :delay 10})
          m (-> (create-tile-map 10 10)
                (add-entity player))
          result (execute-action :look player m)
          updated (get-player (:map result))]
      (is (= 0 (entity-next-action updated))))))
