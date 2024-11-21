(ns ied.nostr
  (:require
   [promesa.core :as p]
   [clojure.string :as str]
   ["@noble/secp256k1" :as secp]
   ["nostr-tools/nip19" :as nip19]
   ["nostr-tools/pure" :as nostr]
   [cljs.core :as c]))

;;;;;;;;;;;;;;;;;;;;
;; Profile
;;;;;;;;;;;;;;;;;;;;

(defn profile-picture [profile pubkey]
  (if (:picture profile)
    (:picture profile)
    (str "https://robohash.org/" pubkey)))

(defn event-to-serialized-json [m]
  (let [event [0
               (:pubkey m)
               (:created_at m)
               (:kind m)
               (:tags m)
               (:content m)]]

    (js/JSON.stringify (clj->js event))))

(defn pad-start [s len pad]
  (let [padding (apply str (repeat (- len (count s)) pad))]
    (str padding s)))

(defn to-hex [b]
  (pad-start (.toString b 16) 2 "0"))

(defn byte-array-to-hex [byte-array]
  (apply str (map to-hex byte-array)))

(defn sha256 [text]
  (p/let [encoder (new js/TextEncoder)
          data (.encode encoder text)
          hash (.crypto.subtle.digest js/window "SHA-256" data)
          hashArray (.from js/Array (new js/Uint8Array hash))
          byteArray (js/Uint8Array. hashArray)
          hexArray (byte-array-to-hex byteArray)]
    (.log js/console hexArray)
    hexArray))

(defn finalize-event [event sk]
  (.finalizeEvent nostr (clj->js event) sk))

(defn generate-sk []
  ;; Generate a secure (private) key as byte array
  (.utils.randomPrivateKey secp))

(defn sk-as-hex [sk]
  (byte-array-to-hex sk))

(defn string-to-byte-array [s]
  (let [encoder (js/TextEncoder.)]
    (.encode encoder s)))

(defn sk-as-nsec [sk]
  ;; let nsec = nip19.nsecEncode(sk)
  ;; sk should be byte-array
  (.nsecEncode nip19 (string-to-byte-array sk)))

(defn nsec-as-sk [nsec]
  ;; byte array is returned
  (.-data (.decode nip19 nsec)))

(defn get-pk-from-sk [sk]
  (.getPublicKey nostr sk))

(comment
  (let [nsec "nsec16f87vq2xvvus3qxxtmdhgvq6pyfmn7nr9ck9yw8sc8ar7xradmss66z5fz"]
    (sk-as-hex (nsec-as-sk nsec)))

  (.getPublicKey secp (.utils.randomPrivateKey secp))
  (byte-array-to-hex (.utils.randomPrivateKey secp))
  (get-pk-from-sk (generate-sk)))

(comment
  (p/let [event {:content "hello world"
                 :created_at 1722606842
                 :kind 1
                 :pubkey "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
                 :tags []}

          serialized-event (event-to-serialized-json event)
          id (sha256 serialized-event)]
    (println serialized-event)
    (println id)))

(defn string-to-uint8array [s]
  (let [encoder (new js/TextEncoder)]
    (.encode encoder s)))

(defn get-npub-from-pk [pk]
  (.npubEncode nip19  pk))

(comment
  (get-npub-from-pk "1c5ff3caacd842c01dca8f378231b16617516d214da75c7aeabbe9e1efe9c0f6"))

(defn get-pk-from-npub [npub]
  (.-data (.decode nip19 npub)))

(comment
  (get-pk-from-npub "npub1r30l8j4vmppvq8w23umcyvd3vct4zmfpfkn4c7h2h057rmlfcrmq9xt9ma"))

