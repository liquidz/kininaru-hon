(ns kininaru-hon.controller
  (:use
    [kininaru-hon model util]
    )
  (:require
    [appengine-magic.services.user :as du]
    )
  )

(defn kininaru-key->str [k]
  (assoc k :userkey (key->str (:userkey k))))


;; User Controller
(defn get-user-controller
  "キー(string)からユーザ情報とそのユーザの気になる一覧を取得"
  [{key-str :key}]
  (when-let [key (str->key key-str)]
    (let [user (get-user key)
          kininaru-list (map kininaru-key->str (get-kininaru-list-from-user user))]
      (remove-extra-key
        (assoc user :key (entity->key-str user) :kininaru kininaru-list)))))

;; Book Controller
(defn get-book-controller
  "ISBNから登録済みの書籍情報とその本が気になっているユーザ情報を取得"
  [{:keys [isbn limit] :or {limit 10}}]
  (if (du/user-logged-in?)
    (let [book (get-book isbn)
          keys (distinct (map :userkey (get-kininaru-list-from-isbn isbn :limit limit)))
          users (map (comp remove-extra-key add-key-str get-user) keys)]
      (assoc book :users users))))

(defn get-book-user-controller
  "ISBNからその本が気になっているユーザ情報を取得"
  [{:keys [isbn limit] :or {limit 10}}]
  (let [keys (distinct (map :userkey (get-kininaru-list-from-isbn isbn :limit limit)))]
    (map (comp remove-extra-key add-key-str get-user) keys)))

;; Kininaru Controller
(defn get-kininaru-list-controller
  "気になる一覧を日付順で取得。 with_totalオプションで総数を追加"
  [{:keys [with_total]:or {with_total "false"} :as params}]
  (let [[limit page] (params->limit-and-page params)
        res (map kininaru-key->str (get-kininaru-list :limit limit :page page))]
    (if (= with_total "true")
      {:total (count-kininaru) :result res} res)))


(defn- get-kininaru-list-from-controller [type f {:keys [key with_total] :or {with_total "false"} :as params}]
  (let [[limit page] (params->limit-and-page params)
        key* (str->key key)]
    (when key*
      (let [res (map kininaru-key->str (f key* :limit limit :page page))]
        (if (= with_total "true")
          {:total (count-kininaru type key*) :result res}
          res)))))

(def get-kininaru-list-from-user-controller
  (partial get-kininaru-list-from-controller :user get-kininaru-list-from-user))

(defn get-my-kininaru-list-controller [{:keys [with_total] :or {with_total "false"} :as params}]
  (let [[limit page] (params->limit-and-page params)
        user (get-current-user)
        res (map kininaru-key->str (get-kininaru-list-from :userkey user :limit limit :page page))
        ]
    (if (= with_total "true")
      {:total (count-kininaru :user user) :result res} res)))

(defn add-kininaru-book-controller [{:keys [isbn comment] :or {comment ""}}]
  (if (du/user-logged-in?)
    (if (add-kininaru-book isbn (aif comment it ""))
      "success"
      "failed")
    "not logged in"))

;; Etc

(defn get-message-controller [_ session]
  (with-message session (:message session) ""))

(defn check-login-controller [_]
  (if-let [user (get-current-user)]
    {:loggedin true
     :nickname (:nickname user)
     :avatar (:avatar user)
     :url (du/logout-url)}
    {:loggedin false
     :url (du/login-url)}))

