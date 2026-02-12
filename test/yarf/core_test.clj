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
    (let [t (make-tile :lava {:walkable false :transparent true :damage 10})]
      (is (= :lava (:type t)))
      (is (not (walkable? t)))
      (is (transparent? t))
      (is (= 10 (:damage t))))))
