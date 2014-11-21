# clj-der

A Clojure library designed to parse ASN.1 binary stream.

## References and Tools

These tools and documents were used during the development of clj-asn1:

 * [ASN.1 Javascript decoder](http://lapo.it/asn1js/): Paste in a PEM file and
   the it will base64 decode the binary. Great display which shows what bytes 
   correspond to what ASN.1 value.
 * [ASN.1 Playground] (http://asn1-playground.oss.com/): Great web app for 
   specifying, compiling and binary encoding ASN.1.
 * [ASN.1 Complete](http://www.oss.com/asn1/resources/books-whitepapers-pubs/larmouth-asn1-book.pdf)
   by _Prof. John Larmouth_. Great reference to the ASN.1 specification language
   as well as ASN.1 binary representation. 

## Todo
                                                
 * Ability to parse DER from byte arrays.
 * Support parsing types
   * GeneralizedTime
   * Real
   * Enumerated
   * Other string types
 * Look into the weird extension types; add support or throw/warn?
   * Extension
   * Embedded PDV
   * Character string
 * Long tag numbers
 * Parse constructed values with an indefinite length.

## Roadmap

 * Binary encoding of Clojure representation of ASN.1.
 * Create an internal DSL which allows for specifying ASN.1 schemas.
 * Build an ASN.1 compiler which compiles down to the Clojure DSL. 

## License

Copyright © 2014 Justin May

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
