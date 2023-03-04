(ns itt.core-test
  (:require [clojure.test :refer :all]
            [itt.core :refer [->trigger ->trigger-component]]))

(deftest in-its-basic-form-the-trigger-enqueues-successfully-test
  (let [trigger (->trigger (constantly 42))]
    (is (= :enqueued-successfully (trigger)))))

(deftest the-given-fn-is-called-anyways-test
  (let [p (promise)
        f #(deliver p 42)
        trigger (->trigger f)]
    (is (= :enqueued-successfully (trigger)))
    (is (= 42
           (deref p 1000 :timeout)))))

(deftest when-the-trigger-is-called-the-second-time-it-is-enqueued-and-also-called-test
  (let [current-run (atom 0)
        promises [(promise)
                  (promise)]
        f (fn []
            (deliver (get promises @current-run) :success)
            (swap! current-run inc))
        trigger (->trigger f)]
    (trigger)
    (trigger)
    (is (= [:success
            :success]
           (->> promises
                (mapv #(deref % 100 :not-run)))))))

(deftest three-triggers-at-the-same-time-should-result-in-two-runs-test
  (let [current-run (atom 0)
        block-tasks (promise)
        promises [(promise)
                  (promise)
                  (promise)]
        error? (atom nil)
        f (fn []
            (if (= :still-blocked (deref block-tasks 1000 :still-blocked))
              (swap! error? (constantly true))
              (do
                (deliver (get promises @current-run) :success)))
            (swap! current-run inc))
        {:keys [shutdown trigger]} (->trigger-component f)]
    (is (= :enqueued-successfully (trigger)))
    (is (= :enqueued-successfully (trigger)))
    (is (= :already-enqueued-again (trigger)))
    (deliver block-tasks :unblocked)
    (shutdown)
    (is (nil? @error?))
    (is (= [:success
            :success
            :not-run]
           (->> promises
                (mapv #(deref % 100 :not-run)))))))
