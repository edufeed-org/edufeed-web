(ns ied.db)

(def default-db
  {:name "re-frame"
   :current-path nil
   :show-add-event false
   :events #{}
   :pk nil
   :sk nil
   :list-kinds [30001 30004]
   :default-relays [{:name "strfry-1"
                     :uri "http://localhost:7777"
                     :id (random-uuid)
                     :status "disconnected"}
                    {:name "strfry-2" 
                     :uri "http://localhost:7778" 
                     :id (random-uuid)
                     :status "disconnected"}
                    {:name "rust-relay"
                     :uri "http://localhost:4445"
                     :id (random-uuid)
                     :status "disconnected"}
                    ; {:name "damus"
                    ;  :uri "wss://relay.damus.io"
                    ;  :status "disconnected"}
                    {:name "SC24"
                     :uri "wss://relay.sc24.steffen-roertgen.de"
                     :status "disconnected"}
                    ]
   :selected-events #{}
   :selected-list-ids #{}
   :show-lists-modal false
   :show-create-list-modal false
   :show-event-data-modal false
   :sockets []})
