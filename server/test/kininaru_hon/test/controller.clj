(ns kininaru-hon.test.controller
  (:use
     [kininaru-hon core util model]
     :reload)
  (:use clojure.test ring.mock.request clj-gravatar.core)

  (:require
     [appengine-magic.testing :as ae-testing]
     [appengine-magic.services.datastore :as ds]
     [appengine-magic.services.user :as du]
     [clojure.contrib.json :as json]
     [clojure.contrib.string :as string]
     )
  )

(use-fixtures :each (ae-testing/local-services
                      :all :hook-helper (ae-testing/login "hoge@fuga.com")))

(def *sample-isbn* "4001156768")

; private {{{
(defn- body->json [res] (-> res :body json/read-json))
(defn- testGET [& urls] (app-handler (request :get (apply str urls))))
(defn- testGET-with-session [res & urls]
  (let [ring-session (first (get (:headers res) "Set-Cookie"))]
    (app-handler (header (request :get (apply str urls)) "Cookie" ring-session))))
(defn- testPOST [data & urls]
  (app-handler (body (request :post (apply str urls)) data)))
(defn- testPOST-with-session [res data & urls]
  (let [ring-session (first (get (:headers res) "Set-Cookie"))]
    (app-handler (header (body (request :post (apply str urls)) data) "Cookie" ring-session))))
; }}}

(defn put-test-data []
  (let [user1 (create-user "hoge@fuga.com" "hoge")
        user2 (create-user "fuga@fuga.com" "fuga")
        book1 (create-book "1" :static? true :title "a" :author "b" :publisher "xx" :smallimage "c" :mediumimage "cc" :largeimage "ccc")
        book2 (create-book "2" :static? true :title "d" :author "e" :publisher "yy" :smallimage "f" :mediumimage "ff" :largeimage "fff")
        col1 (create-kininaru user1 "1" :static? true :date "YYYY-01-01")
        col2 (create-kininaru user2 "1" :static? true :date "YYYY-01-02")
        col3 (create-kininaru user1 "2" :static? true :date "YYYY-01-03")
        col4 (create-kininaru user2 "2" :static? true :date "YYYY-01-04")]
    [
     (entity->key-str user1) (entity->key-str user2)
     (entity->key-str book1) (entity->key-str book2)
     (entity->key-str col1) (entity->key-str col2)
     (entity->key-str col3) (entity->key-str col4)]))

(deftest test-check-login-controller
  (let [res (body->json (testGET "/check/login"))]
    (are [x y] (= x y)
      true (:loggedin res)
      "hoge" (:nickname res)
      (gravatar-image "hoge@fuga.com") (:avatar res))))

(deftest test-get-user-controller
  (let [uri "/user?"
        [user-key-str] (put-test-data)]

    (let [res (body->json (testGET uri "key=" user-key-str))]
      (are [x y] (= x y)
        nil (body->json (testGET uri "key=unknown"))

        "hoge" (:nickname res)
        (gravatar-image "hoge@fuga.com") (:avatar res)
        true (nil? (:email res))
        2 (count (:kininaru res))

        "2" (-> res :kininaru first :isbn)
        "1" (-> res :kininaru second :isbn)

        "hoge" (-> res :kininaru first :nickname)
        "hoge" (-> res :kininaru second :nickname)
        )
      )
    )
  )



(deftest test-get-my-kininaru-list-controller
  (let [uri "/kininaru/my?"
        [user-key-str] (put-test-data)]
    (let [res (body->json (testGET uri "with_total=true"))]
      (are [x y] (= x y)
        2 (:total res)
        "hoge" (-> res :result first :nickname)
        "2" (-> res :result first :isbn)))
    (let [res (body->json (testGET uri "limit=1"))]
      (are [x y] (= x y)
        1 (count res)
        "2" (-> res first :isbn)))
    (let [res (body->json (testGET uri "limit=1&page=2"))]
      (are [x y] (= x y)
        1 (count res)
        "1" (-> res first :isbn)))
    )
  )

