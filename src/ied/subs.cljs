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
   (:active-panel db)))

(re-frame/reg-sub
  ::sockets
  (fn [db _]
    (:sockets db)))

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