(defn get-d-id-from-event [event]
  (second (first (filter #(= "d" (first %)) (:tags event)))))

(defn naddr-from-event [event]
  (let [pk (:pubkey event)
        relays #_(get-relays-from-event event) ["ws://localhost:7777" "wss://relay.sc24.steffen-roertgen.de"] ;; TODO how to know what relays to use here? do i need to remember where i fetched an event from? or maybe just skip?
        kind (:kind event)
        identifier (get-d-id-from-event event)
        naddr (.naddrEncode nip19 (clj->js {:pubkey pk
                                            :relays relays
                                            :kind kind
                                            :identifier identifier}))]
    naddr))

(defn decode-naddr
  "Takes a nip-19 naddr and returns its data
  Returns:
  A map with `:identifier` `:pubkey` `:relays` `:kind`.
  "
  [naddr]

  (:data (js->clj (.decode nip19 naddr) :keywordize-keys true)))

(comment
  (def addressable-event {:content "Added AMB Resource with d-tag",
                          :created_at 1729616701,
                          :id
                          "dec183af8f1ac91dac3bb7716b8564ab515c050480721c7ee2ea308b51caf982",
                          :kind 30142,
                          :pubkey
                          "1c5ff3caacd842c01dca8f378231b16617516d214da75c7aeabbe9e1efe9c0f6",
                          :sig
                          "17d54fc1a418100a41d8b9dd0eac5db0cad544bff84c5d13befa1c1791979fbf319234c9749a690d1cb166116741642deafb8b9c8f91a16590931f7eb80f3bb4",
                          :tags
                          [["d" "https://av.tib.eu/media/40427"]
                           ["r" "https://av.tib.eu/media/40427"]
                           ["id" "https://av.tib.eu/media/40427"]
                           ["name" "Digitale Identitäten: Sicher, dezentral und Europäisch?"]
                           ["description"
                            "(de)#INFORMATIK2018 Panelgespräch: Digitale Identitäten: Sicher, dezentral und Europäisch? Moderation: Dr. Jan Sürmeli, TU Berlin • Prof. Dr. Reinhard Riedl, Präsident Schweizer InformatikGesellschaft (FH Bern) • Prof. Dr. Hannes Federrath, Präsident der Gesellschaft für Informatik (Universität Hamburg) • Prof. Dr. Kai Rannenberg, Präsidium der Gesellschaft für Informatik (Uni Frankfurt) • Prof. Dr. Stefan Jähnichen, TU Berlin / FZI Forschungszentrum Informatik • Benjamin Helfritz, DIN Deutsches Institut für Normung e.V. • Arno Fiedler, Vorstand Sichere Identität Berlin Brandenburg"]
                           ["keywords" "Computer Science"]
                           ["image" "https://av.tib.eu/thumbnail/40427"]
                           ["about"
                            "https://w3id.org/kim/hochschulfaechersystematik/n71"
                            "Studienbereich Informatik"
                            "de"]
                           ["about"
                            "https://w3id.org/kim/hochschulfaechersystematik/n8"
                            "Ingenieurwissenschaften"
                            "de"]
                           ["creator" "test-uri" "maxi muster"]
                           ["creator" "" "maxa muster"]
                           ["inLanguage" "de"]]})
  (naddr-from-event addressable-event)
  (get-creators-from-metadata-event addressable-event)
  (.log js/console (clj->js addressable-event))

  (decode-naddr "naddr1qvzqqqr4hcpzq8zl7092ekzzcqwu4rehsgcmzesh29kjznd8t3aw4wlfu8h7ns8kqqwksar5wpen5te0v9mzuarfvghx2af0d4jkg6tp9u6rqdpjxunlx7u8"))

;; TODO make multimethod?
(defn get-list-name [list]
  (or (second (first (filter #(= "title" (first %)) (:tags list))))
      (second (first (filter #(= "alt" (first %)) (:tags list))))
      (second (first (filter #(= "d" (first %)) (:tags list))))
      (str "No name found for List-ID: " (:id list))))

(defn get-name-from-metadata-event [event]
  (or (second (first (filter #(= "name" (first %)) (:tags event))))
      (second (first (filter #(= "title" (first %)) (:tags event))))
      (second (first (filter #(= "id" (first %)) (:tags event))))
      (str "No name found for Metadata-Event: " (:id event))))

(defn get-creators-from-metadata-event [event]
  (or (map (fn [e] {:id (second e)
                    :name (nth e 2 "n/a")}) (filter #(= "creator" (first %)) (:tags event)))
      (str "No creators found for Metadata-Event: " (:id event))))

(defn get-keywords-from-metadata-event [event]
  (rest (first (filter (fn [e] (= "keywords" (first e))) (:tags event)))))

(defn get-image-from-metadata-event [event]
  (or (let [img-url (second (first (filter #(= "image" (first %)) (:tags event))))]
        (if (= "" img-url) false img-url))
      "/assets/edu-feed-logo.webp"))

(defn get-description-from-metadata-event [event]
  (or (second (first (filter #(= "description" (first %)) (:tags event))))
      (str "No description found for Metadata-Event: " (:id event))))

(defn get-about-tags-from-metadata-event [event]
  (filter #(= "about" (first %)) (:tags event)))

(defn get-about-names-from-metadata-event [event]
  (->> (get-about-tags-from-metadata-event event)
       (map #(nth % 2 nil))))

(comment
  (get-npub-from-pk "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d")
  (get-pk-from-npub "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"))

(defn valid-timestamp? [t]
  (and (integer? t)
       (pos? t)
       (< t (* 1e10))))

(defn valid-kind? [k]
  (integer? k))

(defn valid-tags? [tags]
  (and (vector? tags)
       (every? (fn [tag]
                 (and
                  (vector? tag)
                  (every? some? tag)))
               tags)))

(defn valid-unsigned-nostr-event? [event]
  (let [{:keys [created_at kind tags content]} event]
    (and
     (valid-timestamp? created_at)
     (valid-kind? kind)
     (valid-tags? tags)
     (string? content))))

(comment
  (valid-tags? [["hello" "there"] ["fa" ""]]))

(defn get-tag-value [tags tag-prefix]
  (some #(when (= tag-prefix (first %)) (second %)) tags))

(defn sort-lists [events]
  (sort-by (fn [event]
             (let [tags (:tags event)
                   name-value (get-tag-value tags "name")
                   d-value (get-tag-value tags "d")]
               (or name-value d-value)))
           events))

;; TODO rename to ..."from-d-tag" 
(defn extract-id-from-tag
  [s]
  (let [parts (str/split s #":")]
    (if (>= (count parts) 2)
      (second parts)
      s)))

(comment
  (extract-id-from-tag "30142:29b2dc8b83e3f8c79a9ee2535b4adb6105b90af893612e72f675a5d16f8544b5:https://wtcs.pressbooks.pub/digitalliteracy/"))

(defn list-contains-metadata-event? [list event]
  (let [tags (:tags list)
        metadata-event-ids-in-list (set (map extract-id-from-tag (filter (fn [t] (= "a" (first t))) tags)))
        event-id (:id event)]
    (.log js/console "metadata event ids in list" metadata-event-ids-in-list)
    (and
     (seq metadata-event-ids-in-list)
     (contains? metadata-event-ids-in-list event-id))))

(defn build-tag-for-adressable-event [event]
  ["a" (str "30142:" (:id event) ":" (second (first (filter #(= "d" (first %)) (:tags event)))))])

(defn get-event-ids-from-list [list]
  (->> (:tags list)
       (filter #(= (or "a" "e") (first %))) ;; just a and e tags
       (map second) ;; just the id
       (map extract-id-from-tag)
       (set)))
