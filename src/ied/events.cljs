(ns ied.events
  (:require
   [re-frame.core :as re-frame]
   [ied.db :as db]
   [day8.re-frame.tracing :refer-macros [fn-traced]]
   [ied.subs :as subs]
   [ied.nostr :as nostr]

   [promesa.core :as p]
   [wscljs.client :as ws]
   [wscljs.format :as fmt]
   [clojure.string :as str]
   [clojure.set :as set]

   ["js-confetti" :as jsConfetti]))

(def list-kinds [30001 30004])

(re-frame/reg-event-db
 ::initialize-db
 (fn-traced [_ _]
            db/default-db))

(re-frame/reg-event-fx
 ::navigate
 (fn-traced [_ [_ handler]]
            {:dispatch [::set-visit-timestamp]
             :navigate handler}))

(re-frame/reg-event-fx
 ::set-active-panel
 (fn-traced [{:keys [db]} [_ active-panel]]
            {:db (assoc db :active-panel active-panel)}))

(re-frame/reg-event-fx
 ::set-route
 (fn-traced [{:keys [db]} [_ route]]
            {:db (assoc db :route route)}))

;; Database Event?
(re-frame/reg-event-fx
 ::save-event
 ;; TODO if EOSE retrieved end connection identified by uri
 (fn-traced [{:keys [db]} [_ [uri raw-event]]]
            (let [event (nth raw-event 2 raw-event)]
              (when (and
                     (= (first raw-event) "EVENT"))
                {:db (update db :events conj event)}))))

(defn handlers
  [ws-uri]
  {:on-message (fn [e] (re-frame/dispatch [::save-event [ws-uri (-> (.-data e)
                                                                    js/JSON.parse
                                                                    (js->clj :keywordize-keys true))]]))
   :on-open    #(re-frame/dispatch  [::load-events ws-uri])
   :on-close   #(prn "Closing a connection")
   :on-error   (fn [e] (.log js/console "Error with uri: " ws-uri (clj->js e))
                 (re-frame/dispatch  [::update-ws-connection-status ws-uri "error"]))})

