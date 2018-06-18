(ns c1pod.core  
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<! go go-loop timeout chan pipe]]
            [c1pod.mygpo :as mygpo]
            [c1pod.state :as state]
            [tubax.core :as xml]
            [tubax.helpers :as xmlh]
            [c1pod.utils :as utils]))

(enable-console-print!)

(defn podcast-card [podcast]
  [:span [:a {:href (podcast :mygpo_link)}
    [:img.thumbnail {:src (podcast :scaled_logo_url)}]
    (podcast :title)]
    [:div "subscribers: "[:b (podcast :subscribers)]]])

(defn podcast-list-item [podcast]
  [:li.list-group-item
   [podcast-card podcast]
   [:input.btn.btn-primary {:type "button" :value "more"
                            :on-click #(state/more! podcast)}]
   ])

(defn episode-list-item [episode]
  [:li (xmlh/text (xmlh/find-first episode {:tag :title}))])

(defn logo []
  [:input.btn.btn-secondary {:type "button" :value "c1pod"}])

(defn tag-component [tag selected]
  [:span.tag-component.nowrap
   {:on-click (if selected
                #(state/deselect-tag! tag)
                #(state/select-tag! tag))}
   (str ":" (tag :tag) " ") [:b (tag :usage) " "]
   (if selected
     [:b "X"]
     [:b "+"])])

;;popups
(defn login-box []
  (let [box-width 300]
    [:div.popup-box {:style {:left (/ (- (.-innerWidth js/window) box-width) 2)
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

(defn more [podcast podcast-rss]
  (let [box-width 300]
    [:div.popup-box {:style {:left (/ (- (.-innerWidth js/window) box-width) 2)
                   :width box-width}}
     [podcast-card podcast]
     (if (nil? podcast-rss)
       [:em "Loading ..."]
       (into [:ul]
             (map (fn [episode]
                    [episode-list-item episode])
                  (mygpo/episodes podcast-rss))))
     [:input.btn.btn-danger
       {:value "close" :type "button"
        :on-click state/close-more!}]]))

;;mid level components
(defn search-result [title podcasts]
  (if-not (empty? @state/loading)
    [:em "Loading ..."]
    (if (empty? podcasts)
      [:b "No results found :( Maybe try rephrasing what your looking for..."]
    (into [:ul.list-group
           [:li.list-group-item [:h2 title]]]
          (map (fn [data] [podcast-list-item data])
               podcasts)))))

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
       :on-click state/search-subscriptions!}]]
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
   [:div {:class-name (if (@state/app :blur?) "blur" "noblur")}
   [toolbar]
    [:div.container [search-result (@state/app :title)
                     (@state/app :search-result)]]]
   (if (@state/app :show-login-box?)
     [login-box])
   (if (@state/app :show-more?)
     [more (@state/app :selected-podcast) (@state/app :selected-podcast-rss)])])

(state/search-top-podcasts!)
(state/fetch-tags!)
(state/fetch-stopwords!)

(reagent/render-component [app]
                          (. js/document (getElementById "app")))
(defn on-js-reload [])
