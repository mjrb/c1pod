(ns c1pod.mygpo
  (:refer-clojure :exclude [get])
  (:require [cljs-http.client :as http]
            [tubax.core :as xml]
            [tubax.helpers :as xmlh]
            [cljs.core.async :refer [<! go >! chan]]))
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

(defn check-login [uname pass]
  "does a request to see if login is valid returns boolean channel"
  (go (let [auth (gen-auth uname pass)
            endpoint (str "/api/2/auth/" uname "/login.json")
            response (<! (post auth endpoint))
            status (response :status)]
        (= status 200))))

(defn get-array [request-chan]
  "returns chanel with requested array. if not 200 it puts response code on chanel"
    (go (let [response (<! request-chan)]
          (if (= (response :status) 200)
            (response :body)
            (response :status)
            ))))

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

(defn process-fetch-array [response]
  "checks status and turns it into clj array of hashmap"
  (if (= (.-status response) 200)
    (-> (.json response)
        (.then js->clj)
        (.then (partial map fix-keys)))
    (.-status response)
    ))

(defn get-tag [tag]
  (-> (js/fetch (str api-root "/api/2/tag/"
                      tag
                      "/100.json"))
      (.then process-fetch-array)))

(defonce rss-memos (atom {}))
;;we are manualy memoing to save the request data not the chanel object
;;this alows us to sneak and precache the subfeed on login
(defn get-podcast-rss [rss-url]
  "rss data on a channel"
  (go (if (contains? @rss-memos rss-url)
        (@rss-memos rss-url)
        (let [ response (<! (http/get (str proxy-root "/rssget.php")
                                      {:with-credentials? false
                                       :query-params {:url rss-url}}))]
          (if (= (response :status) 200);
            (try
              (let [rss (xml/xml->clj (response :body))]
                ;;remember the rss
                (swap! rss-memos assoc rss-url rss)
                rss)
              ;;don't cache or render errors
              ;;some rss feeds had bad formated xml
              (catch :default e {}))
            (response :status)
            )))))

(defn get-podcast-rss-prom [rss-url]
  "promise containing rss data"
  (-> (js/fetch (str proxy-root "/rssget.php?url=" rss-url))
      (.then #(.text %))
      (.then #(try (xml/xml->clj %) (catch :default e (print "here333"))))))
  
            

(defn episodes [rss]
  (xmlh/find-all rss {:path [:rss :channel :item]}))

(defn get-subscriptions [auth uname]
  (go (let [response (<! (get auth (str "/subscriptions/" uname ".json")))]
        (if (= (response :status) 200)
          ;;response given as a string of json so we have to parse :(
          (do (->> (response :body)
                   (. js/JSON parse)
                   (js->clj)
                   (map fix-keys)))
          (response :status))
        )))

(defn assoc-subscription-data [[rss subscription]]
  (str "takes a vector pair of rss and subscription and returns a sequence with"
       "the rss episodes with the subscription data")
  (map (fn [episode]
         (assoc episode :subscription subscription))
       (episodes rss)))

(defn episodes-with-subscription-data [subscriptions rss-vec]
  (str "takes in multiple rss feeds and multiple corisponding subscriptions"
       "and gives all of the episodes with their corrisponding subscription"
       "for potential rendering")
  (let [pairs (seq (zipmap rss-vec subscriptions))]
    (flatten (map assoc-subscription-data pairs))))

(defn get-subscription-episodes [auth uname]
  (let [result (chan)]
    (go (let [subscriptions (<! (get-subscriptions auth uname))]
          (if (number? subscriptions)
            (>! result subscriptions)
            (let [episodes-prom (->> (map (fn [subscription]
                        (get-podcast-rss-prom (subscription :url)))
                      subscriptions)
                 (. js/Promise all))]
              (-> (.then episodes-prom (partial map episodes))
                  (.then (partial map flatten))
                         ;(partial episodes-with-subscription-data subscriptions))
                  (.then (fn [episode-list]
                           (go (>! result episode-list))))
                  
                  ))
            )))
    result))
