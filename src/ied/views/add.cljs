(ns ied.views.add
  (:require [re-frame.core :as re-frame]
            [clojure.string :as str]
            [reagent.core :as reagent]
            [ied.subs :as subs]
            [ied.events :as events]
            [ied.components.icons :as icons]
            [ied.routes :as routes]))

(def md-scheme-map
  {:amblight {:name {:title "Name"
                     :type :string}
              :uri {:title "URL"
                    :type :string}
              :author {:title "Author"
                       :type :string}
              :keywords {:title "Schlagworte"
                         :type :array
                         :items {:type :string}}
              :about {:title "Worum geht es??"
                      :type :skos
                      :schemes ["https://w3id.org/kim/hochschulfaechersystematik/scheme"]}
              :learningResourceType {:type :skos
                                     :schemes ["https://w3id.org/kim/hcrt/scheme"]}}
   :genmint (array-map :name {:title "Name"
                              :type :string}
                       :uri {:title "URL"
                             :type :string}
                       :image {:title "Thumbnail"
                               :type :string}
                       :description {:title "Beschreibung"
                                     :type :string
                                     :x-string-type :textarea}
                       :creator {:title "Autor:innen"
                                 :type :array
                                 :items {:type :string}}
                       :publisher {:title "Veröffentlicher:innen"
                                   :type :array
                                   :items {:type :string}}
                       :funder {:title "Gefördert durch"
                                :type :array
                                :items {:type :string}}
                       :datePublished {:title "Publikationsdatum"
                                       :type :string
                                       :format :date}
                       :learningResourceType {:type :skos
                                              :schemes ["https://w3id.org/kim/hcrt/scheme"]}
                       :teaches {:title "Welche Kompetenzen werden adressiert?"
                                 :type :skos
                                 :schemes [""]}
                       :about {:title "Fächersystematik"
                               :type :skos
                               :schemes ["https://w3id.org/kim/hochschulfaechersystematik/scheme"]}
                       :keywords {:title "Schlagworte"
                                  :type :array
                                  :items {:type :string}}
                       :inLanguage {:title "Sprache"
                                    :type :string
                                    :enum ["de" "en"]}
                       :isAccessibleForFree {:title "Frei zugänglich"
                                             :type :string
                                             :enum ["ja" "nein"]}
                      ;; LICENSE
                       :license {:title "Lizenz"
                                 :type :string
                                 :enum ["CC-0" "CC-BY"]}
                      ;; ConditionsOfAccess
                      ;; audience
                     ;; didaktische Methoden und Planungshilfen
                    ;;  educationalLevel 
                       )
   :amb {:name {:title "Name"
                :type :string}
         :uri {:title "URL"
               :type :string}
         :author {:title "Author"
                  :type :string}
         :learningResourceType {:title "Learning Resource Typ"
                                :type :skos
                                :schemes ["https://w3id.org/kim/hcrt/scheme"]}
         :about {:type :skos
                 :schemes ["https://w3id.org/kim/hochschulfaechersystematik/scheme"]}}})

(defn concept-checkbox [concept field toggled disable-on-change]
  [:input {:type "checkbox"
           :checked (or toggled false)
           :class "checkbox checkbox-warning"
           :on-change (fn [] (when-not disable-on-change (re-frame/dispatch [::events/toggle-concept [concept field]])))}])

(defn highlight-match
  "Wraps the matching part of the text in bold markers, preserving the original capitalization.
   Arguments:
   - `text`: The full text (string).
   - `query`: The search term (string).
   Returns:
   - A Hiccup structure with the matching part bolded, or the original text if no match."
  [text query]
  (if (and text query)
    (let [lower-text (str/lower-case text)
          lower-query (str/lower-case query)
          index (str/index-of lower-text lower-query)]
      (if index
        [:span
         (subs text 0 index)
         [:span {:class "font-bold"} (subs text index (+ index (count query)))]
         (subs text (+ index (count query)))]
        text))
    text))

