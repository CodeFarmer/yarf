(ns yarf.core-test
  (:require [clojure.test :refer :all]
            [clojure.edn :as edn]
            [yarf.core :refer :all]))

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
  (let [reg (register-default-tile-types (create-type-registry))]
    (testing "predefined tiles have display characters via registry"
      (is (= \. (tile-char reg floor-tile)))
      (is (= \# (tile-char reg wall-tile)))
      (is (= \+ (tile-char reg door-closed-tile)))
      (is (= \/ (tile-char reg door-open-tile)))
      (is (= \~ (tile-char reg water-tile))))
    (testing "instance char overrides registry"
      (let [t (make-tile :floor {:char \*})]
        (is (= \* (tile-char reg t)))))
    (testing "returns ? for tiles with unknown type"
      (is (= \? (tile-char reg {:type :unknown}))))))

(deftest tile-color-test
  (let [reg (register-default-tile-types (create-type-registry))]
    (testing "predefined tiles have display colors via registry"
      (is (= :white (tile-color reg floor-tile)))
      (is (= :white (tile-color reg wall-tile)))
      (is (= :yellow (tile-color reg door-closed-tile)))
      (is (= :yellow (tile-color reg door-open-tile)))
      (is (= :blue (tile-color reg water-tile))))
    (testing "instance color overrides registry"
      (let [t (make-tile :floor {:color :red})]
        (is (= :red (tile-color reg t)))))
    (testing "returns :white for tiles with unknown type"
      (is (= :white (tile-color reg {:type :unknown}))))))

(deftest tile-walkable-test
  (let [reg (register-default-tile-types (create-type-registry))]
    (testing "walkable? returns correct values for tile types"
      (let [e (create-entity :player \@ :white 5 5)]
        (is (walkable? reg e floor-tile))
        (is (not (walkable? reg e wall-tile)))
        (is (not (walkable? reg e door-closed-tile)))
        (is (walkable? reg e door-open-tile))
        (is (not (walkable? reg e water-tile)))))
    (testing "entities with :can-swim can walk on water"
      (let [fish (create-entity :fish \f :blue 5 5 {:can-swim true})
            player (create-entity :player \@ :white 5 5)]
        (is (walkable? reg fish water-tile))
        (is (not (walkable? reg player water-tile)))))
    (testing "special abilities don't override unwalkable solid tiles"
      (let [fish (create-entity :fish \f :blue 5 5 {:can-swim true})]
        (is (not (walkable? reg fish wall-tile)))))))

(deftest tile-transparent-test
  (let [reg (register-default-tile-types (create-type-registry))]
    (testing "transparent? returns correct values for tile types"
      (is (transparent? reg floor-tile))
      (is (not (transparent? reg wall-tile)))
      (is (not (transparent? reg door-closed-tile)))
      (is (transparent? reg door-open-tile))
      (is (transparent? reg water-tile)))))

(deftest make-tile-test
  (let [reg (register-default-tile-types (create-type-registry))]
    (testing "make-tile creates tile with specified properties"
      (let [t (make-tile :lava {:char \* :color :red :walkable false :transparent true :damage 10})
            e (create-entity :player \@ :white 5 5)]
        (is (= :lava (:type t)))
        (is (= \* (tile-char reg t)))
        (is (= :red (tile-color reg t)))
        (is (not (walkable? reg e t)))
        (is (transparent? reg t))
        (is (= 10 (:damage t)))))
    (testing "make-tile with no properties creates minimal tile"
      (let [t (make-tile :floor)]
        (is (= :floor (:type t)))
        (is (= \. (tile-char reg t)))))))

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
          custom-floor (make-tile :floor {:description "Marble floor"})]
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
  (let [reg (register-default-tile-types (create-type-registry))]
    (testing "entities cannot move off left edge"
      (let [e (create-entity :player \@ :white 0 5)
            m (-> (create-tile-map 10 10)
                  (add-entity e))
            m2 (:map (execute-action reg :move-left e m))]
        (is (= 0 (first (entity-pos (first (get-entities m2))))))))
    (testing "entities cannot move off right edge"
      (let [e (create-entity :player \@ :white 9 5)
            m (-> (create-tile-map 10 10)
                  (add-entity e))
            m2 (:map (execute-action reg :move-right e m))]
        (is (= 9 (first (entity-pos (first (get-entities m2))))))))
    (testing "entities cannot move off top edge"
      (let [e (create-entity :player \@ :white 5 0)
            m (-> (create-tile-map 10 10)
                  (add-entity e))
            m2 (:map (execute-action reg :move-up e m))]
        (is (= 0 (second (entity-pos (first (get-entities m2))))))))
    (testing "entities cannot move off bottom edge"
      (let [e (create-entity :player \@ :white 5 9)
            m (-> (create-tile-map 10 10)
                  (add-entity e))
            m2 (:map (execute-action reg :move-down e m))]
        (is (= 9 (second (entity-pos (first (get-entities m2))))))))
    (testing "diagonal movement respects boundaries"
      (let [e (create-entity :player \@ :white 0 0)
            m (-> (create-tile-map 10 10)
                  (add-entity e))
            m2 (:map (execute-action reg :move-up-left e m))]
        (is (= 0 (first (entity-pos (first (get-entities m2))))))
        (is (= 0 (second (entity-pos (first (get-entities m2))))))))))

