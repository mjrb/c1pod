(ns c1pod.state
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :refer [<! >! chan]]
            [c1pod.mygpo :as mygpo]
            [c1pod.utils :as utils])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce app (atom {:logged-in? false
                    :auth ""
                    :content []
                    :show-login-box? false
                    :login-err ""
                    :username ""
                    :password ""
                    :search-result []
                    :query-set []
                    :toplist []
                    :search-query ""
                    :title ""
                    :query-size 200
                    :tags []
                    :relevant-tags []
                    :selected-tags []
                    }))
;;login events
(defn set-auth! [uname pass]
  "set http basicauth string for use with api, creates base64 of username:password"
  (swap! app assoc :auth (js/btoa (str uname ":" pass))))

(defn login! [uname pass]
  (go
    (if (<! (mygpo/check-login uname pass))
      (do (swap! app assoc
                 :login-err ""
                 :show-login-box? false
                 :logged-in? true
                 :username uname :password pass)
          (set-auth! uname pass))
      ;; if login is bad
      (swap! app assoc :login-err "login failed")
      )))

(defn close-login! []
  (swap! app assoc
         :show-login-box? false
         :login-err ""))

(defn show-login! []
  (swap! app assoc
         :show-login-box? true))
(defn logout! []
  (swap! app assoc
         :login-err ""
         :logged-in? false
         :auth ""
         :username "" :password ""))

;;search events

(defn search-titles [query coll]
  (filter
   (fn [item]
     (utils/contains? query (:title item)))
   coll))

(defonce search-memos (atom {}))
(defn mygpo-search-memo [query]
  "memoizes mygpo/search results to avoid some network requests"
  (let [result-chan (chan)]
    (go (>! result-chan
            (if (contains? @search-memos query)
              (@search-memos query)
              (let [result (<! (mygpo/search query))]
                (swap! search-memos assoc query result)
                result))))
    result-chan
    ))
(defn query-podcasts! [query]
  (go (let [result (search-titles query (@app :toplist))]
        (swap! app assoc
               :search-result
               (if (> (count result) 10)
                 result
                 (concat result (<! (mygpo-search-memo query))))
               :relevant-tags
               (take 5 (search-titles query (@app :tags)))
               )
        (print (@app :relevant-tags))
        )))

(defn search-top-podcasts! []
  "change app state to search top podcasts"
  ;;toplist has a max of 100
  (go (let [toplist (<! (mygpo/toplist 100))]
        (swap! app assoc
               :title "Top Podcasts"
               :toplist toplist
               :search-result toplist
               :query-function query-podcasts!)
        )))
(defn fetch-tags! []
  (go (swap! app assoc :tags
             (->> (utils/maximize 200 mygpo/get-tags)
                 (<!)
                 (sort-by :usage)
                 (reverse)))))

(defn search! [query]
  "search the current stash for the query"
  ((@app :query-function) query))
