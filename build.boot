(set-env!
  :source-paths #{"src"}
  :dependencies '[[org.clojure/clojure "1.9.0-alpha17"]
                  [org.clojars.scsibug/feedparser-clj "0.4.0"]
                  [clojure.java-time "0.3.0"]
                  [org.clojure/core.async "0.3.443"]
                  [org.clojure/data.codec "0.1.0"]
                  [org.clojure/data.json "0.2.6"]
                  [enlive "1.1.6"]
                  [http-kit "2.1.18"]
                  [jarohen/chime "0.2.1"]
                  [clj-time "0.13.0"]
                  [samestep/boot-refresh "0.1.0" :scope "test"]])

(task-options! pom {:project 'artesonraju/caf-diff
                    :version "0.1.0"})

(require '[samestep.boot-refresh :refer [refresh]])

(deftask run []
  (require 'cafdiff.main)
  (let [main (resolve 'cafdiff.main/-main)]
    (with-pass-thru _
      (main))))

(deftask dev []
         (comp
           (repl :init-ns 'cafdiff.main)
           (watch)
           (refresh)
           (target :dir #{"target"})))


(deftask prod []
         (comp
           (aot :namespace '#{cafdiff.main})
           (pom)
           (uber)
           (jar :main 'cafdiff.main)
           (target :dir #{"target"})))
