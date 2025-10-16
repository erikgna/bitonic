(ns bitonic-sequence.redis
  "Redis integration for storing bitonic sequences and benchmark results.
  
  Uses Carmine library for Redis operations."
  (:require [taoensso.carmine :as car]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]))

;; Redis connection configuration
(def redis-conn 
  "Redis connection specification.
  Can be configured via environment variables."
  {:pool {}
   :spec {:host (or (System/getenv "REDIS_HOST") "localhost")
          :port (Integer/parseInt (or (System/getenv "REDIS_PORT") "6379"))
          :password (System/getenv "REDIS_PASSWORD")
          :timeout-ms 4000}})

(defmacro wcar* 
  "Wrapper macro for Redis commands."
  [& body]
  `(car/wcar redis-conn ~@body))

;; Sequence Storage

(defn store-sequence
  "Store a bitonic sequence in Redis.
  
  Parameters:
  - id: Unique identifier for the sequence
  - sequence: The bitonic sequence vector
  - metadata: Optional map with additional info (n, low, high, timestamp)
  
  Returns:
  - :ok on success
  - :error with message on failure"
  [id sequence metadata]
  (try
    (let [data {:id id
                :sequence sequence
                :metadata (merge metadata {:timestamp (System/currentTimeMillis)})
                :validated (bitonic-sequence.core/validate-bitonic sequence)}
          json-data (json/generate-string data)]
      (wcar* 
        (car/setex (str "sequence:" id) 3600 json-data)  ; Expire after 1 hour
        (car/lpush "sequences:recent" id)
        (car/ltrim "sequences:recent" 0 99))  ; Keep only last 100
      (log/info (format "Stored sequence %s" id))
      {:status :ok :id id})
    (catch Exception e
      (log/error e "Failed to store sequence")
      {:status :error :message (.getMessage e)})))

(defn get-sequence
  "Retrieve a stored sequence by ID.
  
  Returns:
  - Map with sequence data if found
  - nil if not found"
  [id]
  (try
    (let [json-data (wcar* (car/get (str "sequence:" id)))]
      (when json-data
        (json/parse-string json-data true)))
    (catch Exception e
      (log/error e "Failed to retrieve sequence")
      nil)))

(defn get-recent-sequences
  "Get list of recently stored sequence IDs.
  
  Parameters:
  - limit: Maximum number to return (default 10)
  
  Returns:
  - Vector of sequence IDs"
  [limit]
  (try
    (wcar* (car/lrange "sequences:recent" 0 (dec limit)))
    (catch Exception e
      (log/error e "Failed to get recent sequences")
      [])))

;; Benchmark Results Storage

(defn store-benchmark-result
  "Store benchmark results in Redis.
  
  Parameters:
  - benchmark-id: Unique identifier
  - results: Map containing benchmark data
  
  The data is stored with automatic expiration."
  [benchmark-id results]
  (try
    (let [data (merge results
                     {:id benchmark-id
                      :timestamp (System/currentTimeMillis)})
          json-data (json/generate-string data)]
      (wcar*
        (car/setex (str "benchmark:" benchmark-id) 86400 json-data)  ; 24 hours
        (car/zadd "benchmarks:index" 
                  (System/currentTimeMillis) 
                  benchmark-id))
      (log/info (format "Stored benchmark result %s" benchmark-id))
      {:status :ok :id benchmark-id})
    (catch Exception e
      (log/error e "Failed to store benchmark")
      {:status :error :message (.getMessage e)})))

(defn get-benchmark-result
  "Retrieve benchmark results by ID."
  [benchmark-id]
  (try
    (let [json-data (wcar* (car/get (str "benchmark:" benchmark-id)))]
      (when json-data
        (json/parse-string json-data true)))
    (catch Exception e
      (log/error e "Failed to retrieve benchmark")
      nil)))

(defn get-recent-benchmarks
  "Get recent benchmark results.
  
  Parameters:
  - limit: Maximum number to return
  
  Returns:
  - Vector of benchmark data maps"
  [limit]
  (try
    (let [ids (wcar* (car/zrevrange "benchmarks:index" 0 (dec limit)))]
      (mapv get-benchmark-result ids))
    (catch Exception e
      (log/error e "Failed to get recent benchmarks")
      [])))

;; Statistics and Aggregation

(defn increment-counter
  "Increment a counter in Redis.
  
  Useful for tracking number of sequences generated, API calls, etc."
  [counter-name]
  (wcar* (car/incr (str "counter:" counter-name))))

(defn get-counter
  "Get current value of a counter."
  [counter-name]
  (or (wcar* (car/get (str "counter:" counter-name))) "0"))

(defn get-stats
  "Get overall system statistics from Redis.
  
  Returns map with:
  - :total-sequences - Total sequences generated
  - :total-benchmarks - Total benchmarks run
  - :recent-sequences - Count of sequences in recent list
  - :recent-benchmarks - Count of benchmarks in index"
  []
  (try
    {:total-sequences (get-counter "sequences")
     :total-benchmarks (get-counter "benchmarks")
     :recent-sequences-count (wcar* (car/llen "sequences:recent"))
     :recent-benchmarks-count (wcar* (car/zcard "benchmarks:index"))}
    (catch Exception e
      (log/error e "Failed to get stats")
      {})))

;; Health Check

(defn health-check
  "Check if Redis connection is working.
  
  Returns:
  - {:status :ok} if connected
  - {:status :error :message ...} if failed"
  []
  (try
    (wcar* (car/ping))
    {:status :ok :message "Redis connected"}
    (catch Exception e
      {:status :error :message (.getMessage e)})))