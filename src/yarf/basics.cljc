(ns yarf.basics
  "Ready-to-use building blocks for roguelike games.
   Provides named tiles, door mechanics, combat, and basic AI."
  (:require [yarf.core :as core]
            #?(:cljs [cljs.core.async :as async :refer [<!]]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]])))

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

;; Ranged combat

(defn projectile-effect
  "Creates a multi-frame projectile effect along the line from `from` to `to`.
   Each frame shows a single character at successive positions (excluding start).
   Default: '*' :cyan, 30ms per frame."
  ([from to]
   (projectile-effect from to \* :cyan))
  ([from to ch color]
   (let [points (rest (core/line from to))]
     (core/make-effect
       (mapv (fn [p] (core/make-effect-frame [(core/make-effect-cell p ch color)])) points)
       30))))

(defn ranged-attack
  "Resolves a ranged attack: roll(ranged-attack) >= defense -> hit -> max(0, roll(ranged-damage) - armor).
   Returns action-result with :message, updated :map, and :hit true on hit.
   Uses type properties :ranged-attack (dice) and :ranged-damage (dice)."
  [attacker game-map defender ctx]
  (let [registry (:registry ctx)
        atk-dice (core/get-property registry attacker :ranged-attack)
        def-val (core/get-property registry defender :defense)
        atk-roll (core/roll atk-dice)
        a-name (attacker-name registry attacker)
        d-name (defender-name registry defender)]
    (if (>= atk-roll def-val)
      (let [dmg-dice (core/get-property registry attacker :ranged-damage)
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
      {:map game-map
       :message (str a-name " " (miss-verb attacker) " " d-name ".")})))

(defn ranged-on-target
  "on-ranged-attack callback: checks LOS, range, and entity at target.
   Resolves ranged attack with projectile + hit/miss effects.
   Uses type property :range (default 6) for max range.
   Returns action-result with :effect and :message."
  [attacker game-map [tx ty] ctx]
  (let [registry (:registry ctx)
        [ax ay] (core/entity-pos attacker)
        from [ax ay]
        to [tx ty]]
    (cond
      (= from to)
      {:map game-map :retry true :no-time true}

      (not (core/line-of-sight? registry game-map from to))
      {:map game-map :message "You can't see there." :no-time true}

      (let [max-range (or (core/get-property registry attacker :range) 6)]
        (> (core/chebyshev-distance from to) max-range))
      {:map game-map :message "Out of range." :no-time true}

      :else
      (let [targets (core/get-entities-at game-map tx ty)
            target (first targets)]
        (if-not target
          {:map game-map :message "Nothing to shoot at." :no-time true}
          (let [proj (projectile-effect from to)
                result (ranged-attack attacker game-map target ctx)
                hit-miss (if (:hit result)
                           (hit-effect to)
                           (miss-effect to))
                combined (core/concat-effects proj hit-miss)]
            (-> result
                (assoc :effect combined)
                (dissoc :hit))))))))

;; AI

#?(:clj
(defn wander
  "Act function: moves entity in a random direction via try-move.
   Stands still if blocked."
  [entity game-map ctx]
  (let [dx (- (rand-int 3) 1)
        dy (- (rand-int 3) 1)]
    (core/try-move (:registry ctx) game-map entity dx dy)))
:cljs
(defn wander
  "Act function (CLJS): moves entity in a random direction via try-move.
   Returns a core.async channel."
  [entity game-map ctx]
  (let [dx (- (rand-int 3) 1)
        dy (- (rand-int 3) 1)]
    (go (core/try-move (:registry ctx) game-map entity dx dy)))))

(defn player-target-fn
  "Common target function: returns the player entity from the map."
  [entity game-map ctx]
  (core/get-player game-map))

(defn- chase-act-logic
  "Core chase logic: returns action-result (not wrapped in channel)."
  [entity game-map ctx target-fn]
  (let [target (target-fn entity game-map ctx)]
    (if-not target
      nil ;; signals: wander
      (let [registry (:registry ctx)
            entity-pos (core/entity-pos entity)
            target-pos (core/entity-pos target)
            dist (core/chebyshev-distance entity-pos target-pos)]
        (if (<= dist 1)
          {:attack target}
          (if-let [path (core/find-path registry game-map entity entity-pos target-pos
                                        {:max-distance 20})]
            (if (>= (count path) 2)
              (let [[nx ny] (second path)
                    [ex ey] entity-pos
                    dx (- nx ex)
                    dy (- ny ey)]
                {:move [dx dy]})
              nil)
            nil))))))

#?(:clj
(defn make-chase-act
  "Returns an act function that chases a target. If adjacent, attacks via melee-attack.
   If pathable (max-distance 20), follows find-path. Otherwise wanders.
   target-fn: (fn [entity game-map ctx]) -> target entity or nil."
  [target-fn]
  (fn [entity game-map ctx]
    (let [decision (chase-act-logic entity game-map ctx target-fn)]
      (cond
        (nil? decision) (wander entity game-map ctx)
        (:attack decision) (melee-attack entity game-map (:attack decision) ctx)
        (:move decision) (let [[dx dy] (:move decision)]
                           (core/try-move (:registry ctx) game-map entity dx dy))))))
:cljs
(defn make-chase-act
  "Returns an act function that chases a target (CLJS). Returns a core.async channel."
  [target-fn]
  (fn [entity game-map ctx]
    (let [decision (chase-act-logic entity game-map ctx target-fn)]
      (go (cond
            (nil? decision) (core/try-move (:registry ctx) game-map entity
                                           (- (rand-int 3) 1) (- (rand-int 3) 1))
            (:attack decision) (melee-attack entity game-map (:attack decision) ctx)
            (:move decision) (let [[dx dy] (:move decision)]
                               (core/try-move (:registry ctx) game-map entity dx dy))))))))
