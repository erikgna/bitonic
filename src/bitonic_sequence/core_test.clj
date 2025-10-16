(ns bitonic-sequence.core-test
  "Unit tests for bitonic sequence generation."
  (:require [clojure.test :refer :all]
            [bitonic-sequence.core :refer :all]))

(deftest test-generate-bitonic-sequence
  (testing "Basic bitonic sequence generation"
    (let [seq (generate-bitonic-sequence 5 1 20)]
      (is (= 5 (count seq)) "Sequence should have correct length")
      (is (every? #(and (>= % 1) (<= % 20)) seq) "All values should be in range")))
  
  (testing "Minimum length sequence"
    (let [seq (generate-bitonic-sequence 2 1 10)]
      (is (= 2 (count seq)) "Should handle minimum length")
      (is (> (first seq) (second seq)) "Should be decreasing for n=2")))
  
  (testing "Large range"
    (let [seq (generate-bitonic-sequence 10 1 1000)]
      (is (= 10 (count seq)))
      (is (every? #(and (>= % 1) (<= % 1000)) seq))))
  
  (testing "Small range with adequate space"
    (let [seq (generate-bitonic-sequence 5 1 10)]
      (is (= 5 (count seq)))
      (is (every? #(and (>= % 1) (<= % 10)) seq)))))

(deftest test-validate-bitonic
  (testing "Valid increasing then decreasing sequence"
    (let [seq [1 3 5 7 4 2]
          result (validate-bitonic seq)]
      (is (:valid? result) "Should validate correct bitonic sequence")
      (is (= 3 (:peak-index result)) "Should find correct peak")))
  
  (testing "Invalid - only increasing"
    (let [seq [1 2 3 4 5]
          result (validate-bitonic seq)]
      (is (not (:valid? result)) "Should reject monotonically increasing")))
  
  (testing "Invalid - only decreasing"
    (let [seq [5 4 3 2 1]
          result (validate-bitonic seq)]
      (is (not (:valid? result)) "Should reject monotonically decreasing")))
  
  (testing "Too short sequence"
    (let [seq [1]
          result (validate-bitonic seq)]
      (is (not (:valid? result)) "Should reject single element")))
  
  (testing "Valid minimal bitonic"
    (let [seq [5 3]
          result (validate-bitonic seq)]
      (is (:valid? result) "Should accept two-element decreasing sequence"))))

(deftest test-generate-multiple
  (testing "Generate multiple sequences"
    (let [sequences (generate-multiple 5 10 1 100)]
      (is (= 5 (count sequences)) "Should generate correct number")
      (is (every? #(= 10 (count %)) sequences) "All should have correct length")
      (is (every? #(every? (fn [v] (and (>= v 1) (<= v 100))) %) sequences) 
          "All values should be in range"))))

(deftest test-preconditions
  (testing "Invalid parameters throw exceptions"
    (is (thrown? AssertionError (generate-bitonic-sequence 1 1 10)) 
        "Should reject n < 2")
    (is (thrown? AssertionError (generate-bitonic-sequence 5 10 5)) 
        "Should reject low > high")
    (is (thrown? AssertionError (generate-bitonic-sequence 10 1 5)) 
        "Should reject insufficient range")))

(deftest test-deterministic-properties
  (testing "Generated sequences maintain properties"
    (dotimes [_ 100]  ; Run 100 times to test randomness
      (let [seq (generate-bitonic-sequence 8 1 50)]
        (is (= 8 (count seq)) "Always correct length")
        (is (every? #(and (>= % 1) (<= % 50)) seq) "Always in range")))))

(defn run-tests []
  (run-tests 'bitonic-sequence.core-test))