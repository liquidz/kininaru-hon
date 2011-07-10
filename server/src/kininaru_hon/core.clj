(ns kininaru-hon.core
  (:use
    [compojure.core :only [GET POST defroutes wrap!]]
    [compojure.route :only [not-found]]
    [ring.util.response :only [redirect]]
    [ring.middleware.params :only [wrap-params]]
    [ring.middleware.keyword-params :only [wrap-keyword-params]]
    [ring.middleware.session :only [wrap-session]]
    [kininaru-hon model util]
    [kininaru-hon controller]
    clj-gravatar.core)
  (:require
    [appengine-magic.core :as ae]
    [appengine-magic.services.datastore :as ds]
    [appengine-magic.services.user :as du]
    [clojure.contrib.string :as string]))


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


(defroutes api-handler
  ; user routes
  (apiGET "/user" get-user-controller)

  ; book routes
  (apiGET "/book" get-book-controller)
  (apiGET "/book/user" get-book-user-controller)

  ; kininaru routes
  (apiGET "/kininaru/list" get-kininaru-list-controller)
  (apiGET "/kininaru/user" get-kininaru-list-from-user-controller)
  (apiGET "/kininaru/my" get-my-kininaru-list-controller)
  (apiGET "/kininaru/add" add-kininaru-book-controller)

  ; etc routes
  (apiGET-with-session "/message" get-message-controller)
  (apiGET "/check/login" check-login-controller))


(defroutes main-handler
  (GET "/login" _ (redirect (du/login-url)))
  (GET "/logout" _ (with-session (redirect "/") {}))
  (GET "/add" {{isbn :isbn, comment :comment} :params, :as req}
    (if (du/user-logged-in?)
      (if (add-kininaru-book isbn (aif comment it ""))
        (with-message (redirect "/") "success")
        (with-message (redirect "/") "failed"))
      (with-message (redirect "/") "not logged in")))

  ; data recovery {{{
  ;(GET "/admin/null_publisher" _
  ;  (doseq [book (get-book-list)]
  ;    (if-not (string/blank? (:publisher book))
  ;      (ds/save! (assoc book :publisher "dummy"))))
  ;  "ok")

  ;(GET "/admin/get_publisher" _
  ;  (doseq [book (get-book-list)]
  ;    (when (or (string/blank? (:publisher book))
  ;              (= "dummy" (:publisher book)))
  ;      (let [isbn (:isbn book)
  ;            res (search-book isbn)]
  ;        ; update book
  ;        (ds/save! (assoc book :publisher (:publisher res)))
  ;        ; update kininaru
  ;        (doseq [kininaru (get-kininaru-list-from-isbn isbn :limit 100)]
  ;          (ds/save! (assoc kininaru :publisher (:publisher res))))

  ;        (Thread/sleep 1000))))
  ;  "ok")
  ; }}}

  (not-found "page not found"))


(defroutes app-handler
  api-handler
  main-handler)

(wrap! app-handler wrap-session wrap-keyword-params wrap-params)
(ae/def-appengine-app kininaru-hon-app #'app-handler)
