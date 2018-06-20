(ns c1pod.core  
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<! timeout chan pipe]]
            [c1pod.mygpo :as mygpo]
            [c1pod.state :as state]
            [tubax.core :as xml]
            [tubax.helpers :as xmlh]
            [c1pod.utils :as utils])
  (:require-macros [cljs.core.async.macros :refer [go]]))

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

(defn small-episode-list-item [episode]
  (let [title (xmlh/text (xmlh/find-first episode {:tag :title}))
        link (xmlh/text (xmlh/find-first episode {:tag :link}))
        description (xmlh/text (xmlh/find-first episode {:tag :description}))]
  [:li.list-group-item.text-dark
   title
   [:a {:href link} " download/watch"]
   ;;I tried xss but aparently react saves me here
   ;;the descriptions could have html in them, which makes my app look cooler
   [:div {:dangerouslySetInnerHTML {:__html description}}]
  ]))

(defn episode-list-item [episode]
  (let [title (xmlh/text (xmlh/find-first episode {:tag :title}))
        link (xmlh/text (xmlh/find-first episode {:tag :link}))
        description (xmlh/text (xmlh/find-first episode {:tag :description}))
        date (xmlh/text (xmlh/find-first episode {:tag :pubDate}))]
    [:li.list-group-item
     [:div
      [:img.thumbnail {:src ((episode :subscription) :scaled_logo_url)}]
      [:b ((episode :subscription) :title)]
      [:span.btn-group.episode-list-buttons
       [:input.btn.btn-primary
        {:type "button" :value "more"
         :on-click #(state/more! (episode :subscription))}]
       [:a.btn.btn-primary {:href link} "watch/download"]]]
     [:h4 title]
     ;;I tried xss but aparently react saves me here
     ;;the descriptions could have html in them, which makes my app look cooler
     [:div {:dangerouslySetInnerHTML {:__html description}}]
     [:div [:em date] ]
     ]))

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
  (let [box-width (* 0.80 (.-innerWidth js/window))]
    [:div.popup-box {:style {:left (/ (- (.-innerWidth js/window) box-width) 2)
                             :width box-width}}
     [podcast-card podcast]
     [:input.btn.btn-danger
       {:value "close" :type "button"
        :on-click state/close-more!}]
     (cond
       (nil? podcast-rss)    [:em "Loading ..."]
       (number? podcast-rss) [:b "Some podcast data was not available :( sorry"]
       :esle (into [:ul.list-group]
                   (map (fn [episode]
                          [small-episode-list-item episode])
                        (mygpo/episodes podcast-rss))))
     ]))


(defn loading-empty
  ([coll message back-button content]
   (str "abstracts away weather or not to tell the user we're loading,"
        "empty or to display the content that loaded")
   (cond
     (not (empty? @state/loading)) [:em "Loading ..."]
     (empty? coll)                 [:span back-button [:b message]]
     :else                         content))
  ([coll message content]
   "backbuttonless version"
   (loading-empty coll message nil content)))

(defn search-result [title podcasts]
  (loading-empty
   podcasts
   "No results found :( Maybe try rephrasing what your looking for..."
   (into [:ul.list-group
          [:li.list-group-item [:h2 title]]]
         (map (fn [data] [podcast-list-item data])
              podcasts))))

(defn tag-bar [relevant-tags selected-tags]
  (if (and (empty? relevant-tags) (empty? selected-tags))
    [:center "type for tags"]
    (vec (concat [:center.tagbar.nowrap [:span "select tags: "]]
            (map (fn [tag] [tag-component tag true])
                 selected-tags)
            (map (fn [tag] [tag-component tag false])
                 relevant-tags)
            ))))

(defn podcast-search []
  [:div
   [tag-bar (@state/app :relevant-tags) (@state/app :selected-tags)]
   [search-result
    (if (and (empty? (@state/app :query))
             (empty? (@state/app :selected-tags)))
      "Top Podcasts"
      "Search Results")
    (@state/app :search-result)]])

(defn back-to-search-button []
  [:input.btn.btn-secondary
         {:type "button" :value "Back to Search"
          :on-click #(do(state/search-top-podcasts!)
                        (state/set-content! [podcast-search]))
          }])

(declare subscription-list subscription-feed)
(defn sub-tools []
  [:span.sub-tools.btn-group [back-to-search-button]
   [:input.btn.btn-secondary {:type "button" :value "feed"
                              :on-click #(state/set-content! [subscription-feed])}]
   [:input.btn.btn-secondary {:type "button" :value "subscribed podcasts"
                              :on-click #(state/set-content! [subscription-list])}]])
(defn subscription-list []
  (loading-empty
   (@state/app :subscriptions)
   (str "No results found :( try subscribing to some podcasts"
        " or upload subscriptions to gpodder.net from any other clients you use")
   [back-to-search-button]
   [:div
    [sub-tools]
    (into [:ul.list-group
           [:li.list-group-item [:h2 "Subscription Feed"]]]
          (map (fn [podcast] [podcast-list-item podcast])
               (@state/app :subscriptions)))]
   ))

(defn subscription-feed []
  (loading-empty
   @state/subscription-feed
   "More loading ..."
   [back-to-search-button]
   [:div
    [sub-tools]
    (into [:ul.list-group
           [:li.list-group-item
            [:h2 "Subscription Feed"]]]
          (map (fn [[_ episode]] [episode-list-item episode])
               ;;limit results to display to save time loading images
               (take @state/subscription-feed-limit @state/subscription-feed)
               ))
    [:input {:type "button" :value "more episodes!"
             :on-click state/feed-more!}]]))

(defn searchbar []
  [:input.form-control
   {:type "text" :placeholder "search" :id "query"
    :on-change #(state/search! (utils/feild-value "query"))}])

(defn auth-buttons []
  (if (@state/app :logged-in?)
    [:span.auth-buttons.btn-group
     [:input.btn.btn-primary
      {:type "button" :value "logout"
       :on-click #(do (state/logout!)
                      (state/set-content! [podcast-search]))}]
     [:input.btn.btn-primary
      {:type "button" :value "subscriptions"
       :on-click #(do (state/show-subscriptions!)
                      (state/set-content! [subscription-feed]))}]]
    [:input.btn.btn-primary
     {:type "button" :value "login"
      :on-click state/show-login!}]))

(defn toolbar []
  [:div.toolbar
   [:span.btn-group.container-fluid.searchbar
    [logo] [searchbar] [auth-buttons]]])


(defn app []
  [:div
   [:div {:class-name (if (@state/app :blur?) "blur" "noblur")}
    [toolbar]
    [:div.container (@state/app :content)]]
   (if (@state/app :show-login-box?)
     [login-box])
   (if (@state/app :show-more?)
     [more (@state/app :selected-podcast) (@state/app :selected-podcast-rss)]
     )
   ])

(state/set-content! [podcast-search])
(state/search-top-podcasts!)
(state/fetch-tags!)
(state/fetch-stopwords!)
(state/check-relogin!)
(reagent/render-component [app]
                          (. js/document (getElementById "app")))
(defn on-js-reload [])
