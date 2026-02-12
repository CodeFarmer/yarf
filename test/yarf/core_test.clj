(ns yarf.core-test
  (:require [clojure.test :refer :all]
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
    (is (walkable? floor-tile))
    (is (not (walkable? wall-tile)))
    (is (not (walkable? door-closed-tile)))
    (is (walkable? door-open-tile))
    (is (not (walkable? water-tile)))))

(deftest tile-transparent-test
  (testing "transparent? returns correct values for tile types"
    (is (transparent? floor-tile))
    (is (not (transparent? wall-tile)))
    (is (not (transparent? door-closed-tile)))
    (is (transparent? door-open-tile))
    (is (transparent? water-tile))))

(deftest make-tile-test
  (testing "make-tile creates tile with specified properties"
    (let [t (make-tile :lava \* :red {:walkable false :transparent true :damage 10})]
      (is (= :lava (:type t)))
      (is (= \* (tile-char t)))
      (is (= :red (tile-color t)))
      (is (not (walkable? t)))
      (is (transparent? t))
      (is (= 10 (:damage t))))))

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
      (is (= 5 (entity-x e)))
      (is (= 10 (entity-y e)))))
  (testing "creates entity with additional properties"
    (let [e (create-entity :goblin \g :green 3 4 {:hp 10 :attack 2})]
      (is (= :goblin (entity-type e)))
      (is (= 10 (:hp e)))
      (is (= 2 (:attack e))))))

(deftest move-entity-test
  (testing "moves entity to new position"
    (let [e (-> (create-entity :player \@ :white 5 5)
                (move-entity 10 15))]
      (is (= 10 (entity-x e)))
      (is (= 15 (entity-y e)))))
  (testing "move-entity-by adds to current position"
    (let [e (-> (create-entity :player \@ :white 5 5)
                (move-entity-by 2 -1))]
      (is (= 7 (entity-x e)))
      (is (= 4 (entity-y e))))))

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
      (is (= 10 (entity-x updated)))
      (is (= 10 (entity-y updated)))
      (is (empty? (get-entities-at m2 5 5))))))

;; Player entity tests

(deftest create-player-test
  (testing "creates player entity with standard properties"
    (let [p (create-player 5 10)]
      (is (= :player (entity-type p)))
      (is (= \@ (entity-char p)))
      (is (= :yellow (entity-color p)))
      (is (= 5 (entity-x p)))
      (is (= 10 (entity-y p)))))
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
