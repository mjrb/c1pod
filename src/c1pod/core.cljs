(ns c1pod.core  
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<! go go-loop]]
            [c1pod.mygpo :as mygpo]
            [c1pod.state :as state]
            [c1pod.utils :as utils]))

(enable-console-print!)

(defn login-box []
  (let [box-width 300]
    [:div.login-box {:style {:left (/ (- (.-innerWidth js/window) box-width) 2)
                             :width box-width}}
     (utils/label "email" "uname"
                  [:input.form-control {:type "text" :id "uname"}])
     (utils/label "password" "pass"
                  [:input.form-control {:type "password" :id "pass"}])
     [:span.btn-group
      [:input.btn.btn-primary {:type "button" :value "login"
                               :on-click #(state/login! (utils/feild-value "uname")
                                                        (utils/feild-value "pass"))}]
      [:input.btn.btn-primary
       {:value "forgot password?" :type "button"
        :on-click #(set! js/window.location.href
                         "https://gpodder.net/register/restore_password")}]
      [:input.btn.btn-danger
       {:value "cancel" :type "button"
        :on-click state/close-login!}]]
     (if-not (empty? (@state/app :login-err))
       [:div.login-err.alert-danger.alert (@state/app :login-err)]
       [:div.invisible])]
  ))

(defn podcast-list-item [data]
  [:li.list-group-item
   [:img.thumbnail {:src (:scaled_logo_url data)}]
   (:title data)])

(defn tag-component [tag selected]
  [:span.tag-component.nowrap (str ":" (tag :tag) " ") [:b (tag :usage)]
   (if selected
     [:span "x"])
   ])

(defn search-result [title podcasts]
  (if (empty? podcasts)
    [:em "Loading ..."]
    (into [:ul.list-group
           [:li.list-group-item [:h2 title]]]
          (map (fn [data] [podcast-list-item data])
               podcasts))))

(defn logo []
  [:input.btn.btn-secondary {:type "button" :value "c1pod"}])

(defn searchbar []
  [:input.form-control
   {:type "text" :placeholder "search" :id "query"
    :on-change #(state/search! (utils/feild-value "query"))}])

(defn auth-buttons []
  (if (@state/app :logged-in?)
    [:span.auth-buttons.btn-group
     [:input.btn.btn-primary
      {:type "button" :value "logout"
       :on-click state/logout!}]
     [:input.btn.btn-primary
      {:type "button" :value "subscriptions"
       :on-click #(js/alert "not yet implemented")}]]
    [:input.btn.btn-primary
     {:type "button" :value "login"
      :on-click state/show-login!}]))

(defn tag-bar [relevant-tags selected-tags]
  (if (and (empty? relevant-tags) (empty? selected-tags))
    [:center "type for tags"]
    (vec (concat [:center.tagbar.nowrap [:span "select tags: "]]
            (map (fn [tag] [tag-component tag true])
                 selected-tags)
            (map (fn [tag] [tag-component tag false])
                 relevant-tags)
            ))))

(defn toolbar []
  [:div.toolbar
   [:span.btn-group.container-fluid.searchbar
    [logo] [searchbar] [auth-buttons]]
    [tag-bar (@state/app :relevant-tags) (@state/app :selected-tags)]])

(defn app []
  [:div
   [:div {:class-name (if (@state/app :show-login-box?) "blur" "noblur")}
   [toolbar]
    [:div.container [search-result (@state/app :title)
                     (@state/app :search-result)]]]
   (if (@state/app :show-login-box?)
     [login-box state/app])])

(state/search-top-podcasts!)
(state/fetch-tags!)
(reagent/render-component [app]
                          (. js/document (getElementById "app")))
(defn on-js-reload [])

(go (let [result (<! (mygpo/search "m"))]
      (print (map #(% :subscribers) result))
      (print (count result))))

