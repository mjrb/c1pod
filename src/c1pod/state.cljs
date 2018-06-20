(ns c1pod.state
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :refer [<! >! chan]]
            [c1pod.mygpo :as mygpo]
            [c1pod.utils :as utils]
            [tubax.helpers :as xmlh]
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
                    :loading-feed false
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

(defn set-content! [content]
  (swap! app assoc :content content))

;;login events
(defn set-auth! [uname pass]
  "set http basicauth string for use with api, creates base64 of username:password"
  (swap! app assoc :auth (js/btoa (str uname ":" pass))))

(declare fetch-subscription-feed!)

(defn check-relogin! []
  (let [uname (. js/localStorage getItem "uname")
        auth (. js/localStorage getItem "auth")]
    (if-not (or (empty? uname) (empty? auth))
      (swap! app assoc
             :logged-in? true
             :auth auth
             :username uname)
      (fetch-subscription-feed!)
      )))

(defn login! [uname pass]
  (go
    (if (<! (mygpo/check-login uname pass))
      (do (swap! app assoc
                 :login-err ""
                 :show-login-box? false
                 :logged-in? true
                 :blur? false
                 :username uname :password pass)
          (. js/localStorage setItem "uname" uname)
          (set-auth! uname pass)
          ;;very bad but the authentication scheme for gpodder leaves me no choice
          ;;other than not implementing this
          (. js/localStorage setItem "auth" (@app :auth))
          (go (fetch-subscription-feed!)))
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
         :username "" :password "")
  (. js/localStorage removeItem "uname")
  (. js/localStorage removeItem "auth")
  )

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
    (go (if (contains? @search-memos query)
          (@search-memos query)
          (let [result (<! (mygpo/search query))]
            (swap! search-memos assoc query result)
            result))))

(defonce tag-search-memos (atom {}))
(defn single-tag-search [tag]
  ;;we cant use the builtin memoize because we want to memo the contents
  ;;of the promise not the promise that the function returns
  ;; also, i used a promise because .all is easier than doing it with
  ;; core/async. 
   (if (contains? @tag-search-memos tag)
     (js/Promise. (fn [resolve reject]
                    (resolve (@tag-search-memos tag))))
     (-> (mygpo/get-tag tag)
         (.then (fn [response]
                  (swap! tag-search-memos assoc tag response)
                  response
                  )))))

(defn tag-search-memo [query]
  (let [result-chan (chan)]
    (go (let [search-result-proms
              ;;search each of the tags and resulive all the promises
              (map (fn [tag] (single-tag-search (tag :tag)))
                   (@app :selected-tags))
              search-result (. js/Promise all search-result-proms)]
          (.then search-result
                 (fn [search-result]
                   ;;flatten results and return
                   (go (->> (js->clj search-result)
                            (flatten)
                            (search-titles query)
                            (>! result-chan)))))
          ))
    result-chan))

(defn combine-results [query tag-search top-search api-search]
  "appropriately combines results based on query and selected tags"
  (->> (cond
         ;;if query is "" and we have not tags selected show toplist
         (and (empty? query) (empty? (@app :selected-tags)))
         (@app :toplist)
         ;;if query is just "" and we have tags, then show the tags
         (empty? query)
         tag-search
         ;;else mix all the results.
         :else (concat tag-search top-search api-search))
       ;;make distinct and sort by greatest amount of subs
       ;;sorty-by defaults to lowest first so negating it makes it largest
       (utils/distinct-by :url)
       (sort-by (fn [podcast] (- (podcast :subscribers))))
       ))

(defn query-podcasts! [query]
  "query for podcasts on the main page"
  (set-loading! query)
  (go
    ;;update tags first because its quicker
    (search-tags! query)
    ;;get search results from api enpoints
    (let [top-search (search-titles query (@app :toplist))
          ;;only do the request if there's not enough results in the toplist
          api-search (if (< (count top-search) 10)
                       (<! (mygpo-search-memo query))
                       [])
          tag-search (<! (tag-search-memo query))]
      ;;don't show these results if we're not looking for them anymore
      (if (= query (@app :query))
        (swap! app assoc
               :search-result
               (combine-results query tag-search top-search api-search)
               ))
    (done-loading! query)
    )))

(defn select-tag! [tag]
  (swap! app assoc :selected-tags
         (conj (@app :selected-tags) tag))
  (query-podcasts! (@app :query))
  (search-tags! (@app :query)))

(defn search-top-podcasts! []
  "change app state to search top podcasts"
  (set-loading! "")
  ;;toplist has a max of 100
  (go (let [toplist (<! (mygpo/toplist 100))]
        (swap! app assoc
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
(defonce subscription-feed-limit (atom 20))
(defonce subscription-feed (atom (sorted-map)))
(defn fetch-subscription-feed! []
  (reset! subscription-feed-limit 20)
  (reset! subscription-feed (sorted-map))
  (go (let [subs (<! (mygpo/get-subscriptions
                      (@app :auth)
                      (@app :username)))]
        (doseq [sub subs]
          (go (try (let [eps  (mygpo/episodes (<! (mygpo/get-podcast-rss (sub :url))))]
                     (swap! subscription-feed into
                            (for [ep eps]
                              (let [date (xmlh/text (xmlh/find-first ep {:tag :pubDate}))]
                              [(utils/rfc822-val date) (assoc ep :subscription sub)])))
                     ) (catch :default e (print e))))))))
(defn feed-more! []
  (swap! subscription-feed-limit + 20))

(defn show-subscriptions! []
  (set-loading! :list)
  (go (let [subscriptions (<! (mygpo/get-subscriptions
                               (@app :auth)
                               (@app :username)))]
        (fetch-subscription-feed!)
        (swap! app assoc
               :query-function (fn[]) ;no searching in subscriptions
               :subscriptions subscriptions
               :search-result subscriptions)
        (done-loading! :list)
        )))
