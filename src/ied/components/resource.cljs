(ns ied.components.resource
  (:require [re-frame.core :as re-frame]
            [clojure.string :as str]
            [ied.nostr :as nostr]
            [ied.subs :as subs]
            [ied.events :as events]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components used to display info about resources
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn about-tags [[event]]
  (doall
   (for [about (nostr/get-about-names-from-metadata-event event)]
     [:div {:class "badge badge-primary m-1 truncate "
            :key about} about])))

(defn grouped-about-tags [[group-key values]]
  ;; group key is the identifier of the concept
  ;; values is an array of events that referenced the concept
  (let [_ (println "values " values)
        pubkeys (map :pubkey values)
        profiles (re-frame/subscribe [::subs/profiles pubkeys])
        user-language (re-frame/subscribe [::subs/user-language])
        _ (js/console.log "group key and values" (clj->js values))
        _ (js/console.log (clj->js @profiles))]
    [:div {:on-click #(re-frame/dispatch [::events/handle-filter-search ["about.id" group-key]])
           :data-tip (str
                      (str/join
                       ", "
                       (remove str/blank? (map  (fn [[_ p]] (get p :display_name "unbekannt")) @profiles)))
                      (if (> (count @profiles) 1)
                        " haben"
                        " hat")
                      " das zugeordnet.")
           :class " tooltip  "}
     [:div {:class "badge badge-primary m-1 truncate cursor-pointer"}
      (vals (:about-label (first (filter #(= @user-language (:label-language %)) values))))]]))

;; Helper function to extract the information and associate it with `about`
(defn extract-about-info [tags event]
  (let [abouts (map (fn [e] {:about-id (second e)
                             :label-language (nth e 3 "")
                             :about-label {(keyword (nth e 3 "")) (nth e 2)}
                             :pubkey (:pubkey event)
                             :id (:id event)})
                    tags)
        _ (.log js/console "abouts " (clj->js abouts))]
    abouts))

;; Flatten and group by `about-id`
(defn group-by-for-skos-tags
  ;; TODO update this docstring
  "Expects an array of kind 30142 skos tag arrays, like
 ```
 [
  [
    ['about' 'id' 'val' 'lang']
    ['about' 'id' 'val' 'lang']
  ]
  [
    ['about' 'id' 'val' 'lang']
  ]
 ] 

 Returns:
 - a map of grouped by the ids of the skos tags
 ```
 "
  [events name]
  (println "events array" events)
  (let [tags-array (map
                    (fn [e]
                      (filter #(= name (first %)) e))
                    (map :tags events))
        extracted-about (into [] (apply concat (map extract-about-info tags-array events)))
        grouped-about (group-by :about-id extracted-about)]
    grouped-about))

(defn skos-tags [[events name]]
  (let [grouped (group-by-for-skos-tags events name)]
    (doall
     (for [[k v] grouped]
       ^{:key k} [grouped-about-tags [k v]]))))

(defn keywords-component [kw]
  ^{:key kw}[:div {:on-click #(do
                      (re-frame/dispatch [::events/navigate [:search-view]])
                      (re-frame/dispatch [::events/handle-filter-search ["keywords" kw]]))
         :class "badge badge-secondary m-1 cursor-pointer"}
   (str "#" kw)])

; (defn keywords-component [kw]
;   [:div {:on-click #(re-frame/dispatch 
;                       [::events/navigate [:search-view]]
;                       [::events/handle-filter-search ["keywords" kw]])
;          :class "badge badge-secondary m-1 cursor-pointer"}
;    (str "#" kw)])

(defn authors-component [event]
  (let [creators (nostr/get-creators-from-metadata-event event)]
    [:div {:class "flex flex-row"}
     (doall
      (for  [creator creators]
        ^{:key (:name creator)} [:p {:class "mx-1"} (:name creator)]))]))

(comment
  (hash "https://w3id.org/kim/hcrt/scheme"))