(deftest solid-tile-collision-test
  (let [reg (register-default-tile-types (create-type-registry))]
    (testing "entities cannot move into wall tiles"
      (let [e (create-entity :player \@ :white 5 5)
            m (-> (create-tile-map 10 10)
                  (set-tile 5 4 wall-tile)
                  (add-entity e))
            m2 (:map (execute-action reg :move-up e m))]
        (is (= 5 (first (entity-pos (first (get-entities m2))))))
        (is (= 5 (second (entity-pos (first (get-entities m2))))))))
    (testing "entities can move into floor tiles"
      (let [e (create-entity :player \@ :white 5 5)
            m (-> (create-tile-map 10 10)
                  (add-entity e))
            m2 (:map (execute-action reg :move-up e m))]
        (is (= 5 (first (entity-pos (first (get-entities m2))))))
        (is (= 4 (second (entity-pos (first (get-entities m2))))))))
    (testing "entities cannot move diagonally into walls"
      (let [e (create-entity :player \@ :white 5 5)
            m (-> (create-tile-map 10 10)
                  (set-tile 4 4 wall-tile)
                  (add-entity e))
            m2 (:map (execute-action reg :move-up-left e m))]
        (is (= 5 (first (entity-pos (first (get-entities m2))))))
        (is (= 5 (second (entity-pos (first (get-entities m2))))))))
    (testing "entities can move into open doors"
      (let [e (create-entity :player \@ :white 5 5)
            m (-> (create-tile-map 10 10)
                  (set-tile 5 4 door-open-tile)
                  (add-entity e))
            m2 (:map (execute-action reg :move-up e m))]
        (is (= 4 (second (entity-pos (first (get-entities m2))))))))
    (testing "entities cannot move into closed doors"
      (let [e (create-entity :player \@ :white 5 5)
            m (-> (create-tile-map 10 10)
                  (set-tile 5 4 door-closed-tile)
                  (add-entity e))
            m2 (:map (execute-action reg :move-up e m))]
        (is (= 5 (second (entity-pos (first (get-entities m2))))))))
    (testing "entities with :can-swim can move into water"
      (let [fish (create-entity :fish \f :blue 5 5 {:can-swim true})
            m (-> (create-tile-map 10 10)
                  (set-tile 5 4 water-tile)
                  (add-entity fish))
            result (try-move reg m fish 0 -1)]
        (is (= 4 (second (entity-pos (first (get-entities (:map result)))))))))
    (testing "entities without :can-swim cannot move into water"
      (let [player (create-entity :player \@ :white 5 5)
            m (-> (create-tile-map 10 10)
                  (set-tile 5 4 water-tile)
                  (add-entity player))
            result (try-move reg m player 0 -1)]
        (is (= 5 (second (entity-pos (first (get-entities (:map result)))))))))
    (testing "failed moves set :no-time and :retry flags"
      (let [player (create-entity :player \@ :white 5 5)
            m (-> (create-tile-map 10 10)
                  (set-tile 5 4 wall-tile)
                  (add-entity player))
            result (try-move reg m player 0 -1)]
        (is (:no-time result))
        (is (:retry result))))
    (testing "successful moves don't set retry flag"
      (let [player (create-entity :player \@ :white 5 5)
            m (-> (create-tile-map 10 10)
                  (add-entity player))
            result (try-move reg m player 0 -1)]
        (is (nil? (:retry result)))))))

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
  (testing "act-entity calls act function from registry"
    (let [wander-fn (fn [entity game-map _ctx]
                      {:map (update-entity game-map entity move-entity-by 1 0)})
          registry (-> (create-type-registry)
                       (define-entity-type :goblin {:act wander-fn}))
          e (create-entity :goblin \g :green 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity e))
          result (act-entity m e {:registry registry})]
      (is (= 6 (first (entity-pos (first (get-entities (:map result)))))))))
  (testing "act-entity returns unchanged map for entities without act in registry"
    (let [registry (create-type-registry)
          p (create-player 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity p))
          result (act-entity m p {:registry registry})]
      (is (= m (:map result))))))

(deftest process-actors-test
  (testing "processes all entities with act functions in registry"
    (let [move-right (fn [entity game-map _ctx]
                       {:map (update-entity game-map entity move-entity-by 1 0)})
          registry (-> (create-type-registry)
                       (define-entity-type :goblin {:act move-right})
                       (define-entity-type :orc {:act move-right}))
          e1 (create-entity :goblin \g :green 3 3)
          e2 (create-entity :orc \o :green 7 7)
          m (-> (create-tile-map 10 10)
                (add-entity e1)
                (add-entity e2))
          result (process-actors m {:registry registry})]
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
    (let [act-fn (fn [entity game-map _ctx]
                   {:map (update-entity game-map entity move-entity-by 1 0)})
          registry (-> (create-type-registry)
                       (define-entity-type :goblin {:act act-fn}))
          e (create-entity :goblin \g :green 5 5 {:delay 15})
          m (-> (create-tile-map 10 10)
                (add-entity e))
          result (act-entity m e {:registry registry})
          updated (first (get-entities (:map result)))]
      (is (= 15 (entity-next-action updated)))))
  (testing "acting uses default delay when not specified"
    (let [act-fn (fn [entity game-map _ctx]
                   {:map (update-entity game-map entity move-entity-by 1 0)})
          registry (-> (create-type-registry)
                       (define-entity-type :goblin {:act act-fn}))
          e (create-entity :goblin \g :green 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity e))
          result (act-entity m e {:registry registry})
          updated (first (get-entities (:map result)))]
      (is (= 10 (entity-next-action updated)))))
  (testing "action can specify custom :time-cost"
    (let [act-fn (fn [entity game-map _ctx]
                   {:map (update-entity game-map entity move-entity-by 1 0)
                    :time-cost 5})
          registry (-> (create-type-registry)
                       (define-entity-type :goblin {:act act-fn}))
          e (create-entity :goblin \g :green 5 5 {:delay 15})
          m (-> (create-tile-map 10 10)
                (add-entity e))
          result (act-entity m e {:registry registry})
          updated (first (get-entities (:map result)))]
      (is (= 5 (entity-next-action updated)))))
  (testing ":time-cost overrides entity delay"
    (let [slow-action (fn [entity game-map _ctx]
                        {:map (update-entity game-map entity move-entity-by 1 0)
                         :time-cost 50})
          registry (-> (create-type-registry)
                       (define-entity-type :rat {:act slow-action}))
          fast-entity (create-entity :rat \r :white 5 5 {:delay 2})
          m (-> (create-tile-map 10 10)
                (add-entity fast-entity))
          result (act-entity m fast-entity {:registry registry})
          updated (first (get-entities (:map result)))]
      ;; Even though entity has delay 2, action took 50
      (is (= 50 (entity-next-action updated))))))

