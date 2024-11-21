(ns ied.opencard.views
  (:require
   [re-frame.core :as re-frame]
   [ied.opencard.subs :as subs]
   [clojure.string :as str]
   [ied.routes :as routes]
   [ied.components.icons :as icons]
   [markdown-to-hiccup.core :as m]
   [reagent.core :as reagent]))

;; Sample data
(defonce state (reagent/atom {:lists [{:id 1 :items [{:id 1 :content "*Item 1*"} {:id 2 :content "Item 2"}]}
                                      {:id 2 :items [{:id 3 :content "Item 3"} {:id 4 :content "Item 4"}]}
                                      {:id 3 :items [{:id 5 :content "Item 5"} {:id 6 :content "Item 6"}]}
                                      {:id 4 :items [{:id 7 :content "Item 7"} {:id 8 :content "Item 8"}]}]
                              :show-edit-item-modal false
                              :selected-item {:id nil
                                              :content ""}
                              :dragging nil
                              :dragging-over nil
                              :hover-index nil
                              :hover-item nil
                              :dragged-column nil
                              :hover-column-id nil
                              :dragging-item nil
                              :dragging-over-list nil})) ;; New state to track the current column being dragged over

;; Helper functions
(defn find-item [lists item-id]
  (some (fn [list]
          (some (fn [item]
                  (when (= (:id item) item-id) [list item]))
                (:items list)))
        lists))

(defn reorder-items [items from-index to-index]
  (let [item (nth items from-index)
        items (vec (concat (subvec items 0 from-index) (subvec items (inc from-index))))
        head (subvec items 0 to-index)
        tail (subvec items to-index)]
    (vec (concat head [item] tail))))

(defn reorder-columns [columns from-index to-index]
  (let [column (nth columns from-index)
        columns-filtered (vec (concat (subvec (vec columns) 0 from-index) (subvec (vec columns) (inc from-index))))
        head (subvec columns-filtered 0 to-index)
        tail (subvec columns-filtered to-index)
        reordered (vec (concat head [column] tail))]
    reordered))

(defn handle-drag-start [item-id]
  (swap! state assoc :dragging item-id :hover-index nil :hover-item nil :dragged-column nil :dragging-item item-id))

(defn set-dragged-column
  "Sets `:dragged-column`. It's the id of the column"
  [column-id]
  (swap! state assoc :dragged-column column-id :hover-column-id nil :dragging nil))

(defn handle-drag-over [e list-id & [index item-id]]
  (.preventDefault e)
  (swap! state assoc :dragging-over list-id :dragging-over-list list-id) ;; Track the list being dragged over
  (when (and index item-id) ;; Only update hover-index and hover-item if item details are provided
    (swap! state assoc :hover-index index :hover-item item-id)))

(defn handle-column-drag-over [e column-id]
  (.preventDefault e)
  (swap! state assoc :hover-column-id column-id))

