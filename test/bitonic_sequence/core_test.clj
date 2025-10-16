(ns test.bitonic_project.core_test
  (:require [clojure.test :refer :all]
            [bitonic_sequence.core :refer [generate-bitonic max-bitonic-length]]))

(deftest max-bitonic-length-test
  (testing "Calculate max bitonic length"
    (is (= 7 (max-bitonic-length 3 6)))
    (is (= 9 (max-bitonic-length 2 6)))
    (is (= 5 (max-bitonic-length 8 10)))))

(deftest generate-bitonic-test
  (testing "Example 1: asymmetric sequence"
    (is (= [9 10 9 8 7] (generate-bitonic 5 3 10))))
  
  (testing "Example 2: fully symmetric sequence"
    (is (= [2 3 4 5 4 3 2] (generate-bitonic 7 2 5))))
  
  (testing "Impossible: n too large for range"
    (is (= [-1] (generate-bitonic 8 2 5))))
  
  (testing "Impossible: n exceeds max length"
    (is (= [-1] (generate-bitonic 6 8 10))))
  
  (testing "Short sequence with 3 elements"
    (is (= [9 10 9] (generate-bitonic 3 8 10))))
  
  (testing "Asymmetric sequence"
    (is (= [8 9 10 9 8 7] (generate-bitonic 6 7 10))))
  
  (testing "Edge case: minimum n=3"
    (is (= [4 5 4] (generate-bitonic 3 3 5))))
  
  (testing "Properties: result has correct length"
    (let [result (generate-bitonic 5 1 10)]
      (is (= 5 (count result)))))
  
  (testing "Properties: all elements in range"
    (let [result (generate-bitonic 5 3 10)]
      (when (not= result [-1])
        (is (every? #(and (>= % 3) (<= % 10)) result)))))
  
  (testing "Properties: bitonic property holds"
    (let [result (generate-bitonic 7 2 5)]
      (when (not= result [-1])
        (let [peak-idx (.indexOf result (apply max result))
              increasing (take (inc peak-idx) result)
              decreasing (drop peak-idx result)]
          (is (apply < increasing))
          (is (apply > decreasing)))))))