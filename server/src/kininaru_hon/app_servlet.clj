(ns kininaru-hon.app_servlet
  (:gen-class :extends javax.servlet.http.HttpServlet)
  (:use kininaru-hon.core)
  (:use [appengine-magic.servlet :only [make-servlet-service-method]]))


(defn -service [this request response]
  ((make-servlet-service-method kininaru-hon-app) this request response))