(re-frame/reg-event-db
 ::update-ws-connection-status
 (fn [db [_ ws-uri status]]
   (let [target-ws (first (filter #(= ws-uri (:uri %)) (:sockets db)))]
     (assoc db
            :sockets
            (assoc (:sockets db)
                   (.indexOf (:sockets db) target-ws)
                   (merge target-ws {:status status}))))))

(re-frame/reg-event-fx
 ::load-events
 (fn-traced [cofx [_ ws-uri]]
            {::load-events-fx ws-uri}))

(re-frame/reg-fx
 ::load-events-fx
 (fn [ws-uri]
   (println "loading events")

   (let [sockets (re-frame/subscribe [::subs/sockets])
         target-ws (first (filter #(= ws-uri (:uri %)) @sockets))]
     (ws/send (:socket target-ws) ["REQ" "424242" {:kinds [30004 30142]
                                                   :limit 100}] fmt/json)
     ; (ws/close (:socket (first @sockets))) ;; should be handled otherwise (?)
     )))

(defn create-socket
  [uri]
  (println "creating socket with uri" uri)
  (ws/create uri (handlers uri)))

(re-frame/reg-event-fx
 ::create-websocket
 ;; ws {:id uuid
 ;;     :uri
 ;;     :name
 ;;     :status connected | disconnected | error}
 (fn-traced [{:keys [db]} [_ ws]]
            (if (some #(= (:uri ws) (:uri %)) (:sockets db))
              (doall
               (println "uri already there" (:uri ws))
               {:db (assoc db
                           :sockets
                           (assoc (:sockets db)
                                  (.indexOf (:sockets db) ws)
                                  (merge ws {:socket (create-socket (:uri ws))
                                             :status "connected"})))})
              (doall
               (println "uri not yet known")
               {:db (update db :sockets conj (merge ws {:socket (create-socket (:uri ws))
                                                        :status "connected"}))}))))

(re-frame/reg-event-fx
 ::connect-to-websocket
 (fn-traced [{:keys [db]} [_ ws-uri]]
            {::connect-to-websocket-fx ws-uri}))

(re-frame/reg-fx
 ::connect-to-websocket-fx
 (fn [ws-uri]
   (let [sockets (re-frame/subscribe [::subs/sockets])
         target-ws (first (filter #(= ws-uri (:uri %)) @sockets))]
     (re-frame/dispatch [::create-websocket target-ws]))))

;; TODO use id to close socket
;; add connection status to socket
;; render connect / disconnect button based on status
(re-frame/reg-event-fx
 ::close-connection-to-websocket
 (fn-traced [{:keys [db]} [_ ws-uri]]
            {::close-connection-to-websocket-fx ws-uri}))

(re-frame/reg-fx
 ::close-connection-to-websocket-fx
 (fn [ws-uri]
   (let [sockets (re-frame/subscribe [::subs/sockets])]
     (ws/close (:socket (first (filter #(= ws-uri (:uri %)) @sockets))))
     (re-frame/dispatch [::update-ws-connection-status ws-uri "disconnected"]))))

(re-frame/reg-event-fx
 ::connect-to-default-relays
 (fn-traced [cofx [_]]
            (let [default-relays (re-frame/subscribe [::subs/default-relays])]
              {::connect-to-default-relays-fx @default-relays})))

(re-frame/reg-fx
 ::connect-to-default-relays-fx
 (fn [default-relays]
   (doall
    (for [r default-relays]
      (re-frame/dispatch [::create-websocket r])))))

;; TODO 
(re-frame/reg-event-fx
 ::send-to-relays
 (fn-traced [cofx [_ signedEvent]]
            (let [sockets (-> cofx :db :sockets)]
              {::send-to-relays-fx [sockets signedEvent]})))

(re-frame/reg-fx
 ::send-to-relays-fx
 (fn [[sockets signedEvent]]
   (let [connected-sockets (filter #(= "connected" (:status %)) sockets)]
     (doseq [socket connected-sockets]
       (ws/send (:socket socket) ["EVENT" signedEvent] fmt/json)))))

(re-frame/reg-event-db
 ::update-websockets
 (fn [db [_ sockets]]
   (assoc db :sockets sockets)))

(re-frame/reg-event-fx
 ::remove-websocket
 (fn-traced [{:keys [db]} [_ socket]]
            {::remove-websocket-fx (:id socket)}))

(re-frame/reg-fx
 ::remove-websocket-fx
 (fn [id]
   (let [sockets (re-frame/subscribe [::subs/sockets])
         filtered (filter #(not= id (:id %)) @sockets)] ;; TODO maybe this can also be done using the URI
     (re-frame/dispatch [::update-websockets filtered]))))

(re-frame/reg-event-db
 ::toggle-show-add-event
 (fn [db _]
   (assoc db :show-add-event (not (:show-add-event db)))))

;; TODO just pass an event here that is then passed on to signing
(re-frame/reg-event-fx
 ::publish-resource
 [(re-frame/inject-cofx  :now)]
 (fn-traced [cofx [_ resource]]
            (let [event {:kind 30142
                         :created_at (:now cofx)
                         :content ""
                         :tags [["d" (:id resource)]
                                ["id" (:id resource)]
                                ["author" "" (:author resource)]
                                ["name" (:name resource)]]}]
              {::sign-and-publish-event [event (-> cofx :db :sk)]})))

;; TODO maybe we need some validation before publishing
(re-frame/reg-fx
 ::sign-and-publish-event
 (fn [[unsignedEvent sk]]
   (if (nostr/valid-unsigned-nostr-event? unsignedEvent)
     (p/let [_ (js/console.log (clj->js unsignedEvent))
             _ (js/console.log (nostr/sk-as-hex sk))
             signedEvent (if (nil? sk)
                           (.nostr.signEvent js/window (clj->js unsignedEvent))
                           (nostr/finalize-event unsignedEvent sk))
             _ (js/console.log "Signed event: " (clj->js signedEvent))]
       (re-frame/dispatch [::send-to-relays signedEvent]))
     (.error js/console "Event is not a valid nostr event: " (clj->js unsignedEvent)))))

(re-frame/reg-event-fx
 ::login-with-extension
 (fn-traced [cofx [_ _]]
            {::login-with-extension-fx _}))

(re-frame/reg-fx
 ::login-with-extension-fx
 (fn [db _]
   (p/let [pk (.nostr.getPublicKey js/window)]
     (re-frame/dispatch [::save-pk pk]))))

(re-frame/reg-event-fx
 ::save-pk
 (fn-traced [{:keys [db]} [_ pk]]
            {:db (assoc db :pk pk)
             :dispatch [::get-lists-for-npub (nostr/get-npub-from-pk pk)]}))

(re-frame/reg-event-db
 ::logout
 (fn-traced [db _]
            (assoc db :pk nil :sk nil)))

(re-frame/reg-cofx
 :now
 (fn [cofx _data] ;; _data unused
   (assoc cofx :now (quot (.now js/Date) 1000))))

(re-frame/reg-cofx
 :sk
 (fn [cofx _]
   (assoc cofx :sk (:sk cofx))))

(re-frame/reg-cofx
 :pk
 (fn [cofx _]
   (assoc cofx :pk (:pk cofx))))

(defn convert-amb-to-nostr-event
  [json-string created_at]
  (let [parsed-json (js->clj (js/JSON.parse json-string) :keywordize-keys true)
        tags (into [["d" (:id parsed-json)]
                    ["r" (:id parsed-json)]
                    ["id" (:id parsed-json)]
                    ["name" (:name parsed-json)]
                    ["description" (:description parsed-json "")]
                    ["image" (:image parsed-json "")]]
                   cat [(map (fn [e] ["about" (:id e) (-> e :prefLabel :de)]) (:about parsed-json))
                        (map (fn [e] ["inLanguage" e]) (:inLanguage parsed-json))])
        event {:kind 30142
               :created_at created_at
               :content "Added AMB Resource with d-tag"
               :tags tags}]
    event))

(re-frame/reg-event-fx
 ::convert-amb-and-publish-as-nostr-event
 [(re-frame/inject-cofx  :now)]
 (fn-traced [cofx [_ json-string]]
            (let [event (convert-amb-to-nostr-event json-string (:now cofx))]
              {::sign-and-publish-event [event (-> cofx :db :sk)]})))

(re-frame/reg-event-fx
 ::toggle-selected-events
 (fn [{:keys [db]} [_ event]]
   (let [selected-event-ids (set (map (fn [e] (:id e)) (:selected-events db)))]
     (if (and (seq (:selected-events db))
              (contains? selected-event-ids (:id event)))
       {:db (assoc db  :selected-events (filter #(not= (:id event) (:id %)) (:selected-events db)))}
       {:db (update db :selected-events conj event)}))))

(re-frame/reg-event-db
 ::toggle-selected-list-ids
 (fn [db [_ id]]
   (println "Toggle list ids: " id)
   (let [in-selected-list-ids (contains? (:selected-list-ids db) id)]
     (if in-selected-list-ids
       (update db :selected-list-ids disj id)
       (update db :selected-list-ids conj id)))))

(re-frame/reg-event-fx
 ::add-metadata-event-to-list
 [(re-frame/inject-cofx  :now)]
 (fn [cofx [_ [list resources-to-add]]]
   (let [existing-tags  (:tags list)
         existing-tags-set  (set existing-tags)
         tags-to-add (filter #(not (contains? existing-tags-set %))
                             (map (fn [e] (cond
                                            (= 1 (:kind e)) ["e" (:id e)]
                                            (= 30142 (:kind e)) (nostr/build-kind-30142-tag e)))
                                  resources-to-add))
         new-tags (vec (concat existing-tags tags-to-add))
         event {:kind 30004
                :created_at (:now cofx)
                :content ""
                :tags new-tags}]
     {::sign-and-publish-event [event (:sk cofx)]})))

(re-frame/reg-event-fx
 ::add-metadata-events-to-lists
 (fn [cofx [_ [events lists]]]
   (let [dispatch-events (mapv (fn [l] [::add-metadata-event-to-list [l events]]) lists)
         _ (.log js/console (clj->js dispatch-events))]
     {:fx [[:dispatch-n dispatch-events]]})))

(defn sanitize-subscription-id [s]
  (str/join "" (take 64 s)))

(defn make-sub-id [prefix id]
  (-> (str prefix id)
      (sanitize-subscription-id)))

(re-frame/reg-event-fx
 ::get-lists-for-npub
 (fn [{:keys [db]} [_ npub]]
   (let [query-for-lists ["REQ"
                          (make-sub-id "lists-" npub) ;; TODO maybe make this more explicit later
                          {:authors [(nostr/get-pk-from-npub npub)]
                           :kinds list-kinds}]
         sockets (:sockets db)]
     {::request-from-relay [sockets query-for-lists]
      :dispatch [::get-deleted-lists-for-npub [sockets npub]]})))

(re-frame/reg-event-fx
 ::get-deleted-lists-for-npub
 (fn [cofx [_ [sockets npub]]]
   (let [query-for-deleted-lists ["REQ"
                                  (make-sub-id  "deleted-lists-" npub) ;; TODO maybe make this more explicit later
                                  {:authors [(nostr/get-pk-from-npub npub)]
                                   :kinds [5]}]]
     {::request-from-relay [sockets query-for-deleted-lists]})))

(comment)

(re-frame/reg-fx
 ::request-from-relay
 (fn [[sockets query]]
   (doall
    (for [s (filter (fn [s] (= "connected" (:status s))) sockets)]
      (ws/send (:socket s) query fmt/json)))))

(re-frame/reg-event-fx
 ::query-for-event-ids
 (fn [db [_ [sockets event-ids]]]
   (let [query ["REQ"
                "RAND42"
                {:ids event-ids}]]

     {::request-from-relay [sockets query]})))

(defn cleanup-list-name [s]
  (-> s
      (str/replace #"\s" "-")
      (str/replace #"[^a-zA-Z0-9]" "-")))

(comment
  (cleanup-list-name "this is gönna be an awesüm+ l]st"))

(re-frame/reg-event-fx
 ::create-new-list
 [(re-frame/inject-cofx  :now)]
 (fn [cofx [_ name]]
   (let [tags [["d" (cleanup-list-name name)]
               ["name" name]]
         create-list-event {:kind 30004
                            :created_at (:now cofx)
                            :content ""
                            :tags tags}]
     {::sign-and-publish-event [create-list-event (:sk cofx)]})))

(re-frame/reg-event-fx
 ::delete-list
 [(re-frame/inject-cofx  :now)]
 (fn [cofx [_ l]]
   (let [deletion-event {:kind 5
                         :created_at (:now cofx)
                         :content ""
                         :tags [(cond
                                  (= 1 (:kind l))
                                  ["e" (:id l)]
                                  (some #{(:kind l)} (-> cofx :db :list-kinds))
                                  ["a" (str (:kind l) ":" (:pubkey l) ":" (second (first (filter
                                                                                          #(= "d" (first %))
                                                                                          (:tags l)))))])]}]
     {::sign-and-publish-event [deletion-event (:sk cofx)]})))

(re-frame/reg-event-db
 ::toggle-show-lists-modal
 (fn [db _]
   (assoc db :show-lists-modal (not (:show-lists-modal db)))))

(re-frame/reg-event-db
 ::toggle-show-create-list-modal
 (fn [db _]
   (assoc db :show-create-list-modal (not (:show-create-list-modal db)))))

(re-frame/reg-event-db
 ::toggle-show-event-data-modal
 (fn [db [_ event]]
   (assoc db
          :show-event-data-modal (not (:show-event-data-modal db))
          :selected-event event)))

(re-frame/reg-event-fx
 ::delete-event-from-list
 [(re-frame/inject-cofx  :now)]
 (fn [cofx [_ [event list]]]
   (let [filtered-tags (filterv (fn [t] (not= (:id event) (nostr/extract-id-from-tag (second t)))) (:tags list))
         _ (println (:tags list))
         _ (.log js/console "Filtered Tags: " (clj->js filtered-tags))
         event {:kind 30004
                :created_at (:now cofx)
                :content ""
                :tags filtered-tags}]
     {::sign-and-publish-event [event (:sk cofx)]})))

(re-frame/reg-event-db
 ::create-sk
 (fn [db [_]]
   (let [sk (nostr/generate-sk)
         pk (nostr/get-pk-from-sk sk)]
     (assoc db :sk sk :pk pk))))

(re-frame/reg-fx
 ::get-amb-json-from-uri
 (fn [uri]
   (p/let [raw-html (js/fetch  uri {:headers {"Access-Control-Allow-Origin" "*"}})]
     (println raw-html))))

(comment
  (p/->> (js/fetch "https://oersi.org/resources/aHR0cHM6Ly9lZ292LWNhbXB1cy5vcmcvY291cnNlcy9hcmJlaXRlbnVuZGZ1ZWhyZW5fdXBfMjAyMi0x")
         (println)))

(re-frame/reg-event-fx
 ::publish-amb-uri-as-nostr-event
 (fn [db [_ uri]]
   {::get-amb-json-from-uri uri}))

(re-frame/reg-event-fx
 ::add-confetti
 (fn [_ _]
   (let [confetti-instance (new jsConfetti)]
     (.addConfetti confetti-instance (clj->js {:emojis ["😺" "🐈‍⬛" "🦄"]})))
   {}))

(re-frame/reg-event-fx
 ::set-visit-timestamp
 [(re-frame/inject-cofx  :now)]
 (fn [cofx [_]]
   {:db (assoc (:db cofx) :visited-at (:now cofx))}))


