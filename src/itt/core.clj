(ns itt.core
  (:import (java.util.concurrent ArrayBlockingQueue
                                 RejectedExecutionException
                                 ThreadPoolExecutor
                                 TimeUnit)))

(defn ->trigger-component [f]
  (let [q (ArrayBlockingQueue. 1)
        executor (ThreadPoolExecutor. 1 1 1000 TimeUnit/SECONDS q)]
    {:trigger (fn []
                (try
                  (.execute executor f)
                  :enqueued-successfully
                  (catch RejectedExecutionException e
                    :already-enqueued-again)))
     :shutdown (fn []
                 (.shutdown executor))
     :shutdown-now (fn []
                     (.shutdownNow executor))}))

(defn ->trigger [f]
  (:trigger (->trigger-component f)))
