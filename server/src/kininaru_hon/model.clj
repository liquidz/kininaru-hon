(ns kininaru-hon.model
  (:use
     digest.core
     [kininaru-hon util book-search]
     clj-gravatar.core)
  (:require
     [appengine-magic.services.datastore :as ds]
     [appengine-magic.services.memcache :as mem])

  (:require
     key
     [clojure.contrib.json :as json]
     [clojure.contrib.io :as io]
     [clojure.contrib.logging :as log]))

;(declare get-item)

; entity
(ds/defentity User [^:key email nickname avatar date])
(ds/defentity Book [^:key isbn title author smallimage mediumimage largeimage])
(ds/defentity Kininaru [^:key id userkey nickname avatar isbn title author smallimage mediumimage largeimage comment date])
(ds/defentity Total [^:key id userkey isbn count])

(defn kininaru-id [user isbn]
  (sha1str (str (:email user) isbn (now) (rand-int 100))))

(defn total-id [user isbn]
  (sha1str (str (:email user) isbn)))

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


;; Book
(defn- put-and-get-search-book-result [key res]
  (mem/put! key res) res)
(defn- search-book [isbn]
  (let [key (keyword isbn)]
    (if (mem/contains? key)
      (mem/get key)
      (with-rakuten-developer-id
        key/*rakuten-developer-id*
        (let [rres (rakuten-book-search :isbn isbn)]
          (if (= "NotFound" (-> rres :Header :Status))
            (if-let [gres (google-book-search isbn)]
              (put-and-get-search-book-result
                key {:title (:title gres) :author (:author gres) :small (:thumbnail gres) :medium (:thumbnail gres) :large (:thumbnail gres)}))
            (let [item (-> rres :Body :BooksBookSearch :Items :Item first)]
              (put-and-get-search-book-result
                key {:title (:title item) :author (:author item) :small (:smallImageUrl item) :medium (:mediumImageUrl item) :large (:largeImageUrl item)}))))))))

(defn get-book [key-or-isbn] (when key-or-isbn (ds/retrieve Book key-or-isbn)))

(defn create-book [isbn & {:keys [static? title author smallimage mediumimage largeimage] :or {static? false}}]
  (if-let [obj (if static?
                 (Book. isbn title author smallimage mediumimage largeimage)
                 (if-let [book (search-book isbn)]
                   (Book. isbn (:title book) (:author book) (:small book) (:medium book) (:large book))))]

    (ds/save! obj) (get-book isbn)))


;; Kininaru
(defn get-kininaru [key-or-id] (when key-or-id (ds/retrieve Kininaru key-or-id)))
(defn create-kininaru [user isbn & {:keys [static? title author smallimage mediumimage largeimage comment date]
                                    :or {static? false, comment "", date (now)}}]
  (if-let-and [id (kininaru-id user isbn)
               userkey (ds/get-key-object user)
               obj (if static?
                     (Kininaru. id userkey (:nickname user) (:avatar user) isbn title author smallimage mediumimage largeimage comment date)
                     (if-let [book (aif (get-book isbn) it (create-book isbn))]
                       (Kininaru. id userkey (:nickname user) (:avatar user) isbn (:title book) (:author book) (:smallimage book) (:mediumimage book) (:largeimage book) comment (now))
                       ))]
              (do (ds/save! obj) (get-kininaru id))))

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


;
;
;;; Item
;
;;; History
;(defn get-history [key] (when key (ds/retrieve History key)))
;(defn get-history-list [& {:keys [limit page] :or {limit *default-limit*, page 1}}]
;  (query-history :limit limit :page page))
;
;(defn create-history [item user point read? comment & {:keys [date] :or {date (now)}}]
;  (ds/retrieve History (ds/save! (History. item user point read? comment date))))
;
;(defn get-histories-from [key val & {:keys [limit page] :or {limit *default-limit*, page 1}}]
;  (let [val* (if (entity? val) (ds/get-key-object val) val)]
;    (query-history :filter (= key val*) :limit limit :page page)))
;(def get-histories-from-user (partial get-histories-from :user))
;(def get-histories-from-item (partial get-histories-from :item))
;(defn count-history [& {:keys [user item]}]
;  (cond
;    user (ds/query :kind History :filter (= :user user) :count-only? true)
;    item (ds/query :kind History :filter (= :item item) :count-only? true)
;    :else (ds/query :kind History :count-only? true)))
;
;;; Collections
;(defn get-collection [key-or-id] (when key-or-id (ds/retrieve Collection key-or-id)))
;(defn get-collection-list [& {:keys [limit page] :or {limit *default-limit*, page 1}}]
;  (query-collection :limit limit :page page))
;(defn create-collection [item user & {:keys [point read? secret? date] :or {point 1, read? false, secret? false, date (now)}}]
;  (let [id (collection-id item user)]
;    (aif (get-collection id) it
;      (get-collection (ds/save! (Collection. id item user point read? secret? date))))))
;
;(defn update-collection [item user & {:keys [point read? date point-plus? comment] :or {date (now), point-plus? false, comment nil}}]
;  (let [id (collection-id item user)
;        first? (nil? (if id (get-collection id)))
;        before (create-collection item user)
;        after (get-collection
;                (ds/save! (assoc before
;                                 :point (if point-plus?
;                                          (if first? (:point before) (inc (:point before)))
;                                          (aif point it (:point before)))
;                                 :read? (aif read? it (:read? before))
;                                 :date date)))]
;    (create-history item user (:point after) (:read? after) comment :date date)
;    after))
;
;(defn get-collections-from [key val & {:keys [read? limit page] :or {limit *default-limit*, page 1}}]
;  (let [offset (* limit (dec page))
;        val* (if (entity? val) (ds/get-key-object val) val)]
;    (if (not (nil? read?))
;      (query-collection :filter [(= key val*) (= :read? read?)] :limit limit :page page)
;      (query-collection :filter (= key val*) :limit limit :page page))))
;
;(def get-collections-from-user (partial get-collections-from :user))
;(def get-collections-from-item (partial get-collections-from :item))
;
;(defn get-comment-list [& {:keys [limit page] :or {limit *default-limit*, page 1}}]
;  (take limit (drop (* limit (dec page)) (remove #(nil? (:comment %)) (query-history)))))
;
;(defn count-collection [& {:keys [user item]}]
;  (cond
;    user (ds/query :kind Collection :filter (= :user user) :count-only? true)
;    item (ds/query :kind Collection :filter (= :item item) :count-only? true)
;    :else (ds/query :kind Collection :count-only? true)))
