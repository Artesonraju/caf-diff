(ns cafdiff.main
  (:require [feedparser-clj.core :refer [parse-feed]]
            [net.cgrand.enlive-html :as html]
            [org.httpkit.client :as http]
            [chime :refer [chime-ch]]
            [clj-time.core :as time]
            [clj-time.periodic :refer [periodic-seq]]
            [clojure.core.async :as a :refer [<!! <! >! go-loop timeout]]
            [clojure.data.codec.base64 :as b64]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as string])
  (:import [org.joda.time DateTimeZone])
  (:gen-class))

(def default-user "user")

(def before-url
  "<h1 style=\"display: block;margin: 0;padding: 0;color: #202020;font-family: Helvetica;font-size: 21px;font-style: normal;font-weight: bold;line-height: 125%;letter-spacing: normal;text-align: left;\" class=\"null\">
   <a style=\"mso-line-height-rule: exactly;-ms-text-size-adjust: 100%;-webkit-text-size-adjust: 100%;color: #2BAADF;font-weight: normal;text-decoration: underline;\" target=\"_blank\" href=\"")

(def before-title
  "\" rel=\"noreferrer nofollow noopener\">")

(def before-desc
  "</h1></a><span style=\"font-size:14px\"><br>")

(def after-all
  "</span><br><br>")

(defn read-file [file]
  (read-string (slurp file)))

(defn write-file [file data]
  (spit file (pr-str data)))

(defn rss-urls [url]
  (let [entries (-> (parse-feed url)
                    (get :entries))]
    (map :link entries)))

(defn new-entries [olds news]
  (filter #(not (contains? (set olds) %)) news))

(defn get-dom [url]
  (html/html-snippet
      (:body @(http/get url {:insecure? true}))))

(defn extract-title [dom]
  (->> (html/select dom [:#fiche-sortie :h1])
       (first)
       (:content)
       (drop 4)
       (map #(or (first (:content %)) %))
       (map string/trim)
       (string/join " ")))

(defn extract-props [outing-props dom]
  (->> (html/select dom [:#fiche-sortie :.nice-list])
       (map :content)
       (mapcat #(map :content %))
       (map #(vector (-> % first :content first) (second %)))
       (filter #(contains? outing-props (first %)))
       (map #(apply str %))
       (string/join " | ")))

(defn html-entry [outing-props url]
  (let [dom (get-dom url)]
    (str before-url
         url
         before-title
         (extract-title dom)
         before-desc
         (extract-props outing-props dom)
         after-all)))

(defn html-content [outing-props urls]
  (when (seq urls)
    (apply str (map (partial html-entry outing-props) urls))))

(defn html-header [subject]
  (str "<h1 style=\"display: block;margin: 0;padding: 0;color: #202020;font-family: Helvetica;font-size: 26px;font-style: normal;font-weight: bold;line-height: 125%;letter-spacing: normal;text-align: left;\" class=\"null\">" subject "</h1>"))

(defn subject [title]
  (str title " - Club Alpin Français - Chambéry, le " (.format (java.text.SimpleDateFormat. "dd/MM/yyyy") (java.util.Date.))))

(defn base64 [original]
  (String. (b64/encode (.getBytes original)) "UTF-8"))

(defn authorization-key [key]
  (let [auth (base64 (str default-user ":" key))]
    (str "Basic " auth)))

(defn process-reponse
  ([resp key-str]
   (let [{:keys [status body error]} resp
         content (if (seq body) (json/read-str body) nil)]
     (cond
       (some? error) {:error error}
       (>= status 300) (let [title (get content "title")
                             detail (get content "detail")]
                         {:error (string/join " - " [status title detail])})
       :else (when key-str {(keyword key-str) (get content key-str)}))))
  ([resp] (process-reponse resp nil)))

(defn new-campaign [api auth default-id subject]
  (let [url (str api "/campaigns/" default-id "/actions/replicate")
        req {:url url
             :method :post
             :headers {"Authorization" auth}}
        resp @(http/request req)]
    (process-reponse resp "id")))

(defn update-campaign [api auth campaign-id list-id subject]
  (let [url (str api "/campaigns/" campaign-id)
        req {:url url
             :method :patch
             :headers {"Authorization" auth}
             :body (json/write-str {"recipients" {"list_id" list-id}
                                    "settings" {"subject_line" subject
                                                "title" subject}})}
        resp @(http/request req)]
    (process-reponse resp "id")))

(defn content-campaign [api auth campaign-id template-id header content]
  (let [url (str api "/campaigns/" campaign-id "/content")
        req {:url url
             :method :put
             :headers {"Authorization" auth}
             :body (json/write-str {"template" {"id" (Integer/parseInt template-id)
                                                "sections" {"header" header
                                                            "body" content}}})}
        resp @(http/request req)]
    (process-reponse resp)))

(defn send-campaign [api auth campaign-id]
  (let [url (str api "/campaigns/" campaign-id "/actions/send")
        req {:url url
             :method :post
             :headers {"Authorization" auth}}
        resp @(http/request req)]
    (process-reponse resp)))

(defn campaign [config list-id subject content]
  (let [{:keys [mailchimp-key mailchimp-api default-campaign-id template-id]} config
        auth (authorization-key mailchimp-key)
        {:keys [id error]} (new-campaign mailchimp-api auth default-campaign-id subject)
        {:keys [id error]} (if (some? error)
                              {:error error}
                              (update-campaign mailchimp-api auth id list-id subject))
        {:keys [error]} (if (some? error)
                            {:error error}
                            (content-campaign mailchimp-api auth id template-id (html-header subject) content))
        {:keys [error]} (if (some? error)
                            {:error error}
                            (send-campaign mailchimp-api auth id))]
    (when (some? error)
      error)))

(defn process-list [config data-file]
  (let [{:keys [title rss previous-urls list-id] :as data} (read-file data-file)
        outing-props (:outing-properties config)
        _ (println "INFO start processing campaign " title)
        subject (subject title)
        new-urls (rss-urls rss)
        news (try
              (new-entries previous-urls new-urls)
              (catch Exception e
                (println "ERROR : Unable to reach or parse RSS feed " rss)))]
    (if (empty? news)
      (println "INFO campaign : " subject " : No new rss item")
      (if-let [error (campaign config list-id subject (html-content outing-props news))]
        (println "ERROR campaign : " subject " : " error)
        (do
          (println "INFO campaign : " subject " : New rss items sent")
          (write-file data-file (assoc data :previous-urls new-urls)))))))

(defn -main [& args]
  (let [exploitation (first args)
        config-file (io/file exploitation "config.edn")
        {:keys [sending-hour] :as config} (read-file config-file)
        periodic (->> (periodic-seq (.. (time/now)
                                        (withZone (DateTimeZone/forID "Europe/Paris"))
                                        (withTime sending-hour 0 0 0))
                                    (-> 1 time/days)))
        chimes (chime-ch periodic)]
    (println "INFO launching caf-diff")
    (<!! (go-loop []
            (when (<! chimes)
              (doseq [file (.listFiles (io/file exploitation "data"))]
                (try
                  (process-list config file)
                  (catch Exception e
                   (println "ERROR : Exception processing list : " (.getMessage e)))))
              (recur))))))
