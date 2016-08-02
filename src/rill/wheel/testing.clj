(ns rill.wheel.testing
  "Tools for unit-testing ring.wheel code."
  (:require [rill.event-store.memory :refer [memory-store]]
            [rill.wheel.bare-repository :refer [bare-repository]]))

(defn sub?
  "true if sub is either nil, equal to x, or a recursive subcollection
  of x.

  If sub is a sequential collection of size N the first N
  elements of x are tested. If sub is a map every value in sub is
  tested with the corresponding value in x. If sub is a set every
  *key* in sub should exist in x.

    (sub? nil
          {:anything :at-all})
    (sub? [:a :b]
          [:a :b :c])
    (not (sub? [:a :b :c]
               [:a :b]))
    (sub? {:a [1 2 3]}
          {:a [1 2 3 4] :b 2})
    (sub? {:a [1 nil 3]}
          {:a [1 2 3 4] :b 2})
    (not (sub? {:a [1 2 3 4]}
               {:a [1 2 3] :b 2}))
    (sub? #{:a}
          {:a 1 :b 2})
    (sub? #{:a}
          #{:a :b})
    (not (sub? #{:a :c}
               #{:a :b}))
    (sub? :something
          :something)
"
  [sub x]
  (cond (nil? sub)
        true
        (sequential? sub)
        (every? (fn [[i el]]
                  (sub? el (get x i)))
                (map-indexed vector sub))
        (map? sub)
        (every? (fn [[k v]] (sub? v (get x k)))
                sub)
        (set? sub)
        (every? #(contains? x %)
                sub)
        :else
        (= sub x)))


(defn ephemeral-repository
  "Return an empty repostory backed by an in-memory event-store."
  []
  (bare-repository (memory-store)))
