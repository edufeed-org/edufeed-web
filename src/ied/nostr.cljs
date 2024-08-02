(ns ied.nostr
  (:require
   [promesa.core :as p]
   ["@noble/secp256k1" :as secp]))

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

