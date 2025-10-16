(ns bitonic-sequence.api
  "REST API for bitonic sequence generation and management.
  
  Endpoints:
  - POST /api/generate - Generate new sequence
  - GET /api/sequence/:id - Retrieve sequence by ID
  - GET /api/sequences/recent - List recent sequences
  - POST /api/benchmark - Run benchmark
  - GET /api/benchmark/:id - Get benchmark results
  - GET /api/benchmarks/recent - List recent benchmarks
  - GET /api/stats - System statistics
  - GET /api/health - Health check"
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.util.response :refer [response status]]
            [ring.adapter.jetty :refer [run-jetty]]
            [bitonic-sequence.core :as bitonic]
            [bitonic-sequence.redis :as redis]
            [bitonic-sequence.benchmark :as bench]
            [clojure.tools.logging :as log])
  (:import [java.util UUID]))

;; Helper Functions

(defn generate-id
  "Generate unique ID for resources."
  []
  (str (UUID/randomUUID)))

(defn success-response
  "Create successful response with data."
  [data]
  (response {:status "success" :data data}))

(defn error-response
  "Create error response with message and status code."
  [message status-code]
  (-> (response {:status "error" :message message})
      (status status-code)))

;; API Handlers

(defn handle-generate
  "Handle POST /api/generate
  
  Request body:
  {
    \"n\": 10,           // Sequence length (required)
    \"low\": 1,          // Min value (required)
    \"high\": 100,       // Max value (required)
    \"store\": true      // Store in Redis (optional, default true)
  }
  
  Response:
  {
    \"status\": \"success\",
    \"data\": {
      \"id\": \"uuid\",
      \"sequence\": [1, 3, 5, 7, 4, 2],
      \"metadata\": {...},
      \"validation\": {...}
    }
  }"
  [request]
  (try
    (let [body (:body request)
          n (get body :n)
          low (get body :low)
          high (get body :high)
          should-store (get body :store true)]
      
      ;; Validate parameters
      (cond
        (nil? n) (error-response "Parameter 'n' is required" 400)
        (nil? low) (error-response "Parameter 'low' is required" 400)
        (nil? high) (error-response "Parameter 'high' is required" 400)
        (< n 2) (error-response "Parameter 'n' must be >= 2" 400)
        (> low high) (error-response "Parameter 'low' must be <= high" 400)
        (< (- high low) (- n 1)) (error-response "Range too small for given n" 400)
        
        :else
        (let [id (generate-id)
              sequence (bitonic/generate-bitonic-sequence n low high)
              validation (bitonic/validate-bitonic sequence)
              metadata {:n n :low low :high high}
              result {:id id
                     :sequence sequence
                     :metadata metadata
                     :validation validation}]
          
          ;; Store in Redis if requested
          (when should-store
            (redis/store-sequence id sequence metadata)
            (redis/increment-counter "sequences"))
          
          (log/info (format "Generated sequence %s (n=%d)" id n))
          (success-response result))))
    
    (catch Exception e
      (log/error e "Error generating sequence")
      (error-response (.getMessage e) 500))))

(defn handle-get-sequence
  "Handle GET /api/sequence/:id"
  [id]
  (try
    (if-let [sequence (redis/get-sequence id)]
      (success-response sequence)
      (error-response "Sequence not found" 404))
    (catch Exception e
      (log/error e "Error retrieving sequence")
      (error-response (.getMessage e) 500))))

(defn handle-recent-sequences
  "Handle GET /api/sequences/recent?limit=10"
  [request]
  (try
    (let [limit (Integer/parseInt (get-in request [:params :limit] "10"))
          ids (redis/get-recent-sequences limit)
          sequences (mapv redis/get-sequence ids)]
      (success-response {:sequences (remove nil? sequences)
                        :count (count sequences)}))
    (catch Exception e
      (log/error e "Error retrieving recent sequences")
      (error-response (.getMessage e) 500))))

(defn handle-benchmark
  "Handle POST /api/benchmark
  
  Request body:
  {
    \"type\": \"single\",     // or \"multiple\"
    \"n\": 100,
    \"low\": 1,
    \"high\": 1000,
    \"count\": 10           // for type=multiple
  }"
  [request]
  (try
    (let [body (:body request)
          bench-type (get body :type "single")
          n (get body :n 100)
          low (get body :low 1)
          high (get body :high 1000)
          count (get body :count 10)
          id (generate-id)]
      
      (log/info (format "Running benchmark %s (type=%s, n=%d)" id bench-type n))
      
      (let [results (case bench-type
                     "single" (bench/benchmark-single-generation n low high)
                     "multiple" (bench/benchmark-multiple-generation count n low high)
                     "validation" (bench/benchmark-validation n low high)
                     (bench/benchmark-single-generation n low high))
            
            result-data {:id id
                        :type bench-type
                        :parameters {:n n :low low :high high :count count}
                        :results results}]
        
        ;; Store in Redis
        (redis/store-benchmark-result id result-data)
        (redis/increment-counter "benchmarks")
        
        (success-response result-data)))
    
    (catch Exception e
      (log/error e "Error running benchmark")
      (error-response (.getMessage e) 500))))

