(ns clj-asn1.core
  (:import (java.io InputStream)
           (java.nio ByteBuffer)
           (java.nio.channels Channel Channels))
  (:require [schema.core :as schema]
            [gloss.io :as gloss-io]
            [gloss.core :as gloss]
            [byte-streams :as bs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schemas

(def Asn1Id {:constructed?     schema/Bool
             :tag              schema/Int
             :class            (schema/enum :universal :application
                                            :context-specific :private)})

(def Asn1Len {:length           schema/Int
              :infinite-length? schema/Bool})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private

(schema/defn fill-buffer :- ByteBuffer
  [input size]
  (cond
    ;; TODO: Make schemas out of "Is Channel", "Is ByteBUffer" "or both"
    ;; use them in the conds and the schema for input
    (instance? Channel input)
    (let [buffer (ByteBuffer/allocate size)]
      (.read input buffer)
      (.flip buffer))

    (instance? ByteBuffer input)
    (let [arr (make-array Byte size)]
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

(defn id->class [id]
  (condp = (bit-and 0xC0 id)
    0x00 :universal
    0x40 :application
    0x80 :context-specific
    0xC0 :private))

(defn id->constructed? [id]
  (let [masked (bit-and 0x20 id)]
    (= 0x20 masked)))

(defn id->tag [id]
  (bit-and 0x1F id))

(schema/defn read-asn1-id :- Asn1Id
  [input]
  (let [[_ id] (read-frame input [:id :ubyte] 1)]
    {:class        (id->class id)
     :constructed? (id->constructed? id)
     :tag          (id->tag id)}))

(schema/defn read-asn1-len :- Asn1Len
  [input]
  (let [[_ len-byte] (read-frame input [:len :ubyte] 1)]
    {:length len-byte
     :infinite-length? false}))

(schema/defn read-asn1
  [input]
  (let [header (read-asn1-id input)
        length (read-asn1-len input)]
    (merge header length)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn decode
  "Decode the provided DER-encoded data into a Clojure data structure."
  [input-stream :- InputStream]
  (let [channel (Channels/newChannel input-stream)]
    (read-asn1 channel)))

(defn encode
  "Encode the provided Clojure data structure describing a DER data structure
   into its binary representation."
  [der-clj-data]
  ;; TODO: Create a "to-DER" function that works for each DER data type
  )
