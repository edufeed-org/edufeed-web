(ns ied.subs
  (:require
   [re-frame.core :as re-frame]
   [clojure.string :as str]
   [clojure.set :as set]))

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
 ::list-kinds
 (fn [db _]
   (:list-kinds db)))

(defn get-d-id-from-tags
  [tags]
  (second (first (filter (fn [t] (= "d" (first t))) tags))))

(comment
  (get-d-id-from-tags [["d" "https://wtcs.pressbopub/digitalliteracy/"]]))

(defn d-id-not-in-deleted-list-ids
  [d-id deleted-list-ids]
  (println d-id
           deleted-list-ids)
  (not (contains? deleted-list-ids d-id)))

(re-frame/reg-sub
 ::lists
 :<- [::list-kinds]
 :<- [::events]
 :<- [::deleted-list-ids]
 (fn [[list-kinds events deleted-lists]]
   (let [all-lists (filter #(and (some #{(:kind %)} list-kinds)
                                 (d-id-not-in-deleted-list-ids (get-d-id-from-tags (:tags %)) deleted-lists))
                           events)]
     all-lists)))

(defn extract-d-id-from-tags
  [s]
  (println s)
  (let [parts (str/split s #":")]
    (nth parts 2 nil)))

(defn get-d-ids-from-events
  [events]
  (->> (into [] cat (map (fn [l] (:tags l)) events))
       (filter #(= "a" (first %))) ;; just a and e tags
       (map second) ;; just the id
       (map extract-d-id-from-tags)
       (set)))

(re-frame/reg-sub
 ::deleted-list-ids
 (fn [db _]
   (let [kind-5-events (filter (fn [e] (= 5 (:kind e))) (:events db))
         deleted-list-ids (get-d-ids-from-events kind-5-events)]
     (.log js/console "kind-5-events: " (clj->js kind-5-events))
     (.log js/console "Deleted list-ids " (clj->js deleted-list-ids))
     deleted-list-ids)))

(defn extract-id-from-tags
  [s]
  (println s)
  (let [parts (str/split s #":")]
    (if (>= (count parts) 2)
      (second parts)
      s)))

(re-frame/reg-sub
 ::missing-events-from-lists
 :<- [::events]
 :<- [::lists]
 (fn [[events lists]]
   (let [event-ids-from-list-tags (->> (into [] cat (map (fn [l] (:tags l)) lists))
                                       (filter #(= (or "a" "e") (first %))) ;; just a and e tags
                                       (map second) ;; just the id
                                       (map extract-id-from-tags)
                                       (set))
         event-ids (set (map #(:id %) events))
         missing-events (set/difference event-ids-from-list-tags event-ids)]
     (.log js/console "Missing IDs: " (clj->js missing-events))
     missing-events)))

(comment
  (>= 2 (count (str/split "3013:fjkldj:https://jfdajdfklö" #":")))

  (extract-id-from-tags "30142:e2d8b8e3381386976a57091199d23:https://wtcs.pressbooks.pub/digitalliteracy/"))
