(ns ied.events
  (:require
   [re-frame.core :as re-frame]
   [ied.db :as db]
   [day8.re-frame.tracing :refer-macros [fn-traced]]
   [ied.subs :as subs]
   [ied.nostr :as nostr]

   [promesa.core :as p]
   [wscljs.client :as ws]
   [wscljs.format :as fmt]))

(re-frame/reg-event-db
 ::initialize-db
 (fn-traced [_ _]
            db/default-db))

(re-frame/reg-event-fx
 ::navigate
 (fn-traced [_ [_ handler]]
            {:navigate handler}))

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
;; TODO make events a set (?)
 (fn-traced [{:keys [db]} [_ [uri raw-event]]]
            (let [event (nth raw-event 2 raw-event)]
              (println uri raw-event event)
              (when (and
                     (= (first raw-event) "EVENT")
                     (not (some #(= (:id event) (:id %)) (get db :events {}))))
                {:db (update db :events conj event)}))))

(defn handlers
  [ws-uri]
  {:on-message (fn [e] (re-frame/dispatch [::save-event [ws-uri (-> (.-data e)
                                                                    js/JSON.parse
                                                                    (js->clj :keywordize-keys true))]]))
   ; :on-open    #(prn "Opening a new connection")
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
     (ws/send (:socket target-ws) ["REQ" "4242" {:kinds [1 30142]
                                                 :limit 10}] fmt/json)
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
     (re-frame/dispatch [::create-websocket target-ws])
     ; (ws/create (:uri target-ws) (handlers (:uri target-ws)))
     )))

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
   (println "got relays" sockets)
   (let [connected-sockets (filter #(= "connected" (:status %)) sockets)]
     (doseq [socket connected-sockets]
       (.log js/console "sending to relay")
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
                         :content "hello world"
                         :tags [["author" "" (:author resource)]]}]
              {::publish-resource-fx event})))

;; TODO maybe we need some validation before publishing
(re-frame/reg-fx
 ::publish-resource-fx
 (fn [unsignedEvent]
   (p/let [_ (js/console.log (clj->js unsignedEvent))
           signedEvent (.nostr.signEvent js/window (clj->js unsignedEvent))]
     (re-frame/dispatch [::send-to-relays signedEvent]))))

(re-frame/reg-event-fx
 ::login-with-extension
 (fn-traced [cofx [_ _]]
            {::login-with-extension-fx _}))

(re-frame/reg-fx
 ::login-with-extension-fx
 (fn [db _]
   (p/let [pk (.nostr.getPublicKey js/window)]
     (re-frame/dispatch [::save-pk pk]))))

(re-frame/reg-event-db
 ::save-pk
 (fn-traced [db [_ pk]]
            (assoc db :pk pk)))

(re-frame/reg-event-db
 ::logout
 (fn-traced [db _]
            (assoc db :pk nil)))

(re-frame/reg-cofx
 :now
 (fn [cofx _data] ;; _data unused
   (assoc cofx :now (quot (.now js/Date) 1000))))

(defn convert-amb-to-nostr-event
  [json-string created_at]
  (let [parsed-json (js->clj (js/JSON.parse json-string) :keywordize-keys true)
        tags (into [["id" (:id parsed-json)]
                    ["name" (:name parsed-json)]
                    ["image" (:image parsed-json)]]
                   cat [(map (fn [e] ["about" (:id e) (-> e :prefLabel :de)]) (:about parsed-json))
                        (map (fn [e] ["inLanguage" e]) (:inLanguage parsed-json))])
        event {:kind 30142
               :created_at created_at
               :content "Added AMB Resource"
               :tags tags}]
    event))

(re-frame/reg-event-fx
 ::convert-amb-and-publish-as-nostr-event
 [(re-frame/inject-cofx  :now)]
 (fn-traced [cofx [_ json-string]]
            (let [event (convert-amb-to-nostr-event json-string (:now cofx))]
              {::publish-resource-fx event})))

(re-frame/reg-event-db
 ::toggle-selected-events
 (fn [db [_ event]]
   (js/console.log "toggling selected event with id" (:id event))
   (if (some #(= event (:id %)) (:selected-events db))
     (assoc db :selected-events (filter #(not= event (:id %)) (:selected-events db)))
     (update db :selected-events conj event))))

(re-frame/reg-event-fx
 ::add-resources-to-list
 [(re-frame/inject-cofx  :now)]
 (fn [cofx [_ [list resources-to-add]]]
   (let [tags (into [["d" (:d list)
                      "name" (:name list)]]
                    (map (fn [e] (cond
                                   (= 1 (:kind e)) ["e" (:id e)]
                                   (= 30142 (:kind e)) ["a" (str "30142:" (:id e))]))

                         resources-to-add))
         _ (.log js/console (clj->js tags))
         event {:kind 30004
                :created_at (:now cofx)
                :content ""
                :tags tags}]
     {::publish-resource-fx event})))

(re-frame/reg-event-fx
 ::get-lists-for-npub
 (fn [cofx [_ npub]]
   (let [query-for-lists ["REQ"
                          (str "lists-for-npub") ;; TODO maybe make this more explicit later
                          {:authors [(nostr/get-pk-from-npub npub)]
                           :kinds [30004]}]]
     {::request-from-relay query-for-lists})))

(re-frame/reg-fx
 ::request-from-relay
 (fn [query]
   (println "requesting from relay this query: " query)

   (let [sockets @(re-frame/subscribe [::subs/sockets])]
     (doall
      (for [s (filter (fn [s] (= "connected" (:status s))) sockets)]
        (ws/send (:socket s) query fmt/json))))))

