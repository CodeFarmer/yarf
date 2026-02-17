(ns yarf.display-test
  (:require [clojure.test :refer :all]
            [yarf.display :refer :all]
            [yarf.core :as core]))

;; Mock display for testing
(defrecord MockDisplay [input-atom rendered-atom message-atom]
  Display
  (get-input [this] @input-atom)
  (render-tile [this x y tile registry]
    (swap! rendered-atom conj {:x x :y y :char (core/tile-char registry tile) :color (core/tile-color registry tile)}))
  (render-entity [this x y entity]
    (swap! rendered-atom conj {:x x :y y :char (core/entity-char entity) :color (core/entity-color entity)}))
  (clear-screen [this] (reset! rendered-atom []))
  (refresh-screen [this] nil)
  (start-display [this] this)
  (stop-display [this] nil)
  (display-message [this message]
    (reset! message-atom message)))

(defn make-mock-display
  ([] (make-mock-display nil))
  ([input] (->MockDisplay (atom input) (atom []) (atom nil))))

(deftest display-protocol-test
  (testing "mock display implements protocol"
    (let [d (make-mock-display :up)]
      (is (satisfies? Display d))
      (is (= :up (get-input d)))))
  (testing "mock display tracks rendered content"
    (let [reg (core/register-default-tile-types (core/create-type-registry))
          d (make-mock-display)]
      (render-tile d 5 5 {:type :wall} reg)
      (is (= 1 (count @(:rendered-atom d))))
      (is (= \# (:char (first @(:rendered-atom d))))))))

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

(deftest display-message-test
  (testing "display-message shows message in message bar"
    (let [d (make-mock-display)]
      (display-message d "Hello, world!")
      (is (= "Hello, world!" @(:message-atom d))))))
