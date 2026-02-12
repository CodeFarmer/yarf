(ns yarf.display-test
  (:require [clojure.test :refer :all]
            [yarf.display :refer :all]
            [yarf.core :as core]))

(deftest create-viewport-test
  (testing "creates viewport with specified dimensions"
    (let [vp (create-viewport 40 20)]
      (is (= 40 (:width vp)))
      (is (= 20 (:height vp)))
      (is (= 0 (:offset-x vp)))
      (is (= 0 (:offset-y vp))))))

(deftest center-viewport-on-test
  (testing "centers viewport on given coordinates"
    (let [vp (-> (create-viewport 20 10)
                 (center-viewport-on 50 30))]
      (is (= 40 (:offset-x vp)))
      (is (= 25 (:offset-y vp)))))
  (testing "clamps to map bounds"
    (let [m (core/create-tile-map 40 30)
          vp (-> (create-viewport 20 10)
                 (center-viewport-on 5 5)
                 (clamp-to-map m))]
      (is (>= (:offset-x vp) 0))
      (is (>= (:offset-y vp) 0)))
    (let [m (core/create-tile-map 40 30)
          vp (-> (create-viewport 20 10)
                 (center-viewport-on 35 25)
                 (clamp-to-map m))]
      (is (<= (+ (:offset-x vp) (:width vp)) 40))
      (is (<= (+ (:offset-y vp) (:height vp)) 30)))))

(deftest world-to-screen-test
  (testing "converts world coordinates to screen coordinates"
    (let [vp (-> (create-viewport 20 10)
                 (assoc :offset-x 5 :offset-y 3))]
      (is (= [5 7] (world-to-screen vp 10 10)))
      (is (= [0 0] (world-to-screen vp 5 3))))))

(deftest screen-to-world-test
  (testing "converts screen coordinates to world coordinates"
    (let [vp (-> (create-viewport 20 10)
                 (assoc :offset-x 5 :offset-y 3))]
      (is (= [10 10] (screen-to-world vp 5 7)))
      (is (= [5 3] (screen-to-world vp 0 0))))))
