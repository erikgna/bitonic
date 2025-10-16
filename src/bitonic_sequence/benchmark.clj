(ns bitonic-sequence.benchmark
  "Performance benchmarks for bitonic sequence generation.
  
  Uses Criterium library for accurate benchmarking with:
  - JVM warmup
  - Statistical analysis
  - Outlier detection"
  (:require [criterium.core :as crit]
            [bitonic-sequence.core :as bitonic]
            [clojure.tools.logging :as log]))

(defn benchmark-single-generation
  "Benchmark generating a single bitonic sequence.
  
  Parameters:
  - n: Sequence length
  - low: Minimum value
  - high: Maximum value
  
  Returns map with:
  - :mean - Average execution time
  - :variance - Variance in execution time
  - :samples - Number of samples taken"
  [n low high]
  (println (format "\n=== Benchmarking single generation (n=%d) ===" n))
  (let [result (crit/benchmark 
                 (bitonic/generate-bitonic-sequence n low high)
                 {})]
    (println "Results:")
    (println (format "  Mean execution time: %.3f ms" 
                     (/ (first (:mean result)) 1000000.0)))
    (println (format "  Standard deviation: %.3f ms"
                     (/ (Math/sqrt (first (:variance result))) 1000000.0)))
    {:mean (first (:mean result))
     :variance (first (:variance result))
     :samples (count (:results result))}))

(defn benchmark-multiple-generation
  "Benchmark generating multiple sequences.
  
  Parameters:
  - count: Number of sequences
  - n: Length per sequence
  - low: Minimum value
  - high: Maximum value"
  [count n low high]
  (println (format "\n=== Benchmarking multiple generation (count=%d, n=%d) ===" count n))
  (let [result (crit/benchmark
                 (bitonic/generate-multiple count n low high)
                 {})]
    (println "Results:")
    (println (format "  Mean execution time: %.3f ms"
                     (/ (first (:mean result)) 1000000.0)))
    (println (format "  Standard deviation: %.3f ms"
                     (/ (Math/sqrt (first (:variance result))) 1000000.0)))
    {:mean (first (:mean result))
     :variance (first (:variance result))
     :samples (count (:results result))}))

(defn benchmark-validation
  "Benchmark sequence validation."
  [n low high]
  (println (format "\n=== Benchmarking validation (n=%d) ===" n))
  (let [seq (bitonic/generate-bitonic-sequence n low high)
        result (crit/benchmark
                 (bitonic/validate-bitonic seq)
                 {})]
    (println "Results:")
    (println (format "  Mean execution time: %.3f µs"
                     (/ (first (:mean result)) 1000.0)))
    {:mean (first (:mean result))
     :variance (first (:variance result))
     :samples (count (:results result))}))

(defn quick-benchmark
  "Quick benchmark using time measurement (no warmup).
  Useful for rapid testing during development."
  [f description]
  (println (format "\n--- Quick benchmark: %s ---" description))
  (let [start (System/nanoTime)
        _ (dotimes [_ 1000] (f))
        end (System/nanoTime)
        elapsed-ms (/ (- end start) 1000000.0)]
    (println (format "  1000 iterations: %.2f ms (%.3f ms per iteration)"
                     elapsed-ms
                     (/ elapsed-ms 1000.0)))
    elapsed-ms))

(defn scaling-benchmark
  "Benchmark how performance scales with input size."
  []
  (println "\n=== SCALING BENCHMARK ===")
  (println "Testing performance across different sequence lengths...\n")
  
  (doseq [n [10 50 100 500 1000]]
    (println (format "Testing n=%d..." n))
    (let [start (System/nanoTime)
          _ (dotimes [_ 100] (bitonic/generate-bitonic-sequence n 1 10000))
          end (System/nanoTime)
          avg-ms (/ (- end start) 100000000.0)]
      (println (format "  Average time: %.3f ms" avg-ms))
      (println (format "  Time per element: %.3f µs" (/ (* avg-ms 1000) n)))))
  
  (println "\nScaling analysis complete."))

(defn comprehensive-benchmark
  "Run all benchmarks with standard parameters."
  []
  (println "╔════════════════════════════════════════════════════════╗")
  (println "║   BITONIC SEQUENCE - COMPREHENSIVE BENCHMARK SUITE    ║")
  (println "╚════════════════════════════════════════════════════════╝")
  
  ;; Quick benchmarks for rapid feedback
  (quick-benchmark 
    #(bitonic/generate-bitonic-sequence 10 1 100)
    "Small sequence (n=10)")
  
  (quick-benchmark
    #(bitonic/generate-bitonic-sequence 100 1 1000)
    "Medium sequence (n=100)")
  
  (quick-benchmark
    #(bitonic/generate-bitonic-sequence 1000 1 10000)
    "Large sequence (n=1000)")
  
  ;; Detailed benchmarks with Criterium
  (benchmark-single-generation 10 1 100)
  (benchmark-single-generation 100 1 1000)
  (benchmark-single-generation 1000 1 10000)
  
  (benchmark-multiple-generation 10 10 1 100)
  (benchmark-multiple-generation 100 10 1 100)
  
  (benchmark-validation 100 1 1000)
  
  ;; Scaling analysis
  (scaling-benchmark)
  
  (println "\n✓ Benchmark suite complete!"))

(defn -main
  "Run benchmark suite from command line."
  [& args]
  (comprehensive-benchmark))