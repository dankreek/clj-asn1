(ns clj-asn1.core
  (:import (java.io InputStream)
           (java.nio ByteBuffer)
           (java.nio.channels Channel Channels)
           (java.util Date))
  (:refer-clojure :exclude [set sequence boolean])
  (:require [schema.core :as schema]
            [clj-asn1.schemas :refer [Asn1TypeValue Asn1Universal]
                              :as asn1-schemas]
            [gloss.io :as gloss-io]
            [gloss.core :as gloss]
            [byte-streams :as bs]
            [clojure.string :as string]
            [clj-time.core :as time]
            [clj-time.format :as time-format]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defines

(def utc-date-parser
  (time-format/formatter time/utc
                         "YYYYMMddHHmm'Z"
                         "YYYYMMddHHmmss'Z"
                         "YYYYMMddHHmmZ"
                         "YYYYMMddHHmmssZ"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schemas

(def ValidInput
  "Valid input into all parsing functions which read in streaming data."
  (schema/either
    (schema/pred (partial instance? Channel))
    (schema/pred (partial instance? ByteBuffer))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private

(defn to-unsigned
  "Java has signed bytes. Why?"
  [val]
  (if (< val 0)
    (+ val 256)
    val))

(schema/defn fill-buffer :- ByteBuffer
  [input :- ValidInput
   size :- schema/Int]
  (cond
    (instance? Channel input)
    (let [buffer (ByteBuffer/allocate size)]
      (.read input buffer)
      (.flip buffer))

    (instance? ByteBuffer input)
    (let [arr (make-array Byte/TYPE size)]
      (.get input arr)
      (ByteBuffer/wrap arr))

    :else
    (throw (Exception. "Only ByteBuffers and Channels can be read from."))))

(defn read-frame
  "Read number of bytes specified by size from the input channel/buffer using
  the gloss frame provided."
  [input frame size]
  (let [bytes (fill-buffer input size)]
    (gloss-io/decode frame bytes)))

(defn id->class
  [id]
  (condp = (bit-and 0xC0 id)
    0x00 :universal
    0x40 :application
    0x80 :context-specific
    0xC0 :private))

(defn id->constructed?
  "Parse the id byte to check if this is a constructed type."
  [id]
  (let [masked (bit-and 0x20 id)]
    (= 0x20 masked)))

(defn id->tag [id]
  "Return the tag number from the id byte."
  (bit-and 0x1F id))

(defn decode-type-id
  [id]
  (let [class (id->class id)
        tag   (id->tag id)]
    (merge {:constructed? (id->constructed? id)
            :class         class}
           (if (= :universal class)
             {:class-tag (get asn1-schemas/asn1-class-tags tag)}
             {:tag tag}))))

(schema/defn read-type-id
  [input]
  (let [[_ id] (read-frame input [:id :ubyte] 1)]
    (decode-type-id id)))

(defn compute-unsigned-byte-list
  "Given a list of bytes, least-significant first, compute the unsigned integer
  value of all the bytes strung together. The base signifies the exponent value
  of each successive integer."
  [bytes-list base]
  (loop [bytes (reverse bytes-list)
         exp   0
         acc   0]
    (if (seq bytes)
      (recur (rest bytes)
             (inc exp)
             (+ acc (* (first bytes) (Math/pow base exp))))
      (int acc))))

(defn parse-int
  "Given byte buffer parse it into the smallest signed integer type that
  is will fit into."
  [buf]
  (let [b (bigint (.array buf))]
    (condp >= (.bitLength b)
      32 (int b)
      64 (long b)
      b)))

(defn read-next-oid-value!
  "Read the individual bytes that comprise one OID value. This is done by
  reading each octet while the MSB is 1, then stopping when an octet's MSB is
  0. The MSB is stripped off of each value for later computation."
  [buffer]
  (compute-unsigned-byte-list
    (loop [acc []]
      (let [val (to-unsigned (.get buffer))]
        (if (= 0x80 (bit-and val 0x80))
          (recur (conj acc (bit-and val 0x7f)))
          (conj acc val))))
    128))

(defn read-oid-values!
  "Read a list of base-127 OID values from a ByteBuffer. Stops when the buffer
  is empty."
  [buffer]
  (loop [acc []]
    (if (.hasRemaining buffer)
      (recur (conj acc (read-next-oid-value! buffer)))
      acc)))

(defn parse-oid
  "Decode a BER-encoded OID from the given ByteBuffer"
  [buf]
  (let [[_ first-byte] (read-frame buf [:head :ubyte] 1)
        digit1 (int (/ first-byte 40))
        digit2 (- first-byte (* 40 digit1))
        rest-digits (read-oid-values! buf)]
    (concat [digit1 digit2] rest-digits)))

(defn parse-long-len
  "Given an input source and the number of length bytes to read, return the
  total length of the ASN.1 data type being parsed."
  [input num-bytes]
  (let [bytes-list (read-frame input (repeat num-bytes :ubyte) num-bytes)]
    (compute-unsigned-byte-list bytes-list 256)))

(defn read-length-desc
  "Reads the length octet from an ASN.1 stream and returns it. If an indefinite
  length is specified, then nil is returned."
  [input]
  (let [[_ len-byte] (read-frame input [:len :ubyte] 1)
        masked-len   (bit-and len-byte 0x80)]
    (cond
      (= 0x80 len-byte)   nil
      (= 0x80 masked-len) (parse-long-len input (bit-and len-byte 0x7f))
      :else               len-byte)))

(schema/defn parse-bool :- schema/Bool
  "Parses a boolean value from the value bytes of an ASN.1 boolean type."
  [buf]
  (let [val (.get buf)]
    (if (= val 0)
      false
      true)))

(schema/defn calculate-century :- schema/Int
  "Given the last two digits of a year, calculate the the century within a 50
  year sliding window. Note that this algorithm only works until the year
  10,000. I found this on page 91 of the book 'ASN.1 Complete' by
  John Larmoth."
  [year-end :- schema/Int]
  (let [this-year  (.getYear (time/now))
        high-bound (+ this-year 49)
        this-cent  (Integer/parseInt (.substring (str this-year) 0 2))]
    (if (< high-bound (+ (* 100 this-cent) year-end))
      (dec this-cent)
      this-cent)))

(schema/defn parse-utc-time :- Date
  "Parses a UTC time code into its string representation"
  [buf]
  (let [str-form       (gloss-io/decode (gloss/string :ascii) buf)
        year-end       (Integer/parseInt (.substring str-form 2 4))
        century        (calculate-century year-end)
        whole-str-form (str century str-form)]
    (.toDate
      (time-format/parse utc-date-parser whole-str-form))))

(schema/defn parse-utf8-string :- schema/Str
  "Parse a UTF8 string payload"
  [buf]
  (gloss-io/decode (gloss/string :utf-8) buf))

;; Needed beause read-tlv is a rescursivley called.
(declare read-tlv)

(defn parse-constructed-value
  [buffer]
  (loop [acc []]
    (if (.hasRemaining buffer)
      (recur (conj acc (read-tlv buffer)))
      acc)))

(defn parse-value
  "Given a header and length read the value of the ASN.1 type from the input
  stream."
  [input header length]
  (condp = length
    nil (throw (Exception. "Indefinite lengths not yet supported."))
    0   nil

    (let [buffer (fill-buffer input length)
          class-tag (:class-tag header)]
      (cond
        (:constructed? header)     (parse-constructed-value buffer)
        (= class-tag :boolean)     (parse-bool buffer)
        (= class-tag :integer)     (parse-int buffer)
        (= class-tag :oid)         (parse-oid buffer)
        (= class-tag :null)        nil
        (= class-tag :utf8-string) (parse-utf8-string buffer)
        (= class-tag :utc-time)    (parse-utc-time buffer)
        ;; TODO: log a warning or something about the fact the type wasn't explicitly parsed
        :else (.array buffer)))))

(schema/defn read-tlv
  "Read an ASN.1 type-length-value "
  [input]
  (let [header     (read-type-id input)
        length     (read-length-desc input)
        value      (parse-value input header length)
        indef-len? (when (:constructed? header)
                     {:indefinite-length? (= -1 length)})]
    (merge
      header
      indef-len?
      {:value value})))

(schema/defn constructed-universal :- Asn1TypeValue
  ([class-tag value]
   (constructed-universal class-tag value false))
  ([class-tag :- (schema/enum :sequence :set)
    value :- [Asn1TypeValue]
    infinite :- schema/Bool]
   {:class              :universal
    :class-tag          class-tag
    :constructed?       true
    :indefinite-length? infinite
    :value              value}))

(schema/defn universal-type :- asn1-schemas/Asn1Universal
  [class-tag :- (apply schema/enum (vals asn1-schemas/asn1-class-tags))
   value]
  {:constructed? false
   :class        :universal
   :class-tag    class-tag
   :value        value})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn decode :- Asn1TypeValue
  "Decode the provided DER-encoded data into a Clojure data structure."
  ;; TODO: Ensure an InputStream, Channel, ByteBuffer or byte[] is passed in
  ;; TODO: and converted properly to a ByteBuffer
  [input-stream]
  (let [input (cond
                (instance? InputStream input-stream)
                (Channels/newChannel input-stream)

                (asn1-schemas/byte-array? input-stream)
                (ByteBuffer/wrap input-stream)

                :else
                input-stream)]
    (read-tlv input)))

(schema/defn encode
  "Encode the provided Clojure data structure describing a DER data structure
   into its binary representation."
  [der-clj-data]
  ;; TODO: Create a "to-DER" function that works for each DER data type
  )

(schema/defn ^:always-validate
             sequence :- Asn1Universal
  [values :- [Asn1TypeValue]]
  (constructed-universal :sequence values))

(schema/defn ^:always-validate
             set :- Asn1Universal
  [values :- [Asn1TypeValue]]
  (constructed-universal :set values))

(schema/defn ^:always-validate
             integer :- Asn1Universal
  [value :- schema/Int]
  (universal-type :integer value))

(schema/defn ^:always-validate
             boolean :- Asn1Universal
  [value :- schema/Bool]
  (universal-type :boolean value))

(schema/defn ^:always-validate
             oid :- Asn1Universal
  [value :- [schema/Int]]
  (universal-type :oid value))

(schema/defn ^:always-validate
             utf8-str :- Asn1Universal
  [value :- schema/Str]
  (universal-type :utf8-string value))

