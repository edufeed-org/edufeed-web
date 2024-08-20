(ns ied.nostr
  (:require
   [promesa.core :as p]
   [clojure.string :as str]
   ["@noble/secp256k1" :as secp]
   ["nostr-tools/nip19" :as nip19]
   ["nostr-tools/pure" :as nostr]
   [cljs.core :as c]))

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
    hexArray))

(defn signEvent [hashedEvent sk]
  (.sign secp hashedEvent sk))

(defn generate-sk []
  ;; Generate a secure (private) key as byte array
  (.utils.randomPrivateKey secp))

(defn sk-as-hex [sk]
  (byte-array-to-hex sk))

(defn sk-as-nsec [sk]
  ;; let nsec = nip19.nsecEncode(sk)
  ;; sk should be byte-array
  (.nsecEncode nip19 sk))

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
  (.npubEncode nip19 pk))

(defn get-pk-from-npub [npub]
  (.-data (.decode nip19 npub)))

;; TODO make multimethod?
(defn get-list-name [list]
  (or (second (first (filter #(= "title" (first %)) (:tags list))))
      (second (first (filter #(= "alt" (first %)) (:tags list))))
      (second (first (filter #(= "d" (first %)) (:tags list))))
      (str "No name found for List-ID: " (:id list))))

(defn get-d-id-from-event [event]
  (second (first (filter #(= "d" (first %)) (:tags event)))))

(defn get-name-from-metadata-event [event]
  (or (second (first (filter #(= "name" (first %)) (:tags event))))
      (second (first (filter #(= "id" (first %)) (:tags event))))
      (str "No name found for Metadata-Event: " (:id event))))

(defn get-image-from-metadata-event [event]
  (or (let [img-url (second (first (filter #(= "image" (first %)) (:tags event))))]
       (if (= "" img-url ) false img-url))
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

(defn build-kind-30142-tag [event]
  ["a" (str "30142:" (:id event) ":" (second (first (filter #(= "d" (first %)) (:tags event)))))])

(defn get-event-ids-from-list [list]
  (->> (:tags list)
       (filter #(= (or "a" "e") (first %))) ;; just a and e tags
       (map second) ;; just the id
       (map extract-id-from-tag)
       (set)))
