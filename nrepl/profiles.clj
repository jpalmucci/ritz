{:codox {:codox {:writer codox-md.writer/write-docs
                 :output-dir "../doc/0.4/nrepl/api"}
         :dependencies [[codox-md "0.1.0"]]
         :pedantic :warn}
 :marginalia {:pedantic :warn
              :dir "../doc/0.4/nrepl/source"}
 :release
 {:set-version
  {:updates [{:path "README.md"
              :no-snapshot true
              :search-regex #"ritz/ritz-nrepl \"\d+\.\d+\.\d+\""}
             {:path "README.md"
              :no-snapshot true
              :search-regex #"lein-ritz \"\d+\.\d+\.\d+\""}
             {:path "elisp/nrepl-ritz.el"
              :no-snapshot true
              :search-regex #";; Version: \d+\.\d+\.\d+"}]}}}
