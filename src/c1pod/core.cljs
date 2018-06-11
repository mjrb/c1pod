(ns c1pod.core  
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<! go]]
            [c1pod.mygpo :as mygpo]
            [c1pod.state :as state]
            [c1pod.utils :as utils]))

(enable-console-print!)

(defn login [uname pass]
  (js/alert "not yet implemented"))

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
                               :on-click #(login (utils/feild-value "uname")
                                                 (utils/feild-value "pass"))}]
      [:input.btn.btn-primary
       {:value "forgot password?" :type "button"
        :on-click #(js/alert "not yet implemented")}]
      [:input.btn.btn-danger
       {:value "cancel" :type "button"
        :on-click #(swap! state/app assoc :show-login-box? false)
        }]]]))

(defn podcast-list-item [data]
  ;(print data)
  [:li.list-group-item [:img {:src (:scaled_logo_url data)}](:title data)])

(defn toplist [number]
  (let [list-data (atom [1 2 3])
        ]
    
    (reagent/create-class
     {:display-name "toplist"
      :component-will-mount
      #(go (let [response (<! (mygpo/getw (str "/toplist/" number ".json")))]
             (reset! list-data (response :body))
             (print "sus")
             ))
      :reagent-render
      #(into [:ul.list-group]
             (map (fn [data] [podcast-list-item data]) @list-data))
      })))

(defn toolbar []
  [:span.btn-group.container-fluid.toolbar
   [:input.btn.btn-secondary {:type "button" :value "c1pod"}]
   [:input.form-control {:type "text" :placeholder "search"}]
   [:input.btn.btn-primary {:type "button" :value "login"
                            :on-click #(swap! state/app assoc
                                              :show-login-box? true)}]])
(utils/set-content! [:div
                     [:h1 "this is some example content"]
                     [:p "text"]
                     [toplist 30]
                     ])
(defn app []
  [:div
   [:div {:class-name (if (@state/app :show-login-box?)
                       "blur"
                       "noblur")}
   [toolbar]
   [:div.container (@state/app :content)]]
   (if (@state/app :show-login-box?)
     [login-box state/app])])

(reagent/render-component [app]
                          (. js/document (getElementById "app")))
(defn on-js-reload [])
