(defproject configurati "0.1.0-SNAPSHOT"
  :description "Define and resolve application configuration."
  :url "http://github.com/tobyclemson/configurati"
  :license {:name "The MIT License"
            :url  "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :source-paths ["src/clojure"]
  :test-paths ["test/clojure"]
  :java-source-paths ["src/java" "test/java"]
  :deploy-repositories [["releases" {:url     "https://clojars.org/repo/"
                                     :creds   :gpg}]])