(deftest next-actor-test
  (testing "process-next-actor processes entity with lowest next-action"
    (let [move-right (fn [entity game-map _ctx]
                       {:map (update-entity game-map entity move-entity-by 1 0)})
          registry (-> (create-type-registry)
                       (define-entity-type :goblin {:act move-right})
                       (define-entity-type :orc {:act move-right})
                       (define-entity-type :troll {:act move-right}))
          e1 (create-entity :goblin \g :green 0 0 {:next-action 20})
          e2 (create-entity :orc \o :green 1 1 {:next-action 5})
          e3 (create-entity :troll \T :green 2 2 {:next-action 15})
          m (-> (create-tile-map 10 10)
                (add-entity e1)
                (add-entity e2)
                (add-entity e3))
          result (process-next-actor m {:registry registry})]
      ;; Orc had lowest next-action (5), so it moved right
      (is (= 1 (count (get-entities-at (:map result) 2 1))))
      ;; Others stayed
      (is (= 1 (count (get-entities-at (:map result) 0 0))))
      (is (= 1 (count (get-entities-at (:map result) 2 2))))))
  (testing "process-next-actor returns unchanged map when no actors in registry"
    (let [registry (create-type-registry)
          e (create-entity :rock \* :gray 0 0)
          m (-> (create-tile-map 10 10)
                (add-entity e))
          result (process-next-actor m {:registry registry})]
      (is (= m (:map result)))))
  (testing "process-next-actor only considers entities with act in registry"
    (let [move-right (fn [entity game-map _ctx]
                       {:map (update-entity game-map entity move-entity-by 1 0)})
          registry (-> (create-type-registry)
                       (define-entity-type :goblin {:act move-right}))
          rock (create-entity :rock \* :gray 0 0 {:next-action 0})  ;; lowest but no act
          goblin (create-entity :goblin \g :green 1 1 {:next-action 10})
          m (-> (create-tile-map 10 10)
                (add-entity rock)
                (add-entity goblin))
          result (process-next-actor m {:registry registry})]
      ;; Rock stayed, goblin moved
      (is (= 1 (count (get-entities-at (:map result) 0 0))))
      (is (= 1 (count (get-entities-at (:map result) 2 1)))))))

