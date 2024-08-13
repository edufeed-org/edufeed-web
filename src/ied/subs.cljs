(ns ied.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::name
 (fn [db]
   (:name db)))

(re-frame/reg-sub
 ::active-panel
 (fn [db _]
   (get-in db [:route :panel])))

(re-frame/reg-sub
 ::sockets
 (fn [db _]
   (:sockets db)))

(re-frame/reg-sub
 ::connected-sockets
 :<- [::sockets]
 (fn [[sockets]]
   (filter #(= (:status %) "connected") sockets)))

(re-frame/reg-sub
 ::events
 (fn [db _]
   (:events db)))

(re-frame/reg-sub
 ::show-add-event
 (fn [db _]
   (:show-add-event db)))

(re-frame/reg-sub
 ::pk
 (fn [db _]
   (:pk db)))

(re-frame/reg-sub
 ::default-relays
 (fn [db _]
   (:default-relays db)))

(re-frame/reg-sub
 ::selected-events
 (fn [db _]
   (:selected-events db)))

(re-frame/reg-sub
 ::route-params
 (fn [db _]
   (get-in db [:route :route :route-params])))

(re-frame/reg-sub
 ::lists
 (fn [db _]
   (filter #(= 30004 (:kind %)) (:events db))))
