(ns c1pod.utils
  (:refer-clojure :exclude [contains?])
  (:require [reagent.core :as reagent]
            [cljs.core.async :refer [<! go >! chan]]))

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

;; (defn set-content! [element-vec]
;;   (let [element (reagent/as-element element-vec)]
;;     (print (. js/JSON stringify element))
;;     (swap! state/app assoc :content element)))

(defn contains? [string other]
  "case insensitive version of contains? with regex."
  (try
    (some? (re-find (re-pattern (str "(?i)" string)) other))
    (catch js/Error e )))
(defn maximize [start func]
  (str "used to get the max resources from a network request"
       "given an initial amount, and a function that tames an amount and"
       "returns an array on a channelit will attempt to use that function"
       "and grow the collenction exponentially until it can't get any more")
  (let [all (chan)]
    (go (loop [amount start]
      (let [some (<! (func amount))]
        (if (= (count some) amount)
          (recur (* amount 4))
          (>! all some)
          ))))
    all))