(defn handle-get-benchmark
  "Handle GET /api/benchmark/:id"
  [id]
  (try
    (if-let [benchmark (redis/get-benchmark-result id)]
      (success-response benchmark)
      (error-response "Benchmark not found" 404))
    (catch Exception e
      (log/error e "Error retrieving benchmark")
      (error-response (.getMessage e) 500))))

(defn handle-recent-benchmarks
  "Handle GET /api/benchmarks/recent?limit=10"
  [request]
  (try
    (let [limit (Integer/parseInt (get-in request [:params :limit] "10"))
          benchmarks (redis/get-recent-benchmarks limit)]
      (success-response {:benchmarks (remove nil? benchmarks)
                        :count (count benchmarks)}))
    (catch Exception e
      (log/error e "Error retrieving recent benchmarks")
      (error-response (.getMessage e) 500))))

(defn handle-stats
  "Handle GET /api/stats"
  []
  (try
    (let [stats (redis/get-stats)]
      (success-response stats))
    (catch Exception e
      (log/error e "Error retrieving stats")
      (error-response (.getMessage e) 500))))

(defn handle-health
  "Handle GET /api/health"
  []
  (let [redis-health (redis/health-check)]
    (if (= (:status redis-health) :ok)
      (success-response {:service "bitonic-sequence"
                        :status "healthy"
                        :redis "connected"})
      (error-response "Redis connection failed" 503))))

;; Routes Definition

(defroutes app-routes
  (GET "/" [] 
    (response {:message "Bitonic Sequence API"
               :version "0.1.0"
               :endpoints ["/api/generate"
                          "/api/sequence/:id"
                          "/api/sequences/recent"
                          "/api/benchmark"
                          "/api/benchmark/:id"
                          "/api/benchmarks/recent"
                          "/api/stats"
                          "/api/health"]}))
  
  (POST "/api/generate" request (handle-generate request))
  (GET "/api/sequence/:id" [id] (handle-get-sequence id))
  (GET "/api/sequences/recent" request (handle-recent-sequences request))
  
  (POST "/api/benchmark" request (handle-benchmark request))
  (GET "/api/benchmark/:id" [id] (handle-get-benchmark id))
  (GET "/api/benchmarks/recent" request (handle-recent-benchmarks request))
  
  (GET "/api/stats" [] (handle-stats))
  (GET "/api/health" [] (handle-health))
  
  (route/not-found (error-response "Route not found" 404)))

;; Application with Middleware

(def app
  "Application with middleware stack."
  (-> app-routes
      (wrap-keyword-params)
      (wrap-json-body {:keywords? true})
      (wrap-json-response)
      (wrap-params)))

;; Server Management

(defonce server (atom nil))

(defn start-server
  "Start the API server.
  
  Parameters:
  - port: Port number (default 3000)
  
  Returns:
  - Server instance"
  [& [port]]
  (let [port (or port 3000)]
    (log/info (format "Starting server on port %d" port))
    (reset! server
            (run-jetty app {:port port
                           :join? false}))
    (println (format "✓ Server started on http://localhost:%d" port))
    @server))

(defn stop-server
  "Stop the API server."
  []
  (when @server
    (log/info "Stopping server")
    (.stop @server)
    (reset! server nil)
    (println "✓ Server stopped")))

(defn -main
  "Start server from command line."
  [& args]
  (let [port (if (first args) 
               (Integer/parseInt (first args)) 
               3000)]
    (start-server port)
    (println "\nAPI Documentation:")
    (println "==================")
    
    (println "POST /api/generate - Generate bitonic sequence")
    (println "GET  /api/sequence/:id - Get sequence by ID")
    (println "GET  /api/sequences/recent - List recent sequences")
    (println "POST /api/benchmark - Run performance benchmark")
    (println "GET  /api/benchmark/:id - Get benchmark results")
    (println "GET  /api/benchmarks/recent - List recent benchmarks")
    (println "GET  /api/stats - System statistics")
    (println "GET  /api/health - Health check")
    (println "\nPress Ctrl+C to stop")))