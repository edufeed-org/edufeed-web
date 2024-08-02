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

;; Database Event?
(re-frame/reg-event-fx
 ::save-event
 ;; TODO if EOSE retrieved end connection identified by uri
;; TODO make events a set (?)
 (fn-traced [{:keys [db]} [_ [uri event]]]
            (println uri event)
            (when (= (first event) "EVENT")
              {:db (update db :events conj event)})))

(defn handlers
  [ws-uri]
  {:on-message (fn [e] (re-frame/dispatch [::save-event [ws-uri (-> (.-data e)
                                                                    js/JSON.parse
                                                                    (js->clj :keywordize-keys true))]]))
   :on-open    #(prn "Opening a new connection")
   :on-close   #(prn "Closing a connection")})

(defn create-socket
  [uri]
  (ws/create uri (handlers uri)))

(re-frame/reg-event-fx
 ::create-websocket
 (fn-traced [{:keys [db]} [_ ws]]
            {:db (update db :sockets conj (merge ws {:socket (create-socket (:uri ws))}))}))

(re-frame/reg-event-fx
 ::connect-to-websocket
 (fn-traced [{:keys [db]} [_ _]]
            {::connect-to-websocket-fx _})) ;; TODO identify the socket to connect to

(re-frame/reg-fx
 ::connect-to-websocket-fx
 (fn [_]
   (let [sockets (re-frame/subscribe [::subs/sockets])]
     (println "Sockets: " @sockets)
     (ws/send (:socket (first @sockets)) ["REQ" "4242" {:kinds [1 30142]
                                                        :limit 10}] fmt/json)
     ; (ws/close (:socket (first @sockets))) ;; should be handled otherwise (?)
     )))

;; TODO use id to close socket
;; add connection status to socket
;; render connect / disconnect button based on status
(re-frame/reg-event-fx
 ::close-connection-to-websocket
 (fn-traced [{:keys [db]} [_ _]]
            {::close-connection-to-websocket-fx _}))

(re-frame/reg-fx
 ::close-connection-to-websocket-fx
 (fn [_]
   (let [sockets (re-frame/subscribe [::subs/sockets])]
     (ws/close (:socket (first @sockets))))))

;; TODO 
(re-frame/reg-event-fx
 ::send-to-relays
 (fn-traced [cofx [_ signedEvent]]
            {::send-to-relays-fx signedEvent}))

(re-frame/reg-fx
 ::send-to-relays-fx
 (fn [signedEvent]
   (let [sockets (re-frame/subscribe [::subs/sockets])]
     (.log js/console (clj->js ["EVENT" signedEvent]))
     (ws/send (:socket (first @sockets)) ["EVENT" signedEvent] fmt/json))))

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

(re-frame/reg-event-fx
 ::publish-resource
 [(re-frame/inject-cofx  :now)]
 (fn-traced [cofx [_ resource]]
            (let [event {:kind 30142
                         :created_at (:now cofx)
                         :content "hello world"
                         ::tags []
                         ;;:tags [["author" "" (:author resource)]]
                         }]
              {::publish-resource-fx event})))

(re-frame/reg-fx
 ::publish-resource-fx
 (fn [resource]
   (p/let [signedEvent (.nostr.signEvent js/window (clj->js resource))]
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

(comment
  (quot (.now js/Date) 1000))
