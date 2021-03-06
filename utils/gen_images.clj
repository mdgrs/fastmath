(ns fastmath.utils.gen-images
  "Generate images from examples attached to metadata."
  (:require [fastmath.core :as m]
            [fastmath.complex :as c]
            [fastmath.kernel :as k]
            [fastmath.random :as r]
            [fastmath.vector :as v]
            [fastmath.transform :as t]
            [fastmath.interpolation :as i]
            [fastmath.distance :as d]
            [fastmath.fields :as fields]
            [fastmath.gp :as gp]
            [fastmath.easings :as e]
            [fastmath.grid :as g]
            [fastmath.optimization :as opt]
            [fastmath.signal :as sig]
            [cljplot.build :as b]
            [cljplot.core :as cljplot]
            [clojure2d.color :as clr]
            [clojure2d.core :as c2d]
            [clojure2d.extra.utils :as utls]))

(r/set-seed! r/default-rng 1234)

(def ^:const bg-color 0x30426a)
(def ^:const fg-color 0xb2bfdc)

(defn save-chart
  "Save chart under given path and name"
  ([c prefix n suff]
   (cljplot/save c (str "docs/images/" prefix "/" n suff)))
  ([c prefix n]
   (save-chart c prefix n ".jpg")))

(defn symbol->fn
  "Convert symbol to function"
  [s] (eval `(fn [x#] (~s x#))))

(defn symbol->fn2
  "Convert symbol to 2 parameter function"
  [s] (eval `(fn [x# y#] (~s x# y#))))

;; core

(defn function-chart
  ([f] (function-chart f nil))
  ([f d]
   (cljplot/xy-chart {:width 400 :height 200 :background bg-color}
                     (b/series [:vline 0 {:color [60 100 120]}]
                               [:hline 0 {:color [60 100 120]}]
                               [:function f {:domain (or d [-3.1 3.1])
                                             :color :white
                                             :samples 300}])
                     (b/update-scale :x :ticks 5)
                     (b/update-scale :y :ticks 5)
                     (b/add-axes :bottom {:ticks {:color fg-color}
                                          :line {:color fg-color}})
                     (b/add-axes :left {:ticks {:color fg-color}
                                        :line {:color fg-color}}))))

(defn function2d-chart
  [f d]
  (cljplot/xy-chart {:width 300 :height 300 :background bg-color}
                    (b/series [:function-2d f (merge d {:gradient (clr/gradient [bg-color :white])})])
                    (b/update-scale :x :ticks 5)
                    (b/update-scale :y :ticks 5)
                    (b/add-axes :bottom {:ticks {:color fg-color}
                                         :line {:color fg-color}})
                    (b/add-axes :left {:ticks {:color fg-color}
                                       :line {:color fg-color}})))


(doseq [f `(m/abs m/acos m/acosh m/acot m/acoth m/acsc m/acsch m/asec m/asech m/asin m/asinh m/atan m/atanh
                  m/cbrt m/ceil m/cos m/cosh m/cot m/coth m/csc m/csch
                  [m/digamma [0 5]]
                  m/erf m/erfc m/exp m/expm1
                  m/floor m/frac
                  [m/gamma [0 5]]
                  m/haversine [m/high-2-exp [0.01 10]]
                  m/iabs m/inv-erf m/inv-erfc [m/inv-gamma-1pm1 [-0.5 1.5]] m/itrunc
                  m/ln m/log m/log-gamma [m/log-gamma-1p [-0.5 1.5]] m/log10 m/log1p m/log1pexp m/log2 [m/low-2-exp [0.01 10]]
                  m/logit
                  m/pow3 m/pow2 
                  [m/qsqrt [0.01 5]] m/qsin [m/qlog [0.01 5]] m/qexp m/qcos
                  [m/rqsqrt [0.01 5]] m/round-up-pow2 m/round m/rint 
                  m/sin m/sqrt m/sq m/cb m/sinh m/sinc m/signum m/sigmoid m/sgn m/sfrac m/sech m/sec m/safe-sqrt 
                  m/trunc [m/trigamma [0 2]] m/tanh m/tan)]
  (let [[f d] (if (vector? f) f [f nil])]
    (save-chart (function-chart (symbol->fn f) d) "m" (name f) ".png")))

(doseq [f `([m/bessel-j {:x [0 5] :y [0 5]}]
            [m/log-beta {:x [0 5] :y [0 5]}]
            m/atan2 m/hypot m/hypot-sqrt)]
  (let [[f d] (if (vector? f) f [f nil])]
    (save-chart (function2d-chart (symbol->fn2 f) (or d {})) "m" (name f) ".png")))


(doseq [i `(m/cos-interpolation m/lerp m/wrap m/smooth-interpolation m/quad-interpolation)]
  (save-chart (function-chart (eval `(fn [x#] (~i 0.0 1.0 x#))) [0 1]) "m" (name i) ".png"))


(save-chart (function2d-chart (fn [x y] (m/erf x y)) {}) "m" "erf2" ".png")

;; random

(defn scatter-chart
  [d]
  (cljplot/xy-chart {:width 300 :height 300 :background bg-color}
                    (b/series [:scatter d {:color fg-color}])
                    (b/update-scale :x :ticks 5)
                    (b/update-scale :y :ticks 5)
                    (b/add-axes :bottom {:ticks {:color fg-color}
                                         :line {:color fg-color}})
                    (b/add-axes :left {:ticks {:color fg-color}
                                       :line {:color fg-color}})))

(doseq [nm r/sequence-generators-list]
  (save-chart (scatter-chart (take 500 (r/sequence-generator nm 2))) "r" (name nm) ".jpg"))

(doseq [nm r/sequence-generators-list]
  (save-chart (scatter-chart (take 500 (r/jittered-sequence-generator nm 2 0.5))) "r" (str "j" (name nm)) ".jpg"))

;; noise

(doseq [[nm noise-fn] [[:noise r/noise]
                       [:vnoise r/vnoise]
                       [:simplex r/simplex]
                       [:random1 (r/random-noise-fn {:seed 1234 :generator :fbm :interpolation :linear})]
                       [:random2 (r/random-noise-fn {:seed 1234 :generator :billow})]
                       [:random3 (r/random-noise-fn {:seed 1234 :generator :ridgemulti :octaves 2})]
                       [:fbm (r/fbm-noise {:seed 1234})]
                       [:billow (r/billow-noise {:seed 1234})]
                       [:ridgedmulti (r/ridgedmulti-noise {:seed 1234})]
                       [:single (r/single-noise {:seed 1234})]]]
  (save-chart (function2d-chart noise-fn {:x [-2 2] :y [-2 2]}) "n" (name nm) ".jpg"))

(save-chart (function2d-chart r/discrete-noise {:x [-10 10] :y [-10 10]}) "n" "discrete_noise" ".jpg")
(save-chart (function2d-chart (r/warp-noise-fn) {:x [-2 2] :y [-2 2]}) "n" "warp" ".jpg")

;; distributions

(def pal (reverse (clr/palette :category20)))

(defn distr-chart
  ([f d pnames ps] (distr-chart f d pnames ps nil))
  ([f d pnames ps domain]
   (let [ff (case f
              :cdf r/cdf
              :pdf r/pdf
              :icdf r/icdf)
         fs (map-indexed (fn [id p]
                           [:function
                            (partial ff (r/distribution d (zipmap pnames p)))
                            {:domain (or domain [-3.1 3.1])
                             :color (nth pal id)
                             :samples 300}]) ps)]
     (cljplot/xy-chart {:width 400 :height 230 :background bg-color}
                       (-> (b/series [:vline 0 {:color [60 100 120]}]
                                     [:hline 0 {:color [60 100 120]}])
                           (b/add-series fs))
                       (b/update-scale :x :ticks 5)
                       (b/update-scale :y :ticks 5)
                       (b/add-axes :bottom {:ticks {:color fg-color}
                                            :line {:color fg-color}})
                       (b/add-axes :left {:ticks {:color fg-color}
                                          :line {:color fg-color}})
                       (b/add-label :bottom (name d) {:color fg-color})
                       (b/add-label :left (name f) {:color fg-color})))))

(defn save-distr-charts
  ([distr params ps] (save-distr-charts distr params ps nil))
  ([distr params ps domain]
   (binding [c2d/*jpeg-image-quality* 0.75]
     (save-chart (distr-chart :pdf distr params ps domain) "d" (str "pdf-" (name distr)) ".jpg")
     (save-chart (distr-chart :cdf distr params ps domain) "d" (str "cdf-" (name distr)) ".jpg")
     (save-chart (distr-chart :icdf distr params ps [0 0.99]) "d" (str "icdf-" (name distr)) ".jpg"))))

#_(defn show-distr-charts
    ([distr params ps] (show-distr-charts distr params ps nil))
    ([distr params ps domain]
     (binding [c2d/*jpeg-image-quality* 0.75]
       (cljplot/show (pdf-chart :pdf distr params ps domain))
       (cljplot/show (pdf-chart :cdf distr params ps domain))
       (cljplot/show (pdf-chart :icdf distr params ps [0 0.99])))))

(def empirical-data (sort (repeatedly 1000 #(r/grand (r/randval 0.3 -1 1) 1))))

(save-distr-charts :beta [:alpha :beta] [[5 2] [2 5] [0.5 0.5] [5 5]] [0.01 0.99])
(save-distr-charts :cauchy [:median :scale] [[0 1] [1 2] [-1 0.5]])
(save-distr-charts :chi-squared [:degrees-of-freedom] [[2] [3] [5]] [0 5])
(save-distr-charts :exponential [:mean] [[0.1] [0.5] [1] [2]] [0 1])
(save-distr-charts :f [:numerator-degrees-of-freedom :denominator-degrees-of-freedom] [[1 1] [2 2] [5 2] [100 100]] [0 5])
(save-distr-charts :gamma [:shape :scale] [[2 2] [10 0.5] [5 0.5]] [0 10])
(save-distr-charts :gumbel [:mu :beta] [[1 2] [0.5 1] [3 4]] [-5 10])
(save-distr-charts :laplace [:mu :beta] [[-1 2] [0.5 0.5] [0 1]])
(save-distr-charts :levy [:mu :c] [[-1 2] [0.5 0.5] [0 1]] [-1 5])
(save-distr-charts :logistic [:mu :s] [[-1 2] [0.5 0.5] [0 1]])
(save-distr-charts :log-normal [:scale :shape] [[1 1] [0.5 0.5] [2 2]] [0 5])
(save-distr-charts :nakagami [:mu :omega] [[1 1] [0.5 0.5] [2 2]] [0 5])
(save-distr-charts :normal [:mu :sd] [[0 1] [1 2] [-1 0.5]])
(save-distr-charts :pareto [:scale :shape] [[2 1] [0.5 0.5] [1 3]] [0 5])
(save-distr-charts :t [:degrees-of-freedom] [[1] [5] [0.5]])
(save-distr-charts :triangular [:a :c :b] [[-1 0 1] [-3 -1 3] [0.5 1 3]])
(save-distr-charts :uniform-real [:lower :upper] [[-1 1] [-3 2] [1.5 3]])
(save-distr-charts :weibull [:alpha :beta] [[2 1] [5 1] [1 1]] [0 5])
(save-distr-charts :empirical [:data :bin-count] [[empirical-data 1000]])
(save-distr-charts :enumerated-real [:data] [[empirical-data]])

(save-distr-charts :negative-binomial [:r :p] [[20 0.5] [10 0.9] [10 0.2]] [0 50])
(save-distr-charts :bernoulli [:p] [[0.5] [0.9] [0.2]] [0 2])
(save-distr-charts :enumerated-int [:data] [[(map int empirical-data)]])
(save-distr-charts :binomial [:trials :p] [[20 0.5] [10 0.9] [10 0.2]] [0 20])
(save-distr-charts :geometric [:p] [[0.5] [0.9] [0.2]] [0 7])
(save-distr-charts :hypergeometric [:population-size :number-of-successes :sample-size] [[100 50 25] [50 10 5] [50 40 30]] [0 40])
(save-distr-charts :pascal [:r :p] [[20 0.5] [10 0.9] [10 0.2]] [0 50])
(save-distr-charts :poisson [:p] [[0.5] [0.9] [0.2]] [0 7])
(save-distr-charts :uniform-int [:lower :upper] [[-1 1] [-3 2] [1.5 3]] [-4 4])
(save-distr-charts :zipf [:number-of-elements :exponent] [[100 3] [50 0.5] [10 1]] [0 7])

(save-distr-charts :anderson-darling [:n] [[1] [3]] [0 3])
(save-distr-charts :inverse-gamma [:alpha :beta] [[2 1] [1 2] [2 2]] [0 5])
(save-distr-charts :chi [:nu] [[1] [2] [3]] [0 3])
(save-distr-charts :chi-squared-noncentral [:nu :lambda] [[1 1] [1 3] [2 0.5]] [0 5])
(save-distr-charts :erlang [:k :lambda] [[1 1] [2 1] [2 2]] [0 3])
(save-distr-charts :fatigue-life [:mu :beta :gamma] [[0 1 1] [1 2 3] [0 2 2]] [0 3])
(save-distr-charts :folded-normal [:mu :sigma] [[0 1] [1 2] [1 0.5]] [0 5])
(save-distr-charts :frechet [:alpha :beta :delta] [[1 1 0] [2 1 -1] [0.5 0.5 2]] [-1 5])
(save-distr-charts :hyperbolic-secant [:mu :sigma] [[0 1] [1 2] [-1 0.5]])
(save-distr-charts :inverse-gaussian [:mu :lambda] [[2 1] [1 2] [2 2]] [0 5])
(save-distr-charts :hypoexponential-equal [:n :k :h] [[1 1 1] [2 2 2] [2 2 3]] [0 5])

(save-distr-charts :johnson-sb [:gamma :delta :xi :lambda] [[0 1 0 1] [1 1 -2 2] [-2 2 1 1]])
(save-distr-charts :johnson-sl [:gamma :delta :xi :lambda] [[0 1 0 1] [1 1 -2 2] [-2 2 1 1]] [-3 5])
(save-distr-charts :johnson-su [:gamma :delta :xi :lambda] [[0 1 0 1] [1 1 -2 2] [-2 2 1 1]] [-6 5])

(save-distr-charts :kolmogorov-smirnov [:n] [[1] [2] [3]] [0 3])
(save-distr-charts :kolmogorov-smirnov+ [:n] [[1] [2] [3]] [0 3])

(save-distr-charts :log-logistic [:alpha :beta] [[3 1] [1 3] [2 2]] [0 5])
(save-distr-charts :pearson-6 [:alpha1 :alpha2 :beta] [[1 1 1] [0.5 2 2] [3 3 0.5]] [0 5])
(save-distr-charts :power [:a :b :c] [[0 1 2] [0 2 3] [1 3 2]] [0 5])
(save-distr-charts :rayleigh [:a :beta] [[0 1] [2 0.5] [-1 2]] [-3 5])

(save-distr-charts :watson-g [:n] [[2] [40]] [0 2])
(save-distr-charts :watson-u [:n] [[2] [40]] [0 0.5])

(save-distr-charts :hypoexponential [:lambdas] [[[1.0]] [[2 3 4]] [[0.5 0.1 2 5]]] [0 5])

(save-distr-charts :reciprocal-sqrt [:a] [[0.5] [2] [3]] [0 5])

(save-distr-charts :continuous-distribution [:data] [[empirical-data]])
(save-distr-charts :real-discrete-distribution [:data] [[empirical-data]])
(save-distr-charts :integer-discrete-distribution [:data] [[empirical-data]])

(save-distr-charts :half-cauchy [:scale] [[1] [2] [0.5]] [0 5])

(save-chart (function2d-chart (fn [x y] (r/pdf (r/distribution :multi-normal) [x y])) {:x [-3.1 3.1] :y [-3.1 3.1]})
            "d" "multi-normal" ".jpg")
(save-chart (function2d-chart (fn [x y] (r/pdf (r/distribution :multi-normal {:covariances [[1 -1] [-1 2]]}) [x y])) {:x [-3.1 3.1] :y [-3.1 3.1]})
            "d" "multi-normal2" ".jpg")
(save-chart (function2d-chart (fn [x y] (r/pdf (r/distribution :dirichlet {:alpha [2 0.8]}) [x y])) {:x [0 1] :y [0 1]})
            "d" "dirichlet" ".jpg")

;; kernels

(doseq [rbf k/rbf-list]
  (save-chart (function-chart (k/rbf rbf)) "k" (str "rbf_" (name rbf)) ".png"))

(doseq [ks k/kernels-list]
  (save-chart (function2d-chart (let [k (k/kernel ks)]
                                  (fn [x y] (k [x] [y]))) {:x [-3 3] :y [-3 3]}) "k" (str "k_" (name ks)) ".jpg"))

(def density-data (repeatedly 200 r/grand))

(defn density-chart
  [k]
  (cljplot/xy-chart {:width 400 :height 200 :background bg-color}
                    (b/series [:vline 0 {:color [60 100 120]}]
                              [:hline 0 {:color [60 100 120]}]
                              [:histogram density-data {:type :lollipops :density? true :palette [[200 200 220]] :bins 35}]
                              [:density density-data {:kernel-type k :color (clr/set-alpha fg-color 180) :area? true
                                                      :kernel-bandwidth 0.25}])
                    (b/update-scale :x :ticks 5)
                    (b/update-scale :y :ticks 5)
                    (b/add-axes :bottom {:ticks {:color fg-color}
                                         :line {:color fg-color}})
                    (b/add-axes :left {:ticks {:color fg-color}
                                       :line {:color fg-color}})))

(doseq [kd k/kernel-density-list]
  (save-chart (density-chart kd) "k" (str "d_" (name kd)) ".jpg"))

(save-chart (function2d-chart (let [k (k/approx (k/kernel :gaussian) 1)]
                                (fn [x y] (k [x] [y]))) {:x [-3 3] :y [-3 3]}) "k" "approx" ".jpg")

(save-chart (function2d-chart (let [k (k/cpd->pd (k/kernel :periodic))]
                                (fn [x y] (k [x] [y]))) {:x [-3 3] :y [-3 3]}) "k" "cpdpd" ".jpg")

(save-chart (function2d-chart (let [k (k/exp (k/kernel :dirichlet) 5.0)]
                                (fn [x y] (k [x] [y]))) {:x [-3 3] :y [-3 3]}) "k" "exp" ".jpg")


(defn ci-chart
  []
  (let [d (k/kernel-density-ci :epanechnikov density-data 0.5)
        r (range -3.0 3.0 0.05)
        top (map #(vector % (second (d %))) r)
        bottom (map #(vector % (last (d %))) r)]
    (cljplot/xy-chart {:width 400 :height 200 :background bg-color}
                      (b/series [:vline 0 {:color [60 100 120]}]
                                [:hline 0 {:color [60 100 120]}]
                                [:histogram density-data {:type :lollipops :density? true :palette [[200 200 220]] :bins 35}]
                                [:ci [top bottom] {:color (clr/set-alpha (clr/darken (clr/darken fg-color)) 200)}]
                                [:function (comp first d) {:domain [-3.0 3.0] :color fg-color}])
                      (b/update-scale :x :ticks 5)
                      (b/update-scale :y :ticks 5)
                      (b/add-axes :bottom {:ticks {:color fg-color}
                                           :line {:color fg-color}})
                      (b/add-axes :left {:ticks {:color fg-color}
                                         :line {:color fg-color}}))))


(save-chart (ci-chart) "k" "ci" ".jpg")

(save-chart (function2d-chart (let [k1 (k/kernel :periodic)
                                    k2 (k/kernel :laplacian)
                                    k (k/mult k1 k2)]
                                (fn [x y] (k [x] [y]))) {:x [-3 3] :y [-3 3]}) "k" "mult" ".jpg")

(save-chart (function2d-chart (let [k1 (k/kernel :periodic)
                                    k2 (k/kernel :laplacian)
                                    k (k/wadd [0.2 0.8] [k1 k2])]
                                (fn [x y] (k [x] [y]))) {:x [-3 3] :y [-3 3]}) "k" "wadd" ".jpg")

(save-chart (function-chart (k/rbf :thin-plate 2 1)) "k" "thin-plate" ".jpg")

;; easings

(doseq [[e ef] e/easings-list]
  (save-chart (function-chart ef [0.0 1.0]) "e" (name e) ".png"))

(save-chart (function-chart (e/out e/sin-in) [0 1]) "e" "out" ".png")
(save-chart (function-chart (e/in-out e/sin-out) [0 1]) "e" "in-out" ".png")
(save-chart (function-chart (e/reflect e/elastic-in-out 0.2) [0 1]) "e" "reflect" ".png")

;; complex

(defn complex-chart
  [f]
  (cljplot/xy-chart {:width 300 :height 300 :background bg-color}
                    (b/series [:complex f])
                    (b/update-scale :x :ticks 5)
                    (b/update-scale :y :ticks 5)
                    (b/add-axes :bottom {:ticks {:color fg-color}
                                         :line {:color fg-color}})
                    (b/add-axes :left {:ticks {:color fg-color}
                                       :line {:color fg-color}})))

(doseq [c `(c/acos c/asin c/atan c/cos c/cosh c/csc c/exp c/log c/reciprocal
                   c/sec c/sin c/sinh c/sq c/sqrt c/sqrt1z c/tan c/tanh)]
  (save-chart (complex-chart (symbol->fn c)) "c" (name c) ".jpg"))

(save-chart (complex-chart identity) "c" "identity" ".jpg")

;; interpolation

(defn ifun
  ^double [^double x]
  (m/sin (* x (* 0.5 (m/cos (inc x))))))

(defn interpolation-chart
  ([inter] (interpolation-chart inter false))
  ([inter r?] (apply interpolation-chart inter (if r? [0.69 6.22] [0 7])))
  ([inter mn mx]
   (let [xs [0.69 1.73 2.0 2.28 3.46 4.18 4.84 5.18 5.53 5.87 6.22]
         ys (map ifun xs)]
     (cljplot/xy-chart {:width 450 :height 250 :background bg-color}
                       (b/series [:function ifun {:domain [0 7] :color fg-color :stroke {:dash [5 5]}}]
                                 [:function (inter xs ys) {:domain [mn mx] :samples 300 :color :white}]
                                 [:scatter (map vector xs ys) {:color :lightgoldenrodyellow}])
                       (b/update-scale :x :ticks 5)
                       (b/update-scale :y :ticks 5)
                       (b/add-axes :bottom {:ticks {:color fg-color}
                                            :line {:color fg-color}})
                       (b/add-axes :left {:ticks {:color fg-color}
                                          :line {:color fg-color}})))))

(save-chart (function-chart ifun [0 7]) "i" "1d" ".png")

(save-chart (interpolation-chart i/akima-spline true) "i" "akima" ".png")
(save-chart (interpolation-chart i/divided-difference true) "i" "divided-difference" ".png")
(save-chart (interpolation-chart i/linear true) "i" "linear" ".png")

(save-chart (interpolation-chart i/loess true) "i" "loess" ".png")
(save-chart (interpolation-chart (partial i/loess 0.7 2) true) "i" "loess2" ".png")
(save-chart (interpolation-chart (partial i/loess 0.2 1) true) "i" "loess1" ".png")

(save-chart (interpolation-chart i/neville true) "i" "neville" ".png")
(save-chart (interpolation-chart i/spline true) "i" "spline" ".png")

(save-chart (interpolation-chart (partial i/microsphere-projection 6 0.1 0.1 0.1 1.5 false 0.01) true) "i" "microsphere" ".png")

(save-chart (interpolation-chart i/cubic-spline) "i" "cubic-spline" ".png")
(save-chart (interpolation-chart i/kriging-spline) "i" "kriging-spline" ".png")
(save-chart (interpolation-chart i/linear-smile) "i" "linear-smile" ".png")

(save-chart (interpolation-chart i/rbf) "i" "rbf" ".png")
(save-chart (interpolation-chart (partial i/rbf (k/rbf :mattern-c0))) "i" "rbf1" ".png")
(save-chart (interpolation-chart (partial i/rbf (k/rbf :gaussian) true)) "i" "rbf2" ".png")
(save-chart (interpolation-chart (partial i/rbf (k/rbf :truncated-power 3 0.3))) "i" "rbf3" ".png")
(save-chart (interpolation-chart (partial i/rbf (k/rbf :wendland-53))) "i" "rbf4" ".png")

(save-chart (interpolation-chart i/shepard) "i" "shepard" ".png")
(save-chart (interpolation-chart (partial i/shepard 0.9)) "i" "shepard1" ".png")

(save-chart (interpolation-chart i/step) "i" "step" ".png")
(save-chart (interpolation-chart i/step-after) "i" "step-after" ".png")
(save-chart (interpolation-chart i/step-before) "i" "step-before" ".png")
(save-chart (interpolation-chart i/monotone) "i" "monotone" ".png")

(save-chart (interpolation-chart i/polynomial true) "i" "polynomial" ".png")

(save-chart (interpolation-chart i/b-spline) "i" "bspline1" ".png")
(save-chart (interpolation-chart (partial i/b-spline 1)) "i" "bspline2" ".png")
(save-chart (interpolation-chart (partial i/b-spline (map #(/ % 7.0) [0 0.1 0.3 0.6 1.7 2.0 2 3 4 4.8 5 5.5 5.8 6 6.5 7])))
            "i" "bspline3" ".png")

(save-chart (interpolation-chart i/b-spline-interp) "i" "bsplinei1" ".png")
(save-chart (interpolation-chart (partial i/b-spline-interp 5)) "i" "bsplinei2" ".png")
(save-chart (interpolation-chart (partial i/b-spline-interp 3 6)) "i" "bsplinei3" ".png")

(defn ifun2d
  ^double [^double x ^double y]
  (m/sin (* (/ (- x 100.0) 10.0) (m/cos (/ y 20.0)))))

(defn interpolation2d-chart
  [f]
  (let [xs [20 50 58 66 100 121 140 150 160 170 180]
        ys [20 30 58 66 90  121 140 152 170 172 180] 
        vs (partition (count ys) (for [x xs y ys] (ifun2d x y)))]
    (cljplot/xy-chart {:width 400 :height 400 :background bg-color}
                      (b/series [:function-2d (f xs ys vs) {:x [20 180] :y [20 180] :gradient (clr/gradient [bg-color :white])}] 
                                [:scatter (for [x xs y ys] [x y]) {:color :lightgoldenrodyellow :margins {:x [0 0] :y [0 0]}}])
                      ;; (b/update-scale :x :ticks 5)
                      ;; (b/update-scale :y :ticks 5)
                      (b/add-axes :bottom {:ticks {:color fg-color}
                                           :line {:color fg-color}})
                      (b/add-axes :left {:ticks {:color fg-color}
                                         :line {:color fg-color}}))))

(save-chart (interpolation2d-chart i/bicubic) "i" "bicubic" ".jpg")
(save-chart (interpolation2d-chart i/piecewise-bicubic) "i" "piecewise-bicubic" ".jpg")
(save-chart (interpolation2d-chart i/bilinear) "i" "bilinear" ".jpg")
(save-chart (interpolation2d-chart i/bicubic-smile) "i" "bicubic-smile" ".jpg")
(save-chart (interpolation2d-chart i/cubic-2d) "i" "cubic-2d" ".jpg")
(save-chart (interpolation2d-chart (partial i/microsphere-2d-projection 10 0.5 0.0001 0.5 1.5 false 0.1)) "i" "microsphere-2d" ".jpg")

(save-chart (function2d-chart ifun2d {:x [0 200]
                                      :y [0 200]}) "i" "2d" ".jpg")

;; grids

(doseq [gs g/cell-names]
  (let [grid (g/grid gs 40)
        cells (distinct (map (partial g/coords->mid grid) (take 100 (map #(v/add (v/mult % 100) (v/vec2 150 150)) (r/sequence-generator :sphere 2)))))]
    (c2d/save (c2d/with-canvas [c (c2d/canvas 300 300 :highest)]
                (-> (c2d/set-background c bg-color)
                    (c2d/set-color fg-color)
                    (c2d/set-stroke 2))
                (doseq [[x y] cells]
                  (c2d/grid-cell c grid x y true))
                (c2d/set-stroke c 1)
                (doseq [[x y :as mid] cells
                        :let [[ax ay] (g/coords->anchor grid mid)]]
                  (-> c
                      (c2d/set-color 250 100 100)
                      (c2d/crect x y 3 3)
                      (c2d/set-color 100 250 250)
                      (c2d/ellipse ax ay 7 7 true)))
                c) (str "docs/images/g/" (name grid) ".jpg"))))

;; optimization

(defn opt-1d-chart
  ([f d pts]
   (cljplot/xy-chart {:width 400 :height 200 :background bg-color}
                     (b/series [:vline 0 {:color [60 100 120]}]
                               [:hline 0 {:color [60 100 120]}]
                               [:function f {:domain (or d [-3.1 3.1])
                                             :color :white
                                             :samples 300}]
                               [:scatter pts {:color :red :size 8}])
                     (b/update-scale :x :ticks 5)
                     (b/update-scale :y :ticks 5)
                     (b/add-axes :bottom {:ticks {:color fg-color}
                                          :line {:color fg-color}})
                     (b/add-axes :left {:ticks {:color fg-color}
                                        :line {:color fg-color}}))))


(defn opt-2d-chart
  [f d pts]
  (cljplot/xy-chart {:width 300 :height 300 :background bg-color}
                    (b/series [:contour-2d f (merge d {:palette (clr/resample [bg-color :white] 100)
                                                       :contours 100})]
                              [:scatter pts {:color (clr/color :red 140) :size 8}])
                    (b/update-scale :x :ticks 5)
                    (b/update-scale :y :ticks 5)
                    (b/add-axes :bottom {:ticks {:color fg-color}
                                         :line {:color fg-color}})
                    (b/add-axes :left {:ticks {:color fg-color}
                                       :line {:color fg-color}})))


(doseq [ooo [:powell :nelder-mead :multidirectional-simplex :cmaes :gradient :brent]]
  (let [bounds [[-5.0 5.0]]
        f (fn ^double [^double x] (+ (* 0.2 (m/sin (* 10.0 x))) (/ (+ 6.0 (- (* x x) (* 5.0 x))) (inc (* x x)))))
        o1 (opt/minimize ooo f {:bounds bounds})
        o2 (opt/maximize ooo f {:bounds bounds})]
    (save-chart (opt-1d-chart f (first bounds)
                              (map (juxt ffirst second) [o1 o2])) "o" (str (name ooo) "-1d") ".png")))

(doseq [ooo [:powell :nelder-mead :multidirectional-simplex :cmaes :gradient :bobyqa]]
  (let [bounds [[-5.0 5.0] [-5.0 5.0]]
        f (fn ^double [^double x ^double y] (+ (m/sq (+ (* x x) y -11.0))
                                              (m/sq (+ x (* y y) -7.0)))) ;; Himmelblau's function
        o1 (opt/minimize ooo f {:bounds bounds})]
    (save-chart (opt-2d-chart f {:x (first bounds)
                                 :y (second bounds)} [(first o1)]) "o" (str (name ooo) "-2d") ".jpg")))

(let [bounds [[-5.0 5.0] [-5.0 5.0]]
      f (fn ^double [^double x ^double y] (+ (m/sq (+ (* x x) y -11.0))
                                            (m/sq (+ x (* y y) -7.0)))) ;; Himmelblau's function
      bo (nth (opt/bayesian-optimization (fn ^double [^double x ^double y] (- (f x y))) {:bounds bounds
                                                                                        :init-points 5
                                                                                        :utility-function-type :poi}) 20)]
  #_(cljplot/show (opt-2d-chart f {:x (first bounds)
                                   :y (second bounds)} (:xs bo)))
  (save-chart (opt-2d-chart f {:x (first bounds)
                               :y (second bounds)} (:xs bo)) "o" "bo" ".jpg"))

#_(cljplot/show (interpolation2d-chart i/piecewise-bicubic))

;; signal

(def signal-f (sig/oscillators-sum
               (sig/oscillator :square 30 0.25 0.1)
               (sig/oscillator :triangle 2.2 0.25 0.2)
               (sig/oscillator :sin 4 0.5 0)))

(def ^doubles signal (sig/oscillator->signal signal-f 1000 5))

(defn signal-chart
  ([effect] (signal-chart effect {}))
  ([effect params]
   (cljplot/xy-chart {:width 600 :height 200 :background bg-color}
                     (b/series [:vline 0 {:color [60 100 120]}]
                               [:hline 0 {:color [60 100 120]}]
                               [:function signal-f {:domain [0 5]
                                                    :samples 500}]
                               [:function (sig/signal->oscillator (sig/apply-effects-raw signal (sig/effect effect params)) 5)
                                {:domain [0 5]
                                 :color :white
                                 :samples 500}])
                     (b/update-scale :x :ticks 5)
                     (b/update-scale :y :ticks 5)
                     (b/add-axes :bottom {:ticks {:color fg-color}
                                          :line {:color fg-color}})
                     (b/add-axes :left {:ticks {:color fg-color}
                                        :line {:color fg-color}})
                     (b/add-label :top (str (name effect) " " params) {:color fg-color}))))

(doseq [[e p] [[:simple-lowpass {:rate 1000 :cutoff 10}]
               [:simple-highpass {:rate 1000 :cutoff 100}]
               [:biquad-eq {:fs 1000 :fc 20 :gain -10 :bw 5}]
               [:biquad-hs {:fs 1000 :fc 10 :gain -10}]
               [:biquad-ls {:fs 1000 :fc 10 :gain -10}]
               [:biquad-lp {:fs 1000 :fc 20 :bw 5}]
               [:biquad-hp {:fs 1000 :fc 10 :bw 5}]
               [:biquad-bp {:fs 1000 :fc 20 :bw 2}]
               [:dj-eq {:rate 1000 :hi -25 :mid -25 :low 5}]
               [:phaser-allpass {:delay 0.001}]
               [:divider {:denom 4}]
               [:fm {:quant 100 :phase 0.05 :omega 0.02}]
               [:bandwidth-limit {:rate 1000 :freq 15}]
               [:distort {:factor 0.1}]
               [:foverdrive {:drive 0.1}]
               [:decimator {:fs 35 :rate 1000 :bits 4}]
               [:basstreble {:rate 1000 :bass-freq 2 :treble-freq 20 :bass 10 :treble -10}]
               [:echo {:rate 1000 :delay 0.1 :decay 0.5}]
               [:vcf303 {:rate 1000 :trigger true :gain 3 :resonance 1.2}]
               [:slew-limit {:rate 1000 :maxfall 20 :maxrise 30}]
               [:mda-thru-zero {:rate 1000}]]]
  (save-chart (signal-chart e p) "s" (name e) ".jpg"))

(doseq [o (disj (set sig/oscillators) :constant)]
  (save-chart (function-chart (sig/oscillator o 1 0.75 0.25) [-5 5]) "s" (name o) ".jpg"))

(save-chart (function-chart (sig/oscillators-sum
                             (sig/oscillator :triangle 1.5 0.5 0.5)
                             (sig/oscillator :sin 1 0.5 0)) [-3 3]) "s" "sum" ".jpg")


(defn smoothing-chart
  [filter name & params]
  (let [flt (apply filter params)]
    (cljplot/xy-chart {:width 600 :height 200 :background bg-color}
                      (b/series [:vline 0 {:color [60 100 120]}]
                                [:hline 0 {:color [60 100 120]}]
                                [:function signal-f {:domain [0 5]
                                                     :samples 500}]
                                [:function (sig/signal->oscillator (flt signal) 5)
                                 {:domain [0 5]
                                  :color :white
                                  :samples 500}])
                      (b/update-scale :x :ticks 5)
                      (b/update-scale :y :ticks 5)
                      (b/add-axes :bottom {:ticks {:color fg-color}
                                           :line {:color fg-color}})
                      (b/add-axes :left {:ticks {:color fg-color}
                                         :line {:color fg-color}})
                      (b/add-label :top name {:color fg-color}))))

(save-chart (smoothing-chart sig/savgol-filter "Savitzky-Golay filter (length=121, order=2)" 121 2) "s" "savgol" ".jpg")
(save-chart (smoothing-chart sig/moving-average-filter "Moving average (length=51)" 51) "s" "movavg" ".jpg")
(save-chart (smoothing-chart sig/kernel-smoothing-filter "Gaussian kernel smoothing (length=51, kernel sigma=11)" (k/kernel :gaussian 11) 51) "s" "gaussian" ".jpg")
(save-chart (smoothing-chart sig/kernel-smoothing-filter "Mattern-12 kernel smoothing (length=51, step=0.1)" (k/kernel :mattern-12) 51 0.1)  "s" "mattern12" ".jpg")

;; gp

(def xs [0 1 -2 -2.001])
(def ys [-2 3 0.5 -0.6])

(let [gp (gp/gaussian-process xs ys)]
  (gp/prior-samples gp (range 0 1 0.1)))

(defn draw-prior
  ([gp] (draw-prior gp 10))
  ([gp cnt]
   (let [xs (range -3 3.1 0.1)
         priors (map #(vector % (map vector xs (gp/prior-samples gp xs))) (range cnt))]
     (cljplot/xy-chart {:width 700 :height 300 :background bg-color}
                       (-> (b/series [:grid])
                           (b/add-multi :line priors {} {:color (cycle (map #(clr/set-alpha % 200) (clr/palette :pubu-9)))})
                           (b/add-serie [:hline 0 {:color :black :stroke {:size 2}}]))
                       (b/add-axes :bottom {:ticks {:color fg-color}
                                            :line {:color fg-color}})
                       (b/add-axes :left {:ticks {:color fg-color}
                                          :line {:color fg-color}})
                       (b/add-label :top "Priors" {:color fg-color})))))

(save-chart (draw-prior (gp/gaussian-process xs ys)) "gp" "prior" ".jpg")
(save-chart (draw-prior (gp/gaussian-process xs ys {:noise 0.1})) "gp" "priorn" ".jpg")
(save-chart (draw-prior (gp/gaussian-process xs ys {:kernel (k/kernel :periodic 2)
                                                    :noise 0.01})) "gp" "priorp" ".jpg")



(def xs [-4 -1 2])
(def ys [-5 -1 2])

(let [gp (gp/gaussian-process xs ys)]
  (gp/posterior-samples gp (range 0 1 0.1)))


(defn draw-posterior
  ([gp] (draw-posterior gp 10))
  ([gp cnt]
   (let [xxs (range -5 5.1 0.1)
         posteriors (map #(vector % (map vector xxs (gp/posterior-samples gp xxs))) (range cnt))]
     (cljplot/xy-chart {:width 700 :height 300 :background bg-color}
                       (-> (b/series [:grid])
                           (b/add-multi :line posteriors {} {:color (cycle (map #(clr/set-alpha % 200) (clr/palette :pubu-9)))})
                           (b/add-serie [:function gp {:domain [-5 5] :color :black :stroke {:size 3 :dash [5 2]}}])
                           (b/add-serie [:scatter (map vector xs ys) {:size 16
                                                                      :color (clr/color fg-color 230)}]))
                       (b/add-axes :bottom {:ticks {:color fg-color}
                                            :line {:color fg-color}})
                       (b/add-axes :left {:ticks {:color fg-color}
                                          :line {:color fg-color}})
                       (b/add-label :top "Posteriors" {:color fg-color})))))

(save-chart (draw-posterior (gp/gaussian-process xs ys {:kernel (k/kernel :gaussian 0.5)})) "gp" "posteriork" ".jpg")
(save-chart (draw-posterior (gp/gaussian-process xs ys {:kernel (k/kernel :periodic 0.2 6.5)})) "gp" "posteriorkp" ".jpg")

(def xs [-5 1 2])
(def ys [17 10 12])

(defn draw-stddev
  [gp]
  (let [xxs (range -5 5.1 0.2)
        pairs (gp/predict-all gp xxs true)
        mu (map first pairs)
        stddev (map second pairs)
        s95 (map (partial * 1.96) stddev)
        s50 (map (partial * 0.67) stddev)]
    (cljplot/xy-chart {:width 700 :height 300 :background bg-color}
                      (b/series [:grid]
                                [:ci [(map vector xxs (map - mu s95)) (map vector xxs (map + mu s95))] {:color (clr/color :lightblue 180)}]
                                [:ci [(map vector xxs (map - mu s50)) (map vector xxs (map + mu s50))] {:color (clr/color :lightblue)}]
                                [:line (map vector xxs mu) {:color :black :stroke {:size 2 :dash [5 2]}}]
                                [:scatter (map vector xs ys) {:size 8 :color fg-color}])
                      (b/add-axes :bottom {:ticks {:color fg-color}
                                           :line {:color fg-color}})
                      (b/add-axes :left {:ticks {:color fg-color}
                                         :line {:color fg-color}})
                      (b/add-label :top "Confidence intervals" {:color fg-color}))))

(save-chart (draw-stddev (gp/gaussian-process xs ys)) "gp" "ci" ".jpg")
(save-chart (draw-stddev (gp/gaussian-process xs ys {:normalize? true})) "gp" "cin" ".jpg")


