(ns ied.nostr
  (:require
   [promesa.core :as p]
   ["@noble/secp256k1" :as secp]
   ["nostr-tools/nip19" :as nip19]))

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

(comment
  (.getPublicKey secp (.utils.randomPrivateKey secp)))

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
  (.decode nip19 npub))

(comment
  (get-npub-from-pk "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d")
  (get-pk-from-npub "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6" )
  )
