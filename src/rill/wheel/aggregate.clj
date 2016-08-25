(ns rill.wheel.aggregate

  "# Aggregates and Events

  ### Synopsis

      (require '[rill.wheel.aggregate :as aggregate
                 :refer [defaggregate defevent]])

      (defaggregate user
        \"a user is identified by a single `email` property\"
        [email])

      (defevent registered
        \"user was correctly registered\"
        [user]
        (assoc user :registered? true))

      (defevent unregistered
        \"user has unregistered\"
        [user]
        (dissoc user :registered?))

      (-> (user \"user@example.com\") registered :registered?)
        => true

      (registered-event (user \"user@example.com\"))
        => {:rill.message/type :user/registered,
            :email \"user@example.com\",
            :rill.wheel.aggregate/type :user/user}

      (aggregate/new-events some-aggreate)
        => seq-of-events

  ### Store and retrieve aggregates in a repository

      (-> (get-user repo \"user@example.com)
          (registered)
          (command/commit!))
      ;; ...
      (get-user some-repository \"user@example.com\")

  ### Full example of defaggregate

      (defaggregate turnstile
        \"An aggregate with docstring\"
        [turnstile-id]
        {:pre [(instance? java.util.UUID turnstile-id)]}
        ((installed
          \"A turnstile was installed\"
          [turnstile]
          (assoc turnstile
                 :installed? true
                 :locked? true
                 :coins 0
                 :turns 0
                 :pushes 0))

         (coin-inserted
          \"A Coin was inserted into the turnstile\"
          [turnstile]
          (-> turnstile
              (update :coins inc)
              (assoc :locked? false)))

         (arm-turned
          \"The turnstile's arm was turned\"
          [turnstile]
          (-> turnstile
              (update :pushes inc)
              (update :turns inc)
              (assoc :locked? true)))

         (arm-pushed-ineffectively
          \"The arm was pushed but did not move\"
          [turnstile]
          (-> turnstile
              (update :pushes inc))))

        ((install-turnstile
          [repo turnstile-id]
          (let [turnstile (get-turnstile repo turnstile-id)]
            (if (aggregate/exists turnstile)
              (rejection turnstile \"Already exists\")
              (installed turnstile))))

         (insert-coin
          \"Insert coin into turnstile, will unlock\"
          [repo turnstile-id]
          (let [turnstile (get-turnstile repo turnstile-id)]
            (if (:installed? turnstile)
              (coin-inserted turnstile)
              (rejection turnstile \"Turnstile not installed\"))))

         (push-arm
          \"Push the arm, might turn or be ineffective\"
          {::aggregate/events [::arm-pushed-ineffectively ::arm-turned]}
          [repo turnstile-id]
          (let [turnstile (get-turnstile repo turnstile-id)]
            (cond
              (not (:installed? turnstile))
              (rejection turnstile \"Not installed\")
              (:locked? turnstile)
              (arm-pushed-ineffectively turnstile)
              :else
               (arm-turned turnstile))))))


  # Commands

  Commands are functions that apply new events to aggregates.

  ## Command flow

       (-> (get-some-aggregate repository id) ; 1.
           (cmd-call additional-argument)     ; 2.
           (commit!))                         ; 3.

  ### 1. Fetch aggregate

  Before calling the command, the aggregate it applies to should get
  fetched from the `repository`. In rill/wheel, this will always work
  and must be done even for aggregates that have no events applied to
  them - this will result in an `rill.wheel.aggregate/empty?`
  aggregate that can be committed later.

  ### 2. Calling the command

  A command can have any number of arguments, and it's idiomatic for
  commands to take the aggregate-to-change as the first argument.

  As a matter of style, it's suggested that commands do not fetch
  other objects from the repostory but are explicitly passed any
  necessary aggregates.

  #### Success

  A successful command returns an `uncomitted?` aggregate.

  #### Rejection

  A command may be rejected, in which case the command returns a
  `rejection` - meaning the request was denied for business
  reasons. Rejections are explicitly constructed in the `defcommand`
  body by the application writer.

  It's typically useless to retry a rejected command.

  ### 3. Committing results

  The result of a command can be persisted back to the repository by
  calling `commit!`. If `commit!` is passed a `rejection` it will
  return it. Otherwise the argument should be an aggregate that will
  be persisted.

  ### ok

  A successful commit returns an `ok?` object describing the committed
  events and aggregate.

  ### conflict

  Commiting an updated aggregate can return a `conflict`, meaning
  there were changes to the aggregate in the repository in the time
  between fetching the aggregate and calling `commit!`.

  Depending on the use case, it may be useful to update the aggregate
  and retry a conflicted command.

  ## Defining commands


       (defevent x-happened                            ; 1.
          \"X happened to obj\"
          [obj arg1]
          (assoc obj :a arg1))

       (defcommand do-x                                ; 2.
          \"Make X happen to obj\"
          [obj arg1]
          (if (= (:a obj) arg1))                       ; 3
              (rejection obj \"Arg already applied\")
              (x-happened obj)))                       ; 4.


  ### 1. Define events to apply

  Commands can only affect aggregates by applying events to them. Here
  we define an event with `defevent`. When the `x-happened` event is
  applied it will set key `:a` of aggregate `obj`.

  It's idiomatic to give events a past-tense name, to indicate that
  the event happened and is not .

  ### 2. Define command

  Commands are defined by calling `defcommand`, specifying a name,
  optional docstring, argument vector and a command body.

  ### 3. Test state and reject command

  Aggregate state is typically only used to keep track of information
  that must be used to validate commands. When a command must not
  proceed, the command body can return a `rejection` with a reason.

  ### 4. Apply new event(s)

  When the command is acceptable, it should apply the necessary events
  to the aggregate and return the result (the updated aggregate).

  ## See also

  - `rill.event-store`
  "
  (:refer-clojure :exclude [empty empty? type])
  (:require [rill.event-store :refer [retrieve-events append-events]]
            [rill.wheel.repository :as repo]
            [rill.wheel.macro-utils :refer [parse-args keyword-in-current-ns parse-pre-post]]))

(defmulti apply-event
  "Update the properties of `aggregate` given `event`. Implementation
  for different event types will be given by `defevent`"
  (fn [aggregate event]
    (:rill.message/type event)))

(defmethod apply-event :default
  [aggregate _]
  aggregate)

(defn apply-new-event
  "Apply a new event to the aggregate. The new events will be committed
  when the aggregate is committed to a repository."
  [aggregate event]
  (-> aggregate
      (apply-event event)
      (update ::new-events (fnil conj []) event)))

(defn new-events
  "The events that will be committed when this aggregate is committed."
  [aggregate]
  (::new-events aggregate))

(defn aggregate?
  "Test that `obj` is an aggregate"
  [obj]
  (boolean (::id obj)))

(defn empty
  "Create a new aggregate with id `aggregate-id` and no
  events. Aggregate version will be -1. Note that empty aggregates
  cannot be stored."
  [aggregate-id]
  (let [base {::id aggregate-id ::version -1}]
    (if (map? aggregate-id)
      (merge base aggregate-id)
      base)))

(defn new?
  "Test that the aggregate has no committed events."
  [aggregate]
  (= (::version aggregate) -1))

(defn empty?
  "Test that the aggregate is new and has no uncommitted events"
  [aggregate]
  (and (new? aggregate)
       (clojure.core/empty? (::new-events aggregate))))

(defn exists
  "If aggregate is not new, return aggregate, otherwise nil"
  [aggregate]
  (when-not (new? aggregate)
    aggregate))

(defn apply-stored-event
  "Apply a previously committed event to the aggregate. This
  increments the version of the aggregate."
  [aggregate event]
  (-> aggregate
      (apply-event event)
      (update ::version inc)))

(defn merge-aggregate-props
  [aggregate partial-event]
  (if (map? (::id aggregate))
    (merge partial-event (::id aggregate))
    partial-event))

(defmacro defevent
  "Defines function that takes aggregate + properties, constructs an
  event and applies the event as a new event to aggregate. Properties
  defined on the aggregate definition will be merged with the event;
  do not define properties with `defevent` that are already defined in
  the corresponding `defaggregate`.

  For cases where you only need the event and can ignore the
  aggregate, the function \"{name}-event\" is defined with the same
  signature. This function is used by the \"{name}\" function to
  generate the event befor calling `apply-event` (see below).

  The given `prepost-map`, if supplied gets included in the definiton
  of the \"{name}-event\" function.

  The given `body`, if supplied, defines an `apply-event` multimethod
  that applies the event to the aggregate. If no `body` is supplied,
  the default `apply-event` will be used, which will return the
  aggregate as is."
  {:arglists '([name doc-string? attr-map? [aggregate properties*] pre-post-map?  body*])}
  [& args]
  (let [[n [aggregate & properties :as handler-args] & body] (parse-args args)
        [prepost body]                                       (parse-pre-post body)
        n                                                    (vary-meta n assoc :rill.wheel.aggregate/event-fn true)
        n-event                                              (symbol (str (name n) "-event"))]
    `(do ~(when (seq body)
            `(defmethod apply-event ~(keyword-in-current-ns n)
               [~aggregate {:keys ~(vec properties)}]
               ~@body))
         (defn ~n-event
           (~handler-args
            ~@(when prepost
                [prepost])
            (merge-aggregate-props ~aggregate
                                   ~(into {:rill.message/type (keyword-in-current-ns n)}
                                          (map (fn [k]
                                                 [(keyword k) k])
                                               properties)))))
         (defn ~n
           (~handler-args
            (apply-new-event ~aggregate (~n-event ~aggregate ~@properties)))))))



(defn type
  "Return the type of this aggregate"
  [aggregate]
  (::type aggregate))

(defn type-properties
  "the properties of the identifier of aggreate type `t`"
  [t]
  (-> (symbol (namespace t) (name t))
      resolve
      meta
      ::properties))

(defn repository
  "Return the repository of `aggregate`."
  [aggregate]
  {:pre  [(aggregate? aggregate)]
   :post [%]}
  (::repository aggregate))

;;;; Command handling

(defn rejection? [result]
  (= (::status result) :rejected))

(defn ok? [result]
  (= (::status result) :ok))

(defn conflict? [result]
  (= (::status result) :conflict))

(defn ok [aggregate]
  {::status :ok ::events (:rill.wheel.aggregate/new-events aggregate) ::aggregate aggregate})

(defn rejection
  [aggregate reason]
  {::status :rejected ::reason reason ::aggregate aggregate})

(defn conflict
  [aggregate]
  {::status :conflict ::aggregate aggregate})

(defn uncommitted?
  "`aggregate` has events applied that can be committed."
  [aggregate]
  (boolean (seq (:rill.wheel.aggregate/new-events aggregate))))

(defn commit!
  "Commit the result of a command execution. If the command returned a
  `rejection` nothing is committed and the rejection is returned. If the
  result is an aggregate it is committed to the repository. If that
  succeeds an `ok` is returned. Otherwise a `conflict` is returned."
  [aggregate-or-rejection]
  (cond
    (rejection? aggregate-or-rejection)
    aggregate-or-rejection
    (repo/commit! (:rill.wheel.aggregate/repository aggregate-or-rejection) aggregate-or-rejection)
    (ok aggregate-or-rejection)
    :else
    (conflict aggregate-or-rejection)))

(defmulti fetch-aggregate
  "Given a command and repository, fetch the target aggregate"
  (fn [repo command]
    (:rill.message/type command)))

(defmulti apply-command
  "Given a command and aggregate, apply the command to the
  aggregate. Should return an updated aggregate or a rejection"
  (fn [repo command]
    (:rill.message/type command)))

(defn transact!
  "Run and commit the given command against the repository"
  [repo command]
  (-> (fetch-aggregate repo command)
      (apply-command command)
      (commit!)))

;;;;----TODO(Joost) Insert pre-post checks at the right places, update
;;;;----documentation

(defmacro defcommand
  "Defines a command as a named function that takes any arguments and
  returns an `aggregate` or `rejection` that can be passed to
  `commit!`.

  The metadata of the command may contain a
  `:rill.wheel.aggregate/events` key, which will specify the types of
  the events that may be generated. As a convenience, the
  corresponding event functions are `declare`d automatically so the
  `defevent` statements can be written after the command. This usually
  reads a bit nicer.

       (defcommand answer-question
         \"Try to answer a question\"
         {::aggregate/events [::answered-correctly ::answered-incorrectly]
          ::aggregate/aggregate ::question}
         [repo question user-id answer]
         (let [question (question repo question-id)]
           (if (some-check question answer)
            (answered-correctly question user-id answer)
            (answered-incorrectly question user-id answer))))

  "
  {:arglists '([name doc-string? attr-map? [repository properties*] pre-post-map? body])}
  [n t & args]
  (when-not (or (symbol? t) (keyword? t))
    (throw (IllegalArgumentException. "Second argument to defcommand should be the type of the aggregate.")))
  (let [[n [aggregate & props] & body] (parse-args (cons n args))
        n                              (vary-meta n assoc
                                                  ::command-fn true
                                                  ::aggregate t)
        m                              (meta n)
        fetch-props                    (type-properties t)
        _                              (when-not (vector? fetch-props)
                                         (throw (IllegalStateException. (format "Can't fetch type properties for aggregate %s" (str t)))))
        fetch-props                    (mapv #(-> % name symbol) fetch-props)
        getter                         (symbol (namespace t) (str "get-" (name t)))]
    `(do ~(when-let [event-keys (::events m)]
            `(declare ~@(map (fn [k]
                               (symbol (subs (str k) 1)))
                             event-keys)))

         (defmethod apply-command ~(keyword-in-current-ns n)
           [~aggregate {:keys ~(vec props)}]
           ~@body)

         (defmethod fetch-aggregate ~(keyword-in-current-ns n)
           [repository# {:keys ~fetch-props}]
           (~getter repository# ~@fetch-props))

         (defn ~(symbol (str n "-command"))
           ~(format "Construct a %s command message" (name n))
           ~(into fetch-props props)
           ~(into {:rill.message/type (keyword-in-current-ns n)}
                  (map (fn [k]
                         [(keyword k) k])
                       (into fetch-props props))))
         (defn ~n
           ~(format "Apply command %s to %s. Does not commit" (name n) (name aggregate))
           ~(into [aggregate] props)
           (apply-command ~aggregate (~(symbol (str n "-command"))
                                      ~@(map (fn [p]
                                                 `(get ~aggregate ~(keyword p)))
                                               fetch-props)
                                      ~@props)))

         (defn ~(symbol (str (name n) "!"))
           ~(format "Apply command %s to repository and commit" (name n))
           [repository# ~@fetch-props ~@props]
           (transact! repository# (~(symbol (str (name n) "-command")) ~@fetch-props ~@props))))))

(defmacro defaggregate
  "Defines an aggregate type, and aggregate-id function. The
  aggregate's id key is a map with a key for every property in
  `properties*`, plus the aggregate type, a qualified keyword from
  `name`.

  Also defines a function `get-{name}`, which takes an additional
  first repository argument and retrieves the aggregate.

  events? and commands? are sequences of event specs and command specs
  and passed to `defevent` and `rill.wheel.aggregate/defcommand`
  respectively.


  "
  {:arglists '([name doc-string? attr-map? [properties*] pre-post-map? events? commands?])}
  [& args]
  (let [[n descriptor-args & body] (parse-args args)
        n                          (vary-meta n assoc
                                              ::descriptor-fn true
                                              ::properties (mapv keyword descriptor-args))
        [prepost body]             (parse-pre-post body)
        repo-arg                   `repository#]
    `(do (defn ~n
           ~(vec descriptor-args)
           ~@(when prepost
               [prepost])
           (empty (sorted-map ::type ~(keyword-in-current-ns n)
                              ~@(mapcat (fn [k]
                                          [(keyword k) k])
                                        descriptor-args))))
         (defn ~(symbol (str "get-" (name n)))
           ~(format "Fetch `%s` from repository `%s`" (name n) (name repo-arg))
           ~(into [repo-arg] descriptor-args)
           (-> (repo/update ~repo-arg (apply ~n ~descriptor-args))
               (assoc :rill.wheel.aggregate/repository ~repo-arg)))

         ~@(map (fn [event]
                  `(defevent ~@event))
                (first body))
         ~@(map (fn [command]
                  `(defcommand ~(first command) ~(keyword-in-current-ns n) ~@(rest command)))
                (second body)))))