(defn handle-column-drop []
  (let [is-dragged (:dragged-column @state)
        columns (:lists @state)
        hover-column-id (:hover-column-id @state)
        hover-column-index (.indexOf (map :id columns) hover-column-id)
        from-index (.indexOf (map :id columns) is-dragged)]
    (when (and is-dragged (not= from-index hover-column-index)) ;; Only reorder if we are dragging a column
      (swap! state update :lists #(reorder-columns % from-index hover-column-index)))
    ;; Clear column drag state
    (swap! state assoc :dragged-column nil :hover-column-id nil)))

(defn handle-drop [list-id]
  (when-not (:dragged-column @state) ;; Only handle item drop if not dragging a column
    (let [item-id (:dragging @state)
          [source-list item] (find-item (:lists @state) item-id)
          hover-index (:hover-index @state)
          is-same-list (= (:id source-list) list-id)]
      (when item
        ;; Handle reorder if the item is dropped in the same list
        (if is-same-list
          (when (not= (.indexOf (:items source-list) item) hover-index) ; Only reorder if the position changed
            (swap! state update :lists
                   (fn [lists]
                     (map (fn [list]
                            (if (= (:id list) list-id)
                              (update list :items #(reorder-items % (.indexOf (:items list) item)
                                                                  (if hover-index hover-index (count (:items list)))))
                              list))
                          lists))))
          ;; Move the item to another list
          (swap! state update :lists
                 (fn [lists]
                   (map (fn [list]
                          (cond
                             ;; Remove item from the source list
                            (= (:id list) (:id source-list)) (update list :items #(remove (fn [i] (= (:id i) item-id)) %))
                             ;; Insert item into the destination list at the hover index or at the end if not hovering over any item
                            (= (:id list) list-id) (update list :items
                                                           (fn [items]
                                                             (if hover-index
                                                               (let [split-items (split-at hover-index items)]
                                                                 (vec (concat (first split-items) [item] (second split-items))))
                                                                ;; Insert at the end if no hover index is provided
                                                               (conj items item))))
                            :else list))
                        lists)))))

      ;; Clear dragging state
      (swap! state assoc :dragging nil :dragging-over nil :hover-index nil :hover-item nil :dragging-item nil :dragging-over-list nil))))

(defn handle-drag-end []
  (swap! state assoc :dragging nil :dragging-over nil :hover-index nil :hover-item nil :dragging-item nil :dragging-over-list nil))

(defn handle-add-item [column]
  (swap! state update :lists
         (fn [lists]
           (map (fn [list]
                  (if (= (:id column) (:id list))
                    (update list :items
                            (fn [items] (conj items {:id (rand-int 1000) :content "huhu"})))
                    list))
                lists))))

(defn handle-add-column []
  (let  [item {:id (rand-int 1000) :content "huhu"}
         column {:id (rand-int 1000) :items [item]}]
    (swap! state update :lists
           (fn [lists]
             (conj lists column)))))

(defn update-item-content [data-atom target-id new-content]
  (println "update item content with : " new-content)
  (swap! data-atom
         (fn [data]
           (update data :lists
                   (fn [lists]
                     (mapv (fn [list]
                             (update list :items
                                     (fn [items]
                                       (mapv (fn [item]
                                               (if (= (:id item) target-id)
                                                 (assoc item :content new-content)
                                                 item))
                                             items))))
                           lists))))))

(defn find-item-by-id [target-id]
  (some #(when (= (:id %) target-id) %)
        (mapcat :items (:lists @state))))

(defn open-edit-item-modal []
  (.showModal (.getElementById js/document "my_modal_1")))

;; Components
(defn draggable-item [{:keys [id content]} list-id index]
  (fn []
    (let [item (find-item-by-id id)
          is-hovered (= id (:hover-item @state))
          is-dragging (= id (:dragging-item @state))] ;; Determine if this item is being dragged
      [:div {:draggable true
             :on-drag-start #(handle-drag-start id)
             :on-drag-over (fn [e] (handle-drag-over e list-id index id))
             :on-double-click #((swap! state assoc :selected-item {:id id
                                                                   :content content})
                                (open-edit-item-modal))
             :class (str/join " " ["hover:cursor-grab"
                                   "hover:bg-yellow-200"
                                   "bg-slate-100"
                                   "min-h-16"
                                   "p-2"
                                   "text-black"
                                   (when is-dragging "hidden")
                                   ])} ;; Bring to front if dragging
       (->> (:content item)
            (m/md->hiccup)
            (m/component))])))

