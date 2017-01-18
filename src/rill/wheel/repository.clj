(ns rill.wheel.repository
  "The protocol for implementing repositories."
  (:refer-clojure :exclude [update]))

(defprotocol Repository
  (commit! [repo aggregate]
    "Commit changes to `aggregate` by storing its
    `:rill.wheel/new-events`.  Returns `true` on success or
    when there are no new events. `nil` otherwise.

    Application writers should use `rill.wheel/commit!`
    instead.")
  (update [repo aggregate]
    "Update an aggregate by applying any new committed events, as
    determined by `:rill.wheel/version`.

    Application writers should call the `get-{aggregate-name}`
    functions generated by `rill.wheel/defaggregate`
    instead.")
  (event-store [repo]
    "The backing event store for this repository"))

(defn repository?
  "Test if `repo` is a repository"
  [repo]
  (satisfies? Repository repo))
