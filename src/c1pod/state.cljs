(ns c1pod.state
  (:require [reagent.core :as reagent :refer [atom]]))
(defonce app (atom {:logged-in? false
                    :auth ""
                    :content []
                    :show-login-box? false
                    }))
