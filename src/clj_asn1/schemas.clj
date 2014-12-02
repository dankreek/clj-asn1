(ns clj-asn1.schemas
  "Utilities for creating schemas for ASN.1 objects."
  (:require [schema.core :as schema]))

(defn byte-array? [x]
  (= (Class/forName "[B")
     (.getClass x)))

;; TODO: Make this a vector and just use the index as the lookup value
(def asn1-class-tags
  {0  :eoc
   1  :boolean
   2  :integer
   3  :bit-string
   4  :octet-string
   5  :null
   6  :oid
   7  :object-descriptor
   8  :external
   9  :real
   10 :enum
   11 :embedded-pdv
   12 :utf8-string
   13 :relative-oid
   16 :sequence
   17 :set
   18 :numeric-string
   19 :printable-string
   20 :t61-string
   21 :videotex-string
   22 :ia5-string
   23 :utc-time
   24 :generalized-time
   25 :graphic-string
   26 :visible-string
   27 :general-string
   28 :universal-string
   29 :character-string
   30 :bmp-string})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Partial schemas

(def Asn1UniversalConstructed
  "The common fields in all ASN.1 constructed types."
  {:constructed? true
   :class :universal
   (schema/optional-key
     :indefinite-length?) schema/Bool})

(def Asn1UniversalAtomic
  "The common fields in all ASN.1 atomic types."
  {:constructed? false
   :class :universal})

(def Asn1TypeValueCommon
  {:value                 schema/Any
   :constructed?          schema/Bool
   (schema/optional-key
     :indefinite-length?) schema/Bool})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schemas

(def Asn1Universal
  "The headers needed for an universal ASN.1 type."
  (merge Asn1TypeValueCommon
         {:class     (schema/enum :universal)
          :class-tag (apply schema/enum (vals asn1-class-tags))}))

(def Asn1Other
  "The headers needed for a context-specific or private ASN.1 type."
  (merge Asn1TypeValueCommon
         {:tag   schema/Int
          :class (schema/enum :application :context-specific :private)}))

(def Asn1TypeValue
  "An ASN.1 type and value. The length is not included since it is computed
  during encoding."
  (schema/either
    Asn1Universal
    Asn1Other))

(def Asn1ObjectId
  (merge Asn1UniversalAtomic
         {:class-tag :oid
          :value     [schema/Int]}))

(def Asn1Null
  (merge Asn1UniversalAtomic
         {:class-tag :null
          :value     nil}))

(def Asn1BitString
  (merge Asn1UniversalAtomic
         {:class-tag :bit-string
          :value     (schema/pred byte-array?)}))

(schema/defn seq-schema
  "Create a schema for an ASN.1 sequence that has a value that adheres to the
  provided sequence."
  [value-schema :- [schema/Any]]
  (merge Asn1UniversalConstructed
         {:class-tag :sequential
          :value     value-schema}))

(schema/defn set-schema
  "Create a schema for an ASN.1 set that has a value that adheres to the
  provided sequence."
  [value-schema :- #{schema/Any}]
  (merge Asn1UniversalConstructed
         {:class-tag :set
          :value     value-schema}))

