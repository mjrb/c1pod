(ns c1pod.utils
  (:refer-clojure :exclude [contains?])
  (:require [c1pod.state :as state]
            [reagent.core :as reagent]))

(defn feild-value [id]
  "get the value of the input with id"
  (.-value (.getElementById js/document id)))

(defn label
  ([label-text id elem-vec]
   "wraps an element with a label and br"
  [:span
   [:label {:for id} label-text]
   elem-vec
   [:br]])
  ([label-text]
   "generates a label with a br"
   [:span
    [:label label-text]
    [:br]]))

(defn set-content! [element-vec]
  (let [element (reagent/as-element element-vec)]
    (print (. js/JSON stringify element))
    (swap! state/app assoc :content element)))

(defn contains? [string other]
  "case insensitive version of contains?"
  (try
    (some? (re-find (re-pattern (str "(?i)" string)) other))
    (catch js/Error e e)
  ))
