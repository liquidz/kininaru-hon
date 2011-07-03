(defproject kininaru-hon "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [compojure "0.6.3"]
                 [org.clojars.liquidz/clj-gravatar "0.0.1"]
                 [org.clojars.liquidz/digest "0.0.1"]
                 ]

  :dev-dependencies [
                     [appengine-magic "0.4.2"]
                     [ring-mock "0.1.1"]
                     ]
  :aot [kininaru-hon.app_servlet]
  )
