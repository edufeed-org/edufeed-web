(ns ied.core
  (:require
   [reagent.dom :as rdom]
   [re-frame.core :as re-frame]
   [ied.events :as events]
   [ied.routes :as routes]
   [ied.views :as views]
   [ied.config :as config]))

(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/main-panel] root-el)))

(defn init []
  (routes/start!)
  (re-frame/dispatch-sync [::events/initialize-db])
  (re-frame/dispatch [::events/connect-to-default-relays])
  (re-frame/dispatch [::events/set-visit-timestamp])
  (dev-setup)
  (mount-root))
