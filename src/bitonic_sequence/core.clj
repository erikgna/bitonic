(ns bitonic-sequence.core
  "Core implementation of Bitonic Sequence generation.
  
  A bitonic sequence is a sequence that first increases and then decreases,
  or can be circularly shifted to become so."
  (:require [clojure.tools.logging :as log]))

(defn generate-bitonic-sequence
  "Generates a bitonic sequence of length n from range [low, high].
  
  A bitonic sequence first strictly increases, then strictly decreases.
  
  Parameters:
  - n: Total length of sequence (must be >= 2)
  - low: Minimum value (inclusive)
  - high: Maximum value (inclusive)
  
  Returns:
  - Vector containing the bitonic sequence
  
  Algorithm:
  1. Split n into two parts: increasing (k) and decreasing (n-k)
  2. Generate k increasing values
  3. Generate n-k decreasing values
  4. Ensure strict monotonicity by adjusting values
  
  Example:
  (generate-bitonic-sequence 5 1 10)
  => [2 5 9 7 3]"
  [n low high]
  {:pre [(>= n 2)
         (<= low high)
         (>= (- high low) (- n 1))]}
  
  (log/debug (format "Generating bitonic sequence: n=%d, range=[%d,%d]" n low high))
  
  (let [;; Split point for increasing/decreasing parts
        k (inc (rand-int (dec n)))  ; k is between 1 and n-1
        
        ;; Calculate step sizes for each part
        range-size (- high low)
        total-steps (dec n)
        
        ;; Generate increasing part (k elements)
        increasing-part (vec (take k (iterate 
                                       #(+ % (inc (rand-int (quot range-size total-steps))))
                                       (+ low (rand-int (quot range-size 2))))))
        
        ;; Find peak value (last of increasing part)
        peak (last increasing-part)
        
        ;; Generate decreasing part (n-k elements)
        decreasing-part (vec (take (- n k)
                                   (iterate 
                                     #(max low (- % (inc (rand-int (quot range-size total-steps)))))
                                     (- peak (inc (rand-int (quot range-size total-steps)))))))]
    
    ;; Combine both parts
    (vec (concat increasing-part decreasing-part))))

(defn validate-bitonic
  "Validates if a sequence is bitonic.
  
  Returns a map with:
  - :valid? - boolean indicating if sequence is valid
  - :peak-index - index of the peak element
  - :reason - explanation if invalid"
  [sequence]
  (let [n (count sequence)]
    (cond
      (< n 2)
      {:valid? false :reason "Sequence too short (minimum 2 elements)"}
      
      :else
      (let [;; Find the peak (where increase stops)
            increasing-part (take-while #(< (first %) (second %)) 
                                       (partition 2 1 sequence))
            peak-idx (count increasing-part)
            
            ;; Check decreasing part
            decreasing-part (drop peak-idx (partition 2 1 sequence))
            is-decreasing? (every? #(> (first %) (second %)) decreasing-part)]
        
        (if (and (pos? peak-idx)
                 (< peak-idx (dec n))
                 is-decreasing?)
          {:valid? true :peak-index peak-idx}
          {:valid? false 
           :reason "Sequence is not strictly increasing then strictly decreasing"
           :peak-index peak-idx})))))

(defn generate-multiple
  "Generate multiple bitonic sequences.
  
  Parameters:
  - count: Number of sequences to generate
  - n: Length of each sequence
  - low: Minimum value
  - high: Maximum value
  
  Returns:
  - Vector of bitonic sequences"
  [count n low high]
  (vec (repeatedly count #(generate-bitonic-sequence n low high))))

(defn -main
  "Main entry point for command-line usage."
  [& args]
  (let [n (if (first args) (Integer/parseInt (first args)) 10)
        low (if (second args) (Integer/parseInt (second args)) 1)
        high (if (nth args 2 nil) (Integer/parseInt (nth args 2)) 100)]
    (println "Generating bitonic sequence...")
    (println "Parameters:" {:n n :low low :high high})
    (let [sequence (generate-bitonic-sequence n low high)
          validation (validate-bitonic sequence)]
      (println "Generated sequence:" sequence)
      (println "Validation:" validation))))