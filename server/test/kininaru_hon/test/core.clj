(ns kininaru-hon.test.core
  (:use [kininaru-hon core model util] :reload)
  (:use clojure.test ring.mock.request clj-gravatar.core)
  (:require
     [appengine-magic.testing :as ae-testing]
     [appengine-magic.services.datastore :as ds]
     [appengine-magic.services.user :as du]
     [clojure.contrib.string :as string])
  (:import
     [com.google.appengine.tools.development.testing
      LocalServiceTestConfig
      LocalServiceTestHelper
      LocalUserServiceTestConfig]))

(defmacro with-test-user [mail & body]
  `(let [domain# (second (string/split #"@" ~mail))
         helper# (.. (LocalServiceTestHelper.
                       (into-array LocalServiceTestConfig [(LocalUserServiceTestConfig.)]))
                   (setEnvIsLoggedIn true) (setEnvEmail ~mail) (setEnvAuthDomain domain#))]
     (.setUp helper#)
     (do ~@body)
     ; tearDown effects another tearDown in ae-testing/local-services
     ;(.tearDown helper#)
     )
  )

(use-fixtures :each (ae-testing/local-services :all))

(def *sample-isbn* "4001156768")

(deftest test-with-test-user
  (is (not (du/user-logged-in?)))
  (is (nil? (du/current-user)))
  (with-test-user
    "hoge@fuga.com"
    (are [x y] (= x y)
      true (du/user-logged-in?)
      "hoge@fuga.com" (.getEmail (du/current-user))
      "hoge" (.getNickname (du/current-user)))))

;; User
(deftest test-create-user
  (with-test-user
    "hoge@fuga.com"
    (let [user  (create-user (du/current-user))
          avatar (gravatar-image "hoge@fuga.com")
          ]
      (are [x y] (= x y)
        "hoge@fuga.com" (:email user)
        "hoge" (:nickname user)
        avatar (:avatar user))))
  (let [user (create-user "neko@fuga.com" "neko")
        avatar (gravatar-image "neko@fuga.com")]
    (are [x y] (= x y)
      "neko@fuga.com" (:email user)
      "neko" (:nickname user)
      avatar (:avatar user))))

(deftest test-get-user
  (let [user (create-user "hoge@fuga.com" "hoge")
        key (ds/get-key-object user)]
    (are [x y] (= x y)
      user (get-user "hoge@fuga.com")
      user (get-user key))))




;; Book
(deftest test-create-book
  (let [item1 (create-book *sample-isbn*)
        item2 (create-book "123" :static? true :title "aa" :author "bb" :publisher "dd" :smallimage "cc" :mediumimage "ccc" :largeimage "cccc")
        item3 (create-book "unknownisbn")
        ]
    (are [x y] (= x y)
      false (string/blank? (:title item1))
      false (string/blank? (:author item1))
      false (string/blank? (:publisher item1))
      false (nil? (:smallimage item1))
      false (nil? (:mediumimage item1))
      false (nil? (:largeimage item1))

      "aa" (:title item2)
      "bb" (:author item2)
      "dd" (:publisher item2)
      "cc" (:smallimage item2)
      "ccc" (:mediumimage item2)
      "cccc" (:largeimage item2)

      nil item3
      )))

(deftest test-get-book
  (let [isbn *sample-isbn*
        key (ds/get-key-object (create-book isbn :static? true))]

    (are [x y] (= x y)
      true (nil? (get-book "unknown"))
      false (nil? (get-book isbn))
      false (nil? (get-book key)))))


;; Kininaru
(deftest test-create-kininaru
  (create-book *sample-isbn* :static? true :title "aa" :author "bb" :publisher "ff" :smallimage "cc" :mediumimage "dd" :largeimage "ee")
  (let [user (create-user "hoge@fuga.com" "hoge")
        kininaru (create-kininaru user *sample-isbn*)
        kininaru2 (create-kininaru user "123" :static? true :comment "hello")
        date (now)]
    (are [x y] (= x y)
      "hoge" (-> kininaru :nickname)
      "aa" (:title kininaru)
      "bb" (:author kininaru)
      "ff" (:publisher kininaru)
      "cc" (:smallimage kininaru)
      "dd" (:mediumimage kininaru)
      "ee" (:largeimage kininaru)
      "" (:comment kininaru)
      date (:date kininaru)

      "123" (:isbn kininaru2)
      "hello" (:comment kininaru2)
      )))

(deftest test-get-kininaru
  (create-book *sample-isbn* :static? true :title "aa" :author "bb" :publisher "ff" :smallimage "cc" :mediumimage "dd" :largeimage "ee")
  (let [user (create-user "hoge@fuga.com" "hoge")
        key1 (ds/get-key-object (create-kininaru user *sample-isbn*))
        key2 (ds/get-key-object (create-kininaru user "123" :static? true))]
    (are [x y] (= x y)
      true (nil? (get-kininaru "unknown"))
      false (nil? (get-kininaru key1))
      false (nil? (get-kininaru key2)))))

(deftest test-get-kininaru-list-from-user
  (create-book "1" :static? true)
  (create-book "2" :static? true)
  (let [user1 (create-user "hoge@fuga.com" "hoge")
        user2 (create-user "fuga@fuga.com" "fuga")
        user1-key (ds/get-key-object user1)
        user2-key (ds/get-key-object user2)]

    (create-kininaru user1 "1" :static? true :date "YYYY-01-01")
    (create-kininaru user1 "2" :static? true :date "YYYY-01-02")
    (create-kininaru user2 "2" :static? true :date "YYYY-01-03")

    (are [x y] (= x y)
      2 (count (get-kininaru-list-from-user user1))
      2 (count (get-kininaru-list-from-user user1-key))
      1 (count (get-kininaru-list-from-user user1 :limit 1))
      3 (count (get-kininaru-list))
      1 (count (get-kininaru-list :limit 1))
      )
    )
  )

(deftest test-count-kininaru
  (is (zero? (count-kininaru)))

  (create-book "1" :static? true)
  (create-book "2" :static? true)
  (let [user1 (create-user "hoge@fuga.com" "hoge")
        user2 (create-user "fuga@fuga.com" "fuga")]
    (create-kininaru user1 "1")
    (create-kininaru user2 "1")
    (create-kininaru user2 "2")

    (are [x y] (= x y)
      3 (count-kininaru)
      1 (count-kininaru :user user1)
      2 (count-kininaru :user user2)
      2 (count-kininaru :isbn "1")
      1 (count-kininaru :isbn "2")
      )
    )
  )

;; Total

(deftest test-set-total
  (create-book "1" :static? true)
  (let [user (create-user "hoge@fuga.com" "hoge")
        _ (set-total user "1" 10)
        res (get-all-total)]

    (are [x y] (= x y)
      nil (set-total nil nil 0)
      nil (set-total user "-1" 10)
      nil (set-total user "1" -1)

      1 (count res)
      "hoge" (-> res first :userkey get-user :nickname)
      "1" (-> res first :isbn)
      10 (-> res first :count))

    (set-total user "1" 20)
    (let [res2 (get-all-total)]
      (are [x y] (= x y)
        1 (count res2)
        "hoge" (-> res2 first :userkey get-user :nickname)
        "1" (-> res2 first :isbn)
        20 (-> res2 first :count)))))

(deftest test-get-total
  (create-book "1" :static? true)
  (create-book "2" :static? true)
  (let [user1 (create-user "hoge@fuga.com" "hoge")
        user2 (create-user "fuga@fuga.com" "fuga")
        _ (do
            (set-total user1 "1" 10)
            (set-total user1 "2" 20)
            (set-total user2 "1" 30))
        res1 (get-total user1 "1")
        res2 (get-total user1 "2")
        res3 (get-total user2 "1")
        ]

    (are [x y] (= x y)
      nil (get-total nil "1")
      nil (get-total user1 "3")

      3 (count (get-all-total))

      "hoge" (-> res1 :userkey get-user :nickname)
      "hoge" (-> res2 :userkey get-user :nickname)
      "fuga" (-> res3 :userkey get-user :nickname)
      "1" (:isbn res1)
      "2" (:isbn res2)
      "1" (:isbn res3)
      10 (:count res1)
      20 (:count res2)
      30 (:count res3))
    )
  )

(deftest test-inc-total
  (create-book "1" :static? true)
  (let [user (create-user "hoge@fuga.com" "hoge")]
    (are [x y] (= x y)
      nil (inc-total nil nil)
      nil (inc-total user nil)
      nil (inc-total user "-1"))

    (inc-total user "1")
    (let [res (get-total user "1")]
      (are [x y] (= x y)
        false (nil? res)
        "hoge" (-> res :userkey get-user :nickname)
        "1" (:isbn res)
        1 (:count res)
        1 (count (get-all-total))))

    (inc-total user "1")
    (let [res (get-total user "1")]
      (are [x y] (= x y)
        2 (:count res)
        1 (count (get-all-total))))
    )
  )