(defn droppable-list [id]
  (fn []
    (let [items (:items (some #(when (= id (:id %)) %) (:lists @state)))
          dragging-over? (= id (:dragging-over @state))
          dragging-item (:dragging-item @state)
          dragging-over-list (:dragging-over-list @state)] ;; Get the list currently being dragged over
      [:div {:on-drag-over (fn [e] (handle-drag-over e id)) ; Only list id is passed here to mark the column as hovered
             :on-drop (fn [_] (handle-drop id))
             :class (str/join " " ["relative"
                                   "flex flex-col gap-1"
                                   "rounded-md"
                                   "min-h-32"
                                   (if dragging-over? "bg-green-200" "")
                                   (if dragging-over? "border-dashed border-2 border-green-500" "")])}
     ;; Ensure the dragged item is positioned correctly
       (doall
        (for [index (range (count items))]
          (let [item (nth items index)]
          ;; Render the placeholder only in the correct column
            ^{:key (:id item)} [:<>
                                (when (and dragging-item (= id dragging-over-list) (= index (:hover-index @state)))
                                  [:div {:class (str/join " " ["min-h-16"
                                                               "m-2"
                                                               "bg-slate-100"
                                                               "opacity-50"])}])
                                [draggable-item item id index]])))])))

;; TODO update title
(defn title-field []
  (let [edit (reagent/atom false)]
    (fn []
      [:div {:class "flex justify-between items-center"}
       (if @edit
         [:input {:class "input"}]
         [:p {} "Title of column"])
       [:label {:on-click #(reset! edit (not @edit))
                :class "btn"} [icons/pencil]]])))

(defn draggable-column [column index]
  (fn []
    (let [is-hovered (= (:id column) (:hover-column-id @state))
          is-dragged (:dragged-column @state)] ;; Get the currently dragged column
      [:div {:on-drag-over (fn [e] (when is-dragged (handle-column-drag-over e (:id column)))) ;; Only handle if a column is being dragged
             :on-drag-end #(handle-drag-end)
             :on-drop (fn [_] (handle-column-drop))
             :class (str/join " " [""
                                   "overflow-y-auto"
                                   "max-h-screen"
                                   "m-2"
                                   "p-4"
                                   "rounded-md"
                                   "w-96"
                                   (if (and is-hovered is-dragged)
                                     "border-dashed border-2 border-red-200 bg-red-200"
                                     "border-solid border-1 border-slate-100 bg-slate-200")])}

;; Add the handle for column dragging
       [:div {:class "flex flex-col gap-1"}
        [:span {:draggable true
                :on-drag-start (fn [_] (set-dragged-column (:id column)))
                ; :on-drag-start (fn [_] (handle-column-drag-start index))
                :class "cursor-grab p-2 mr-0 ml-auto bg-slate-100 border-solid rounded-full"}
         [icons/move]]
        [:div {:class "rounded-md bg-primary p-2 text-xl text-white font-medium"}
         [title-field]]
        [:label
         {:class "btn btn-neutral my-1"
          :on-click #(handle-add-item column)}
         [icons/add "white"]]

        [droppable-list (:id column)]]])))

(defn add-column []
  [:label
   {:class "btn btn-rounded fixed bottom-4 right-4 bg-blue-500 text-white p-3 shadow-lg hover:bg-blue-600"
    :on-click #(handle-add-column)}
   [icons/add]])

(defn drag-and-drop []
  [:div {:class "flex overflow-auto"}
   [:div {:class "flex flex-nowrap "}
    (doall
     (for [index (range (count (:lists @state)))]
       (let [hash (hash (:items (nth (:lists @state) index)))]
         ^{:key hash} [draggable-column (nth (:lists @state) index) index])))]
   [add-column]])

(defn edit-item-modal []
  (let [content (reagent/atom "")
        selected-item-content (reagent/reaction
                               (let [item (:selected-item @state)]
                                 (:content item)))]
    (reagent/track! (fn []
                      (reset! content @selected-item-content)))
    (fn []
      (let [item (:selected-item @state)
            id (:id item)]
        [:dialog
         {:id "my_modal_1", :class "modal"}
         [:div
          {:class "modal-box"}
          [:h3 {:class "text-lg font-bold"} "Hello!"]
          [:p @content]
          [:textarea {:class "textarea textarea-bordered"
                      :value @content
                      :on-change (fn [e] (reset! content (-> e .-target .-value)))}]
          [:p
           {:class "py-4"}
           "Press ESC key or click the button below to close"]
          [:div
           {:class "modal-action"}
           [:form
            {:method "dialog"}
            [:button {:class "btn"
                      :on-click (fn [e]
                                  (update-item-content state id @content))} "Save"]]]]]))))

(defn board-component [board]
  [:div {:class ""}
   [:button {:class "btn"} (:title board)]])

(defn opencard-boards []
  (let [boards  (re-frame/subscribe [::subs/opencard-boards])]
    [:div {:class "grid grid-cols-4 gap-2 mt-2"}
     (doall
      (for [board @boards]
        ^{:key (:id board)} [board-component board]))]))

(defn opencard-component []
  (let [show-board-overview (reagent/atom true)]
    (fn []
      [:div {:class "flex flex-col"}
       [:button {:class "btn btn-square ml-0 mr-auto"
                 :on-click #(reset! show-board-overview (not @show-board-overview))} [icons/grid]]
       [:div {:class ""}
        (if @show-board-overview
          [opencard-boards]
          [drag-and-drop])]])))

(defn opencard-view-panel []
  [:div {:class ""}
   [opencard-component]
   [edit-item-modal]])

(defmethod routes/panels :opencard-view-panel [] [opencard-view-panel])

