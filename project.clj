(defproject clj-asn1 "0.0.1-SNAPSHOT"
  :description "Clojure ASN.1 binary stream encoder and decoder."
  :url "http://github.com/dankreek/clj-der"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [prismatic/schema "0.3.3" :exclusions [potemkin]]
                 [gloss "0.2.3" :exclusions [manifold]]
                 [byte-streams "0.2.0-alpha3"]
                 [clj-time "0.8.0"]])
