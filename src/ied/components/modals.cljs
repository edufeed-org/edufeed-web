(ns ied.components.modals
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [ied.subs :as subs]
            [ied.components.icons :as icons]
            [ied.nostr :as nostr]
            [ied.events :as events]))

(defn open-create-list-modal []
  (.showModal (.getElementById js/document "create-list-modal")))

;; Add to lists modal
(defn create-list-modal []
  (let [name (reagent/atom "")]
    [:dialog {:id "create-list-modal" :class "modal"}
     [:div {:class "modal-box relative flex flex-col"}
      [:h3 {:class "text-lg font-bold"} "Bitte gib einen Namen für deine Liste ein"]
      [:input
       {:type "text"
        :on-change (fn [e] (reset! name (-> e .-target .-value)))
        :placeholder "List Name"
        :class "input input-bordered w-full max-w-xs"}]
      [:form {:method "dialog" :class "modal-backdrop"}
       [:div {:class "flex flex-row justify-between mt-2"}
        [:button {:class "btn"
                  :on-click #(re-frame/dispatch [::events/create-new-list @name])} "Create New List"]
        [:button {:class "btn"} "Close"]]]]]))

(defn open-add-to-list-modal []
  (.showModal (.getElementById js/document "add-to-lists-modal")))

(defn add-to-lists-modal []
  (let [selected-list-ids @(re-frame/subscribe [::subs/selected-list-ids])
        selected-lists @(re-frame/subscribe [::subs/selected-lists])
        selected-metadata-events @(re-frame/subscribe [::subs/selected-events])
        lists @(re-frame/subscribe [::subs/lists-of-user])
        sorted-lists (nostr/sort-lists lists)]
    [:dialog {:id "add-to-lists-modal" :class "modal"}
     [:div {:class "modal-box relative flex flex-col"}
      [:h3 {:class "text-lg font-bold"}
       "Wo möchtest du das hinzufügen?"]
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
              [icons/checkmark])])))
      [:form {:method "dialog"
              :class "modal-backdrop"}
       [:div {:class "flex flex-row gap-2"}
        [:button {:on-click #(re-frame/dispatch [::events/add-metadata-events-to-lists [selected-metadata-events selected-lists]])
                  :class "btn"}
         "Add Resources To Lists"]
        [:button {:class "btn "
                  :on-click #((open-create-list-modal))}
         "Create New List"]

        [:button {:class "btn btn-warning ml-auto mr-0"}
         "Close"]]]]]))

