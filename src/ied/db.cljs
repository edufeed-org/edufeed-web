(ns ied.db
  (:require
   [ied.config :as config]))

(def default-opencard-board
  {:id 1
   :title "Default board"
   :lists [{:id 1 :items [{:id 1 :content "*Item 1*"} {:id 2 :content "Item 2"}]}
           {:id 2 :items [{:id 3 :content "Item 3"} {:id 4 :content "Item 4"}]}
           {:id 3 :items [{:id 5 :content "Item 5"} {:id 6 :content "Item 6"}]}
           {:id 4 :items [{:id 7 :content "Item 7"} {:id 8 :content "Item 8"}]}]})

(def default-db
  {:name "re-frame"
   :current-path nil
   :concept-schemes {}
   :confetti false
   :show-add-event false
   :events #{}
   :md-form-resource nil
   :selected-md-scheme nil
   :pk nil
   :sk nil
   :list-kinds [30001 30004]
   :opencard-kinds [30043 30044 30045]
   :opencard-boards [default-opencard-board]
   :follow-sets [30000]
   :resource-to-add nil
   :default-relays (concat
                    (if config/debug?
                      [{:name "strfry-1"
                        :uri "http://localhost:7777"
                        :id (random-uuid)
                        :status "disconnected"
                        :type ["outbox" "inbox"]}
                       {:name "strfry-2"
                        :uri "http://localhost:7778"
                        :id (random-uuid)
                        :status "disconnected"
                        :type ["outbox" "inbox"]}
                       {:name "rust-relay"
                        :uri "http://localhost:4445"
                        :id (random-uuid)
                        :status "disconnected"
                        :type ["outbox" "inbox"]}]
                      [{:name "SC24"
                        :uri "wss://relay.sc24.steffen-roertgen.de"
                        :status "disconnected"
                        :type ["outbox" "inbox"]}])
                    [{:name "Purplepages"
                      :uri "wss://purplepag.es"
                      :status "disconnected"
                      :type ["search"]}])
   :selected-events #{}
   :selected-list-ids #{}
   :show-lists-modal false
   :show-create-list-modal false
   :show-event-data-modal false
   :sockets []
   :search-results nil
   :user-language "de"})

(comment
  (filter
   (fn [s]
     (some
      #(= "search" %)
      (:type s)))
   (-> default-db :default-relays)))
