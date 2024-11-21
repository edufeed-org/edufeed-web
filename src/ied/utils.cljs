(ns ied.utils
  (:require [re-frame.core :as re-frame]))

(comment
  (def test-form-data
    {:description "eine beschreibung",
     :creator
     {#uuid "36620422-1c79-4610-96e7-dc161fcc823e" "autor 1",
      #uuid "270fc20f-74ca-40a1-89ea-12e35253d714" "autor 2"},
     :publisher
     {#uuid "4b47e9cf-21d5-4e35-8afd-c7989a3c464b" "v1",
      #uuid "340af7ad-e94a-45ff-8221-94bc89d1f976" "v2"},
     :funder
     {#uuid "cd65e75f-e0e6-4b6f-994d-14ab89b5ce7c" "stil",
      #uuid "2f6cf069-8ddb-4e17-b479-d0c388931b83" "daad"},
     :learningResourceType
     ({:id "https://w3id.org/kim/hcrt/application",
       :prefLabel
       {:de "Softwareanwendung",
        :en "Software Application",
        :nl "Computerprogramma",
        :uk "Програмне забезпечення",
        :cs "Počítačový program",
        :fr "Application logicielle"}}
      {:id "https://w3id.org/kim/hcrt/assessment",
       :prefLabel
       {:de "Lernkontrolle",
        :en "Assessment",
        :nl "Evaluatie",
        :uk "Оцінювання",
        :cs "Hodnocení",
        :fr "Contrôle d’apprentissage"}}),
     :name "ein name",
     :datePublished "2018-01-10",
     :keywords
     {#uuid "516abd15-67dd-49fb-b1dd-699c84e7e76d" "s1",
      #uuid "27d95b48-8cbd-4ae8-90fc-2f88d88ee96e" "s2"},
     :image "ein bild",
     :uri "eine uri",
     :about
     [{:id "https://w3id.org/kim/hochschulfaechersystematik/n136",
       :notation ["136"],
       :prefLabel
       {:de "Religionswissenschaft",
        :en "Religious Studies",
        :uk "Релігієзнавство"}}
      {:id "https://w3id.org/kim/hochschulfaechersystematik/n1",
       :notation ["1"],
       :prefLabel
       {:de "Geisteswissenschaften",
        :en "Humanities",
        :uk "Гуманітарні науки"}}
      {:id "https://w3id.org/kim/hochschulfaechersystematik/n02",
       :notation ["02"],
       :prefLabel
       {:de "Evang. Theologie, -Religionslehre",
        :en "Protestant Theology, Protestant Religious Education",
        :uk "Протестантська теологія, протестантська релігійна освіта"}}
      {:id "https://w3id.org/kim/hochschulfaechersystematik/n01",
       :notation ["01"],
       :prefLabel
       {:de "Geisteswissenschaften allgemein",
        :en "Humanities (general)",
        :uk "Гуманітарні науки загалом"}}]})

  (println test-form-data)

  (form-data-to-tags test-form-data :description)
  (form-data-to-tags test-form-data :creator-form)

  (def creator-uuid
    {:creator
     {#uuid "36620422-1c79-4610-96e7-dc161fcc823e" {:id 1
                                                    :name "autor 1"}
      #uuid "270fc20f-74ca-40a1-89ea-12e35253d714" {:id 2
                                                    :name "autor 2"}}})

  (form-data-to-tags creator-uuid :creator-form)

  (def creator-array
    {:creator
     [{:id 1
       :name "autor 1"}
      {:id 2
       :name "autor 2"}]})

  (form-data-to-tags creator-array :creator))

(defmulti form-data-to-tags (fn [form-data k] k))

(defmethod form-data-to-tags :default [form-data k]
  nil)

(defmethod form-data-to-tags :description [form-data _]
  ["description" (:description form-data)])

(defmethod form-data-to-tags :creator-form [form-data _]
  (map (fn  [e]
         ["creator" (get (val e) :id "") (get (val e) :name "")])
       (:creator form-data)))

(defmethod form-data-to-tags :creator [form-data _]
  (map (fn  [e]
         ["creator" (get e :id "") (get e :name "")])
       (:creator form-data)))

(defmethod form-data-to-tags :publisher [form-data _]
  (map (fn  [e] ["creator" (val e)]) (:creator form-data)))

(defmethod form-data-to-tags :funder [form-data _]
  (map (fn  [e] ["creator" (val e)]) (:creator form-data)))

(defmethod form-data-to-tags :learningResourceType [form-data _])
