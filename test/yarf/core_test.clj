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
