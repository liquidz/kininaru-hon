(ns kininaru-hon.core
  (:use
     [compojure.core :only [GET POST defroutes wrap!]]
     [compojure.route :only [not-found]]
     [ring.util.response :only [redirect]]
     [ring.middleware.params :only [wrap-params]]
     [ring.middleware.keyword-params :only [wrap-keyword-params]]
     [ring.middleware.session :only [wrap-session]]
     [kininaru-hon model util]
     clj-gravatar.core)
  (:require
     [appengine-magic.core :as ae]
     [appengine-magic.services.datastore :as ds]
     [appengine-magic.services.user :as du]
     [clojure.contrib.string :as string]))


(defn get-current-user []
  (when (du/user-logged-in?) (create-user (du/current-user))))


(defmacro json-service [method path bind & body] ; {{{
  `(~method ~path ~bind
      (let [res# (do ~@body)]
        (if (and (map? res#) (every? #(contains? res# %) [:status :headers :body]))
          (assoc res# :body (to-json (:body res#)))
          (to-json res#)))))
(defmacro jsonGET [path bind & body] `(json-service GET ~path ~bind ~@body))
(defmacro jsonPOST [path bind & body] `(json-service POST ~path ~bind ~@body))
(defmacro apiGET [path fn] `(jsonGET ~path {params# :params} (~fn (convert-map params#))))
(defmacro apiPOST [path fn] `(jsonPOST ~path {params# :params} (~fn (convert-map params#))))

(defmacro apiGET-with-session [path fn] `(jsonGET ~path {params# :params, session# :session}
                                                  (~fn (convert-map params#) session#)))
(defmacro apiPOST-with-session [path fn] `(jsonPOST ~path {params# :params, session# :session}
                                                    (~fn (convert-map params#) session#)))
; }}}

(defn kininaru-key->str [k]
  (assoc k :userkey (key->str (:userkey k))))

(defn add-key-str [entity]
  (assoc entity :key (entity->key-str entity)))

(defn add-book [isbn comment]
  (let [isbn* (string/replace-str "-" "" (string/trim isbn))
        user (get-current-user)
        ]
    (if (not (string/blank? isbn*))
      (let [res (create-kininaru user isbn* :comment comment)]
        (inc-total user isbn*)
        res))))

;; User
(defn get-user-controller [{key-str :key}]
  (when-let [key (str->key key-str)]
    (let [user (get-user key)
          kininaru-list (map kininaru-key->str (get-kininaru-list-from-user user))]
      (remove-extra-key
        (assoc user
               :key (entity->key-str user)
               :kininaru kininaru-list)))))

;; Book
(defn get-book-controller [{isbn :isbn}]
  (if (du/user-logged-in?)
    (let [book (get-book isbn)
          keys (distinct (map :userkey (get-kininaru-list-from-isbn isbn :limit 10)))
          users (map (comp remove-extra-key add-key-str get-user) keys)]
      (assoc book :users users))))

(defn get-book-user-controller [{isbn :isbn}]
  (let [keys (distinct (map :userkey (get-kininaru-list-from-isbn isbn :limit 10)))]
    (map (comp remove-extra-key add-key-str get-user) keys)))

;; Kininaru
(defn get-kininaru-list-controller [{:keys [with_total]:or {with_total "false"} :as params}]
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

(defn add-kininaru-controller [{:keys [isbn comment] :or {comment ""}}]
  (add-book isbn comment))


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




(defroutes api-handler
  (apiGET "/user" get-user-controller)
  (apiGET "/check/login" check-login-controller)

  (apiGET "/book" get-book-controller)
  (apiGET "/book/user" get-book-user-controller)

  (apiGET "/kininaru/list" get-kininaru-list-controller)
  (apiGET "/kininaru/user" get-kininaru-list-from-user-controller)
  (apiGET "/kininaru/my" get-my-kininaru-list-controller)

  (apiGET-with-session "/message" get-message-controller)

  (GET "/msg" {{msg :msg, :as params} :params}
    (with-message (redirect "/") msg))
  )


(defroutes main-handler
  (GET "/login" _ (redirect (du/login-url)))
  (GET "/logout" _ (with-session (redirect "/") {}))
  (GET "/add" {{isbn :isbn, comment :comment} :params, :as req}
    (if (du/user-logged-in?)
      (if (add-book isbn (aif comment it ""))
        (with-message (redirect "/") "success")
        (with-message (redirect "/") "failed"))
      (with-message (redirect "/") "not logged in")))
  (GET "/auth" _
    (aif (get-current-user)
         (str "logged in as " (:nickname it))
         (str "not logged in")))
  (not-found "page not found"))


(defroutes app-handler
  api-handler
  main-handler)

(wrap! app-handler wrap-session wrap-keyword-params wrap-params)
(ae/def-appengine-app kininaru-hon-app #'app-handler)