(deftest process-next-actor-test
  (testing "process-next-actor processes only the entity with lowest next-action"
    (let [move-right (fn [entity game-map _ctx]
                       {:map (update-entity game-map entity move-entity-by 1 0)})
          registry (-> (create-type-registry)
                       (define-entity-type :rat {:act move-right})
                       (define-entity-type :turtle {:act move-right}))
          fast (create-entity :rat \r :white 3 3 {:delay 5 :next-action 0})
          slow (create-entity :turtle \t :green 7 7 {:delay 20 :next-action 10})
          m (-> (create-tile-map 10 10)
                (add-entity fast)
                (add-entity slow))
          result (process-next-actor m {:registry registry})
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
          input-fn #(deref input-atom)
          registry (-> (create-type-registry)
                       (register-default-tile-types)
                       (define-entity-type :player {:act player-act}))
          p (create-entity :player \@ :yellow 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity p))
          ctx {:input-fn input-fn :key-map default-key-map :registry registry}
          result (act-entity m p ctx)]
      (is (= 6 (first (entity-pos (get-player (:map result))))))))
  (testing "player responds to vi-style hjkl inputs"
    (let [input-atom (atom \k)
          input-fn #(deref input-atom)
          registry (-> (create-type-registry)
                       (register-default-tile-types)
                       (define-entity-type :player {:act player-act}))
          p (create-entity :player \@ :yellow 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity p))
          ctx {:input-fn input-fn :key-map default-key-map :registry registry}]
      ;; k = up
      (reset! input-atom \k)
      (let [result (act-entity m p ctx)]
        (is (= 4 (second (entity-pos (get-player (:map result)))))))
      ;; j = down
      (reset! input-atom \j)
      (let [result (act-entity m p ctx)]
        (is (= 6 (second (entity-pos (get-player (:map result)))))))
      ;; h = left
      (reset! input-atom \h)
      (let [result (act-entity m p ctx)]
        (is (= 4 (first (entity-pos (get-player (:map result)))))))
      ;; l = right
      (reset! input-atom \l)
      (let [result (act-entity m p ctx)]
        (is (= 6 (first (entity-pos (get-player (:map result)))))))))
  (testing "player responds to diagonal yubn inputs"
    (let [input-atom (atom \y)
          input-fn #(deref input-atom)
          registry (-> (create-type-registry)
                       (register-default-tile-types)
                       (define-entity-type :player {:act player-act}))
          p (create-entity :player \@ :yellow 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity p))
          ctx {:input-fn input-fn :key-map default-key-map :registry registry}]
      ;; y = up-left
      (reset! input-atom \y)
      (let [result (act-entity m p ctx)
            player (get-player (:map result))]
        (is (= 4 (first (entity-pos player))))
        (is (= 4 (second (entity-pos player)))))
      ;; u = up-right
      (reset! input-atom \u)
      (let [result (act-entity m p ctx)
            player (get-player (:map result))]
        (is (= 6 (first (entity-pos player))))
        (is (= 4 (second (entity-pos player)))))
      ;; b = down-left
      (reset! input-atom \b)
      (let [result (act-entity m p ctx)
            player (get-player (:map result))]
        (is (= 4 (first (entity-pos player))))
        (is (= 6 (second (entity-pos player)))))
      ;; n = down-right
      (reset! input-atom \n)
      (let [result (act-entity m p ctx)
            player (get-player (:map result))]
        (is (= 6 (first (entity-pos player))))
        (is (= 6 (second (entity-pos player)))))))
  (testing "player retries on unknown input until valid input"
    (let [inputs (atom [:unknown :invalid \l])  ;; two invalid, then valid
          input-fn #(let [i (first @inputs)]
                      (swap! inputs rest)
                      i)
          registry (-> (create-type-registry)
                       (register-default-tile-types)
                       (define-entity-type :player {:act player-act}))
          p (create-entity :player \@ :yellow 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity p))
          ctx {:input-fn input-fn :key-map default-key-map :registry registry}
          result (act-entity m p ctx)]
      ;; Should have moved right (the \l input) after skipping invalid inputs
      (is (= 6 (first (entity-pos (get-player (:map result))))))
      (is (= 5 (second (entity-pos (get-player (:map result))))))))
  (testing "player retries on blocked movement until valid move"
    (let [inputs (atom [\k \k \l])  ;; two blocked moves (into wall), then valid
          input-fn #(let [i (first @inputs)]
                      (swap! inputs rest)
                      i)
          registry (-> (create-type-registry)
                       (register-default-tile-types)
                       (define-entity-type :player {:act player-act}))
          p (create-entity :player \@ :yellow 5 5)
          m (-> (create-tile-map 10 10)
                (set-tile 5 4 wall-tile)  ;; wall above player
                (add-entity p))
          ctx {:input-fn input-fn :key-map default-key-map :registry registry}
          result (act-entity m p ctx)]
      ;; Should have moved right after failing to move up twice
      (is (= 6 (first (entity-pos (get-player (:map result))))))
      (is (= 5 (second (entity-pos (get-player (:map result))))))))
  (testing "process-actors includes player with ctx"
    (let [input-atom (atom \l)
          input-fn #(deref input-atom)
          move-right (fn [entity game-map _ctx]
                       {:map (update-entity game-map entity move-entity-by 1 0)})
          registry (-> (create-type-registry)
                       (register-default-tile-types)
                       (define-entity-type :player {:act player-act})
                       (define-entity-type :goblin {:act move-right}))
          p (create-entity :player \@ :yellow 5 5)
          goblin (create-entity :goblin \g :green 3 3)
          m (-> (create-tile-map 10 10)
                (add-entity p)
                (add-entity goblin))
          ctx {:input-fn input-fn :key-map default-key-map :registry registry}
          result (process-actors m ctx)]
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
          input-fn #(deref input-atom)
          registry (-> (create-type-registry)
                       (register-default-tile-types)
                       (define-entity-type :player {:act player-act}))
          p (create-entity :player \@ :yellow 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity p))
          ctx {:input-fn input-fn :key-map wasd-keys :registry registry}]
      ;; w = up
      (let [result (act-entity m p ctx)]
        (is (= 4 (second (entity-pos (get-player (:map result)))))))
      ;; d = right
      (reset! input-atom \d)
      (let [result (act-entity m p ctx)]
        (is (= 6 (first (entity-pos (get-player (:map result)))))))))
  (testing "custom key map can include quit action"
    (let [custom-keys {\q :quit
                       :escape :quit
                       :up :move-up}
          input-atom (atom \q)
          input-fn #(deref input-atom)
          registry (-> (create-type-registry)
                       (register-default-tile-types)
                       (define-entity-type :player {:act player-act}))
          p (create-entity :player \@ :yellow 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity p))
          ctx {:input-fn input-fn :key-map custom-keys :registry registry}
          result (act-entity m p ctx)]
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

(deftest execute-action-movement-only-test
  (let [reg (register-default-tile-types (create-type-registry))]
    (testing "execute-action handles movement via direction-deltas"
      (let [player (create-entity :player \@ :yellow 5 5)
            m (-> (create-tile-map 10 10)
                  (add-entity player))
            result (execute-action reg :move-up player m)]
        (is (= 4 (second (entity-pos (get-player (:map result))))))))
    (testing "execute-action returns unchanged map for unknown actions"
      (let [player (create-entity :player \@ :yellow 5 5)
            m (-> (create-tile-map 10 10)
                  (add-entity player))
            result (execute-action reg :look player m)]
        (is (= m (:map result)))
        (is (nil? (:look-mode result)))))))

(deftest direction-deltas-test
  (testing "direction-deltas has all 8 directions"
    (is (= [0 -1] (direction-deltas :move-up)))
    (is (= [0 1] (direction-deltas :move-down)))
    (is (= [-1 0] (direction-deltas :move-left)))
    (is (= [1 0] (direction-deltas :move-right)))
    (is (= [-1 -1] (direction-deltas :move-up-left)))
    (is (= [1 -1] (direction-deltas :move-up-right)))
    (is (= [-1 1] (direction-deltas :move-down-left)))
    (is (= [1 1] (direction-deltas :move-down-right)))))

(deftest look-mode-test
  (testing "look-mode calls on-move with initial position"
    (let [registry (-> (create-type-registry)
                       (define-tile-type :floor {:name "Stone Floor" :description "Cold grey stone."}))
          m (create-tile-map 10 10)
          calls (atom [])
          inputs (atom [:escape])
          ctx {:registry registry
               :input-fn #(let [i (first @inputs)] (swap! inputs rest) i)
               :key-map default-key-map
               :on-look-move (fn [_ctx gm cx cy info] (swap! calls conj [cx cy (:name info)]))}]
      (look-mode ctx m 5 5)
      (is (= 1 (count @calls)))
      (is (= [5 5 "Stone Floor"] (first @calls)))))

  (testing "cursor moves with directional input"
    (let [registry (-> (create-type-registry)
                       (define-tile-type :floor {:name "Floor"}))
          m (create-tile-map 10 10)
          calls (atom [])
          inputs (atom [\l \j :escape])
          ctx {:registry registry
               :input-fn #(let [i (first @inputs)] (swap! inputs rest) i)
               :key-map default-key-map
               :on-look-move (fn [_ctx gm cx cy info] (swap! calls conj [cx cy]))}]
      (look-mode ctx m 5 5)
      ;; Initial + 2 moves = 3 calls
      (is (= 3 (count @calls)))
      (is (= [5 5] (nth @calls 0)))
      (is (= [6 5] (nth @calls 1)))
      (is (= [6 6] (nth @calls 2)))))

  (testing "returns description on Enter"
    (let [registry (-> (create-type-registry)
                       (define-tile-type :floor {:name "Stone Floor" :description "Cold grey stone."}))
          m (create-tile-map 10 10)
          inputs (atom [:enter])
          ctx {:registry registry
               :input-fn #(let [i (first @inputs)] (swap! inputs rest) i)
               :key-map default-key-map}
          result (look-mode ctx m 5 5)]
      (is (= "Cold grey stone." (:message result)))
      (is (:no-time result))))

  (testing "returns fallback message on Enter when no description"
    (let [registry (create-type-registry)
          m (create-tile-map 10 10)
          inputs (atom [:enter])
          ctx {:registry registry
               :input-fn #(let [i (first @inputs)] (swap! inputs rest) i)
               :key-map default-key-map}
          result (look-mode ctx m 5 5)]
      (is (= "You see floor." (:message result)))))

  (testing "returns no message on Escape"
    (let [registry (create-type-registry)
          m (create-tile-map 10 10)
          inputs (atom [:escape])
          ctx {:registry registry
               :input-fn #(let [i (first @inputs)] (swap! inputs rest) i)
               :key-map default-key-map}
          result (look-mode ctx m 5 5)]
      (is (nil? (:message result)))
      (is (:no-time result))))

  (testing "cursor stays in bounds"
    (let [registry (-> (create-type-registry)
                       (define-tile-type :floor {:name "Floor"}))
          m (create-tile-map 10 10)
          calls (atom [])
          ;; At 0,0 try to go up-left (should stay), then escape
          inputs (atom [\y :escape])
          ctx {:registry registry
               :input-fn #(let [i (first @inputs)] (swap! inputs rest) i)
               :key-map default-key-map
               :on-look-move (fn [_ctx gm cx cy info] (swap! calls conj [cx cy]))}]
      (look-mode ctx m 0 0)
      ;; Initial + stayed = 2 calls, both at 0,0
      (is (= 2 (count @calls)))
      (is (= [0 0] (first @calls)))
      (is (= [0 0] (second @calls)))))

  (testing "cursor stays within supplied bounds"
    (let [registry (-> (create-type-registry)
                       (define-tile-type :floor {:name "Floor"}))
          m (create-tile-map 50 50) ;; map much larger than bounds
          calls (atom [])
          ;; Bounds: x 10-14, y 10-14 (5x5 window)
          bounds [10 10 14 14]
          ;; Start at 12,12, try to move right 3 times (should stop at x=14)
          inputs (atom [\l \l \l :escape])
          ctx {:registry registry
               :input-fn #(let [i (first @inputs)] (swap! inputs rest) i)
               :key-map default-key-map
               :on-look-move (fn [_ctx gm cx cy info] (swap! calls conj [cx cy]))}]
      (look-mode ctx m 12 12 bounds)
      ;; Initial(12,12) + right(13,12) + right(14,12) + stayed(14,12) = 4 calls
      (is (= 4 (count @calls)))
      (is (= [12 12] (nth @calls 0)))
      (is (= [13 12] (nth @calls 1)))
      (is (= [14 12] (nth @calls 2)))
      (is (= [14 12] (nth @calls 3)))))

  (testing "bounds intersects with map bounds"
    (let [registry (-> (create-type-registry)
                       (define-tile-type :floor {:name "Floor"}))
          m (create-tile-map 10 10)
          calls (atom [])
          ;; Bounds extend beyond map (map is 0-9, bounds say 0-20)
          bounds [0 0 20 20]
          ;; At 9,5, try to move right (blocked by map edge, not bounds)
          inputs (atom [\l :escape])
          ctx {:registry registry
               :input-fn #(let [i (first @inputs)] (swap! inputs rest) i)
               :key-map default-key-map
               :on-look-move (fn [_ctx gm cx cy info] (swap! calls conj [cx cy]))}]
      (look-mode ctx m 9 5 bounds)
      (is (= 2 (count @calls)))
      (is (= [9 5] (first @calls)))
      (is (= [9 5] (second @calls)))))

  (testing "nil bounds uses only map bounds (backward compat)"
    (let [registry (-> (create-type-registry)
                       (define-tile-type :floor {:name "Floor"}))
          m (create-tile-map 10 10)
          calls (atom [])
          inputs (atom [\l :escape])
          ctx {:registry registry
               :input-fn #(let [i (first @inputs)] (swap! inputs rest) i)
               :key-map default-key-map
               :on-look-move (fn [_ctx gm cx cy info] (swap! calls conj [cx cy]))}]
      (look-mode ctx m 5 5 nil)
      (is (= 2 (count @calls)))
      (is (= [5 5] (first @calls)))
      (is (= [6 5] (second @calls)))))

  (testing "shows entity info over tile info"
    (let [registry (-> (create-type-registry)
                       (define-entity-type :goblin {:name "Goblin" :description "A small goblin."})
                       (define-tile-type :floor {:name "Floor" :description "Stone floor."}))
          goblin (create-entity :goblin \g :green 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity goblin))
          inputs (atom [:enter])
          ctx {:registry registry
               :input-fn #(let [i (first @inputs)] (swap! inputs rest) i)
               :key-map default-key-map}
          result (look-mode ctx m 5 5)]
      (is (= "A small goblin." (:message result))))))

