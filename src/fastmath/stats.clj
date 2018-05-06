(ns fastmath.stats
  "Statistics functions.

  * Descriptive statistics for sequence.
  * Correlation / covariance of two sequences.
  * Outliers

  All functions are backed by Apache Commons Math or SMILE libraries. All work with Clojure sequences.

  ### Descriptive statistics

  All in one function [[stats-map]] contains:

  * `:Size` - size of the samples, `(count ...)`
  * `:Min` - [[minimum]] value
  * `:Max` - [[maximum]] value
  * `:Mean` - [[mean]]/average
  * `:Median` - [[median]], see also: [[median-3]]
  * `:Mode` - [[mode]], see also: [[modes]]
  * `:Q1` - first quartile, use: [[percentile]], [[quartile]]
  * `:Q3` - third quartile, use: [[percentile]], [[quartile]]
  * `:Total` - [[sum]] of all samples
  * `:SD` - standard deviation of population, corrected sample standard deviation, use: [[population-stddev]]
  * `:MAD` - [[median-absolute-deviation]]
  * `:SEM` - standard error of mean
  * `:LAV` - lower adjacent value, use: [[adjacent-values]]
  * `:UAV` - upper adjacent value, use: [[adjacent-values]]
  * `:IQR` - interquartile range, `(- q3 q1)`
  * `:LOF` - lower outer fence, `(- q1 (* 3.0 iqr))`
  * `:UOF` - upper outer fence, `(+ q3 (* 3.0 iqr))`
  * `:LIF` - lower inner fence, `(- q1 (* 1.5 iqr))`
  * `:UIF` - upper inner fence, `(+ q3 (* 1.5 iqr))`
  * `:Outliers` - number of [[outliers]], samples which are outside outer fences
  * `:Kurtosis` - [[kurtosis]]
  * `:Skewness` - [[skewness]]
  * `:SecMoment` - second central moment, use: [[second-moment]]

  Note: [[percentile]] and [[quartile]] can have 10 different interpolation strategies. See [docs](http://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/stat/descriptive/rank/Percentile.html)

  ### Correlation / Covariance / Divergence

  * [[covariance]]
  * [[correlation]]
  * [[pearson-correlation]]
  * [[spearman-correlation]]
  * [[kendall-correlation]]
  * [[kullback-leibler-divergence]]
  * [[jensen-shannon-divergence]]

  ### Other

  Normalize samples to have mean=0 and standard deviation = 1 with [[standardize]].

  [[histogram]] to count samples in evenly spaced ranges."
  {:metadoc/categories {:stat "Descriptive statistics"
                        :corr "Correlation"}}
  (:require [fastmath.core :as m])
  (:import [org.apache.commons.math3.stat StatUtils]
           [org.apache.commons.math3.stat.descriptive.rank Percentile Percentile$EstimationType]
           [org.apache.commons.math3.stat.descriptive.moment Kurtosis SecondMoment Skewness]
           [org.apache.commons.math3.stat.correlation KendallsCorrelation SpearmansCorrelation PearsonsCorrelation]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)
(m/use-primitive-operators)

(defn mode
  "Find the value that appears most often in a dataset `vs`.

  See also [[modes]]."
  {:metadoc/categories #{:stat}}
  ^double [vs]
  (let [m (StatUtils/mode (m/seq->double-array vs))]
    (aget ^doubles m 0)))

(defn modes
  "Find the values that appears most often in a dataset `vs`.

  Returns sequence with all most appearing values in increasing order.

  See also [[mode]]."
  {:metadoc/categories #{:stat}}
  [vs]
  (seq ^doubles (StatUtils/mode (m/seq->double-array vs))))

(def
  ^{:metadoc/categories #{:stat}
    :docs "List of estimation strategies for [[percentile]]/[[quantile]] functions."}
  estimation-strategies-list {:legacy Percentile$EstimationType/LEGACY
                              :r1 Percentile$EstimationType/R_1
                              :r2 Percentile$EstimationType/R_2
                              :r3 Percentile$EstimationType/R_3
                              :r4 Percentile$EstimationType/R_4
                              :r5 Percentile$EstimationType/R_5
                              :r6 Percentile$EstimationType/R_6
                              :r7 Percentile$EstimationType/R_7
                              :r8 Percentile$EstimationType/R_8
                              :r9 Percentile$EstimationType/R_9})

(defn percentile
  "Calculate percentile of a `vs`.

  Percentile `p` is from range 0-100.
  
  See [docs](http://commons.apache.org/proper/commons-math/javadocs/api-3.4/org/apache/commons/math3/stat/descriptive/rank/Percentile.html).

  Optionally you can provide `estimation-strategy` to change interpolation methods for selecting values. Default is `:legacy`. See more [here](http://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/index.html)
  
  See also [[quantile]]."
  {:metadoc/categories #{:stat}}
  (^double [vs ^double p] 
   (StatUtils/percentile (m/seq->double-array vs) p))
  (^double [vs ^double p estimation-strategy]
   (let [^Percentile perc (.withEstimationType ^Percentile (Percentile.) (or (estimation-strategies-list estimation-strategy) Percentile$EstimationType/LEGACY))]
     (.evaluate perc (m/seq->double-array vs) p ))))

(defn quantile
  "Calculate quantile of a `vs`.

  Percentile `p` is from range 0.0-1.0.
  
  See [docs](http://commons.apache.org/proper/commons-math/javadocs/api-3.4/org/apache/commons/math3/stat/descriptive/rank/Percentile.html) for interpolation strategy.

  Optionally you can provide `estimation-strategy` to change interpolation methods for selecting values. Default is `:legacy`. See more [here](http://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/index.html)
  
  See also [[percentile]]."
  {:metadoc/categories #{:stat}}
  (^double [vs ^double p]
   (percentile vs (m/constrain (* p 100.0) 0.0 100.0)))
  (^double [vs ^double p estimation-strategy]
   (percentile vs (m/constrain (* p 100.0) 0.0 100.0) estimation-strategy)))

(defn median
  "Calculate median of a list. See [[median-3]]."
  {:metadoc/categories #{:stat}}
  ^double [vs]
  (percentile vs 50.0))

(defn median-3
  "Median of three values. See [[median]]."
  {:metadoc/categories #{:stat}}
  ^double [^double a ^double b ^double c]
  (m/max (m/min a b) (m/min (m/max a b) c)))

(defn mean
  "Calculate mean of a list"
  {:metadoc/categories #{:stat}}
  (^double [vs] (StatUtils/mean (m/seq->double-array vs))))

(defn population-variance
  "Calculate population variance of a list.

  See [[variance]]."
  {:metadoc/categories #{:stat}}
  (^double [vs]
   (StatUtils/populationVariance (m/seq->double-array vs)))
  (^double [vs ^double u]
   (StatUtils/populationVariance (m/seq->double-array vs) u)))

(defn variance
  "Calculate variance of a list.

  See [[population-variance]]."
  {:metadoc/categories #{:stat}}
  (^double [vs]
   (StatUtils/variance (m/seq->double-array vs)))
  (^double [vs ^double u]
   (StatUtils/variance (m/seq->double-array vs) u)))

(defn population-stddev
  "Calculate population standard deviation of a list.

  See [[stddev]]."
  {:metadoc/categories #{:stat}}
  (^double [vs]
   (m/sqrt (population-variance vs)))
  (^double [vs u]
   (m/sqrt (population-variance vs u))))

(defn stddev
  "Calculate population standard deviation of a list.

  See [[population-stddev]]."
  {:metadoc/categories #{:stat}}
  (^double [vs]
   (m/sqrt (variance vs)))
  (^double [vs u]
   (m/sqrt (variance vs u))))

(defn median-absolute-deviation 
  "Calculate MAD"
  {:metadoc/categories #{:stat}}
  ^double [vs]
  (smile.math.Math/mad (double-array vs)))

(defn adjacent-values
  "Lower and upper adjacent values (LAV and UAV).

  Let Q1 is 25-percentile and Q3 is 75-percentile. IQR is `(- Q3 Q1)`.

  * LAV is smallest value which is greater or equal to the LIF = `(- Q1 (* 1.5 IQR))`.
  * UAV is largest value which is lower or equal to the UIF = `(+ Q3 (* 1.5 IQR))`.

  Optional `estimation-strategy` argument can be set to change quantile calculations estimation type. See [[estimation-strategies]]."
  {:metadoc/categories #{:stat}}
  ([vs]
   (adjacent-values vs :legacy))
  ([vs estimation-strategy]
   (let [avs (m/seq->double-array vs)
         q1 (percentile avs 25.0 estimation-strategy)
         q3 (percentile avs 75.0 estimation-strategy)]
     (adjacent-values avs q1 q3)))
  ([vs ^double q1 ^double q3]
   (let [avs (double-array vs)
         iqr (* 1.5 (- q3 q1))
         lav-thr (- q1 iqr)
         uav-thr (+ q3 iqr)]
     (java.util.Arrays/sort avs)
     [(first (filter #(>= (double %) lav-thr) avs))
      (last (filter #(<= (double %) uav-thr) avs))])))

(defn outliers
  "Find outliers defined as values outside outer fences.

  Let Q1 is 25-percentile and Q3 is 75-percentile. IQR is `(- Q3 Q1)`.

  * LOF (Lower Outer Fence) equals `(- Q1 (* 3.0 IQR))`.
  * UOF (Upper Outer Fence) equals `(+ Q3 (* 3.0 IQR))`.

  Returns sequence.

  Optional `estimation-strategy` argument can be set to change quantile calculations estimation type. See [[estimation-strategies]]."
  {:metadoc/categories #{:stat}}
  ([vs]
   (outliers vs :legacy))
  ([vs estimation-strategy]
   (let [avs (m/seq->double-array vs)
         q1 (percentile avs 25.0 estimation-strategy)
         q3 (percentile avs 75.0 estimation-strategy)]
     (outliers avs q1 q3)))
  ([vs ^double q1 ^double q3]
   (let [avs (double-array vs)
         iqr (* 3.0 (- q3 q1))
         lof-thr (- q1 iqr)
         uof-thr (+ q3 iqr)]
     (java.util.Arrays/sort avs)
     (filter #(let [v (double %)]
                (bool-or (< v lof-thr)
                         (> v uof-thr))) avs))))

(defn minimum
  "Minimum value from sequence."
  {:metadoc/categories #{:stat}}
  ^double [vs]
  (if (= (type vs) m/double-array-type)
    (smile.math.Math/min ^doubles vs)
    (reduce clojure.core/min vs)))

(defn maximum
  "Maximum value from sequence."
  {:metadoc/categories #{:stat}}
  ^double [vs]
  (if (= (type vs) m/double-array-type)
    (smile.math.Math/max ^doubles vs)
    (reduce clojure.core/max vs)))

(defn extent
  "Return extent (min, max) values from sequence"
  {:metadoc/categories #{:stat}}
  [vs]
  (let [^double fv (first vs)]
    (reduce (fn [[^double mn ^double mx] ^double v]
              [(min mn v) (max mx v)]) [fv fv] (rest vs))))

(defn sum
  "Sum of all `vs` values."
  {:metadoc/categories #{:stat}}
  ^double [vs]
  (if (= (type vs) m/double-array-type)
    (smile.math.Math/sum ^doubles vs)
    (reduce clojure.core/+ vs)))

(defn kurtosis
  "Calculate kurtosis from sequence."
  {:metadoc/categories #{:stat}}
  ^double [vs]
  (let [^Kurtosis k (Kurtosis.)]
    (.evaluate k (m/seq->double-array vs))))

(defn second-moment
  "Calculate second moment from sequence.

  It's a sum of squared deviations from the sample mean"
  {:metadoc/categories #{:stat}}
  ^double [vs]
  (let [^SecondMoment k (SecondMoment.)]
    (.evaluate k (m/seq->double-array vs))))

(defn skewness
  "Calculate kurtosis from sequence."
  {:metadoc/categories #{:stat}}
  ^double [vs]
  (let [^Skewness k (Skewness.)]
    (.evaluate k (m/seq->double-array vs))))

(defn stats-map
  "Calculate several statistics from the list and return as map.

  Optional `estimation-strategy` argument can be set to change quantile calculations estimation type. See [[estimation-strategies]]."
  {:metadoc/categories #{:stat}}
  ([vs] (stats-map vs :legacy))
  ([vs estimation-strategy]
   (let [avs (m/seq->double-array vs)
         sz (alength avs)
         mn (smile.math.Math/min avs)
         mx (smile.math.Math/max avs)
         sm (smile.math.Math/sum avs)
         u (/ sm sz)
         mdn (median avs)
         q1 (percentile avs 25.0 estimation-strategy)
         q3 (percentile avs 75.0 estimation-strategy)
         iqr (- q3 q1)
         sd (population-stddev avs)
         mad (median-absolute-deviation avs)
         [lav uav] (adjacent-values avs q1 q3)
         outliers (count (outliers avs q1 q3))]
     {:Size sz
      :Min mn
      :Max mx
      :Mean u
      :Median mdn
      :Mode (mode avs)
      :Q1 q1
      :Q3 q3
      :Total sm
      :SD sd
      :MAD mad
      :SEM (/ sd (m/sqrt sz))
      :LAV lav
      :UAV uav
      :IQR iqr
      :LOF (- q1 (* 3.0 iqr))
      :UOF (+ q3 (* 3.0 iqr))
      :LIF (- q1 (* 1.5 iqr))
      :UIF (+ q3 (* 1.5 iqr))
      :Outliers outliers
      :Kurtosis (kurtosis avs)
      :Skewness (skewness avs)
      :SecMoment (second-moment avs)})))

(defn standardize
  "Normalize samples to have mean = 0 and stddev = 1."
  [vs]
  (seq ^doubles (StatUtils/normalize (m/seq->double-array vs))))

(defn covariance
  "Covariance of two sequences."
  {:metadoc/categories #{:corr}}
  [vs1 vs2]
  (smile.math.Math/cov (m/seq->double-array vs1) (m/seq->double-array vs2)))

(defn correlation
  "Correlation of two sequences."
  {:metadoc/categories #{:corr}}
  [vs1 vs2]
  (smile.math.Math/cor (m/seq->double-array vs1) (m/seq->double-array vs2)))

(defn spearman-correlation
  "Spearman's correlation of two sequences."
  {:metadoc/categories #{:corr}}
  [vs1 vs2]
  (.correlation ^SpearmansCorrelation (SpearmansCorrelation.) (m/seq->double-array vs1) (m/seq->double-array vs2)))

(defn pearson-correlation
  "Pearson's correlation of two sequences."
  {:metadoc/categories #{:corr}}
  [vs1 vs2]
  (.correlation ^PearsonsCorrelation (PearsonsCorrelation.) (m/seq->double-array vs1) (m/seq->double-array vs2)))

(defn kendall-correlation
  "Kendall's correlation of two sequences."
  {:metadoc/categories #{:corr}}
  [vs1 vs2]
  (.correlation ^KendallsCorrelation (KendallsCorrelation.) (m/seq->double-array vs1) (m/seq->double-array vs2)))

(defn kullback-leibler-divergence
  "Kullback-Leibler divergence of two sequences."
  {:metadoc/categories #{:corr}}
  [vs1 vs2]
  (smile.math.Math/KullbackLeiblerDivergence (m/seq->double-array vs1) (m/seq->double-array vs2)))

(defn jensen-shannon-divergence
  "Jensen-Shannon divergence of two sequences."
  {:metadoc/categories #{:corr}}
  [vs1 vs2]
  (smile.math.Math/JensenShannonDivergence (m/seq->double-array vs1) (m/seq->double-array vs2)))

;;

(defn histogram
  "Calculate histogram.

  Returns map with keys:

  * `:size` - number of bins
  * `:step` - distance between bins
  * `:bins` - list of pairs of range lower value and number of hits"
  {:metadoc/categories #{:stat}}
  ([vs bins] (histogram vs bins (extent vs)))
  ([vs ^long bins [^double mn ^double mx]]
   (let [diff (- mx mn)
         step (/ diff bins)
         search-array (double-array (map #(+ mn (* ^long % step)) (range bins)))
         buff (long-array bins)
         mx+ (m/next-double mx)]
     
     (doseq [^double v vs] ;; sprawdzić czy się mieści w domenie
       (when (<= mn v mx+)
         (let [b (java.util.Arrays/binarySearch ^doubles search-array v)
               ^int pos (if (neg? b) (m/abs (+ b 2)) b)]
           (fastmath.java.Array/inc ^longs buff pos))))

     {:size bins
      :step step
      :bins (map vector search-array buff)})))

;; TODO - replace with native (SMILE or Apache Commens) algorithms

(defn- closest-mean-fn
  [means]
  (fn [^double v] (reduce (partial min-key #(m/sq (- v ^double %))) means)))

;; `(k-means 4 '(1 2 3 -1 -1 2 -1 11 111)) => (-1.0 2.0 11.0 111.0)`
(defn k-means
  "k-means clustering"
  [^long k vs]
  (let [vs (map double vs)
        svs (set vs)]
    (if (> k (count svs))
      (sort svs)
      (loop [mns (sort (take k (shuffle svs)))
             pmns (repeat k Double/NaN)]
        (if (= mns pmns)
          mns
          (recur (sort (map mean (vals (group-by (closest-mean-fn mns) vs)))) mns))))))