(deftest test-add
  (let [uri "/add?"]
    (create-book "1" :static? true)
    (create-book "2" :static? true)

    (testGET uri "isbn=")

    (testGET uri "isbn=1")
    (let [res (get-kininaru-list)]
      (are [x y] (= x y)
        1 (count res)
        "hoge" (-> res first :nickname)
        "1" (-> res first :isbn)
        "" (-> res first :comment)))

    (testGET uri "isbn=+2&comment=hello")
    (let [res (sort #(pos? (.compareTo (:isbn %1) (:isbn %2))) (get-kininaru-list))]
      (are [x y] (= x y)
        2 (count res)
        "hoge" (-> res first :nickname)
        "2" (-> res first :isbn)
        "hello" (-> res first :comment)))
    )
  )


(deftest test-get-book-controller
  (create-book "1" :static? true :title "aa" :author "bb" :publisher "xx" :smallimage "cc" :mediumimage "dd" :largeimage "ee")
  (create-book "2" :static? true :title "ff" :author "gg" :publisher "yy" :smallimage "hh" :mediumimage "ii" :largeimage "jj")
  (let [uri "/book?isbn="
        user1 (create-user "hoge@hoge.com" "hoge")
        user2 (create-user "fuga@fuga.com" "fuga")
        user1-key (entity->key-str user1)
        user2-key (entity->key-str user2)
        _ (do
            (create-kininaru user1 "1" :static? true :date "YYYYMM01")
            (create-kininaru user2 "1" :static? true :date "YYYYMM02")
            (create-kininaru user2 "1" :static? true :date "YYYYMM03")
            (create-kininaru user2 "2" :static? true :date "YYYYMM04")
            (create-kininaru user2 "2" :static? true :date "YYYYMM05"))
        res1 (body->json (testGET uri "1"))
        res2 (body->json (testGET uri "2"))]

    (are [x y] (= x y)
      "aa" (:title res1)
      "bb" (:author res1)
      "xx" (:publisher res1)
      "cc" (:smallimage res1)
      "dd" (:mediumimage res1)
      "ee" (:largeimage res1)
      2 (-> res1 :users count)
      "fuga" (-> res1 :users first :nickname)
      "hoge" (-> res1 :users second :nickname)
      nil (-> res1 :users first :email)
      nil (-> res1 :users second :email)
      user2-key (-> res1 :users first :key)
      user1-key (-> res1 :users second :key)

      1 (-> res2 :users count)
      "fuga" (-> res2 :users first :nickname)
      nil (-> res2 :users first :email)
      user2-key (-> res2 :users first :key)
      )))

(deftest test-get-book-user-controller
  (create-book "1" :static? true :title "aa" :author "bb" :publisher "xx" :smallimage "cc" :mediumimage "dd" :largeimage "ee")
  (create-book "2" :static? true :title "ff" :author "gg" :publisher "yy" :smallimage "hh" :mediumimage "ii" :largeimage "jj")
  (let [uri "/book/user?isbn="
        user1 (create-user "hoge@hoge.com" "hoge")
        user2 (create-user "fuga@fuga.com" "fuga")
        user1-key (entity->key-str user1)
        user2-key (entity->key-str user2)
        _ (do
            (create-kininaru user1 "1" :static? true :date "YYYYMM01")
            (create-kininaru user2 "1" :static? true :date "YYYYMM02")
            (create-kininaru user2 "1" :static? true :date "YYYYMM03")
            (create-kininaru user2 "2" :static? true :date "YYYYMM04")
            (create-kininaru user2 "2" :static? true :date "YYYYMM05"))
        res1 (body->json (testGET uri "1"))
        res2 (body->json (testGET uri "2"))]
    (are [x y] (= x y)
      2 (count res1)
      "fuga" (-> res1 first :nickname)
      "hoge" (-> res1 second :nickname)
      nil (-> res1 first :email)
      nil (-> res1 second :email)
      user2-key (-> res1 first :key)
      user1-key (-> res1 second :key)

      1 (count res2)
      "fuga" (-> res2 first :nickname)
      nil (-> res2 first :email)
      user2-key (-> res2 first :key))))


