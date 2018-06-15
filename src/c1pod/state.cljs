(ns c1pod.state
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :refer [<!]]
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
                    :search-query ""
                    :title ""
                    :query-size 200
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
                 :show-login-box? false :logged-in? true
                 :username uname :password pass)
          (set-auth! uname pass))
      (swap! app assoc :login-err "login failed")
      )))

(defn close-logout! []
  (swap! app assoc
         :show-login-box? false
         :login-err ""))

(defn show-login! []
  (swap! app assoc
         :show-login-box? true))

;;search events
(defn set-title! [title]
  (swap! app assoc :title title))

(defn query-top-podcasts! [query]
  ; (swap! app assoc :search-result
  ;       (let [result
  ;             (filter
  ;                     (fn [podcast]
  ;                       (utils/contains? query (.-title podcast)))
  ;                     (@app :query-set))
  ;             ]
  ;         result
  ;         )))
  true)

(defn search-top-podcasts! []
  (set-title! "Top Podcasts")
  (print "here")
  (go (let [response (<! (mygpo/noauth-get (str "/toplist/" (@app :query-size) ".json")))]
        (swap! app assoc
               :query-set (response :body)
               :search-result (response :body)
               :query-function query-top-podcasts!)
        )))

(defn search! [query]
  ((@app :query-function) query))
