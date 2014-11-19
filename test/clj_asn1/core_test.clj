(ns clj-asn1.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [schema.test :as schema]
            [clj-asn1.core :refer :all]))

(use-fixtures :once schema/validate-schemas)

(deftest reading-der
  (testing "reading ASN.1 binary data works."
    (with-open [in (io/input-stream "dev-resources/clj_asn1/core/public-key.der.bin")]
      (println (decode in)))))