(ns ied.subs
  (:require
   [re-frame.core :as re-frame]
   [clojure.string :as str]
   [clojure.set :as set]
   [ied.nostr :as nostr]))

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
 ::events
 (fn [db _]
   (:events db)))


(re-frame/reg-sub
 ::metadata-events
 (fn [db _]
   (sort-by :created_at #(> %1 %2) (filter (fn [e] (= 30142 (:kind e))) (:events db)))))

(re-frame/reg-sub
 ::show-add-event
 (fn [db _]
   (:show-add-event db)))

(re-frame/reg-sub
 ::pk
 (fn [db _]
   (:pk db)))

(re-frame/reg-sub
 ::npub
 (fn [db _]
   (nostr/get-npub-from-pk (:pk db))))

(re-frame/reg-sub
 ::nsec
 (fn [db _]
   (nostr/sk-as-nsec (:sk db))))

(re-frame/reg-sub
 ::default-relays
 (fn [db _]
   (:default-relays db)))

(re-frame/reg-sub
 ::selected-events
 (fn [db _]
   (:selected-events db)))

(re-frame/reg-sub
 ::selected-list-ids
 (fn [db]
   (:selected-list-ids db)))

(re-frame/reg-sub
 ::selected-lists
 :<- [::lists-of-user]
 :<- [::selected-list-ids]
 (fn [[lists-of-user selected-list-ids]]
   (filter #(contains? selected-list-ids (:id %)) lists-of-user)))

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

(defn d-id-not-in-deleted-list-ids
  [d-id deleted-list-ids]
  (println d-id
           deleted-list-ids)
  (not (contains? deleted-list-ids d-id)))

(defn most-recent-by-d-tag
  [events]
  (->> events
       (group-by (fn [event]
                   (some (fn [[tag-type tag-value]]
                           (when (= tag-type "d")
                             tag-value))
                         (:tags event))))
       (map (fn [[d-tag events]]
              (apply max-key :created_at events)))
       (into [])))

(re-frame/reg-sub
 ::lists
 :<- [::list-kinds]
 :<- [::events]
 :<- [::deleted-list-ids]
 (fn [[list-kinds events deleted-lists]]
   (let [all-lists (filter #(and (some #{(:kind %)} list-kinds)
                                 #_(d-id-not-in-deleted-list-ids (get-d-id-from-tags (:tags %)) deleted-lists))
                           events)
         most-recent-lists (most-recent-by-d-tag all-lists)]
     (.log js/console "all lists: " (clj->js all-lists))
     (.log js/console "most recent lists: " (clj->js most-recent-lists))
     most-recent-lists)))

(re-frame/reg-sub
 ::feed-events
 :<- [::metadata-events]
 :<- [::lists]
 (fn [[md-events lists]]
   (sort-by :created_at #(> %1 %2) (concat md-events lists))))

(re-frame/reg-sub
 ::lists-of-user
 :<- [::pk]
 :<- [::lists]
 (fn [[pk lists]]
   (set (filter #(= pk (:pubkey %)) lists))))

(re-frame/reg-sub
 ::lists-for-npub
 :<- [::route-params]
 :<- [::lists]
 (fn [[route-params lists]]
   (let [npub (:npub route-params)]
     (set (filter #(= (nostr/get-pk-from-npub npub) (:pubkey %)) lists)))))

(comment
  (seq [1])
  (seq #{1}))

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
         ;; TODO find list events after the timestamp of the last kind5 event
         ;; get d-tags of that events
         ;; filter that d-tags out of the deleted-list-ids
         deleted-list-ids (get-d-ids-from-events kind-5-events)]
     deleted-list-ids)))

(re-frame/reg-sub
 ::missing-events-from-lists
 :<- [::events]
 :<- [::lists]
 (fn [[events lists]]
   (let [event-ids-from-list-tags (->> (into [] cat (map (fn [l] (:tags l)) lists))
                                       (filter #(= (or "a" "e") (first %))) ;; just a and e tags
                                       (map second) ;; just the id
                                       (map nostr/extract-id-from-tag)
                                       (set))
         event-ids (set (map #(:id %) events))
         missing-events (set/difference event-ids-from-list-tags event-ids)]
     ; (.log js/console "Missing IDs: " (clj->js missing-events))
     missing-events)))

(re-frame/reg-sub
 ::show-lists-modal
 (fn [db _]
   (:show-lists-modal db)))

(re-frame/reg-sub
 ::show-create-list-modal
 (fn [db _]
   (:show-create-list-modal db)))

(re-frame/reg-sub
 ::show-event-data-modal
 (fn [db _]
   (:show-event-data-modal db)))

(re-frame/reg-sub
 ::selected-event
 (fn [db _]
   (:selected-event db)))

(re-frame/reg-sub
 ::events-in-list
 (fn [db [_ event-ids]]
   (filter (fn [e] (contains? event-ids (:id e))) (:events db))))

(re-frame/reg-sub
  ::visited-at
  (fn [db _]
    (:visited-at db)))
