(ns ied.db)

(def default-db
  {:name "re-frame"
   :current-path nil
   :show-add-event false
   :events #{}
   :pk nil
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
                    {:name "damus"
                     :uri "wss://relay.damus.io"
                     :status "disconnected"}
                    ]
   :selected-events #{}
   :selected-lists #{}
   :sockets []})