(deftest player-act-look-mode-test
  (testing "with registry in ctx: enters look mode on x, returns message on Enter"
    (let [registry (-> (create-type-registry)
                       (register-default-tile-types)
                       (define-entity-type :player {:name "Player" :description "That's you." :act player-act})
                       (update-in [:tile :floor] merge {:name "Stone Floor" :description "Cold grey stone."}))
          ;; x enters look mode, then move right (away from player), then Enter selects tile
          inputs (atom [\x \l :enter])
          input-fn #(let [i (first @inputs)] (swap! inputs rest) i)
          player (create-entity :player \@ :yellow 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity player))
          ctx {:input-fn input-fn :key-map default-key-map :registry registry}
          result (player-act player m ctx)]
      (is (= "Cold grey stone." (:message result)))
      (is (:no-time result))))

  (testing "look-bounds-fn constrains cursor in look mode"
    (let [registry (-> (create-type-registry)
                       (register-default-tile-types)
                       (define-entity-type :player {:act player-act})
                       (update-in [:tile :floor] merge {:name "Floor" :description "Stone floor."}))
          ;; x enters look, move right 3 times (bounds stop at x=7), then Enter
          inputs (atom [\x \l \l \l :enter])
          input-fn #(let [i (first @inputs)] (swap! inputs rest) i)
          calls (atom [])
          player (create-entity :player \@ :yellow 5 5)
          m (-> (create-tile-map 50 50)
                (add-entity player))
          ctx {:input-fn input-fn :key-map default-key-map
               :registry registry
               :on-look-move (fn [ctx gm cx cy info] (swap! calls conj [cx cy]))
               :look-bounds-fn (fn [ctx gm entity]
                                 (let [[px py] (entity-pos entity)]
                                   ;; player at 5,5; bounds 3-7 in each direction
                                   [(- px 2) (- py 2) (+ px 2) (+ py 2)]))}
          result (player-act player m ctx)]
      ;; Cursor: 5,5 -> 6,5 -> 7,5 -> 7,5 (blocked) -> Enter
      (is (= [5 5] (nth @calls 0)))
      (is (= [6 5] (nth @calls 1)))
      (is (= [7 5] (nth @calls 2)))
      (is (= [7 5] (nth @calls 3)))
      (is (= "Stone floor." (:message result)))))

  (testing "look mode escape returns to input loop"
    (let [registry (-> (create-type-registry)
                       (register-default-tile-types)
                       (define-entity-type :player {:act player-act}))
          ;; x enters look, escape cancels, then l moves
          inputs (atom [\x :escape \l])
          input-fn #(let [i (first @inputs)] (swap! inputs rest) i)
          player (create-entity :player \@ :yellow 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity player))
          ctx {:input-fn input-fn :key-map default-key-map :registry registry}
          result (player-act player m ctx)]
      ;; Escape from look mode -> recurs -> moves right
      (is (= 6 (first (entity-pos (get-player (:map result)))))))))

