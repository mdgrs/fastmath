(ns fastmath.classification
  "Classification algorithms.

  ### Input data

  * features - sequence of sequences of numbers
  * categories - sequence of any values

  When needed you can split input data into training data and test data. Test data are optional.
  
  ### Workflow
  
  * create and train by calling classifier with parameters, train data and optional test data.
  * validate data by calling [[test]] or [[validate]].
  * cross validate
  * repeat or [[predict]]

  Classifier parameters are map of values specific for given algorithm. Check documentation in backend library to find documentation. Classifier can be retrained using [[train]]. New instance will be created.
  
  ### Implementation notes

  * only doubles as input data
  * categories can be any type

  ### SMILE

  [documentation](https://haifengl.github.io/smile/classification.html)

  What is missed:

  * other types than doubles (attributes)
  * maxent
  * Online classifiers
  * General Naive Bayes
  * SVM
  
  ### XGBoost
  
  XGBoost is backed by [clj-boost](https://gitlab.com/alanmarazzi/clj-boost/tree/master). 
  XGBoost uses only `multi:softprob` objective currently. See [documentation](https://xgboost.readthedocs.io/en/latest/parameter.html#parameters-for-tree-booster) for parameters. By default `:early-stopping` is set to `20` and `:round` is set to `10`.

  ### Cross validation

  Currently leave-one-out algorithm is implemented."
  (:refer-clojure :exclude [test])
  (:require [fastmath.core :as m]
            [fastmath.distance :as dist]
            [fastmath.rbf :as rbf]
            [clj-boost.core :as xgboost])
  (:import [clojure.lang IFn]
           [smile.classification SoftClassifier ClassifierTrainer KNN$Trainer AdaBoost$Trainer FLD$Trainer QDA$Trainer
            LDA$Trainer DecisionTree$Trainer DecisionTree$SplitRule GradientTreeBoost$Trainer
            LogisticRegression$Trainer Maxent$Trainer NaiveBayes$Trainer NaiveBayes$Model
            NeuralNetwork$Trainer NeuralNetwork$ErrorFunction NeuralNetwork$ActivationFunction
            RBFNetwork$Trainer RDA$Trainer RandomForest$Trainer]
           [smile.math.distance EuclideanDistance]
           [smile.math.rbf RadialBasisFunction]))

(set! *warn-on-reflection* false)

(defprotocol ClassificationProto
  (backend [_] "Return name of backend library")
  (predict [_ v] [_ v posteriori?] "Predict class for given vector. With posteriori probabilities when required.")
  (predict-all [_ vs] [_ v posteriori?] "Predict classes for given sequence of vectors. With posteriori probabilities when required.")
  (train [_ x y] [_ x y tx ty] "Train another set of data for given classifier. Test data are optional.")
  (data [_] [_ native?] "Return data. Transformed data for backend libarary are returned when `native?` is true.")
  (labels [_]  "Return labels.")
  (test [_] [_ tx ty] "Validate data using given model and provided test data. Same as [[validate]]."))

(defn- labels-converters
  "Convert y into label->int and int->label functions."
  [y]
  (let [ydata (map vector (sort (distinct y)) (range))]
    [(mapv first ydata) (into {} ydata)]))

(defmulti ^:private prepare-data (fn [k & _] k))
(defmethod prepare-data :smile [_ x y labels->int] [(m/seq->double-double-array x) (int-array (map labels->int y))])
(defmethod prepare-data :xgboost [_ x y labels->int] (xgboost/dmatrix x (map labels->int y)))

(declare validate)

(defmulti ^:private classifier (fn [k & _] k))

(defmethod classifier :xgboost
  ([_ booster-params x y tx ty] (classifier :xgboost booster-params x y tx ty nil))
  ([_ booster-params x y tx ty booster]
   (let [[labels labels->int] (labels-converters y)
         data (prepare-data :xgboost x y labels->int)
         test-data (when tx (prepare-data :xgboost tx ty labels->int))

         ;; to fix
         booster-params (if-not (contains? booster-params :rounds) (assoc booster-params :rounds 10) booster-params)
         booster-params (if-not (contains? booster-params :early-stopping) (assoc booster-params :early-stopping 20) booster-params)
         booster-params (-> (dissoc booster-params :booster :watches)
                            (assoc-in [:watches :train] data)
                            (assoc-in [:params :num_class] (count labels))
                            (assoc-in [:params :objective] "multi:softprob"))
         booster-params (if (and tx ty) (assoc-in booster-params [:watches :test] test-data) booster-params)
         booster-params (if booster (assoc booster-params :booster booster) booster-params)

         model (xgboost/fit data booster-params)
         res-range (range (count labels))
         prob->label #(labels (apply max-key (vec %) res-range))]
     (reify
       IFn
       (invoke [_ v]  (prob->label (xgboost/predict model (xgboost/dmatrix [v]))))
       (invoke [c v posteriori?] (if posteriori? (let [r (-> (xgboost/predict model (xgboost/dmatrix [v])))]
                                                   [(prob->label r) r]) (c v)))

       ClassificationProto
       (backend [_] :xgboost)
       
       (predict [c v] (c v))
       (predict [c v posteriori?] (c v posteriori?))
       (predict-all [_ vs] (map prob->label (seq (.predict model (xgboost/dmatrix vs)))))
       (predict-all [c vs posteriori?] (if posteriori?
                                         (map (comp #(vector (prob->label %) %) seq)
                                              (seq (.predict model (xgboost/dmatrix vs))))
                                         (predict-all c vs)))
       
       (train [c x y] (train c x y nil nil))
       (train [_ x y tx ty] (classifier :xgboost booster-params x y tx ty model))
       (test [c] (when (and tx ty) (validate c tx ty)))
       (test [c tx ty] (validate c tx ty))
       
       (data [_] [x y])
       (data [_ native?] (if native? data [x y]))
       (labels [_] labels)))))

(defmethod classifier :smile
  [_ ^ClassifierTrainer trainer x y tx ty]
  (let [[labels labels->int] (labels-converters y)
        [data int-labels] (prepare-data :smile x y labels->int)
        classifier-raw (.train trainer data int-labels)
        predict-raw #(.predict classifier-raw (m/seq->double-array %))
        predict-fn (comp labels predict-raw)
        predict-fn-posteriori (if (instance? SoftClassifier classifier-raw)
                                #(let [posteriori (double-array (count labels))]
                                   [(labels (.predict classifier-raw (m/seq->double-array %) posteriori)) (seq posteriori)])
                                predict-fn)]
    (reify
      
      IFn
      (invoke [_ v] (predict-fn v))
      (invoke [_ v posteriori?] (if posteriori? (predict-fn-posteriori v) (predict-fn v)))

      ClassificationProto
      (backend [_] :smile)
      
      (predict [_ v] (predict-fn v))
      (predict [_ v posteriori?] (if posteriori? (predict-fn-posteriori v) (predict-fn v)))
      (predict-all [_ vs] (map predict-fn vs))
      (predict-all [_ vs posteriori?] (if posteriori? (map predict-fn-posteriori vs) (map predict-fn vs)))
      
      (train [c x y] (train c x y nil nil))
      (train [_ x y tx ty] (classifier :smile trainer x y tx ty))
      (test [c] (when (and tx ty) (validate c tx ty)))
      (test [c tx ty] (validate c tx ty))
      
      (data [_] [x y])
      (data [_ native?] (if native? [data int-labels] [x y]))
      (labels [_] labels))))

(defmacro ^:private wrap-classifier
  {:style/indent 3}
  [typ clname parameter instance]
  (let [[x y tx ty params] (map symbol ["x" "y" "tx" "ty" "params"])
        doc (str clname " classifier. Backend library: " (name typ))]
    `(defn ~clname ~doc
       ([~x ~y] (~clname {} ~x ~y nil nil))
       ([~params ~x ~y] (~clname ~params ~x ~y nil nil))
       ([~x ~y ~tx ~ty] (~clname {} ~x ~y ~tx ~ty))
       ([~parameter ~x ~y ~tx ~ty] (classifier ~typ ~instance ~x ~y ~tx ~ty)))))

(wrap-classifier :smile knn {:keys [distance k]
                             :or {distance (EuclideanDistance.) k 1}}
  (KNN$Trainer. distance k))

(wrap-classifier :smile ada-boost {:keys [number-of-trees max-nodes]
                                   :or {number-of-trees 500 max-nodes 2}}
  (-> (AdaBoost$Trainer.)
      (.setNumTrees number-of-trees)
      (.setMaxNodes max-nodes)))

(wrap-classifier :smile fld {:keys [dimensionality tolerance]
                             :or {dimensionality -1 tolerance 1.0e-4}}
  (let [^FLD$Trainer t (FLD$Trainer.)]
    (-> (if-not (pos? dimensionality) t (.setDimension t dimensionality))
        (.setTolerance tolerance))))

(wrap-classifier :smile qda {:keys [priori tolerance]
                             :or {priori nil tolerance 1.0e-4}}
  (-> (QDA$Trainer.)
      (.setPriori (m/seq->double-array priori))
      (.setTolerance tolerance)))

(wrap-classifier :smile lda {:keys [priori tolerance]
                             :or {priori nil tolerance 1.0e-4}}
  (-> (LDA$Trainer.)
      (.setPriori (m/seq->double-array priori))
      (.setTolerance tolerance)))

(def ^:private split-rules {:gini DecisionTree$SplitRule/GINI
                            :entropy DecisionTree$SplitRule/ENTROPY
                            :classification-error DecisionTree$SplitRule/CLASSIFICATION_ERROR})

(def ^{:doc "List of split rules for [[decision tree]] and [[random-forest]]"} split-rules-list (keys split-rules))

(wrap-classifier :smile decision-tree {:keys [max-nodes node-size split-rule]
                                       :or {max-nodes 100 node-size 1 split-rule :gini}}
  (let [t (-> (DecisionTree$Trainer. max-nodes)
              (.setSplitRule (or (split-rules split-rule) DecisionTree$SplitRule/GINI)))]
    (if (> node-size 1) (.setNodeSize t node-size) t)))

(wrap-classifier :smile gradient-tree-boost {:keys [number-of-trees shrinkage max-nodes subsample]
                                             :or {number-of-trees 500 shrinkage 0.005 max-nodes 6 subsample 0.7}}
  (-> (GradientTreeBoost$Trainer. number-of-trees)
      (.setMaxNodes max-nodes)
      (.setShrinkage shrinkage)
      (.setSamplingRates subsample)))

(wrap-classifier :smile logistic-regression {:keys [lambda tolerance max-iterations]
                                             :or {lambda 0.0 tolerance 1.0e-5 max-iterations 500}}
  (-> (LogisticRegression$Trainer.)
      (.setRegularizationFactor lambda)
      (.setTolerance tolerance)
      (.setMaxNumIteration max-iterations)))

(def ^:private bayes-models {:multinomial NaiveBayes$Model/MULTINOMIAL
                             :bernoulli NaiveBayes$Model/BERNOULLI
                             :polyaurn NaiveBayes$Model/POLYAURN})

(def ^{:doc "List of [[naive-bayes]] models."} bayes-models-list (keys bayes-models))

(wrap-classifier :smile naive-bayes {:keys [model priori sigma]
                                     :or {model :bernoulli sigma 1.0}}
  (let [classes (count (distinct y))
        independent-variables (count (first x))
        model (or (bayes-models model) NaiveBayes$Model/BERNOULLI)]
    (-> (NaiveBayes$Trainer. model classes independent-variables)
        (.setSmooth sigma)
        (.setPriori (m/seq->double-array priori)))))

(def ^:private error-functions {:least-mean-squares NeuralNetwork$ErrorFunction/LEAST_MEAN_SQUARES
                                :cross-entropy NeuralNetwork$ErrorFunction/CROSS_ENTROPY})

(def ^{:doc "List of error functions for [[neural-net]]."} error-functions-list (keys error-functions))

(def ^:private activation-functions {:linear NeuralNetwork$ActivationFunction/LINEAR
                                     :logistic-sigmoid NeuralNetwork$ActivationFunction/LOGISTIC_SIGMOID
                                     :soft-max NeuralNetwork$ActivationFunction/SOFTMAX})

(def ^{:doc "List of activation functions for [[neural-net]]."} activation-functions-list (keys activation-functions))

(wrap-classifier :smile neural-net
    {:keys [error-function activation-function layers learning-rate momentum weight-decay number-of-epochs]
     :or {error-function :cross-entropy learning-rate 0.1 momentum 0.0 weight-decay 0.0 number-of-epochs 25}}
  (let [layers (into-array Integer/TYPE (cons (count (first x)) (conj (vec layers) (count (distinct y)))))
        ef (or (error-functions error-function) NeuralNetwork$ErrorFunction/CROSS_ENTROPY)]
    (-> (if activation-function
          (NeuralNetwork$Trainer. ef layers)
          (NeuralNetwork$Trainer.
           ef (or (activation-functions activation-function)
                  (if (= ef NeuralNetwork$ErrorFunction/CROSS_ENTROPY)
                    NeuralNetwork$ActivationFunction/SOFTMAX
                    NeuralNetwork$ActivationFunction/LINEAR)) layers))
        (.setLearningRate learning-rate)
        (.setMomentum momentum)
        (.setWeightDecay weight-decay)
        (.setNumEpochs number-of-epochs))))

(wrap-classifier :smile rbf-network {:keys [distance rbf number-of-basis normalize?]
                                     :or {distance dist/euclidean number-of-basis 10 normalize? false}}
  (let [cl (RBFNetwork$Trainer. distance)]
    (-> (cond
          (nil? rbf) cl
          (seqable? rbf) (.setRBF cl (into-array RadialBasisFunction (map rbf/rbf-obj rbf)))
          :else (.setRBF cl (rbf/rbf-obj rbf) number-of-basis))
        (.setNormalized normalize?))))

(wrap-classifier :smile rda {:keys [alpha priori tolerance]
                             :or {alpha 0.9 tolerance 1.0e-4}}
  (-> (RDA$Trainer. alpha)
      (.setPriori (m/seq->double-array priori))
      (.setTolerance tolerance)))

(wrap-classifier :smile random-forest {:keys [number-of-trees split-rule mtry node-size max-nodes subsample]
                                       :or {number-of-trees 500 split-rule :gini node-size 1 max-nodes 100 subsample 1.0}}
  (let [mtry (or mtry (m/floor (m/sqrt (count (first x)))))]
    (-> (RandomForest$Trainer. number-of-trees)
        (.setSplitRule (or (split-rules split-rule) DecisionTree$SplitRule/GINI))
        (.setNumRandomFeatures mtry)
        (.setMaxNodes max-nodes)
        (.setSamplingRates subsample)
        (.setNodeSize node-size))))

(wrap-classifier :xgboost xgboost xgboost-params xgboost-params)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; validation metrics

(defn accuracy
  "Calculate accuracy for real and predicted sequences."
  [t p] (double (/ (count (filter (partial apply =) (map vector t p)))
                   (count t))))

(defn validate
  "Validate data against trained classifier. Same as [[test]]."
  ([model] (test model))
  ([model tx ty]
   (let [pred (predict-all model tx)
         invalid (->> (map vector tx ty pred)
                      (filter #(apply not= (rest %))))
         invalid-cnt (count invalid)]
     {:truth ty
      :prediction pred
      :invalid {:count invalid-cnt
                :data (map first invalid)
                :truth (map #(nth % 2) invalid)
                :prediction (map second invalid)}
      :stats {:accuracy (double (- 1.0 (/ invalid-cnt (count ty))))}})))

(defn- drop-nth [coll n]
  (keep-indexed #(if (not= %1 n) %2) coll))

(defn loocv
  "Leave-one-out cross validation of a classification model."
  ([model]
   (let [[data labels] (data model)
         plabels (map #(let [tdata (drop-nth data %)
                             tlabels (drop-nth labels %)
                             value (nth data %)
                             mtrain (train model tdata tlabels)]
                         (predict mtrain value)) (range (count data)))]
     {:truth labels
      :prediction plabels
      :stats {:accuracy (accuracy labels plabels)}})))


