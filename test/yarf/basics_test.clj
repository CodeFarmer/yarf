(ns yarf.basics-test
  (:require [clojure.test :refer :all]
            [yarf.core :as core]
            [yarf.basics :as basics]))

;; Tile registration tests

(deftest register-basic-tile-types-test
  (testing "registers default tile types with names and descriptions"
    (let [reg (basics/register-basic-tile-types (core/create-type-registry))]
      ;; Standard tile properties should work
      (is (= \. (core/tile-char reg {:type :floor})))
      (is (= \# (core/tile-char reg {:type :wall})))
      (is (= \+ (core/tile-char reg {:type :door-closed})))
      (is (= \/ (core/tile-char reg {:type :door-open})))
      (is (= \~ (core/tile-char reg {:type :water})))
      (is (= \> (core/tile-char reg {:type :stairs-down})))
      (is (= \< (core/tile-char reg {:type :stairs-up})))))
  (testing "tiles have names"
    (let [reg (basics/register-basic-tile-types (core/create-type-registry))]
      (is (= "Stone Floor" (core/get-name reg {:type :floor})))
      (is (= "Stone Wall" (core/get-name reg {:type :wall})))
      (is (= "Closed Door" (core/get-name reg {:type :door-closed})))
      (is (= "Open Door" (core/get-name reg {:type :door-open})))
      (is (= "Water" (core/get-name reg {:type :water})))
      (is (= "Stairs Down" (core/get-name reg {:type :stairs-down})))
      (is (= "Stairs Up" (core/get-name reg {:type :stairs-up})))))
  (testing "tiles have descriptions"
    (let [reg (basics/register-basic-tile-types (core/create-type-registry))]
      (is (some? (core/get-description reg {:type :floor})))
      (is (some? (core/get-description reg {:type :wall})))
      (is (some? (core/get-description reg {:type :door-closed})))
      (is (some? (core/get-description reg {:type :door-open})))
      (is (some? (core/get-description reg {:type :water})))
      (is (some? (core/get-description reg {:type :stairs-down})))
      (is (some? (core/get-description reg {:type :stairs-up})))))

  (testing "can add additional tile types on top of basics"
    (let [reg (-> (core/create-type-registry)
                  (basics/register-basic-tile-types)
                  (core/define-tile-type :lava {:char \~ :color :red :walkable false :transparent true
                                                :name "Lava" :description "Molten rock."}))]
      (is (= "Lava" (core/get-name reg {:type :lava})))
      ;; Basic tiles still work
      (is (= "Stone Floor" (core/get-name reg {:type :floor}))))))

;; Door tests

(deftest open-door-test
  (let [reg (basics/register-basic-tile-types (core/create-type-registry))]
    (testing "opens a closed door"
      (let [m (-> (core/create-tile-map 5 5)
                  (core/set-tile 2 2 {:type :door-closed}))
            m2 (basics/open-door m 2 2)]
        (is (= :door-open (:type (core/get-tile m2 2 2))))))
    (testing "preserves instance properties"
      (let [m (-> (core/create-tile-map 5 5)
                  (core/set-tile 2 2 {:type :door-closed :locked false}))
            m2 (basics/open-door m 2 2)]
        (is (= :door-open (:type (core/get-tile m2 2 2))))
        (is (= false (:locked (core/get-tile m2 2 2))))))))

(deftest close-door-test
  (let [reg (basics/register-basic-tile-types (core/create-type-registry))]
    (testing "closes an open door"
      (let [m (-> (core/create-tile-map 5 5)
                  (core/set-tile 2 2 {:type :door-open}))
            m2 (basics/close-door m 2 2)]
        (is (= :door-closed (:type (core/get-tile m2 2 2))))))
    (testing "preserves instance properties"
      (let [m (-> (core/create-tile-map 5 5)
                  (core/set-tile 2 2 {:type :door-open :material :iron}))
            m2 (basics/close-door m 2 2)]
        (is (= :door-closed (:type (core/get-tile m2 2 2))))
        (is (= :iron (:material (core/get-tile m2 2 2))))))))

(deftest door-on-bump-tile-test
  (let [reg (basics/register-basic-tile-types (core/create-type-registry))]
    (testing "opens closed door and returns message"
      (let [player (core/create-entity :player \@ :yellow 5 5)
            m (-> (core/create-tile-map 10 10)
                  (core/set-tile 5 4 {:type :door-closed})
                  (core/add-entity player))
            ctx {:registry reg}
            result (basics/door-on-bump-tile player m [5 4] ctx)]
        (is (= :door-open (:type (core/get-tile (:map result) 5 4))))
        (is (some? (:message result)))
        (is (nil? (:retry result)))))
    (testing "non-door tile returns retry"
      (let [player (core/create-entity :player \@ :yellow 5 5)
            m (-> (core/create-tile-map 10 10)
                  (core/set-tile 5 4 {:type :wall})
                  (core/add-entity player))
            ctx {:registry reg}
            result (basics/door-on-bump-tile player m [5 4] ctx)]
        (is (:retry result))
        (is (:no-time result))))
    (testing "open door returns retry (already open)"
      (let [player (core/create-entity :player \@ :yellow 5 5)
            m (-> (core/create-tile-map 10 10)
                  (core/set-tile 5 4 {:type :door-open})
                  (core/add-entity player))
            ctx {:registry reg}
            result (basics/door-on-bump-tile player m [5 4] ctx)]
        (is (:retry result))))))

;; Combat tests

(defn- make-combat-registry []
  (-> (core/create-type-registry)
      (basics/register-basic-tile-types)
      (core/define-entity-type :player {:name "Player"
                                        :max-hp 20 :attack "1d20+2"
                                        :defense 12 :damage "1d6+1" :armor 0
                                        :blocks-movement true})
      (core/define-entity-type :goblin {:name "Goblin"
                                        :max-hp 5 :attack "1d20"
                                        :defense 8 :damage "1d4" :armor 0
                                        :blocks-movement true})))

(deftest alive-test
  (testing "entity with positive hp is alive"
    (is (basics/alive? {:hp 5})))
  (testing "entity with zero hp is not alive"
    (is (not (basics/alive? {:hp 0}))))
  (testing "entity with negative hp is not alive"
    (is (not (basics/alive? {:hp -1}))))
  (testing "entity without hp is not alive"
    (is (not (basics/alive? {})))))

(deftest apply-damage-test
  (let [reg (make-combat-registry)]
    (testing "reduces hp by damage amount"
      (let [goblin (core/create-entity :goblin \g :green 5 5 {:hp 5})
            m (-> (core/create-tile-map 10 10)
                  (core/add-entity goblin))
            result (basics/apply-damage m goblin 3)]
        (is (= 2 (:hp (first (core/get-entities (:map result))))))
        (is (nil? (:removed result)))))
    (testing "removes entity at 0 hp"
      (let [goblin (core/create-entity :goblin \g :green 5 5 {:hp 3})
            m (-> (core/create-tile-map 10 10)
                  (core/add-entity goblin))
            result (basics/apply-damage m goblin 3)]
        (is (empty? (core/get-entities (:map result))))
        (is (:removed result))))
    (testing "removes entity below 0 hp"
      (let [goblin (core/create-entity :goblin \g :green 5 5 {:hp 2})
            m (-> (core/create-tile-map 10 10)
                  (core/add-entity goblin))
            result (basics/apply-damage m goblin 5)]
        (is (empty? (core/get-entities (:map result))))
        (is (:removed result))))))

(deftest melee-attack-test
  (let [reg (make-combat-registry)]
    (testing "hit deals damage (deterministic roll)"
      (with-redefs [core/roll (fn [dice]
                                (cond
                                  (= dice "1d20+2") 15  ;; attack roll >= defense 8
                                  (= dice "1d6+1") 4    ;; damage roll
                                  :else 0))]
        (let [player (core/create-entity :player \@ :yellow 3 3 {:hp 20})
              goblin (core/create-entity :goblin \g :green 3 4 {:hp 5})
              m (-> (core/create-tile-map 10 10)
                    (core/add-entity player)
                    (core/add-entity goblin))
              ctx {:registry reg}
              result (basics/melee-attack player m goblin ctx)]
          ;; Goblin should take 4 damage (4 - 0 armor)
          (let [updated-goblin (first (filter #(= :goblin (:type %)) (core/get-entities (:map result))))]
            (is (= 1 (:hp updated-goblin))))
          (is (some? (:message result)))
          (is (.contains (:message result) "4")))))
    (testing "miss returns message with no damage"
      (with-redefs [core/roll (fn [dice]
                                (cond
                                  (= dice "1d20+2") 5  ;; attack roll < defense 8
                                  :else 0))]
        (let [player (core/create-entity :player \@ :yellow 3 3 {:hp 20})
              goblin (core/create-entity :goblin \g :green 3 4 {:hp 5})
              m (-> (core/create-tile-map 10 10)
                    (core/add-entity player)
                    (core/add-entity goblin))
              ctx {:registry reg}
              result (basics/melee-attack player m goblin ctx)]
          (let [updated-goblin (first (filter #(= :goblin (:type %)) (core/get-entities (:map result))))]
            (is (= 5 (:hp updated-goblin))))
          (is (some? (:message result))))))
    (testing "killing blow removes entity and message includes kill"
      (with-redefs [core/roll (fn [dice]
                                (cond
                                  (= dice "1d20+2") 15
                                  (= dice "1d6+1") 10  ;; overkill
                                  :else 0))]
        (let [player (core/create-entity :player \@ :yellow 3 3 {:hp 20})
              goblin (core/create-entity :goblin \g :green 3 4 {:hp 5})
              m (-> (core/create-tile-map 10 10)
                    (core/add-entity player)
                    (core/add-entity goblin))
              ctx {:registry reg}
              result (basics/melee-attack player m goblin ctx)]
          (is (empty? (filter #(= :goblin (:type %)) (core/get-entities (:map result)))))
          (is (.contains (:message result) "killing")))))
    (testing "armor reduces damage"
      (let [reg2 (core/define-entity-type reg :goblin
                   {:name "Goblin" :max-hp 5 :attack "1d20"
                    :defense 8 :damage "1d4" :armor 3
                    :blocks-movement true})]
        (with-redefs [core/roll (fn [dice]
                                  (cond
                                    (= dice "1d20+2") 15
                                    (= dice "1d6+1") 4
                                    :else 0))]
          (let [player (core/create-entity :player \@ :yellow 3 3 {:hp 20})
                goblin (core/create-entity :goblin \g :green 3 4 {:hp 5})
                m (-> (core/create-tile-map 10 10)
                      (core/add-entity player)
                      (core/add-entity goblin))
                ctx {:registry reg2}
                ;; goblin has 3 armor, player does 4 raw -> 1 actual
                result (basics/melee-attack player m goblin ctx)]
            (let [updated-goblin (first (filter #(= :goblin (:type %)) (core/get-entities (:map result))))]
              (is (= 4 (:hp updated-goblin))))))))
    (testing "armor cannot reduce damage below 0"
      (let [reg2 (core/define-entity-type reg :goblin
                   {:name "Goblin" :max-hp 5 :attack "1d20"
                    :defense 8 :damage "1d4" :armor 10
                    :blocks-movement true})]
        (with-redefs [core/roll (fn [dice]
                                  (cond
                                    (= dice "1d20+2") 15
                                    (= dice "1d6+1") 4
                                    :else 0))]
          (let [player (core/create-entity :player \@ :yellow 3 3 {:hp 20})
                goblin (core/create-entity :goblin \g :green 3 4 {:hp 5})
                m (-> (core/create-tile-map 10 10)
                      (core/add-entity player)
                      (core/add-entity goblin))
                ctx {:registry reg2}
                result (basics/melee-attack player m goblin ctx)]
            ;; 4 - 10 = 0, not negative
            (let [updated-goblin (first (filter #(= :goblin (:type %)) (core/get-entities (:map result))))]
              (is (= 5 (:hp updated-goblin))))))))
    (testing "player attack messages use 'You'"
      (with-redefs [core/roll (fn [dice]
                                (cond
                                  (= dice "1d20+2") 15
                                  (= dice "1d6+1") 4
                                  :else 0))]
        (let [player (core/create-entity :player \@ :yellow 3 3 {:hp 20})
              goblin (core/create-entity :goblin \g :green 3 4 {:hp 5})
              m (-> (core/create-tile-map 10 10)
                    (core/add-entity player)
                    (core/add-entity goblin))
              ctx {:registry reg}
              result (basics/melee-attack player m goblin ctx)]
          (is (.startsWith (:message result) "You ")))))
    (testing "monster attack messages use 'The <name>'"
      (with-redefs [core/roll (fn [dice]
                                (cond
                                  (= dice "1d20") 15  ;; goblin attack
                                  (= dice "1d4") 2    ;; goblin damage
                                  :else 0))]
        (let [goblin (core/create-entity :goblin \g :green 3 4 {:hp 5})
              player (core/create-entity :player \@ :yellow 3 3 {:hp 20})
              m (-> (core/create-tile-map 10 10)
                    (core/add-entity player)
                    (core/add-entity goblin))
              ctx {:registry reg}
              result (basics/melee-attack goblin m player ctx)]
          (is (.startsWith (:message result) "The Goblin ")))))))

(deftest combat-on-bump-test
  (let [reg (make-combat-registry)]
    (testing "calls melee-attack and returns result with effect"
      (with-redefs [core/roll (fn [dice]
                                (cond
                                  (= dice "1d20+2") 15
                                  (= dice "1d6+1") 4
                                  :else 0))]
        (let [player (core/create-entity :player \@ :yellow 3 3 {:hp 20})
              goblin (core/create-entity :goblin \g :green 3 4 {:hp 5})
              m (-> (core/create-tile-map 10 10)
                    (core/add-entity player)
                    (core/add-entity goblin))
              ctx {:registry reg}
              result (basics/combat-on-bump player m goblin ctx)]
          (is (some? (:message result)))
          (is (some? (:effect result))))))
    (testing "miss returns miss effect"
      (with-redefs [core/roll (fn [dice]
                                (cond
                                  (= dice "1d20+2") 5  ;; miss
                                  :else 0))]
        (let [player (core/create-entity :player \@ :yellow 3 3 {:hp 20})
              goblin (core/create-entity :goblin \g :green 3 4 {:hp 5})
              m (-> (core/create-tile-map 10 10)
                    (core/add-entity player)
                    (core/add-entity goblin))
              ctx {:registry reg}
              result (basics/combat-on-bump player m goblin ctx)]
          (is (some? (:effect result))))))))

(deftest hit-effect-test
  (testing "creates a 2-frame effect"
    (let [effect (basics/hit-effect [5 5])]
      (is (= 2 (count (:frames effect))))
      (is (= [5 5] (:pos (first (first (:frames effect)))))))))

(deftest miss-effect-test
  (testing "creates a 1-frame effect"
    (let [effect (basics/miss-effect [5 5])]
      (is (= 1 (count (:frames effect))))
      (is (= [5 5] (:pos (first (first (:frames effect)))))))))

;; AI tests

(deftest wander-test
  (let [reg (-> (core/create-type-registry)
                (basics/register-basic-tile-types)
                (core/define-entity-type :goblin {:name "Goblin"}))]
    (testing "wander returns an action-result with map"
      (let [goblin (core/create-entity :goblin \g :green 5 5)
            m (-> (core/create-tile-map 10 10)
                  (core/add-entity goblin))
            ctx {:registry reg}
            result (basics/wander goblin m ctx)]
        (is (some? (:map result)))))
    (testing "wander moves entity or stays still"
      (let [goblin (core/create-entity :goblin \g :green 5 5)
            m (-> (core/create-tile-map 10 10)
                  (core/add-entity goblin))
            ctx {:registry reg}
            ;; Run many times - should always produce valid result
            results (repeatedly 20 #(basics/wander goblin m ctx))]
        (is (every? #(some? (:map %)) results))))))

(deftest player-target-fn-test
  (let [reg (core/create-type-registry)]
    (testing "returns the player entity"
      (let [player (core/create-player 3 3)
            goblin (core/create-entity :goblin \g :green 7 7)
            m (-> (core/create-tile-map 10 10)
                  (core/add-entity player)
                  (core/add-entity goblin))
            ctx {:registry reg}]
        (is (= player (basics/player-target-fn goblin m ctx)))))
    (testing "returns nil when no player"
      (let [goblin (core/create-entity :goblin \g :green 7 7)
            m (-> (core/create-tile-map 10 10)
                  (core/add-entity goblin))
            ctx {:registry reg}]
        (is (nil? (basics/player-target-fn goblin m ctx)))))))

(deftest make-chase-act-test
  (let [reg (-> (core/create-type-registry)
                (basics/register-basic-tile-types)
                (core/define-entity-type :player {:name "Player"
                                                  :max-hp 20 :attack "1d20+2"
                                                  :defense 12 :damage "1d6+1" :armor 0
                                                  :blocks-movement true})
                (core/define-entity-type :goblin {:name "Goblin"
                                                  :max-hp 5 :attack "1d20"
                                                  :defense 8 :damage "1d4" :armor 0
                                                  :blocks-movement true}))
        chase-act (basics/make-chase-act basics/player-target-fn)]
    (testing "adjacent to target: attacks"
      (with-redefs [core/roll (fn [dice]
                                (cond
                                  (= dice "1d20") 15  ;; goblin attack hits
                                  (= dice "1d4") 3    ;; goblin damage
                                  :else 0))]
        (let [player (core/create-entity :player \@ :yellow 5 5 {:hp 20})
              goblin (core/create-entity :goblin \g :green 5 6 {:hp 5})
              m (-> (core/create-tile-map 10 10)
                    (core/add-entity player)
                    (core/add-entity goblin))
              ctx {:registry reg}
              result (chase-act goblin m ctx)]
          ;; Should have attacked (message present)
          (is (some? (:message result)))
          ;; Player should have taken damage
          (let [p (core/get-player (:map result))]
            (is (= 17 (:hp p)))))))
    (testing "within pathfinding range: moves toward target"
      (let [player (core/create-entity :player \@ :yellow 5 5 {:hp 20})
            goblin (core/create-entity :goblin \g :green 8 5 {:hp 5})
            m (-> (core/create-tile-map 10 10)
                  (core/add-entity player)
                  (core/add-entity goblin))
            ctx {:registry reg}
            result (chase-act goblin m ctx)
            moved-goblin (first (filter #(= :goblin (:type %)) (core/get-entities (:map result))))]
        ;; Should have moved closer to player
        (is (< (core/chebyshev-distance (core/entity-pos moved-goblin) [5 5])
               (core/chebyshev-distance [8 5] [5 5])))))
    (testing "no target: wanders"
      (let [goblin (core/create-entity :goblin \g :green 5 5 {:hp 5})
            m (-> (core/create-tile-map 10 10)
                  (core/add-entity goblin))
            ctx {:registry reg}
            result (chase-act goblin m ctx)]
        ;; Should still return valid result (wander fallback)
        (is (some? (:map result)))))
    (testing "no path to target: wanders"
      (let [player (core/create-entity :player \@ :yellow 1 1 {:hp 20})
            goblin (core/create-entity :goblin \g :green 8 8 {:hp 5})
            ;; Wall them off from each other
            m (-> (core/create-tile-map 10 10)
                  (core/fill-rect 0 0 10 10 {:type :wall})
                  (core/fill-rect 0 0 3 3 {:type :floor})
                  (core/fill-rect 7 7 3 3 {:type :floor})
                  (core/add-entity player)
                  (core/add-entity goblin))
            ctx {:registry reg}
            result (chase-act goblin m ctx)]
        (is (some? (:map result)))))))

;; Ranged attack tests

(deftest projectile-effect-test
  (testing "creates multi-frame effect along line"
    (let [effect (basics/projectile-effect [2 2] [6 2])]
      ;; Line from [2,2] to [6,2] has 5 points, skip first = 4 frames
      (is (= 4 (count (:frames effect))))
      ;; Each frame has one cell
      (is (every? #(= 1 (count %)) (:frames effect)))
      ;; First frame at [3,2] (not [2,2])
      (is (= [3 2] (:pos (first (first (:frames effect))))))
      ;; Last frame at [6,2]
      (is (= [6 2] (:pos (first (last (:frames effect))))))))
  (testing "custom char and color"
    (let [effect (basics/projectile-effect [0 0] [2 0] \- :red)
          cell (first (first (:frames effect)))]
      (is (= \- (:char cell)))
      (is (= :red (:color cell)))))
  (testing "adjacent positions produce single frame"
    (let [effect (basics/projectile-effect [5 5] [6 5])]
      (is (= 1 (count (:frames effect)))))))

(defn- make-ranged-registry []
  (-> (core/create-type-registry)
      (basics/register-basic-tile-types)
      (core/define-entity-type :player {:name "Player"
                                        :max-hp 20 :attack "1d20+2"
                                        :defense 12 :damage "1d6+1" :armor 0
                                        :ranged-attack "1d20+1" :ranged-damage "1d8"
                                        :range 8
                                        :blocks-movement true})
      (core/define-entity-type :goblin {:name "Goblin"
                                        :max-hp 5 :attack "1d20"
                                        :defense 8 :damage "1d4" :armor 0
                                        :blocks-movement true})))

(deftest ranged-attack-test
  (let [reg (make-ranged-registry)]
    (testing "hit deals damage"
      (with-redefs [core/roll (fn [dice]
                                (cond
                                  (= dice "1d20+1") 15  ;; ranged attack roll >= defense 8
                                  (= dice "1d8") 5      ;; ranged damage
                                  :else 0))]
        (let [player (core/create-entity :player \@ :yellow 3 3 {:hp 20})
              goblin (core/create-entity :goblin \g :green 7 3 {:hp 5})
              m (-> (core/create-tile-map 10 10)
                    (core/add-entity player)
                    (core/add-entity goblin))
              ctx {:registry reg}
              result (basics/ranged-attack player m goblin ctx)]
          (is (empty? (filter #(= :goblin (:type %)) (core/get-entities (:map result)))))
          (is (.contains (:message result) "5"))
          (is (:hit result)))))
    (testing "miss returns message with no damage"
      (with-redefs [core/roll (fn [dice]
                                (cond
                                  (= dice "1d20+1") 5  ;; miss (< defense 8)
                                  :else 0))]
        (let [player (core/create-entity :player \@ :yellow 3 3 {:hp 20})
              goblin (core/create-entity :goblin \g :green 7 3 {:hp 5})
              m (-> (core/create-tile-map 10 10)
                    (core/add-entity player)
                    (core/add-entity goblin))
              ctx {:registry reg}
              result (basics/ranged-attack player m goblin ctx)]
          (is (= 5 (:hp (first (filter #(= :goblin (:type %)) (core/get-entities (:map result)))))))
          (is (nil? (:hit result))))))
    (testing "uses ranged-attack and ranged-damage properties"
      (with-redefs [core/roll (fn [dice]
                                (cond
                                  (= dice "1d20+1") 15
                                  (= dice "1d8") 3
                                  :else 0))]
        (let [player (core/create-entity :player \@ :yellow 3 3 {:hp 20})
              goblin (core/create-entity :goblin \g :green 7 3 {:hp 5})
              m (-> (core/create-tile-map 10 10)
                    (core/add-entity player)
                    (core/add-entity goblin))
              ctx {:registry reg}
              result (basics/ranged-attack player m goblin ctx)]
          (is (= 2 (:hp (first (filter #(= :goblin (:type %)) (core/get-entities (:map result))))))))))))

(deftest ranged-on-target-test
  (let [reg (make-ranged-registry)]
    (testing "self-target returns retry"
      (let [player (core/create-entity :player \@ :yellow 5 5 {:hp 20})
            m (-> (core/create-tile-map 10 10)
                  (core/add-entity player))
            ctx {:registry reg}
            result (basics/ranged-on-target player m [5 5] ctx)]
        (is (:retry result))
        (is (:no-time result))))
    (testing "no LOS returns message"
      (let [player (core/create-entity :player \@ :yellow 3 3 {:hp 20})
            goblin (core/create-entity :goblin \g :green 7 3 {:hp 5})
            m (-> (core/create-tile-map 10 10)
                  (core/set-tile 5 3 {:type :wall})
                  (core/add-entity player)
                  (core/add-entity goblin))
            ctx {:registry reg}
            result (basics/ranged-on-target player m [7 3] ctx)]
        (is (= "You can't see there." (:message result)))
        (is (:no-time result))
        (is (nil? (:retry result)))))
    (testing "out of range returns message"
      (let [player (core/create-entity :player \@ :yellow 1 1 {:hp 20})
            goblin (core/create-entity :goblin \g :green 1 15 {:hp 5})
            m (-> (core/create-tile-map 20 20)
                  (core/add-entity player)
                  (core/add-entity goblin))
            ctx {:registry reg}
            ;; Distance is 14, range is 8
            result (basics/ranged-on-target player m [1 15] ctx)]
        (is (= "Out of range." (:message result)))
        (is (:no-time result))))
    (testing "no entity at target returns message"
      (let [player (core/create-entity :player \@ :yellow 5 5 {:hp 20})
            m (-> (core/create-tile-map 10 10)
                  (core/add-entity player))
            ctx {:registry reg}
            result (basics/ranged-on-target player m [7 5] ctx)]
        (is (= "Nothing to shoot at." (:message result)))
        (is (:no-time result))))
    (testing "successful hit with projectile and hit effect"
      (with-redefs [core/roll (fn [dice]
                                (cond
                                  (= dice "1d20+1") 15
                                  (= dice "1d8") 5
                                  :else 0))]
        (let [player (core/create-entity :player \@ :yellow 3 3 {:hp 20})
              goblin (core/create-entity :goblin \g :green 7 3 {:hp 5})
              m (-> (core/create-tile-map 10 10)
                    (core/add-entity player)
                    (core/add-entity goblin))
              ctx {:registry reg}
              result (basics/ranged-on-target player m [7 3] ctx)]
          (is (some? (:effect result)))
          (is (some? (:message result)))
          ;; Should not leak :hit flag
          (is (nil? (:hit result)))
          ;; Projectile frames (4) + hit frames (2) = 6
          (is (= 6 (count (:frames (:effect result))))))))
    (testing "miss with projectile and miss effect"
      (with-redefs [core/roll (fn [dice]
                                (cond
                                  (= dice "1d20+1") 3  ;; miss
                                  :else 0))]
        (let [player (core/create-entity :player \@ :yellow 3 3 {:hp 20})
              goblin (core/create-entity :goblin \g :green 7 3 {:hp 5})
              m (-> (core/create-tile-map 10 10)
                    (core/add-entity player)
                    (core/add-entity goblin))
              ctx {:registry reg}
              result (basics/ranged-on-target player m [7 3] ctx)]
          (is (some? (:effect result)))
          ;; Projectile frames (4) + miss frames (1) = 5
          (is (= 5 (count (:frames (:effect result))))))))))
