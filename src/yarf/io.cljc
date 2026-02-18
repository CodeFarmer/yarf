(ns yarf.io
  "Cross-platform save/load. JVM uses gzip+EDN files, CLJS uses localStorage."
  (:require [yarf.core :as core]
            #?(:clj [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])))

#?(:clj
(defn save-single-map
  "Saves a single-map game to a gzipped EDN file (v1)."
  [file-path game-map save-state]
  (core/save-game file-path game-map save-state))
:cljs
(defn save-single-map
  "Saves a single-map game to localStorage (v1)."
  [storage-key game-map save-state]
  (let [save-data (core/prepare-save-data game-map save-state)]
    (.setItem js/localStorage storage-key (pr-str save-data)))))

#?(:clj
(defn save-world
  "Saves a world to a gzipped EDN file (v2)."
  [file-path world save-state]
  (core/save-world file-path world save-state))
:cljs
(defn save-world
  "Saves a world to localStorage (v2)."
  [storage-key world save-state]
  (let [save-data (core/prepare-world-save-data world save-state)]
    (.setItem js/localStorage storage-key (pr-str save-data)))))

#?(:clj
(defn load-save
  "Loads game state from a gzipped EDN file."
  [file-path]
  (core/load-game file-path))
:cljs
(defn load-save
  "Loads game state from localStorage. Returns nil if no save exists."
  [storage-key]
  (when-let [data-str (.getItem js/localStorage storage-key)]
    (let [save-data (edn/read-string data-str)]
      (core/restore-save-data save-data)))))

#?(:cljs
(defn delete-save
  "Deletes a save from localStorage."
  [storage-key]
  (.removeItem js/localStorage storage-key)))

#?(:cljs
(defn save-exists?
  "Returns true if a save exists in localStorage."
  [storage-key]
  (some? (.getItem js/localStorage storage-key))))
