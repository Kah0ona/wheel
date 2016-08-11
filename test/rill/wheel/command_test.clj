(ns rill.wheel.command-test
  (:require [clojure.test :refer [deftest is]]
            [rill.wheel.aggregate :as aggregate :refer [defaggregate defevent]]
            [rill.wheel.command :as command :refer [defcommand ok? rejection]]
            [rill.wheel.repository :as repo]
            [rill.wheel.testing :refer [ephemeral-repository]]))

(defaggregate user
  [email])

(defcommand create-or-fail
  "Create user if none exists with the given email address."
  {::command/events [::created]}
  [repo email full-name]
  (let [user (repo/update repo (user email))]
    (if-not (aggregate/new? user)
      (rejection user (format "User with mail '%s' already exists" email))
      (-> user
          (created full-name)))))

(defevent created
  "A new user was created"
  [user full-name]
  (assoc user :full-name full-name))

(defcommand rename
  {::command/events [::name-changed]}
  [repo email new-name]
  (let [user (repo/update repo (user email))]
    (if-not (aggregate/new? user)
      (name-changed user new-name)
      (rejection user (format "No user with mail '%s' exists" email)))))

(defevent name-changed
  "user's `full-name` changed to `new-name`"
  [user new-name]
  (assoc user :full-name new-name))


(deftest defevent-test
  (is (= {::aggregate/id         {:email "user@example.com"}
          :full-name             "joost"
          :email                 "user@example.com"
          ::aggregate/version    -1
          ::aggregate/new-events [{:rill.message/type ::created
                                   :email             "user@example.com"
                                   :full-name         "joost"}]}
         (-> (user "user@example.com")
             (created "joost")))
      "Event fn calls handler with created event"))

(deftest aggregate-creation-test
  (let [repo (ephemeral-repository)]
    (is (ok? (create-or-fail repo "user@example.com" "Full Name")))
    (is (= {:rill.wheel.aggregate/id      {:email "user@example.com"}
            :rill.wheel.aggregate/version 0
            :full-name                    "Full Name"
            :email                        "user@example.com"}
           (repo/update repo (user "user@example.com"))))))
