(ns ied.opencard.subs
  (:require 
    [re-frame.core :as re-frame]))


(re-frame/reg-sub 
  ::opencard-boards
  (fn [db]
    (:opencard-boards db)))

(comment
(re-frame/subscribe [::opencard-boards])
  )
