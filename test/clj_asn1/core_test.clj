(ns clj-asn1.core-test
  (:import (java.nio ByteBuffer))
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [schema.test :as schema-test]
            [schema.core :as schema]
            [clj-asn1.core :as asn1]
            [gloss.core :as gloss]
            [gloss.io :as gloss-io]))

(use-fixtures :once schema-test/validate-schemas)

(defn ubyte [val]
  "Signed bytes, Java? Really???"
  (if (>= val 128)
    (byte (- val 256))
    (byte val)))

(defn vec->byte-arr [v] (into-array Byte/TYPE (map ubyte v)))
(defn vec->buf      [v] (-> v vec->byte-arr ByteBuffer/wrap))
(defn str->buffer   [s] (ByteBuffer/wrap (.getBytes s)))

(defn string-tlv
  "Create a TLV with the provided id and string as the value. This currently
  only works for "
  [id s]
  (let [bytes (.getBytes s)]
    (vec->byte-arr
      (concat (vec->byte-arr [id (count bytes)]) bytes))))

(defn decoded-value [v]
  (:value (asn1/decode
            (if (string? v)
              (str->buffer v)
              (vec->byte-arr v)))))

(deftest length-computations
  (testing "Indefinite length"
    (is (nil? (asn1/read-length-desc (vec->buf [0x80])))))

  (testing "Definite short form"
    (is (= 0x42 (asn1/read-length-desc (vec->buf [0x42])))))

  (testing "Definite long form"
    (is (= 527 (asn1/read-length-desc (vec->buf [0x82 0x02 0x0f]))))))

(deftest reading-der
  (testing "reading integer values"
    (is (= 1 (decoded-value [0x02 0x01 0x01])))
    (is (= -129 (decoded-value [0x02 0x02 0xFF 0x7F])))
    (is (= (asn1/integer -128)
           (asn1/decode (vec->byte-arr [0x02 0x01 0x80]))))
    (is (= (asn1/integer 65537)
           (asn1/decode (vec->byte-arr [0x02 0x03 0x01 0x00 0x01])))))

  (testing "reading OID values"
    (is (= [1 2 840 113549 1 1 1]
           (decoded-value
             [0x06 0x09 0x2A 0x86 0x48 0x86 0xF7 0x0D 0x01 0x01 0x01])))

    (is (= [1 2 840 113549]
           (decoded-value
             [0x06 0x06 0x2a 0x86 0x48 0x86 0xf7 0x0d])))

    (is (= (asn1/oid [1 3 6 1 4 1 34380 1 1 3])
           (asn1/decode
             (vec->byte-arr [0x06 0x0B 0x2B 0x06 0x01 0x04 0x01 0x82 0x8C 0x4C
                             0x01 0x01 0x03])))))

  (testing "reading boolean values"
    (is (= (asn1/decode (vec->byte-arr [0x01 0x01 0xFF]))
           (asn1/boolean true)))

    (is (= (asn1/decode (vec->byte-arr [0x01 0x01 0x00]))
           (asn1/boolean false))))

  (testing "reading a UTC date"
    (let [id 0x17]
      (is (= (str (decoded-value (string-tlv id "141113004932Z")))
             "Wed Nov 12 16:49:32 PST 2014"))

      (is (= (str (decoded-value (string-tlv id "1411130049Z")))
             "Wed Nov 12 16:49:00 PST 2014"))

      (is (= (str (decoded-value (string-tlv id "141113004932-0800")))
             "Thu Nov 13 00:49:32 PST 2014"))

      (is (= (str (decoded-value (string-tlv id "1411130049+1000")))
             "Wed Nov 12 06:49:00 PST 2014"))))

  (testing "reading UTF-8 strings"
    (let [id 0x0C]
      (is (= (asn1/decode   (string-tlv id "OneTwo"))
             (asn1/utf8-str "OneTwo")))

      (is (= (asn1/decode   (string-tlv id "¡™£¢∞§¶•ª"))
             (asn1/utf8-str "¡™£¢∞§¶•ª"))))))
