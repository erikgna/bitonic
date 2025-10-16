(defproject bitonic-sequence "0.1.0-SNAPSHOT"
  :description "Bitonic Sequence Generator with Performance Testing"
  :url "http://example.com/bitonic-sequence"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 ;; Web server and routing
                 [ring/ring-core "1.10.0"]
                 [ring/ring-jetty-adapter "1.10.0"]
                 [ring/ring-json "0.5.1"]
                 [compojure "1.7.0"]
                 ;; Redis client
                 [com.taoensso/carmine "3.2.0"]
                 ;; JSON parsing
                 [cheshire "5.11.0"]
                 ;; Performance benchmarking
                 [criterium "0.4.6"]
                 ;; Logging
                 [org.clojure/tools.logging "1.2.4"]
                 [ch.qos.logback/logback-classic "1.4.5"]]
  :main ^:skip-aot bitonic-sequence.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:dependencies [[ring/ring-mock "0.4.0"]]}})

:test-paths ["test"]
:source-paths ["src"]