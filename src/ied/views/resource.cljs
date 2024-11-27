(ns ied.views.resource
  (:require
   [re-frame.core :as re-frame]
   [ied.subs :as subs]
   [ied.nostr :as nostr]
   [ied.routes :as routes]
   [ied.components.resource :as resource-components]
   [ied.components.resource :as resource-component]))

(defn naddr-view-panel []
  (let [route-params @(re-frame/subscribe [::subs/route-params])
        naddr (:naddr route-params)
        data (nostr/decode-naddr naddr)
        events-with-same-d (re-frame/subscribe [::subs/events-by-d-tag (:identifier data)])]
    (fn []
      (let [latest-event (first @events-with-same-d)]
        [:div
         (case (:kind latest-event)
           30142 [:div {:class "flex flex-col"}
                  [:img {:class "w-full object-contain bg-transparent max-h-[40vh] "
                         :src (nostr/get-image-from-metadata-event  latest-event)}]
                  [:h1 {:class "text-2xl font-bold"} (nostr/get-name-from-metadata-event latest-event)]
                  [:div
                   (resource-component/skos-tags [@events-with-same-d "about"])]
              ;; Keywords
                  [:div
                   (doall
                    (for [kw (nostr/get-keywords-from-metadata-event latest-event)]
                      (resource-components/keywords-component kw)))]
              ;; Author
                  [:div {:class "ml-auto mr-0 my-2"}
                   (resource-component/authors-component latest-event)]
                  [:p (nostr/get-description-from-metadata-event latest-event)]]

           [:p "Dunno how to render that, sorry 🤷"])]))))

(comment
  (re-frame/subscribe [::subs/events-by-d-tag "https://langsci-press.org/catalog/book/406"]))

(defmethod routes/panels :naddr-view-panel [] [naddr-view-panel])
