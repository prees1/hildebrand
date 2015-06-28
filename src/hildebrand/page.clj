(ns hildebrand.page
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [hildebrand :as h]
            [hildebrand.util :as util]
            [glossop :refer :all]))

(defn paginate! [f input {:keys [limit maximum chan]}]
  (assert (or limit chan)
          "Please supply either a page-size (limit) or output channel")
  (let [chan (or chan (async/chan limit))]
    (go-catching
      (try
        (loop [start-key nil n 0]
          (let [items (<? (f (cond-> input start-key
                                     (assoc :start-key start-key))))
                n (+ n (count items))
                {:keys [end-key]} (meta items)]
            (if (and (<? (util/onto-chan? chan items))
                     end-key
                     (or (not maximum) (< n maximum)))
              (recur end-key n)
              (async/close! chan))))
        (catch Exception e
          (async/>! chan e)
          (async/close! chan))))
    chan))

(defn query! [creds table where {:keys [limit] :as extra} & [{:keys [chan maximum]}]]
  (paginate!
   (partial h/query! creds table where)
   extra
   {:limit limit :maximum maximum :chan chan}))

(defn scan! [creds table {:keys [limit] :as extra} & [{:keys [chan maximum]}]]
  (paginate!
   (partial h/scan! creds table)
   extra
   {:limit limit :maximum maximum :chan chan}))
