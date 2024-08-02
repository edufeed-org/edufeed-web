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
       [:button {:on-click #(re-frame/dispatch [::events/publish-resource {:name (:name @s)
                                                                           :uri (:uri @s)
                                                                           :author (:author @s)}])}
        "Publish Resource"]])))

;; events
(defn events-panel []
  (let [events (re-frame/subscribe  [::subs/events])
        show-add-event (re-frame/subscribe [::subs/show-add-event])]
    [:div
     [:p {:on-click #(re-frame/dispatch [::events/toggle-show-add-event])}
      (if @show-add-event "X" "Add Resource!")]
     (when @show-add-event
       [add-resource-form])
     [:p (str "Num of events: " (count @events))]
     (if (> (count @events) 0)
       (doall
        (for [event @events]
          ; [:li (:content (nth event 2 {:content "hello"}))]
          [:li {:key (:id event)} (get (nth event 2) :content "")]
          ))
       [:p "no events there"])]))

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

     (when (> (count @sockets) 0)
       [:ul
        (doall
         (for [socket @sockets]
           [:li {:key (:id socket)}
            [:span (:name socket)]
            [:button {:on-click #(re-frame/dispatch [::events/connect-to-websocket])} "Load events"]
            [:button {:on-click #(re-frame/dispatch [::events/close-connection-to-websocket])} "Disconnect"]
            [:button {:on-click #(re-frame/dispatch [::events/remove-websocket socket])} "Remove relay"]]))])]))

;; Header

(defn header []
  (let [pk (re-frame/subscribe [::subs/pk])]
    [:div
     [:a {:on-click #(re-frame/dispatch [::events/navigate :home])}
      "home"]
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

;; main
(defn main-panel []
  (let [active-panel (re-frame/subscribe [::subs/active-panel])]
    [:div
     [header]
     (routes/panels @active-panel)]))

(comment

  (.log js/console "Hello World"))
