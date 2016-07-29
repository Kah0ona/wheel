(ns rill.wheel.command-test
  (:require [clojure.test :refer [deftest is]]
            [rill.wheel
             [aggregate :as aggregate :refer [defevent]]
             [command :as command :refer [defcommand ok? rejection]]
             [repository :as repo]
             [testing :refer [ephemeral-repository]]]))

(defevent user-created
  "A new user was created"
  [user email full-name]
  (assoc user :email email :full-name full-name))

(defcommand create-or-fail
  "Create user if none exists with the given email address."
  [repo email full-name]
  (let [user (repo/fetch repo email)]
    (if-not (aggregate/new? user)
      (rejection user (format "User with mail '%s' already exists" email))
      (-> (aggregate/empty email)
          (user-created email full-name)))))

(defevent user-name-changed
  "user's `full-name` changed to `new-name`"
  [user new-name]
  (assoc user :full-name new-name))

(defcommand rename
  [repo email new-name]
  (let [user (repo/fetch repo email)]
    (if-not (aggregate/new? user)
      (user-name-changed user new-name)
      (rejection user (format "No user with mail '%s' exists" email)))))

(deftest defevent-test
  (let [event (user-created "user@example.com" "joost")]
    (is (= {:rill.message/type ::user-created
            :email             "user@example.com"
            :full-name         "joost"}
           event)
        "lower-arity variant creates new event")
    (is (= {::aggregate/id         :my-id
            :full-name             "joost"
            :email                 "user@example.com"
            ::aggregate/version    -1
            ::aggregate/new-events [event]}
           (-> (aggregate/empty :my-id)
               (aggregate/apply-new-event event)))
        "event handler multimethod is installed")
    (is (= {::aggregate/id         :my-id
            :full-name             "joost"
            :email                 "user@example.com"
            ::aggregate/version    -1
            ::aggregate/new-events [event]}
           (-> (aggregate/empty :my-id)
               (user-created "user@example.com" "joost")))
        "additional arity veriant calls handler with created event")))

(deftest aggregate-creation-test
  (let [repo (ephemeral-repository)]
    (is (ok? (create-or-fail repo "user@example.com" "Full Name")))
    (is (= {:rill.wheel.aggregate/id      "user@example.com"
            :rill.wheel.aggregate/version 0
            :full-name                    "Full Name"
            :email                        "user@example.com"}
           (repo/fetch repo "user@example.com")))))
