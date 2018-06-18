(ns c1pod.mygpo
  (:refer-clojure :exclude [get])
  (:require [cljs-http.client :as http]
            [tubax.core :as xml]
            [tubax.helpers :as xmlh]
            [cljs.core.async :refer [<! go >! chan pipe]]))
(defonce api-root "http://gpodder.net")
(defonce proxy-root "https://mjwintersphp.000webhostapp.com")

(defn gen-auth [uname pass]
  "generate base64 auth string for Authentication: Basic header"
  (js/btoa (str uname ":" pass)))

(defn auth-request [auth method endpoint]
  "request function for any method endpoint should start with /
  and should correspond to the gpodder docs"
  (http/post (str proxy-root "/mygpo.php")
             {:with-credentials? false
              :form-params {:auth auth
                            :method method
                            :endpoint endpoint}}))

(defn get [auth endpoint]
   "auth is generated mygpo/auth, see request for more info on endpoint"
  (auth-request auth "GET" endpoint))

(defn noauth-get [endpoint]
  "get without auth. endpoints off https://www.gpodder.net"
  (http/get (str api-root endpoint)
            {:with-credentials? false}))

(defn post [auth endpoint]
   "auth is generated mygpo/auth, see request for more info on endpoint"
   (auth-request auth "POST" endpoint))

(defn check-login
  "does a request to see if login is valid returns boolean channel"
  ([uname pass]
   (let [ok? (chan)
         auth (gen-auth uname pass)]
     (go
       (let [endpoint (str "/api/2/auth/" uname "/login.json")
             response (<! (post auth endpoint))
             status (response :status)]
         (if (= status 200)
           (>! ok? true)
           (>! ok? false))))
     ;;return ok? chan
     ok?
     )))

(defn get-array [request-chan]
  "returns chanel with requested array. if not 200 it puts response code on chanel"
  (let [result (chan)]
    (go (let [response (<! request-chan)]
          (>! result (if (= (response :status) 200)
                       (response :body)
                       (response :status)
                       ))))
    result))

(defn get-tags [count]
  "returns chanel with tags. if not status 200 it puts response code on chanel"
  (get-array (noauth-get (str "/api/2/tags/" count ".json"))))

(defn toplist [amount]
  "returns chanel with top podcasts. if not status 200 it puts response code on chanel"
  (get-array (noauth-get (str "/toplist/" amount ".json"))))

(defn search [query]
  (get-array (noauth-get (str "/search.json?q=" query))))

(defn get-stopwords []
  "retrieves list of postgres stopwords"
  (http/get (str proxy-root "/english.stop.php")
            {:with-credentials? false}))

(defn get-episode [podcast-url episode-url]
  (http/get (str api-root "/api/2/data/episode.json")
            {:with-credentials? false
             :query-params {:podcast (js/encodeURIComponent podcast-url)
                            :url (js/encodeURIComponent episode-url)}}))

(defn fix-keys [target-map]
  "makes all  first-level keys in a map keywords"
  ;; destructure key val pair and restructure
  (into {} (map (fn [[key val]]
                  [(keyword key) val])
                target-map)))
(defn get-tag [tag]
  (-> (js/fetch (str api-root "/api/2/tag/"
                      tag
                      "/100.json"))
      (.then (fn [response]
               (if (= (.-status response) 200)
                 (-> (.json response)
                     (.then js->clj)
                     (.then (partial map fix-keys))
                     )
                 (.-status response)
                 )))))

(defn get-podcast-rss [rss-url]
  (let [result (chan)]
    (go (let [ response (<! (http/get (str proxy-root "/rssget.php")
                                      {:with-credentials? false
                                       :query-params {:url rss-url}}))]
          (>! result
              (if (= (response :status) 200)
                (xml/xml->clj (response :body))
                (response :status)
                ))))
    result))

(defn episodes [rss]
  (xmlh/find-all rss {:path [:rss :channel :item]}))

(defn get-subscriptions [uname auth]
  (let [result (chan)]
    (go (let [response (<! (get auth (str "/subscriptions/" uname ".json")))]
          (>! result
              (if (= (response :status) 200)
                (response :body)
                (response :status)
                ))
          ))
    result))
