(ns bitonic_sequence.core
  (:require [org.httpkit.server :as http-kit]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [taoensso.carmine :as car]
            [ring.middleware.params :refer [wrap-params]]
            [cheshire.core :as json])
  (:gen-class))

; Redis connection configuration
(def redis-conn {:pool {} :spec {:uri "redis://redis:6379"}})

(defmacro wcar* [& body] `(car/wcar redis-conn ~@body))

(defn max-bitonic-length
  "Calculate maximum possible bitonic sequence length for range [l, r]"
  [l r]
  (inc (* 2 (- r l))))

(defn generate-bitonic
  "Generate bitonic sequence of length n using range [l, r].
   Returns [-1] if impossible."
  [n l r]
  (if (> n (max-bitonic-length l r))
    [-1]
    (loop [result [(dec r)]
           current r
           phase :decreasing]
      (if (= (count result) n)
        result
        (case phase
          :decreasing
          (if (> current l)
            (recur (conj result current) (inc current) :decreasing)
            (recur result (- r 2) :increasing))
          
          :increasing
          (if (and (>= current l) (< (count result) n))
            (recur (into [current] result) (dec current) :increasing)
            result))))))

(defn- respond
  "Create HTTP response with JSON body"
  [status body]
  {:status status
   :headers {"Content-Type" "application/json; charset=utf-8"
             "Cache-Control" "public, max-age=3600"}
   :body (json/generate-string body)})

(defn- parse-long-safe
  "Safely parse string to Long, returns nil on error"
  [s]
  (try
    (Long/parseLong s)
    (catch NumberFormatException _ nil)))

(defn- validate-params
  "Validate bitonic parameters. Returns [valid? error-msg]"
  [n l r]
  (cond
    (not (and n l r))
    [false "Missing required parameters: n, l, r"]
    
    (<= n 2)
    [false "Parameter n must be greater than 2"]
    
    (>= l r)
    [false "Parameter l must be less than r"]
    
    :else
    [true nil]))

(defn- get-cached-result
  "Attempt to fetch result from Redis cache"
  [redis-key]
  (try
    (when-let [cached (wcar* (car/get redis-key))]
      (json/parse-string cached))
    (catch Exception e
      (println "Redis fetch error:" (.getMessage e))
      nil)))

(defn- cache-result
  "Store result in Redis with 1-hour TTL"
  [redis-key result]
  (try
    (wcar* (car/setex redis-key 3600 (json/generate-string result)))
    (catch Exception e
      (println "Redis cache error:" (.getMessage e)))))

(defn bitonic-handler
  "Handle /bitonic endpoint with caching"
  [req]
  (let [params (:query-params req)
        n (some-> (get params "n") parse-long-safe)
        l (some-> (get params "l") parse-long-safe)
        r (some-> (get params "r") parse-long-safe)
        [valid? error-msg] (validate-params n l r)]
    
    (if-not valid?
      (respond 400 {:error error-msg})
      
      (let [redis-key (str "bitonic:" n ":" l ":" r)]
        (if-let [cached (get-cached-result redis-key)]
          (respond 200 {:n n :l l :r r :result cached :cached true})
          
          (let [result (generate-bitonic n l r)]
            (cache-result redis-key result)
            (respond 200 {:n n :l l :r r :result result :cached false})))))))

(defroutes app-routes
  (GET "/bitonic" [] bitonic-handler)
  (GET "/health" [] (respond 200 {:status "healthy"}))
  (route/not-found (respond 404 {:error "Not Found"})))

(def app (wrap-params app-routes))

(defn -main [& args]
  (let [port 3000]
    (println (str "🚀 Server starting on http://localhost:" port))
    (http-kit/run-server app {:port port})))