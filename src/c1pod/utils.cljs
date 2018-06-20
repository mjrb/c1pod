(ns c1pod.utils
  (:refer-clojure :exclude [contains?])
  (:require [reagent.core :as reagent]
            [tubax.helpers :as xmlh]
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

(defn distinct-by [keyfn coll]
  (map first (vals (group-by keyfn coll))))

(defn month-val [month]
  (condp = month
      "Jan" 1
      "Feb" 2
      "Mar" 3
      "Apr" 4
      "May" 5
      "Jun" 6
      "Jul" 7
      "Aug" 8
      "Sep" 9
      "Oct" 10
      "Nov" 11
      "Dec" 12
    ))
;;D, d M Y H:i:s T
(defn rfc822-val [date-string]
  "turns RFC822 date into a number to compare accurate to the hour. NOT PRECISE"
  ;;ignore weekday and timezone
  (let [[_ day month year time _] (.split date-string " ")
        hour (first (.split time ":"))]
    ;;negate so largest is first
    (- (+ (js/parseInt hour)
          (* 24 (js/parseInt day))
          (* 24 30 (month-val month)) ;amproximate
          (* 24 30 365 (js/parseInt year)))
    )))
