(ns kininaru-hon.model
  (:use
    digest.core
    [kininaru-hon util book-search]
    clj-gravatar.core)
  (:require
    [appengine-magic.services.datastore :as ds]
    [appengine-magic.services.user :as du]
    [appengine-magic.services.memcache :as mem]

    [clojure.contrib.string :as string]
    [clojure.contrib.json :as json]
    [clojure.contrib.io :as io]
    [clojure.contrib.logging :as log]
    key))

; for add-kininaru-book
(declare inc-total)

; entity
(ds/defentity User [^:key email nickname avatar date])
(ds/defentity Book [^:key isbn title author publisher smallimage mediumimage largeimage])
(ds/defentity Kininaru [^:key id userkey nickname avatar isbn title author publisher smallimage mediumimage largeimage comment date])
(ds/defentity Total [^:key id userkey isbn count])

(defn kininaru-id [user isbn]
  (sha1str (str (:email user) isbn (now) (rand-int 100))))

(defn total-id [user isbn]
  (sha1str (str (:email user) isbn)))

(defmacro query-kininaru [& {:keys [filter limit page]}]
  `(ds/query :kind Kininaru :filter ~filter :sort [[:date :desc]] :limit ~limit :offset (if (and ~limit ~page) (* ~limit (dec ~page)))))
(defmacro query-kininaru [& {:keys [filter limit page]}]
  `(ds/query :kind Kininaru :filter ~filter :sort [[:date :desc]] :limit ~limit :offset (if (and ~limit ~page) (* ~limit (dec ~page)))))

;; User
(defn create-user
  ([user]
   (create-user (.getEmail user) (.getNickname user)))
  ([email nickname & {:keys [date] :or {date (now)}}]
   (aif (ds/retrieve User email) it
        (do (ds/save! (User. email nickname (gravatar-image email) date))
          (ds/retrieve User email)))))
(defn get-user [key-or-email]
  (when key-or-email (ds/retrieve User key-or-email)))
(defn get-current-user []
  (when (du/user-logged-in?) (create-user (du/current-user))))

;; Book
(defn- put-and-get-search-book-result [key res]
  (mem/put! key res) res)
;(defn- search-book [isbn]
(defn search-book [isbn]
  (let [key (keyword isbn)]
    (if (mem/contains? key)
      (mem/get key)
      (with-rakuten-developer-id
        key/*rakuten-developer-id*
        (let [rres (rakuten-book-search :isbn isbn)]
          (if (= "NotFound" (-> rres :Header :Status))
            (if-let [gres (google-book-search isbn)]
              (put-and-get-search-book-result
                key {:title (:title gres) :author (:author gres) :publisher (:publisher gres)
                     :small (:thumbnail gres) :medium (:thumbnail gres) :large (:thumbnail gres)}))
            (let [item (-> rres :Body :BooksBookSearch :Items :Item first)]
              (put-and-get-search-book-result
                key {:title (:title item) :author (:author item) :publisher (:publisherName item)
                     :small (:smallImageUrl item) :medium (:mediumImageUrl item) :large (:largeImageUrl item)}))))))))

(defn get-book [key-or-isbn] (when key-or-isbn (ds/retrieve Book key-or-isbn)))
(defn get-book-list [& {:keys [limit] :or {limit 0}}]
  (if (pos? limit)
    (ds/query :kind Book :limit limit)
    (ds/query :kind Book)))

(defn create-book [isbn & {:keys [static? title author publisher smallimage mediumimage largeimage] :or {static? false}}]
  (if-let [obj (if static?
                 (Book. isbn title author publisher smallimage mediumimage largeimage)
                 (if-let [book (search-book isbn)]
                   (Book. isbn (:title book) (:author book) (:publisher book)
                          (:small book) (:medium book) (:large book))))]

    (ds/save! obj) (get-book isbn)))


;; Kininaru
(defn get-kininaru [key-or-id] (when key-or-id (ds/retrieve Kininaru key-or-id)))
(defn create-kininaru [user isbn & {:keys [static? title author publisher smallimage mediumimage largeimage comment date]
                                    :or {static? false, comment "", date (now)}}]
  (if-let-and [id (kininaru-id user isbn)
               userkey (ds/get-key-object user)
               obj (if static?
                     (Kininaru. id userkey (:nickname user) (:avatar user) isbn title author publisher smallimage mediumimage largeimage comment date)
                     (if-let [book (aif (get-book isbn) it (create-book isbn))]
                       (Kininaru. id userkey (:nickname user) (:avatar user) isbn (:title book) (:author book) (:publisher book)
                                  (:smallimage book) (:mediumimage book) (:largeimage book) comment (now))
                       ))]
              (do (ds/save! obj) (get-kininaru id))))

(defn add-kininaru-book [isbn comment]
  (let [isbn* (string/replace-str "-" "" (string/trim isbn))
        user (get-current-user)]
    (if (not (string/blank? isbn*))
      (let [res (create-kininaru user isbn* :comment comment)]
        (inc-total user isbn*)
        res))))

(defn get-kininaru-list [& {:keys [limit page] :or {limit *default-limit*, page 1}}]
  (query-kininaru :limit limit :page page)
  ;(ds/query :kind Kininaru :limit limit :offset (if (and limit page) (* limit (dec page))))
  )
(defn get-kininaru-list-from [key val & {:keys [limit page] :or {limit *default-limit*, page 1}}]
  (let [val* (if (entity? val) (ds/get-key-object val) val)]
    (query-kininaru :filter (= key val*) :limit limit :page page)
    ;(ds/query :kind Kininaru )
    ))
(def get-kininaru-list-from-user (partial get-kininaru-list-from :userkey))
(def get-kininaru-list-from-isbn (partial get-kininaru-list-from :isbn))
(defn count-kininaru [& {:keys [user isbn]}]
  (cond
    user (ds/query :kind Kininaru :filter (= :userkey (if (entity? user) (ds/get-key-object user) user)) :count-only? true)
    isbn (ds/query :kind Kininaru :filter (= :isbn isbn) :count-only? true)
    :else (ds/query :kind Kininaru :count-only? true)))

;; Total
(defn get-all-total []
  (ds/query :kind Total))

(defn get-total [user isbn]
  (when (and user (get-book isbn))
    (ds/retrieve Total (total-id user isbn))))

(defn set-total [user isbn total]
  (when (and user (get-book isbn) (>= total 0))
    (ds/save!
      (aif (get-total user isbn)
           (assoc it :count total)
           (Total. (total-id user isbn) (ds/get-key-object user) isbn total)))))

(defn inc-total [user isbn]
  (set-total user isbn (aif (get-total user isbn) (inc (:count it)) 1)))


