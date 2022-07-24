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

(def selmer-paths
  ["deps.edn"
   "src/clj/core.clj"
   "src/clj/web/middleware/core.clj"
   "resources/system.edn"])

(defn data-fn [{:keys [template-dir target-dir] :as data}]
  (let [full-name (:name data)
        [_ name] (string/split full-name #"/")
        versions (edn/read-string (slurp (str template-dir "/versions.edn")))
        selmer-opts (merge data
                           {:versions versions}
                           (update-keys data #(edn/read-string (str % "?"))))
        selmer-map (->> selmer-paths
                        (map (fn [path]
                               (let [raw (slurp (str template-dir "/root/" path))]
                                 [path {:file-path path
                                        :parsed (render-selmer raw selmer-opts)
                                        :temp-file (fs/create-temp-file)}])))
                        (into {}))]
    (fs/delete-on-exit (fs/file (str target-dir "/gitignore")))
    (doseq [[_ {:keys [temp-file parsed]}] selmer-map]
      (fs/delete-on-exit temp-file)
      (spit (fs/file temp-file) parsed))
    (-> data
        (merge {:selmer-map selmer-map
                :full-name full-name
                :app name
                :ns-name (deps-new-impl/->ns full-name)
                :name name
                :sanitized (deps-new-impl/->file full-name)
                :default-cookie-secret (rand-str 16)}))))

(defn template-fn [template {:keys [template-dir selmer-map]}]
  (let [extra-dir (str (fs/relativize template-dir (str (fs/temp-dir))))
        rename-map (->> selmer-map
                        (map (fn [[_ {:keys [temp-file file-path]}]]
                               [(fs/file-name temp-file) file-path]))
                        (into {}))
        extra-files [extra-dir rename-map :only]]
    (-> template
        (update :transform conj extra-files))))
