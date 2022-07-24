(ns io.github.kit-clj.deps-new-template
  (:require [clojure.pprint :as pprint]
            [clojure.string :as string]
            [babashka.fs :as fs]
            [selmer.parser :as selmer]
            [clojure.edn :as edn]
            [org.corfield.new.impl :as deps-new-impl]))

(defn- render-selmer
  [text options]
  (selmer/render
    (str "<% safe %>" text "<% endsafe %>")
    options
    {:tag-open \< :tag-close \> :filter-open \< :filter-close \>}))

(defn- rand-str
  [n]
  (->> (repeatedly #(char (+ (rand 26) 65)))
       (take n)
       (apply str)))

(defn data-fn [{:keys [template-dir target-dir] :as data}]
  (let [full-name (:name data)
        [_ name] (string/split full-name #"/")
        versions (edn/read-string (slurp (str template-dir "/versions.edn")))
        deps-raw (slurp (str template-dir "/root/deps.edn"))
        deps-path (fs/create-temp-file)
        selmer-opts (merge data
                           {:versions versions}
                           (update-keys data #(edn/read-string (str % "?"))))
        deps-parsed (render-selmer deps-raw selmer-opts)]
    (fs/delete-on-exit (fs/file (str target-dir "/gitignore")))
    (fs/delete-on-exit deps-path)
    (spit (fs/file deps-path) deps-parsed)
    (-> data
        (merge {:deps-path (str deps-path)
                :full-name full-name
                :app name
                :ns-name (deps-new-impl/->ns full-name)
                :name name
                :sanitized (deps-new-impl/->file full-name)
                :default-cookie-secret (rand-str 16)}))))

(defn template-fn [template {:keys [deps-path template-dir target-dir]}]
  (let [extra-dir (str (fs/relativize template-dir (str (fs/temp-dir))))
        extra-files [extra-dir {(fs/file-name deps-path) "deps.edn"} :only]]
    (-> template
        (update :transform conj extra-files))))
