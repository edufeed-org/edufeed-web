(ns ied.views
  (:require
   [re-frame.core :as re-frame]
   [ied.events :as events]
   [ied.routes :as routes]
   [ied.subs :as subs]
   [reagent.core :as reagent]))

;; add resource form
(defn add-resource-form
  [name uri author]
  (let [s (reagent/atom {:name name
                         :uri uri
                         :author author})]
    (fn []
      [:form {:on-submit (fn [e]
                           (.preventDefault e)
                            ;; do something with the state @s
                           )}
       [:label {:for name} "Name: "]
       [:input {:type :text :name :name
                :value (:name @s)
                :on-change (fn [e]
                             (swap! s assoc :name (-> e .-target .-value)))}]
       [:label {:for uri} "Uri: "]
       [:input {:type :text :name :uri
                :value (:uri @s)
                :on-change (fn [e]
                             (swap! s assoc :uri (-> e .-target .-value)))}]
       [:label {:for uri} "Author: "]
       [:input {:type :text :name :author
                :value (:author @s)
                :on-change (fn [e]
                             (swap! s assoc :author (-> e .-target .-value)))}]
       [:button {:on-click #(re-frame/dispatch [::events/publish-resource {:name (:name @s) ;; TODO this should be sth like build event
                                                                           :uri (:uri @s)
                                                                           :author (:author @s)}])}
        "Publish Resource"]])))

(defn add-resource-by-json []
  (let [s (reagent/atom {:json-string ""})]
    (fn []
      [:form {:on-submit (fn [e] (.preventDefault e))}
       [:textarea {:on-change (fn [e]
                                (swap! s assoc :json-string (-> e .-target .-value)))}]
       [:button {:class "btn btn-warning"
                 :on-click #(re-frame/dispatch [::events/convert-amb-and-publish-as-nostr-event (:json-string @s)])}
        "Publish as Nostr Event"]])))

;; events
(defn events-panel []
  (let [events (re-frame/subscribe  [::subs/events])
        selected-events @(re-frame/subscribe  [::subs/selected-events])
        show-add-event (re-frame/subscribe [::subs/show-add-event])]
    [:div {:class "border-2 rounded"}
     [:p {:on-click #(re-frame/dispatch [::events/toggle-show-add-event])}
      (if @show-add-event "X" "Add Resource!")]
     (when @show-add-event
       [add-resource-form])
     [:p (str "Num of events: " (count @events))]
     (if (> (count @events) 0)
       (doall
        (for [event @events]
          [:li {:key (:id event)} (get event :content "")
           [:input {:type "checkbox"
                    :on-click #(re-frame/dispatch [::events/toggle-selected-events event])}]]))
       [:p "no events there"])
     [:button {:class "btn"
               :disabled (not (boolean (seq selected-events)))
               :on-click #(re-frame/dispatch [::events/add-resources-to-list [{:d "unique-id-1"
                                                                               :name "Test List SC"}
                                                                              selected-events]])}
      "Add To Lists"]]))

;; relays
(defn add-relay-form
  [name uri]
  (let [s (reagent/atom {:name name
                         :uri uri})]
    (fn []
      [:form {:on-submit (fn [e]
                           (.preventDefault e)
                            ;; do something with the state @s
                           )}
       [:label {:for name} "Name: "]
       [:input {:type :text :name :name
                :value (:name @s)
                :on-change (fn [e]
                             (swap! s assoc :name (-> e .-target .-value)))}]
       [:label {:for uri} "Uri: "]
       [:input {:type :text :name :uri
                :value (:uri @s)
                :on-change (fn [e]
                             (swap! s assoc :uri (-> e .-target .-value)))}]
       [:button {:on-click #(re-frame/dispatch [::events/create-websocket {:name (:name @s)
                                                                           :id (random-uuid)
                                                                           :uri (:uri @s)}])}

        "Add Relay"]])))

