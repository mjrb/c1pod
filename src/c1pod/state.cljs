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

;;this makes sure that all the requests are done loading before
;;the app says it cant find anything. each request conjoins on its query
;;its loading and disjoints it when its done. (hash-set)
(defonce loading (atom #{}))
(defn set-loading! [query]
  (swap! loading conj query))

(defn done-loading! [query]
  (swap! loading disj query))

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
  (set-loading! query)
  (go (let [result (search-titles query (@app :toplist))]
          (if (= query (@app :query))
            (swap! app assoc
                   :search-result
                   (distinct (if (> (count result) 10)
                               result
                               (concat result (<! (mygpo-search-memo query)))))
                   :relevant-tags
                   (take 5 (search-titles query (@app :tags))))
                ))
          (done-loading! query)))

(defn search-top-podcasts! []
  "change app state to search top podcasts"
  (set-loading! "")
  ;;toplist has a max of 100
  (go (let [toplist (<! (mygpo/toplist 100))]
        (swap! app assoc
               :title "Top Podcasts"
               :toplist toplist
               :search-result toplist
               :query-function query-podcasts!)
        ;;done loading
        (done-loading! "")
        )))

(defn fetch-tags! []
  (go (swap! app assoc :tags
             (->> (utils/maximize 200 mygpo/get-tags)
                 (<!)
                 (sort-by :usage)
                 (reverse)))))

(defn search! [query]
  (str "search the current stash for the query. this "
       "allows us to change the query behavior on the fly")
  (swap! app assoc :query query)
  ((@app :query-function) query))
