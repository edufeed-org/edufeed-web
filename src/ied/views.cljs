(ns ied.views
  (:require
   [re-frame.core :as re-frame]
   [cljs.pprint :refer [pprint]]
   [ied.events :as events]
   [ied.routes :as routes]
   [ied.subs :as subs]
   [ied.nostr :as nostr]
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
       [:button {:class "btn btn-warning"
                 :on-click #(re-frame/dispatch [::events/publish-resource {:name (:name @s) ;; TODO this should be sth like build event
                                                                           :id (:uri @s)
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

;; TODO try again using xhrio
(defn add-resosurce-by-uri []
  (let [uri (reagent/atom {:uri ""})]
    (fn []
      [:form {:on-submit (fn [e] (.preventDefault e))}
       [:label {:for "uri"} "URI: "]
       [:input {:id "uri"
                :on-change (fn [e]
                             (swap! uri assoc :uri (-> e .-target .-value)))}]
       [:button {:class "btn btn-warning"
                 :on-click #(re-frame/dispatch [::events/publish-amb-uri-as-nostr-event (:uri @uri)])}
        "Publish as Nostr Event"]])))

;; event data modal
(defn event-data-modal []
  (let [visible? @(re-frame/subscribe [::subs/show-event-data-modal])
        selected-event @(re-frame/subscribe [::subs/selected-event])]
    (when visible?
      [:dialog {:open visible? :class "modal"}
       [:div {:class "modal-box relative flex flex-col"}
        [:h3 {:class "text-lg font-bold"} (nostr/get-name-from-metadata-event selected-event)]
        [:pre (with-out-str (pprint selected-event))]
        [:p {:class "py-4"} "Press ESC key or click outside to close"]]

       [:form {:on-click #(re-frame/dispatch [::events/toggle-show-event-data-modal])
               :method "dialog" :class "modal-backdrop"}
        [:button "close"]]])))

;; metadata event component
(defn metadata-event-component [event]
  (let [selected-events @(re-frame/subscribe  [::subs/selected-events])
        visited-at @(re-frame/subscribe [::subs/visited-at])
        now (quot (.now js/Date) 1000)
        diff (- now visited-at)
        _ (when (>= diff 5)
            (re-frame/dispatch [::events/add-confetti]))]
    [:div
     {:class "animate-flyIn card bg-base-100 w-96 shadow-xl min-h-[620px]"}
     [:figure
      [:img
       {:class "h-48 object-cover"
        :src
        (nostr/get-image-from-metadata-event event)
        :alt ""}]]
     [:div
      {:class "card-body"}
      [:a {:href (nostr/get-d-id-from-event event)
           :class "card-title hover:underline"}
       (nostr/get-name-from-metadata-event event)]
      (doall
       (for [about (nostr/get-about-names-from-metadata-event event)]
         [:div {:class "badge badge-primary m-1 truncate "
                :key about} about]))
      [:p {:class "break-all"}
       (nostr/get-description-from-metadata-event event)]
      [:button {:on-click #(re-frame/dispatch [::events/toggle-show-event-data-modal event])} "Show Event Data"]
      [:div
       {:class "card-actions justify-end"}
       [:div
        {:class "form-control"}
        [:label
         {:class "cursor-pointer label"}
         [:span {:class "label-text"} ""]
         [:input
          {:type "checkbox"
           :checked (contains? (set (map #(:id %) selected-events)) (:id event))
           :class "checkbox checkbox-success"
           :on-change #(re-frame/dispatch [::events/toggle-selected-events event])}]]]]]]))

;; events
(defn events-panel []
  (let [events @(re-frame/subscribe  [::subs/feed-events])
        selected-events @(re-frame/subscribe  [::subs/selected-events])]
    [:div {:class "border-2 rounded"}
     [:p (str "Num of events: " (count events))]
     (if (> (count events) 0)
       [:div {:class "flex flex-wrap justify-center gap-2"}
        (doall

         (for [event  events]
           [:div {:key (:id event)}
            [metadata-event-component event]]))]

       [:p "no events there"])
     [:button {:class "btn"
               :disabled (not (boolean (seq selected-events)))
               :on-click #(re-frame/dispatch [::events/add-metadata-event-to-list [{:d "unique-id-1"
                                                                                    :name "Test List SC"}
                                                                                   selected-events]])}
      "Add To Lists"]]))

;; event feed component
(defn event-feed-component [event]
  (let [_ () #_(re-frame/dispatch [::events/add-confetti])]
    [:div
     {:class "animate-flyIn card bg-base-100 w-64 h-64 shadow-xl border border-white border-w "}
     (cond
       (= 30004 (:kind event)) [:p {:class "text-2xl float-right"} "📖"]
       (= 30142 (:kind event)) [:p {:class "text-2xl float-right"} "📚"])
     [:figure
      [:img
       {:class "h-48 object-contain"
        :src
        (nostr/get-image-from-metadata-event event)
        :alt ""}]]
     [:div
      {:class "card-body"}
      [:button {:on-click #(re-frame/dispatch [::events/toggle-show-event-data-modal event])} "Show Event Data"]]]))

(defn event-feed-panel []
  (let [events @(re-frame/subscribe  [::subs/feed-events])]
    [:div {:class ""}
     [:h1 "Event Feed"]
     [:p (str "Num of events: " (count events))]
     (if (> (count events) 0)
       [:div {:class "flex flex-row gap-2"}
        (doall
         (for [event events]
           [:div {:key (:id event)}
            [event-feed-component event]]))]
       [:p "no events there"])]))

(defmethod routes/panels :event-feed-panel [] [event-feed-panel])

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

;; checkmark
(defn checkmark []
  [:svg
   {:version "1.1",
    :class "fa-icon ml-auto mr-2 svelte-1mc5hvj",
    :width "16",
    :height "16",
    :aria-label "",
    :role "presentation",
    :viewBox "0 0 1792 1792",
    ; :style "color: black;"
    }
   [:path
    {:d
     "M1671 566q0 40-28 68l-724 724-136 136q-28 28-68 28t-68-28l-136-136-362-362q-28-28-28-68t28-68l136-136q28-28 68-28t68 28l294 295 656-657q28-28 68-28t68 28l136 136q28 28 28 68z"}]])

;; Add to lists modal
(defn create-list-modal []
  (let [name (reagent/atom "")
        visible? @(re-frame/subscribe [::subs/show-create-list-modal])]
    (when visible?
      [:dialog {:open visible? :class "modal"}
       [:div {:class "modal-box relative flex flex-col"}
        [:h3 {:class "text-lg font-bold"} "Hello!"]
        [:p {:class "py-4"} "Press ESC key or click outside to close"]
        [:input
         {:type "text"
          :on-change (fn [e] (reset! name (-> e .-target .-value)))
          :placeholder "List Name"
          :class "input input-bordered w-full max-w-xs"}]
        [:button {:class "btn"
                  :on-click #(re-frame/dispatch [::events/create-new-list @name])} "Create New List"]]

       [:form {:on-click #(re-frame/dispatch [::events/toggle-show-create-list-modal])
               :method "dialog" :class "modal-backdrop"}
        [:button "close"]]])))

(defn add-to-lists-modal []
  (let [selected-list-ids @(re-frame/subscribe [::subs/selected-list-ids])
        selected-lists @(re-frame/subscribe [::subs/selected-lists])
        selected-metadata-events @(re-frame/subscribe [::subs/selected-events])
        visible? @(re-frame/subscribe [::subs/show-lists-modal])
        lists @(re-frame/subscribe [::subs/lists-of-user])
        sorted-lists (nostr/sort-lists lists)]
    (when visible?
      [:dialog {:open visible? :class "modal"}
       [:div {:class "modal-box relative flex flex-col"}
        [:h3 {:class "text-lg font-bold"}
         "Hello!"]
        [:p {:class "py-4"}
         "Press ESC key or click outside to close"]
        (doall
         (for [l lists]
           (let [in-selected-lists (or (and (seq selected-metadata-events)
                                            (every? (fn [e] (nostr/list-contains-metadata-event? l e)) selected-metadata-events))
                                       (contains? selected-list-ids (:id l)))
                 div-class (str "m-2 flex flex-row items-center rounded border border-solid border-white p-2 "
                                (if in-selected-lists
                                  "bg-green-500 text-black"
                                  "hover:bg-orange-500 hover:text-black"))]

             [:div {:class div-class
                    :key (nostr/get-list-name l)
                    :on-click #(re-frame/dispatch [::events/toggle-selected-list-ids (:id l)])}
              [:p {:class "text-xl font-bold"}
               (nostr/get-list-name l)]
              (when in-selected-lists
                [checkmark])])))
        [:div {:class "flex flex-row"}
         [:button {:on-click #(re-frame/dispatch [::events/add-metadata-events-to-lists [selected-metadata-events selected-lists]])
                   :class "btn"}
          "Add Resources To Lists"]
         [:button {:class "btn ml-auto mr-0"
                   :on-click #(re-frame/dispatch [::events/toggle-show-create-list-modal])}
          "Create New List"]]]

       [:form {:on-click #(re-frame/dispatch [::events/toggle-show-lists-modal])
               :method "dialog"
               :class "modal-backdrop"}
        [:button
         "close"]]])))

;; shopping cart
(defn shopping-cart []
  (let [selected-events @(re-frame/subscribe [::subs/selected-events])]
    [:div {:class "dropdown dropdown-end"}
     [:div
      {:tabIndex "0", :role "button", :class "btn btn-ghost btn-circle"}
      [:div
       {:class "indicator"}
       [:svg
        {:xmlns "http://www.w3.org/2000/svg",
         :class "h-5 w-5",
         :fill "none",
         :viewBox "0 0 24 24",
         :stroke "currentColor"}
        [:path
         {:stroke-linecap "round",
          :stroke-linejoin "round",
          :stroke-width "2",
          :d
          "M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"}]]
       [:span {:class "badge badge-sm indicator-item"} (count selected-events)]]]
     [:div
      {:tabIndex "0",
       :class
       "card card-compact dropdown-content bg-base-100 z-[1] mt-3 w-52 shadow"}
      [:div
       {:class "card-body"}
       [:span {:class "text-lg font-bold"}
        (str (count selected-events)
             " Items")]
       [:div {:class "card-actions"}
        [:button {:class "btn"
                  :on-click #(re-frame/dispatch [::events/toggle-show-lists-modal])}
         "Add To Lists"]]]]]))

(defn user-menu []
  (let [pk @(re-frame/subscribe [::subs/pk])]
    (if pk
      [:div
       {:class "dropdown dropdown-end"}
       [:div
        {:tabIndex "0",
         :role "button",
         :class "btn btn-ghost btn-circle avatar"}
        [:div
         {:class "w-10 rounded-full"}
         [:img
          {:alt "user image",
           :src
           (str "https://robohash.org/" pk)}]]]
       [:ul
        {:tabIndex "0",
         :class
         "menu menu-sm dropdown-content bg-base-100 rounded-box z-[1] mt-3 w-52 p-2 shadow"}
        [:li
         [:a
          {:on-click #(re-frame/dispatch [::events/navigate [:npub-view :npub (nostr/get-npub-from-pk pk)]])}
          "Profile"]]
        [:li [:a {:on-click #(re-frame/dispatch [::events/navigate [:settings]])} "Settings"]]
        [:li [:a {:on-click #(re-frame/dispatch [::events/navigate [:keys]])} "Keys"]]
        [:li [:a {:on-click #(re-frame/dispatch [::events/logout])} "Logout"]]]]
      [:div
       {:class "dropdown dropdown-end"}
       [:div {:tabIndex "0", :role "button", :class "btn m-1"} "Login"]
       [:ul
        {:tabIndex "0",
         :class
         "dropdown-content menu bg-base-100 rounded-box z-[1] w-52 p-2 shadow"}
        [:li [:a {:on-click #(re-frame/dispatch [::events/create-sk])}
              "... Anonymously"]]
        [:li [:a {:on-click #(re-frame/dispatch [::events/login-with-extension])}
              "... with Extension"]]]])))

;; Header
(defn new-header []
  (let [selected-events @(re-frame/subscribe [::subs/selected-events])]
    [:div
     {:class "navbar bg-base-100"}
     [:div
      {:class "flex-1"}
      [:a {:class "btn btn-ghost text-xl"
           :on-click #(re-frame/dispatch [::events/navigate [:home]])}
       "edufeed"]
      #_[:a {:class "btn btn-circle"
             :on-click #(re-frame/dispatch [::events/navigate [:event-feed]])} "Event-Feed"]]
     [:div
      {:class "flex-none"}

      [:a {:class "btn btn-circle btn-primary"
           :on-click #(re-frame/dispatch [::events/navigate [:add-resource]])} "+"]
      [shopping-cart]
      [user-menu]]]))

;; Keys Panel
(defn keys-panel []
  (let [npub @(re-frame/subscribe [::subs/npub])
        nsec @(re-frame/subscribe [::subs/nsec])] [:div
                                                   [:h1 "Keys"]
                                                   [:p (str "Your Npub: " npub)]
                                                   [:p (str "Your Nsec: " nsec)]]))

(defmethod routes/panels :keys-panel [] [keys-panel])

;; Add Resource Panel
(defn add-resource-panel []
  [:div
   [:h1 "Add Resource"]
   [add-resource-form]
   [add-resource-by-json]
   [add-resosurce-by-uri]])

(defmethod routes/panels :add-resource-panel [] [add-resource-panel])

;; Settings
(defn settings-panel []
  [:div
   [:h1 "Settings"]
   [relays-panel]])

(defmethod routes/panels :settings-panel [] [settings-panel])

;; Home
(defn home-panel []
  (let [events (re-frame/subscribe [::subs/events])]
    [:div
     [events-panel]
     [:p (count @events)]]))

(defmethod routes/panels :home-panel [] [home-panel])

;; about
(defn about-panel []
  [:div
   [:h1 "This is the About Page."]
   [:div
    [:a {:on-click #(re-frame/dispatch [::events/navigate [:home]])}
     "go to Home Page"]]])

(defmethod routes/panels :about-panel [] [about-panel])

;; Lists
(defn list-component [list]
  (let [pk @(re-frame/subscribe [::subs/pk])
        ids-in-list (nostr/get-event-ids-from-list list)
        events-in-list @(re-frame/subscribe [::subs/events-in-list ids-in-list]) ;; TODO should be sorted after appeareande in tags
        ]
    [:div {:key (:id list)
           :class "p-2"}
     [:details
      {:class "collapse bg-base-200"}
      [:summary
       {:class "collapse-title text-xl font-medium"}
       (nostr/get-list-name list)]
      [:div {:class "collapse-content"}

       (doall
        (for [event events-in-list]
          [:div {:key  (:id event)}
           [metadata-event-component event]
           (when pk
             [:button {:class "btn btn-warning"
                       :on-click #(re-frame/dispatch [::events/delete-event-from-list [event list]])}
              "Delete Resource from List"])]))

       (when pk [:button {:class "btn btn-error"
                          :on-click #(re-frame/dispatch [::events/delete-list list])} "Delete List"])]]]))

;; npub / Profile
(defn npub-view-panel []
  (let [sockets @(re-frame/subscribe [::subs/sockets])
        route-params @(re-frame/subscribe [::subs/route-params])
        lists @(re-frame/subscribe [::subs/lists-for-npub])
        _ (when (not (seq lists)) (re-frame/dispatch [::events/get-lists-for-npub (:npub route-params)]))
        missing-events-from-lists @(re-frame/subscribe [::subs/missing-events-from-lists])
        _ (when (seq missing-events-from-lists) (re-frame/dispatch [::events/query-for-event-ids [sockets missing-events-from-lists]]))]
    [:div
     [:h1 (str "Hello Npub: " (:npub route-params))]
     (if-not (seq lists)
       [:p "No lists there...yet"]
       (doall
        (for [l lists]
          ^{:key (:id l)} [list-component l])))
     [:button {:class "btn ml-auto mr-0"
               :on-click #(re-frame/dispatch [::events/toggle-show-create-list-modal])}
      "Create New List"]]))

(defmethod routes/panels :npub-view-panel [] [npub-view-panel])

;; main
(defn main-panel []
  (let [active-panel (re-frame/subscribe [::subs/active-panel])]
    [:div
     [new-header]
     [:div
      (routes/panels @active-panel)
      [add-to-lists-modal]
      [create-list-modal]
      [event-data-modal]]]))