(defn concept-label-component
  [[concept field search-input]]
  (fn []
    (let [toggled-concepts @(re-frame/subscribe [::subs/toggled-concepts])
          toggled (some #(= (:id %) (:id concept)) toggled-concepts) ;; TODO could also be a subscription?
          prefLabel (highlight-match (-> concept :prefLabel :de) search-input)]
      [:li
       (if-let [narrower (:narrower concept)]
         [:details {:open false}
          [:summary {:class (when toggled "bg-orange-400 text-black")}
           [concept-checkbox concept field toggled]
           prefLabel]
          [:ul {:tabIndex "0"}
           (for [child narrower]
             ^{:key (:id child)} [concept-label-component [child field]])]]
         [:a {:class (when toggled "bg-orange-400 text-black")
              :on-click (fn [] (re-frame/dispatch [::events/toggle-concept [concept field]]))}
          [concept-checkbox concept field toggled true]
          [:p
           prefLabel]])])))

(defn get-nested
  "Retrieve all values from nested paths in a node.
   Arguments:
   - `node`: The map representing a node.
   - `paths`: A sequence of keyword paths to retrieve values from the node."
  [node paths]
  (map #(get-in node %) paths))

(defn match-node?
  "Checks if the query matches any field value in the provided paths.
   Arguments:
   - `node`: The map representing a node.
   - `query`: The search term (string).
   - `paths`: A sequence of keyword paths to check in the node."
  [node query paths]
  (some #(str/includes? (str/lower-case (str %)) (str/lower-case  query)) (get-nested node paths)))

(defn search-concepts
  "Recursively search for matches in `hasTopConcept` and `narrower`.
   Arguments:
   - `cs`: The Concept Scheme
   - `query`: The search term (string).
   - `paths`: A sequence of keyword paths to check in each concept node.
   Returns:
   - A map with the same structure as `cs`, containing only matching concepts
     and their parents."
  [cs query paths]
  (letfn [(traverse [concept]
            (let [children (:narrower concept)
                  matched-children (keep traverse children)
                  node-matches (match-node? concept query paths)]
              (when (or node-matches (seq matched-children))
                (-> concept
                    (assoc :narrower (when (seq matched-children) matched-children))))))]
    (if-let [filtered-concepts (keep traverse (:hasTopConcept cs))]
      (assoc cs :hasTopConcept filtered-concepts)
      nil)))

(comment
  (def data {:id "http://w3id.org/openeduhub/vocabs/locationType/"
             :type "ConceptScheme"
             :title {:de "Typ eines Ortes, einer Einrichtung oder Organisation"
                     :en "Type of place, institution or organisation"}
             :hasTopConcept [{:id "http://w3id.org/openeduhub/vocabs/locationType/bdc2d9f1-bef6-4bf4-844d-4efc1c99ddbe"
                              :prefLabel {:de "Lernort"}
                              :narrower [{:id "http://w3id.org/openeduhub/vocabs/locationType/0d08a1e9-09d4-4024-9b5c-7e4028e28ce5"
                                          :prefLabel {:de "Kindergarten/-betreuung"}}
                                         {:id "http://w3id.org/openeduhub/vocabs/locationType/1d0965c1-4a3e-4228-a600-bfa1d267e4d9"
                                          :prefLabel {:de "Schule"}}
                                         {:id "http://w3id.org/openeduhub/vocabs/locationType/729b6724-1dd6-476e-b871-44383ac11ef3"
                                          :prefLabel {:de "berufsbildende Schule"}}]}
                             {:id "http://test.com/"
                              :prefLabel {:de "Test"}}]})

  (def fields [[:prefLabel :de] [:prefLabel :en]])
  (def query "Test")
  (println (pr-str (search-concepts data query fields))))

(defn skos-concept-scheme-multiselect-component
  [[cs field field-title]]
  (let [search-input (reagent/atom "")]
    (fn []
      (let [filtered-cs (search-concepts cs @search-input [[:prefLabel :de]])] ;; TODO needs to be otherwise configured for multi language stuff
        [:div
         {:class "dropdown w-full"
          :key (:id cs)}
         [:div
          {:tabIndex "0"
           :role "button"
           :class "btn m-1 grow w-full"}
          field-title]
         [:div {:class "dropdown-content z-[1]"}
          [:ul {:class "menu bg-base-200 rounded-box w-96 "
                :tabIndex "0"}
           [:label {:class "input flex items-center gap-2"}
            [:input {:class "grow"
                     :placeholder "Suche..."
                     :type "text"
                     :on-change (fn [e]
                                  (reset! search-input (-> e .-target .-value)))}]
            [icons/looking-glass]]
           (doall
            (for [concept (:hasTopConcept filtered-cs)]
              ^{:key (str @search-input (:id concept))} [concept-label-component [concept field @search-input]]))]]]))))

(defn array-fields-component [selected-md-scheme field field-title]
  (let [array-items-type (get-in selected-md-scheme [field :items :type])
        fields (re-frame/subscribe [::subs/md-form-array-input-fields field])]
    (fn []
      [:div {:class "flex flex-col gap-1"}
       [:p field-title]
       (case array-items-type
         :string (doall (for [[k v] @fields]
                          [:div {:class "flex flex-row gap-1"
                                 :key k}
                           [:input {:type "text"
                                    :class "input input-bordered w-full"
                                    :default-value ""
                                    :on-blur (fn [e]
                                               (re-frame/dispatch
                                                [::events/handle-md-form-array-input
                                                 [field
                                                  k
                                                  (-> e .-target .-value)]]))}]
                           [:button {:class "btn btn-square"
                                     :on-click
                                     #(re-frame/dispatch
                                       [::events/handle-md-form-rm-input
                                        [field k]])}  [icons/close-icon]]])))
       [:button {:class "btn"
                 :on-click #(re-frame/dispatch
                             [::events/handle-md-form-add-input [field]])}
        "Add field"]])))

;; add resource form
(defn add-resource-form
  []
  (let [md-scheme (re-frame/subscribe [::subs/selected-md-scheme])
        md-form-image (re-frame/subscribe [::subs/md-form-image])]
    (fn []
      [:form  {:class "flex flex-col space-y-4"
               :on-submit (fn [e]
                            (.preventDefault e))}
       [:select
        {:default-value @md-scheme
         :on-change (fn [e]
                      (let [value (-> e .-target .-value keyword)]
                        (re-frame/dispatch [::events/set-md-scheme value])))
         :class "select select-bordered w-full max-w-xs"}
        [:option {:disabled true} "Select a Metadata Scheme"]
        [:option {:value "amblight"} "AMB Light"]
        [:option {:value "genmint"} "GenMint"]]

       (let [selected-md-scheme (get md-scheme-map @md-scheme (:amblight md-scheme-map))]
         [:div {:class "flex flex-col gap-2"}
          (when @md-form-image
            [:img {:src @md-form-image}])
          (doall
           (for [field (keys selected-md-scheme)]
             (let [field-type (-> selected-md-scheme field :type)
                   field-title (get-in selected-md-scheme [field :title] (name field))]
               (cond ;; TODO the conditions need to get more logic to evaluate form fields in order
                 (and (= :string field-type)
                      (= :textarea (-> selected-md-scheme field :x-string-type)))
                 [:textarea {:key field
                             :type ""
                             :class "textarea textarea-bordered grow"
                             :placeholder field-title
                             :on-blur #(re-frame/dispatch [::events/handle-md-form-input [field (-> % .-target .-value)]])}]
                 (and (= :string field-type)
                      (-> selected-md-scheme field :enum))
                 (let [enum-vals (-> selected-md-scheme field :enum)]
                   [:select
                    {:key field
                     :class "select select-bordered w-full"}
                    [:option {:disabled "" :selected ""} field-title]
                    (doall
                     (for [val enum-vals]
                       [:option {:key val} val]))])
                 (and (= :string field-type)
                      (= :date (-> selected-md-scheme field :format)))
                 [:div {:class "my-4"}
                  [:label {:for field} field-title]
                  [:input
                   {:type "date"
                    :on-blur #(re-frame/dispatch [::events/handle-md-form-input [field (-> % .-target .-value)]])
                    :id field
                    :name field-title
                    ; :value "2018-07-22"
                    :min "2018-01-01"
                    :max "2018-12-31"}]]
                 (= :string field-type)
                 [:label {:key field
                          :class "input input-bordered flex items-center gap-2"}
                  (-> selected-md-scheme field :title)
                  [:input {:type "text"
                           :class "grow"
                           :placeholder (name field)
                           :on-blur #(re-frame/dispatch [::events/handle-md-form-input [field (-> % .-target .-value)]])}]] ;; TODO maybe better handle this with an atom in the component
                 (= :skos field-type)
                 (let [concept-schemes @(re-frame/subscribe
                                         [::subs/concept-schemes (-> selected-md-scheme field :schemes)])
                       _ (doall (for [[cs-uri _] (filter (fn [[_ v]] (nil? v)) concept-schemes)]
                                  (re-frame/dispatch [::events/skos-concept-scheme-from-uri  cs-uri])))]

                   (doall
                    (for [cs (keys concept-schemes)]
                      ^{:key cs} [skos-concept-scheme-multiselect-component [(get concept-schemes cs)
                                                                             field
                                                                             field-title]])))
                 (= :array field-type)
                 ^{:key field} [array-fields-component selected-md-scheme field field-title]
                 :else
                 [:p {:key field} "field type not found"]))))])

       [:button {:class "btn btn-warning w-1/2 mx-auto"
                 :on-click #(re-frame/dispatch [::events/publish-resource])}
        "Publish Resource"]])))

(defn add-resource-by-json []
  (let [s (reagent/atom {:json-string ""})]
    (fn []
      [:div {:class "flex flex-col"}
       [:p "Paste an AMB object"]
       [:form {:class "flex flex-col"
               :on-submit (fn [e] (.preventDefault e))}
        [:textarea {:rows 10
                    :on-change (fn [e]
                                 (swap! s assoc :json-string (-> e .-target .-value)))}]
        [:button {:class "btn btn-warning"
                  :on-click #(re-frame/dispatch [::events/convert-amb-string-and-publish-as-nostr-event (:json-string @s)])}
         "Publish as Nostr Event"]]])))

;; TODO try again using xhrio
(defn add-resosurce-by-uri []
  (let [uri (reagent/atom {:uri ""})]
    (fn []
      [:form {:class "flex flex-col"
              :on-submit (fn [e] (.preventDefault e))}
       [:label {:for "uri"} "URI: "]
       [:input {:id "uri"
                :on-change (fn [e]
                             (swap! uri assoc :uri (-> e .-target .-value)))}]
       [:button {:class "btn btn-warning"
                 :on-click #(re-frame/dispatch [::events/publish-amb-uri-as-nostr-event (:uri @uri)])}
        "Publish as Nostr Event"]])))

;; Add Resource Panel
(defn add-resource-panel []
  [:div
   {:class "sm:w-1/2 p-2 mx-auto flex flex-col space-y-4"}
   [add-resource-form]
   [:details
    {:tabIndex "0",
     :class "collapse collapse-arrow border-base-300 bg-base-200 border"}
    [:summary
     {:class "collapse-title text-xl font-medium"}
     "Advanced Options"]
    [:div
     {:class "collapse-content"}
     [add-resource-by-json]
     [add-resosurce-by-uri]]]])

(defmethod routes/panels :add-resource-panel [] [add-resource-panel])
