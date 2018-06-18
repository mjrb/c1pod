(ns c1pod.state
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :refer [<! >! chan]]
            [c1pod.mygpo :as mygpo]
            [c1pod.utils :as utils]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce app (atom {:logged-in? false
                    :auth ""
                    :blur? false
                    :content []
                    :show-login-box? false
                    :show-more? false
                    :login-err ""
                    :username ""
                    :password ""
                    :search-result []
                    :query-set []
                    :toplist []
                    :tags []
                    :search-query ""
                    :title ""
                    :query-size 200
                    :relevant-tags []
                    :selected-tags #{}
                    :selected-podcast {}
                    :selected-podcast-rss nil
                    :stopwords #{}
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
                 :blur? false
                 :username uname :password pass)
          (set-auth! uname pass))
      ;; if login is bad
      (swap! app assoc :login-err "login failed")
      )))

(defn close-login! []
  (swap! app assoc
         :show-login-box? false
         :blur? false
         :login-err ""))

(defn show-login! []
  (swap! app assoc
         :show-login-box? true
         :blur? true))

(defn logout! []
  (swap! app assoc
         :login-err ""
         :logged-in? false
         :auth ""
         :username "" :password ""))

;;search-util
(defn search-titles [query coll]
  (filter
   (fn [item]
     (utils/contains? query (:title item)))
   coll))

(defn remove-stopwords [query]
  "removes stopwords from a query."
  (let [words (-> (.toLowerCase query)
                  (.split " "))
        good-words (remove (partial contains? (@app :stopwords))
                           words)]
    (str/join " " good-words)))


(defn fetch-tags! []
  (go (swap! app assoc :tags
             (->> (utils/maximize 200 mygpo/get-tags)
                  (<!)
                  (sort-by :usage)
                  (reverse)))))

(defn fetch-stopwords! []
  (go (swap! app assoc :stopwords
             (let [stopwords (-> (<! (mygpo/get-stopwords))
                                 (:body)
                                 (.split "\n"))]
               (apply hash-set stopwords)))))

;;tags
(defn search-tags! [query]
  (swap! app assoc :relevant-tags
         (->> (search-titles query (@app :tags))
              (remove (partial contains? (@app :selected-tags)))
              (take 5))))


(defn deselect-tag! [tag]
  (swap! app assoc :selected-tags
         (disj (@app :selected-tags) tag))
  (search-tags! (@app :query)))

;;search-podcasts
(defonce search-memos (atom {}))
(defn mygpo-search-memo [query]
  "memoizes mygpo/search results to avoid some network requests"
  ;;we cant use the builtin memoize because we want to memo the contents
  ;;of the channel not the chanel that the function returns
  (let [result-chan (chan)]
    (go (>! result-chan
            (if (contains? @search-memos query)
              (@search-memos query)
              (let [result (<! (mygpo/search query))]
                (swap! search-memos assoc query result)
                result))))
    result-chan
    ))

(defonce tag-search-memos (atom {}))
(defn single-tag-search [tag]
  ;;we cant use the builtin memoize because we want to memo the contents
  ;;of the promise not the promise that the function returns
  ;; also a i used a promise because .all is easier than doing it with
  ;; core/async. 
   (if (contains? @tag-search-memos tag)
     (js/Promise (@tag-search-memos tag))
     (-> (mygpo/get-tag tag)
         (.then (fn [response]
                  (swap! search-memos assoc tag response)
                  response
                  )))))

(defn tag-search-memo [query]
  (let [result-chan (chan)]
    (go (let [search-result-proms (map (fn [tag]
                                         (single-tag-search (tag :tag)))
                                       (@app :selected-tags))
              search-result-prom (. js/Promise all search-result-proms)]
          (.then search-result-prom
                 (fn [search-result]
                   ;;flatten results and return
                   (go (>! result-chan (search-titles query (flatten search-result))))))
          ))
    result-chan))

(defn query-podcasts! [query]
  "query for podcasts on the main page"
  (set-loading! query)
  (if (= query "")
    (do (swap! app assoc :search-result (@app :toplist))
        (done-loading! query))
    (go (let [result (search-titles query (@app :toplist))
              ;;only do the request if there's not enough results in the toplist
              search (if (< (count result) 10)
                       (<! (mygpo-search-memo query))
                       nil)
              tag-search (<! (tag-search-memo query))]
          ;;don't show these results if we're not looking for them anymore
          (if (= query (@app :query))
            (do
              (swap! app assoc
                     :search-result
                     (->> (if (> (count result) 10)
                            (concat tag-search result)
                            (concat tag-search result search))
                          (utils/distinct-by :url)
                          (sort-by (fn [podcast] (- (podcast :subscribers))))
                          ))
              (search-tags! query)
            )))
        (done-loading! query))))

(defn select-tag! [tag]
  (query-podcasts! (@app :query))
  (swap! app assoc :selected-tags
         (conj (@app :selected-tags) tag))
  (search-tags! (@app :query)))

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
        (done-loading! "")
        )))

(defn search! [query]
  (str "search the current stash for the query. this "
       "allows us to change the query behavior on the fly")
  (let [good-query (remove-stopwords query)]
    (swap! app assoc :query good-query)
    ((@app :query-function) good-query)))

;;podcast details
(defn more! [podcast]
  (. js/window scroll 0 0)
  (go (swap! app assoc :selected-podcast-rss
             (<! (mygpo/get-podcast-rss (podcast :url)))))
  (swap! app assoc
         :selected-podcast podcast
         :blur? true
         :show-more? true
         ))

(defn close-more! []
  (swap! app assoc :blur? false :show-more? false :selected-podcast-rss nil))

;;subscriptions
(defn query-subscriptions! [query]
  )

(defn search-subscriptions! []
  (go (let [subscriptions (<! (mygpo/get-subscriptions (@app :auth) (@app :username)))]
        (print "s")
        ((print subscriptions) 0)
        (print "s
               :query-function query-subscriptions!)
        )))
