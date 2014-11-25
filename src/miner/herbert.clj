(ns miner.herbert
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [miner.tagged :as tag]
            [squarepeg.core :as sp]
            [miner.herbert.canonical :as canonical]
            [miner.herbert.private :as internal :refer :all 
                                   :rename {internal-reserved-ops reserved-ops, 
                                            internal-ns->predicates ns->predicates, 
                                            internal-default-predicates default-predicates}])
  (:import miner.tagged.TaggedValue))


(defn constraint-fn [schema]
  (let [schema (canonical/rewrite schema)
        exts (schema->extensions schema)
        start (schema->start schema)
        ;;sp/mkmemo should be faster, need benchmarks
        cfn (sp/mkmemo (mkconstraint start exts))]
       (fn ff
         ([item] (ff item {} {} {}))
         ([item context] (ff item context {} {}))
         ([item context bindings memo] (cfn (list item) context bindings memo)))))


(defn schema->grammar [schema]
  (if (grammar? schema)
    schema
    (list 'grammar schema)))

;; creates a fn that test for conformance to the schema
(defn conform [schema] 
  (if (fn? schema) 
    schema 
    (let [grammar (schema->grammar schema)
          con-fn (constraint-fn schema)]
       (fn 
         ([] grammar)
         ([x] (let [res (con-fn x)]
                (when (sp/success? res)
                  (with-meta (:b res) {::schema grammar}))))))))


(defn blame-fn [schema] 
  (let [grammar (schema->grammar schema)
        con-fn (constraint-fn schema)]
    (fn 
      ([] grammar)
      ([x] (let [res (con-fn x)]
                (when (sp/failure? res)
                  (:fail res)))))))

(defn blame [schema x]
  ((blame-fn schema) x))

(defn conforms? [schema x] 
  (boolean ((conform schema) x)))

(defn schema-merge
  "Makes one schema expression out of several, ignoring the 'start' expression from all but the
first argument, arranging rules so that first schema can use rules from subsequent schemata."
  ([start] (schema->grammar start))
  ([start s1] (let [grammar (schema->grammar start)]
                (concat (list 'grammar (second grammar))
                        (when (grammar? s1) (nnext s1))
                        (nnext grammar))))
  ([start s1 & more] (let [grammar (schema->grammar start)]
                       (concat (list 'grammar (second grammar))
                               (mapcat #(when (grammar? %) (nnext %)) (cons s1 more))
                               (nnext grammar)))))
