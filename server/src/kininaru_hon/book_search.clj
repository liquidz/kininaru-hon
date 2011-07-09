(ns kininaru-hon.book-search
  (:require
     [clojure.contrib.zip-filter.xml :as zfx]
     [clojure.contrib.json :as json]
     [clojure.contrib.io :as io]))

; constants
(def *rakuten-developer-id* nil)
(def *rakuten-affiliate-id* nil)
(def *rakuten-api-urls*
  {"2009-03-26" "http://api.rakuten.co.jp/rws/2.0/json?"
   "2009-04-15" "http://api.rakuten.co.jp/rws/2.0/json?"
   "2010-03-18" "http://api.rakuten.co.jp/rws/3.0/json?"})
(def *rakuten-api-version* "2010-03-18")
(def *google-book-base-uri* "http://books.google.com/books/feeds/volumes?q=ISBN:")

; util {{{
(defn- map->url-parameters [m]
  (apply str (interpose "&" (map (fn [[k v]] (str (name k) "=" v)) m))))
(defn- fxml-> [& args]
  (first (apply zfx/xml-> args)))
(defn- fxml->text [& args]
  (apply fxml-> (concat args (list zfx/text))))
; }}}

; rakuten {{{
(defmacro with-rakuten-developer-id [dev-id & body]
  `(binding [*rakuten-developer-id* ~dev-id] ~@body))
(defmacro with-version [version & body]
  `(binding [*version* ~version] ~@body))
(defmacro with-affiliate-id [aff-id & body]
  `(binding [*rakuten-affiliate-id* ~aff-id] ~@body))

(defn- make-rakuten-url [m operation]
  (let [data (assoc m :developerId *rakuten-developer-id* :operation operation :version *rakuten-api-version*)
        data2 (if (nil? *rakuten-affiliate-id*) data (assoc data :affiliateId *rakuten-affiliate-id*))
        ]
    (str (get *rakuten-api-urls* *rakuten-api-version*) (map->url-parameters data2))))

(defn rakuten-book-search [& {:as m}]
  (json/read-json (apply str (io/read-lines (make-rakuten-url m "BooksBookSearch")))))
(defn rakuten-item-search [& {:as m}]
  (json/read-json (apply str (io/read-lines (make-rakuten-url m "BooksTotalSearch")))))
; }}}

; google book search
(defn get-google-book-result-count [zxml]
  (fxml->text zxml :openSearch:totalResults))

(defn get-google-book-title [zxml]
  (fxml->text zxml :entry :title))

(def google-book-thumb-schema (zfx/attr= :rel "http://schemas.google.com/books/2008/thumbnail"))

(defn get-google-book-thumbnail [zxml]
  (fxml-> zxml :entry :link google-book-thumb-schema (zfx/attr :href)))

(defn get-google-book-author [zxml]
  (fxml->text zxml :entry :dc:creator))

(defn get-google-book-publisher [zxml]
  (fxml->text zxml :entry :dc:publisher))

(defn get-google-book-date [zxml]
  (fxml->text zxml :entry :dc:date))

(defn get-google-book-description [zxml]
  (fxml->text zxml :entry :dc:description))

(defn google-book-search [isbn]
  (let [zxml (clojure.zip/xml-zip (clojure.xml/parse (str *google-book-base-uri* isbn)))]
    (when-not (= "0" (get-google-book-result-count zxml))
      {:title (get-google-book-title zxml)
       :thumbnail (get-google-book-thumbnail zxml)
       :author (get-google-book-author zxml)
       :publisher (get-google-book-publisher zxml)
       :date (get-google-book-date zxml)
       :description (get-google-book-description zxml) })))