;; FOV tests

(deftest compute-fov-basic-test
  (let [reg (register-default-tile-types (create-type-registry))]
    (testing "origin is always visible"
      (let [m (create-tile-map 10 10)
            fov (compute-fov reg m 5 5)]
        (is (contains? fov [5 5]))))
    (testing "returns a set of coordinate pairs"
      (let [m (create-tile-map 10 10)
            fov (compute-fov reg m 5 5)]
        (is (set? fov))
        (is (every? #(and (vector? %) (= 2 (count %))) fov))))
    (testing "open map has all tiles visible"
      (let [m (create-tile-map 5 5)
            fov (compute-fov reg m 2 2)]
        ;; All 25 tiles should be visible on an open 5x5 map
        (is (= 25 (count fov)))
        (doseq [x (range 5) y (range 5)]
          (is (contains? fov [x y])))))))

(deftest compute-fov-walls-test
  (let [reg (register-default-tile-types (create-type-registry))]
    (testing "wall blocks tiles behind it"
      (let [m (-> (create-tile-map 10 10)
                  (set-tile 5 3 wall-tile))
            fov (compute-fov reg m 5 5)]
        ;; Wall itself is visible
        (is (contains? fov [5 3]))
        ;; Tile directly behind wall is not visible
        (is (not (contains? fov [5 2])))))
    (testing "wall doesn't block adjacent columns"
      (let [m (-> (create-tile-map 10 10)
                  (set-tile 5 3 wall-tile))
            fov (compute-fov reg m 5 5)]
        ;; Adjacent tiles should still be visible
        (is (contains? fov [4 3]))
        (is (contains? fov [6 3]))))
    (testing "room interior is visible, outside walls is not"
      (let [m (-> (create-tile-map 20 20)
                  (fill-rect 0 0 20 20 wall-tile)
                  (make-room 5 5 10 10))
            ;; Player at center of room (interior is 6,6 to 13,13)
            fov (compute-fov reg m 10 10)]
        ;; Interior floor visible
        (is (contains? fov [8 8]))
        (is (contains? fov [12 12]))
        ;; Walls visible
        (is (contains? fov [5 5]))
        ;; Outside walls not visible
        (is (not (contains? fov [3 3])))))))

(deftest compute-fov-radius-test
  (let [reg (register-default-tile-types (create-type-registry))]
    (testing "radius limits visible distance"
      (let [m (create-tile-map 20 20)
            fov (compute-fov reg m 10 10 3)]
        ;; Tiles within radius are visible
        (is (contains? fov [10 10]))
        (is (contains? fov [10 7]))
        (is (contains? fov [13 10]))
        ;; Tiles beyond radius are not visible
        (is (not (contains? fov [10 5])))
        (is (not (contains? fov [15 10])))))
    (testing "radius 0 shows only origin"
      (let [m (create-tile-map 10 10)
            fov (compute-fov reg m 5 5 0)]
        (is (= #{[5 5]} fov))))
    (testing "radius 1 shows immediate neighbors"
      (let [m (create-tile-map 10 10)
            fov (compute-fov reg m 5 5 1)]
        (is (contains? fov [5 5]))
        (is (contains? fov [5 4]))
        (is (contains? fov [5 6]))
        (is (contains? fov [4 5]))
        (is (contains? fov [6 5]))
        ;; Should not include tiles at distance 2
        (is (not (contains? fov [5 3])))
        (is (not (contains? fov [5 7])))))))

(deftest compute-fov-edge-cases-test
  (let [reg (register-default-tile-types (create-type-registry))]
    (testing "corner origin works correctly"
      (let [m (create-tile-map 10 10)
            fov (compute-fov reg m 0 0)]
        (is (contains? fov [0 0]))
        (is (contains? fov [1 0]))
        (is (contains? fov [0 1]))))
    (testing "edge origin works correctly"
      (let [m (create-tile-map 10 10)
            fov (compute-fov reg m 9 5)]
        (is (contains? fov [9 5]))
        (is (contains? fov [8 5]))))
    (testing "closed doors block vision"
      (let [m (-> (create-tile-map 10 10)
                  (set-tile 5 3 door-closed-tile))
            fov (compute-fov reg m 5 5)]
        (is (contains? fov [5 3]))
        (is (not (contains? fov [5 2])))))
    (testing "open doors don't block vision"
      (let [m (-> (create-tile-map 10 10)
                  (set-tile 5 3 door-open-tile))
            fov (compute-fov reg m 5 5)]
        (is (contains? fov [5 3]))
        (is (contains? fov [5 2]))))
    (testing "water doesn't block vision"
      (let [m (-> (create-tile-map 10 10)
                  (set-tile 5 3 water-tile))
            fov (compute-fov reg m 5 5)]
        (is (contains? fov [5 3]))
        (is (contains? fov [5 2]))))))

(deftest entity-view-radius-test
  (testing "returns view-radius when set"
    (let [e (create-entity :player \@ :yellow 5 5 {:view-radius 8})]
      (is (= 8 (:view-radius e)))))
  (testing "returns nil when not set"
    (let [e (create-entity :player \@ :yellow 5 5)]
      (is (nil? (:view-radius e))))))

(deftest compute-entity-fov-test
  (let [reg (register-default-tile-types (create-type-registry))]
    (testing "uses entity position and view-radius"
      (let [e (create-entity :player \@ :yellow 5 5 {:view-radius 3})
            m (-> (create-tile-map 20 20)
                  (add-entity e))
            fov (compute-entity-fov reg m e)]
        (is (contains? fov [5 5]))
        (is (contains? fov [5 2]))
        (is (not (contains? fov [5 0])))))
    (testing "unlimited when no view-radius"
      (let [e (create-entity :player \@ :yellow 5 5)
            m (-> (create-tile-map 10 10)
                  (add-entity e))
            fov (compute-entity-fov reg m e)]
        ;; All open tiles should be visible
        (is (contains? fov [0 0]))
        (is (contains? fov [9 9]))))))

(deftest player-act-quit-test
  (testing "quit action works in player-act"
    (let [inputs (atom [\q])
          input-fn #(let [i (first @inputs)] (swap! inputs rest) i)
          key-map (assoc default-key-map \q :quit)
          player (create-entity :player \@ :yellow 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity player))
          ctx {:input-fn input-fn :key-map key-map}
          result (player-act player m ctx)]
      (is (:quit result)))))

;; Save/Load tests


(deftest prepare-save-data-test
  (testing "includes :version 1"
    (let [m (create-tile-map 5 5)
          save-data (prepare-save-data m {})]
      (is (= 1 (:version save-data)))))
  (testing "passes through save-state keys"
    (let [m (create-tile-map 5 5)
          save-data (prepare-save-data m {:explored #{[1 1] [2 2]}
                                          :viewport {:width 50 :height 25}})]
      (is (= #{[1 1] [2 2]} (:explored save-data)))
      (is (= {:width 50 :height 25} (:viewport save-data)))))
  (testing "preserves entity data"
    (let [goblin (create-entity :goblin \g :green 5 5 {:hp 10 :delay 15})
          m (-> (create-tile-map 10 10)
                (add-entity goblin))
          save-data (prepare-save-data m {})
          saved-goblin (first (get-entities (:game-map save-data)))]
      (is (= :goblin (:type saved-goblin)))
      (is (= [5 5] (:pos saved-goblin)))
      (is (= 10 (:hp saved-goblin)))
      (is (= 15 (:delay saved-goblin))))))

(deftest restore-save-data-test
  (testing "returns full state map"
    (let [save-data {:version 1
                     :game-map (-> (create-tile-map 10 10)
                                   (add-entity (create-entity :goblin \g :green 5 5)))
                     :explored #{[1 1]}}
          restored (restore-save-data save-data)]
      (is (= :goblin (:type (first (get-entities (:game-map restored))))))
      (is (= #{[1 1]} (:explored restored)))))
  (testing "throws on unsupported version"
    (let [save-data {:version 99 :game-map (create-tile-map 5 5)}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unsupported save version"
            (restore-save-data save-data))))))

(deftest edn-round-trip-test
  (testing "full pipeline: prepare -> EDN serialize -> deserialize -> restore"
    (let [player (create-entity :player \@ :yellow 5 5 {:view-radius 8})
          goblin (create-entity :goblin \g :green 18 6 {:delay 12 :next-action 30})
          game-map (-> (create-tile-map 20 15)
                       (set-tile 3 3 wall-tile)
                       (add-entity player)
                       (add-entity goblin))
          explored #{[5 5] [5 6] [6 5]}
          save-data (prepare-save-data game-map {:explored explored})
          edn-str (pr-str save-data)
          parsed (edn/read-string edn-str)
          restored (restore-save-data parsed)
          restored-map (:game-map restored)
          restored-player (get-player restored-map)
          restored-goblin (first (filter #(= :goblin (entity-type %)) (get-entities restored-map)))]
      ;; Map structure
      (is (= 20 (map-width restored-map)))
      (is (= 15 (map-height restored-map)))
      ;; Tile data
      (is (= :wall (:type (get-tile restored-map 3 3))))
      (is (= :floor (:type (get-tile restored-map 0 0))))
      ;; Player
      (is (= [5 5] (entity-pos restored-player)))
      (is (= \@ (entity-char restored-player)))
      (is (= :yellow (entity-color restored-player)))
      (is (= 8 (:view-radius restored-player)))
      ;; Goblin
      (is (= [18 6] (entity-pos restored-goblin)))
      (is (= 12 (:delay restored-goblin)))
      (is (= 30 (:next-action restored-goblin)))
      ;; Explored set
      (is (= explored (:explored restored))))))

(deftest save-load-game-test
  (testing "save and load round-trip via gzip file"
    (let [player (create-entity :player \@ :yellow 5 5)
          goblin (create-entity :goblin \g :green 7 7 {:delay 12})
          game-map (-> (create-tile-map 15 15)
                       (set-tile 2 2 wall-tile)
                       (add-entity player)
                       (add-entity goblin))
          explored #{[5 5] [6 6]}
          tmp-file (str (.getAbsolutePath (java.io.File/createTempFile "yarf-test" ".sav")))]
      (try
        (save-game tmp-file game-map {:explored explored})
        (let [restored (load-game tmp-file)
              restored-map (:game-map restored)]
          (is (= 15 (map-width restored-map)))
          (is (= :wall (:type (get-tile restored-map 2 2))))
          (is (= [5 5] (entity-pos (get-player restored-map))))
          (is (= explored (:explored restored))))
        (finally
          (.delete (java.io.File. tmp-file))))))
  (testing "load-game throws on nonexistent file"
    (is (thrown? java.io.FileNotFoundException
          (load-game "/tmp/nonexistent-yarf-save.sav"))))
  (testing "load-game throws on corrupt file"
    (let [tmp-file (str (.getAbsolutePath (java.io.File/createTempFile "yarf-corrupt" ".sav")))]
      (try
        (spit tmp-file "this is not gzipped edn")
        (is (thrown? Exception
              (load-game tmp-file)))
        (finally
          (.delete (java.io.File. tmp-file)))))))

(deftest pass-through-actions-test
  (testing "action in :pass-through-actions returns as flag"
    (let [inputs (atom [\S])
          input-fn #(let [i (first @inputs)] (swap! inputs rest) i)
          key-map (assoc default-key-map \S :save)
          player (create-entity :player \@ :yellow 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity player))
          ctx {:input-fn input-fn :key-map key-map
               :pass-through-actions #{:save}}
          result (player-act player m ctx)]
      (is (= :save (:action result)))
      (is (:no-time result))
      (is (= m (:map result)))))
  (testing "without :pass-through-actions, unrecognized mapped actions go to execute-action"
    (let [inputs (atom [\S])
          input-fn #(let [i (first @inputs)] (swap! inputs rest) i)
          key-map (assoc default-key-map \S :save)
          player (create-entity :player \@ :yellow 5 5)
          m (-> (create-tile-map 10 10)
                (add-entity player))
          ctx {:input-fn input-fn :key-map key-map}
          result (player-act player m ctx)]
      ;; :save goes through execute-action, returns unchanged map (no retry),
      ;; player stays put
      (is (= 5 (first (entity-pos (get-player (:map result))))))
      (is (nil? (:action result))))))