(defn relays-panel
  []
  (let [sockets (re-frame/subscribe [::subs/sockets])]
    [:div
     [add-relay-form]

     (if (> (count @sockets) 0)
       [:ul
        (doall
         (for [socket @sockets]
           [:li {:key (:id socket)}
            [:span (:name socket)]
            [:span (:status socket)]
            [:button {:class "btn"
                      :disabled (not= "connected" (:status socket))
                      :on-click #(re-frame/dispatch [::events/load-events (:uri socket)])} "Load events"]
            (if (not= (:status socket) "connected")
              [:button {:class "btn"
                        :on-click #(re-frame/dispatch [::events/connect-to-websocket (:uri socket)])} "Connect"]
              [:button {:class "btn"
                        :on-click #(re-frame/dispatch [::events/close-connection-to-websocket (:uri socket)])} "Disconnect"])

            [:button {:class "btn btn-error"
                      :on-click #(re-frame/dispatch [::events/remove-websocket socket])} "Remove relay"]]))]
       [:p "No relays found"]
       ;(re-frame/dispatch [::events/connect-to-default-relays])
       )]))

;; Header

(defn header []
  (let [pk (re-frame/subscribe [::subs/pk])]
    [:div
     [:a {:on-click #(re-frame/dispatch [::events/navigate :home])}
      "home"]
     "|"
     [:a {:on-click #(re-frame/dispatch [::events/navigate :add-resource])}
      "add resource"]
     "|"
     [:a {:on-click #(re-frame/dispatch [::events/navigate :about])}
      "about"]
     "|"
     [:a {:on-click #(re-frame/dispatch [::events/navigate :settings])}
      "settings"]
     "|"
     (if @pk
       [:a {:on-click #(re-frame/dispatch [::events/logout])} "logout"]
       [:a {:on-click #(re-frame/dispatch [::events/login-with-extension])} "login"])]))

;; Add Resource Panel

(defn add-resource-panel []
  [:div
   [:h1 "Add Resource"]
   [add-resource-form]
   [add-resource-by-json]])

(defmethod routes/panels :add-resource-panel [] [add-resource-panel])

;; Settings
(defn settings-panel []
  [:div
   [:h1 "Settings"]
   [relays-panel]])

(defmethod routes/panels :settings-panel [] [settings-panel])

;; Home
(defn home-panel []
  (let [name (re-frame/subscribe [::subs/name])
        events (re-frame/subscribe [::subs/events])]
    [:div
     [:h1
      (str "Hello from " @name ". This is the Home Page.")]

     [events-panel]
     [:p (count @events)]]))

(defmethod routes/panels :home-panel [] [home-panel])

;; about
(defn about-panel []
  [:div
   [:h1 "This is the About Page."]
   [:div
    [:a {:on-click #(re-frame/dispatch [::events/navigate :home])}
     "go to Home Page"]]])

(defmethod routes/panels :about-panel [] [about-panel])

;; npub
(defn npub-view-panel []
  (let [sockets @(re-frame/subscribe [::subs/sockets])
        route-params @(re-frame/subscribe [::subs/route-params])
        lists @(re-frame/subscribe [::subs/lists])
        missing-events-from-lists @(re-frame/subscribe [::subs/missing-events-from-lists])
        _ (when (seq missing-events-from-lists) (re-frame/dispatch [::events/query-for-event-ids [sockets missing-events-from-lists]]))]
    [:div
     [:h1 (str "Hello Npub: " (:npub route-params))]
     (if-not (seq lists)
       [:p "No lists there...yet"]
       [:div {:class "p-2"} "Got some lists"
        (doall
         (for [l lists]
           [:div {:class "p-2"
                  :key (:id l)}
            [:li

             [:p (str "ID: " (:id l))]
             [:p (str "Name: " (first (filter #(= "d" (first %)) (:tags l))))]
             ;; TODO filter tags for already being in events
             ;; for all that are not send a query
             [:p (str "Tags: " (:tags l))]
             [:button {:class "btn btn-error"
                       :on-click #(re-frame/dispatch [::events/delete-list l])} "Delete List"]]]))])
     [:button {:class "btn"
               :on-click
               #(re-frame/dispatch [::events/get-lists-for-npub [sockets (:npub route-params)]])} "Load lists"]]))

(defmethod routes/panels :npub-view-panel [] [npub-view-panel])

;; main
(defn main-panel []
  (let [active-panel (re-frame/subscribe [::subs/active-panel])]
    [:div
     [header]
     [:div
      (routes/panels @active-panel)]]))

(comment

  (.log js/console "Hello World"))
