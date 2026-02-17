(ns yarf.basics
  "Ready-to-use building blocks for roguelike games.
   Provides named tiles, door mechanics, combat, and basic AI."
  (:require [yarf.core :as core]))

;; Tiles

(def basic-tile-descriptions
  "Names and descriptions for the standard tile types."
  {:floor {:name "Stone Floor" :description "Cold grey stone."}
   :wall {:name "Stone Wall" :description "A solid wall of rough-hewn stone."}
   :door-closed {:name "Closed Door" :description "A heavy wooden door, firmly shut."}
   :door-open {:name "Open Door" :description "A door standing open."}
   :water {:name "Water" :description "Dark, still water."}
   :stairs-down {:name "Stairs Down" :description "A staircase leading down."}
   :stairs-up {:name "Stairs Up" :description "A staircase leading up."}})

(defn register-basic-tile-types
  "Registers default tile types with names and descriptions.
   Calls core/register-default-tile-types then merges descriptions."
  [registry]
  (reduce-kv (fn [reg tile-type desc]
               (update-in reg [:tile tile-type] merge desc))
             (core/register-default-tile-types registry)
             basic-tile-descriptions))

;; Doors

(defn open-door
  "Opens a closed door at (x, y). Swaps :door-closed to :door-open,
   preserving any instance properties."
  [game-map x y]
  (let [tile (core/get-tile game-map x y)]
    (core/set-tile game-map x y (assoc tile :type :door-open))))

(defn close-door
  "Closes an open door at (x, y). Swaps :door-open to :door-closed,
   preserving any instance properties."
  [game-map x y]
  (let [tile (core/get-tile game-map x y)]
    (core/set-tile game-map x y (assoc tile :type :door-closed))))

(defn door-on-bump-tile
  "on-bump-tile callback: opens a closed door at the bumped position.
   Non-door tiles return {:retry true :no-time true}."
  [mover game-map [x y] ctx]
  (let [tile (core/get-tile game-map x y)]
    (if (= :door-closed (:type tile))
      {:map (open-door game-map x y)
       :message "You open the door."}
      {:map game-map :retry true :no-time true})))

;; Combat

(defn alive?
  "Returns true if the entity has positive HP."
  [entity]
  (and (some? (:hp entity))
       (> (:hp entity) 0)))

(defn apply-damage
  "Reduces entity's HP by damage. Removes entity from map if HP <= 0.
   Returns {:map updated-map :removed true/nil}."
  [game-map entity damage]
  (let [new-hp (- (:hp entity) damage)]
    (if (<= new-hp 0)
      {:map (core/remove-entity game-map entity) :removed true}
      {:map (core/update-entity game-map entity assoc :hp new-hp)})))

(defn- attacker-name
  "Returns display string for an attacker. Player type uses 'You', others 'The <name>'."
  [registry attacker]
  (if (= :player (:type attacker))
    "You"
    (str "The " (core/get-name registry attacker))))

(defn- defender-name
  "Returns display string for a defender. Player type uses 'you', others 'the <name>'."
  [registry defender]
  (if (= :player (:type defender))
    "you"
    (str "the " (core/get-name registry defender))))

(defn- hit-verb
  "Returns the verb form for hit messages."
  [attacker]
  (if (= :player (:type attacker)) "hit" "hits"))

(defn- miss-verb
  "Returns the verb form for miss messages."
  [attacker]
  (if (= :player (:type attacker)) "miss" "misses"))

(defn- kill-suffix
  "Returns the kill message suffix."
  [defender]
  (if (= :player (:type defender))
    "...killing you!"
    "...killing it!"))

(defn hit-effect
  "Creates a 2-frame red/yellow hit flash at the given position."
  [pos]
  (core/make-effect
    [(core/make-effect-frame [(core/make-effect-cell pos \* :red)])
     (core/make-effect-frame [(core/make-effect-cell pos \* :yellow)])]))

(defn miss-effect
  "Creates a 1-frame white dash at the given position."
  [pos]
  (core/make-effect
    [(core/make-effect-frame [(core/make-effect-cell pos \- :white)])]))

(defn melee-attack
  "Resolves a melee attack: roll(attack) >= defense → hit → max(0, roll(damage) - armor).
   Returns action-result with :message and updated :map."
  [attacker game-map defender ctx]
  (let [registry (:registry ctx)
        atk-dice (core/get-property registry attacker :attack)
        def-val (core/get-property registry defender :defense)
        atk-roll (core/roll atk-dice)
        a-name (attacker-name registry attacker)
        d-name (defender-name registry defender)]
    (if (>= atk-roll def-val)
      ;; Hit
      (let [dmg-dice (core/get-property registry attacker :damage)
            armor (or (core/get-property registry defender :armor) 0)
            raw-dmg (core/roll dmg-dice)
            actual-dmg (max 0 (- raw-dmg armor))
            {:keys [map removed]} (apply-damage game-map defender actual-dmg)
            msg (if removed
                  (str a-name " " (hit-verb attacker) " " d-name
                       " for " actual-dmg " damage, " (kill-suffix defender))
                  (str a-name " " (hit-verb attacker) " " d-name
                       " for " actual-dmg " damage."))]
        {:map map :message msg :hit true})
      ;; Miss
      {:map game-map
       :message (str a-name " " (miss-verb attacker) " " d-name ".")})))

(defn combat-on-bump
  "on-bump callback: performs melee attack with hit/miss effects.
   Returns action-result with :message and :effect."
  [mover game-map bumped ctx]
  (let [pos (core/entity-pos bumped)
        result (melee-attack mover game-map bumped ctx)]
    (-> result
        (assoc :effect (if (:hit result) (hit-effect pos) (miss-effect pos)))
        (dissoc :hit))))

;; AI

(defn wander
  "Act function: moves entity in a random direction via try-move.
   Stands still if blocked."
  [entity game-map ctx]
  (let [dx (- (rand-int 3) 1)
        dy (- (rand-int 3) 1)]
    (core/try-move (:registry ctx) game-map entity dx dy)))

(defn player-target-fn
  "Common target function: returns the player entity from the map."
  [entity game-map ctx]
  (core/get-player game-map))

(defn make-chase-act
  "Returns an act function that chases a target. If adjacent, attacks via melee-attack.
   If pathable (max-distance 20), follows find-path. Otherwise wanders.
   target-fn: (fn [entity game-map ctx]) -> target entity or nil."
  [target-fn]
  (fn [entity game-map ctx]
    (let [target (target-fn entity game-map ctx)]
      (if-not target
        (wander entity game-map ctx)
        (let [registry (:registry ctx)
              entity-pos (core/entity-pos entity)
              target-pos (core/entity-pos target)
              dist (core/chebyshev-distance entity-pos target-pos)]
          (if (<= dist 1)
            ;; Adjacent: attack
            (melee-attack entity game-map target ctx)
            ;; Try pathfinding
            (if-let [path (core/find-path registry game-map entity entity-pos target-pos
                                          {:max-distance 20})]
              (if (>= (count path) 2)
                (let [[nx ny] (second path)
                      [ex ey] entity-pos
                      dx (- nx ex)
                      dy (- ny ey)]
                  (core/try-move registry game-map entity dx dy))
                (wander entity game-map ctx))
              (wander entity game-map ctx))))))))
