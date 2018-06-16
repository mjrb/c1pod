(ns c1pod.mygpo
  (:refer-clojure :exclude [get])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<! go >! chan pipe]]))

(defn gen-auth [uname pass]
  "generate base64 auth string for Authentication: Basic header"
  (js/btoa (str uname ":" pass)))

(defn auth-request [auth method endpoint]
  "request function for any method endpoint should start with /
  and should correspond to the gpodder docs"
  (http/post "https://mjwintersphp.000webhostapp.com/mygpo.php"
             {:with-credentials? false
              :form-params {:auth auth
                            :method method
                            :endpoint endpoint}}))

(defn get [auth endpoint]
   "auth is generated mygpo/auth, see request for more info on endpoint"
  (auth-request auth "GET" endpoint))

(defn noauth-get [endpoint]
  "get without auth. endpoints off https://www.gpodder.net"
  (http/get (str "https://www.gpodder.net" endpoint)
            {:with-credentials? false}))

(defn post
  ([auth endpoint]
   "auth is generated mygpo/auth, see request for more info on endpoint"
   (auth-request auth "POST" endpoint))
  ([auth endpoint data]
   "also sends data for post request"
   (auth-request auth "POST" endpoint)))

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
                       (throw (js/Error (str "failed to get resource: "
                                             (response :status))))
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
