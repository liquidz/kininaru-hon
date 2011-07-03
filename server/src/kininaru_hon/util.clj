(ns kininaru-hon.util
  (:require
     [appengine-magic.services.datastore :as ds]
     [appengine-magic.services.user :as du]
     [clojure.contrib.json :as json]
     [clojure.contrib.string :as string])
  (:import
     [java.util TimeZone Calendar]
     [com.google.appengine.api.datastore Key KeyFactory]))


(def *default-limit* 10)

(defmacro aif [expr then & [else]]
  `(let [~'it ~expr] (if ~'it ~then ~else)))


(defmacro if-let-and
  ([bindings then]
   `(if-let-and ~bindings ~then nil))
  ([bindings then else & oldform]
   (let [bind (reverse (partition 2 bindings))]
     (reduce (fn [res x]
               `(if-let [~@x] ~res ~else ~@oldform)
               ) then bind))))


;; Datastore Service
(defn key? [obj] (instance? Key obj))
(defn key->str [obj] (if (key? obj) (KeyFactory/keyToString obj)))
(defn str->key [obj]
  (if (string? obj)
    (try (KeyFactory/stringToKey obj) (catch Exception e nil))))
(defn entity? [obj] (extends? ds/EntityProtocol (class obj)))
(defn get-kind [entity] (.getKind (ds/get-key-object entity)))
(defn entity->key-str [e] (key->str (ds/get-key-object e)))

(defn map-val-map [f m]
  (apply hash-map (mapcat (fn [[k v]] [k (f v)]) m)))

(defn remove-extra-key [obj]
  (cond
    (map? obj) (map-val-map remove-extra-key (dissoc obj :email :user-key))
    (sequential? obj) (map remove-extra-key obj)
    :else obj))

(def delete-html-tag (partial string/replace-re #"<.+?>" ""))


(defn convert-map [m]
  (apply
    hash-map
    (interleave
      (keys m)
      ;(map keyword (keys m))
      (map (comp string/trim delete-html-tag) (vals m)))))

(defn- json-conv [obj]
  (cond
    (or (seq? obj) (list? obj)) (map json-conv obj)
    (map? obj) (map-val-map json-conv (remove-extra-key obj))
    :else obj))
(defn to-json [obj] (json/json-str (json-conv obj)))

(def parse-int #(Integer/parseInt %))

(defn params->limit-and-page [params]
  (let [limit (aif (:limit params) (parse-int it) *default-limit*)
        page (aif (:page params) (parse-int it) 1)]
    [(if (pos? limit) limit *default-limit*)
     (if (pos? page) page 1)]))




(defn default-response [obj]
  (if (map? obj) obj {:status 200 :headers {"Content-Type" "text/html"} :body obj}))


(defn with-session
  ([session res m]
   (assoc (default-response res) :session (conj (aif session it {}) m)))
  ([res m] (with-session nil res m)))

(defn with-message
  ([session res msg] (with-session session res {:message msg}))
  ([res msg] (with-message nil res msg)))



(defn calendar-format
  ([format-str timezone-str]
   (let [calendar-obj (Calendar/getInstance)]
     (.setTimeZone calendar-obj (TimeZone/getTimeZone timezone-str))
     (format format-str calendar-obj)))
  ([format-str] (calendar-format format-str "Asia/Tokyo")))

(def today (partial calendar-format "%1$tY/%1$tm/%1$td"))
(def now (partial calendar-format "%1$tY/%1$tm/%1$td %1$tH:%1$tM:%1$tS"))
;(defn now []
;  (let [cal (Calendar/getInstance)]
;    (.setTimeZone cal (TimeZone/getTimeZone "Asia/Tokyo"))
;    (format "%1$tY/%1$tm/%1$td %1$tH:%1$tM:%1$tS" cal)
;    )
;  )

(defn n-days-ago [n]
  (let [cal (Calendar/getInstance)]
    (.add cal Calendar/DATE (* -1 n))
    (calendar-format cal "%1$tY/%1$tm/%1$td")))

(defn time->day [s] (first (string/split #"\s+" s)))

(defn today? [date] (= (today) (time->day date)))



