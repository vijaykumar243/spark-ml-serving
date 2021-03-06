package io.hydrosphere.spark_ml_serving.classification

import io.hydrosphere.spark_ml_serving._
import org.apache.spark.ml.classification.{DecisionTreeClassificationModel, RandomForestClassificationModel}
import org.apache.spark.ml.linalg.{DenseVector, Vector, Vectors}

class LocalRandomForestClassificationModel(override val sparkTransformer: RandomForestClassificationModel) extends LocalTransformer[RandomForestClassificationModel] {
  override def transform(localData: LocalData): LocalData = {
    localData.column(sparkTransformer.getFeaturesCol) match {
      case Some(column) =>
        val cls = classOf[RandomForestClassificationModel]
        val rawPredictionCol = LocalDataColumn(sparkTransformer.getRawPredictionCol, column.data.map(f => Vectors.dense(f.asInstanceOf[Array[Double]])).map { vector =>
          val predictRaw = cls.getDeclaredMethod("predictRaw", classOf[Vector])
          val res = predictRaw.invoke(sparkTransformer, vector).asInstanceOf[Vector]
          res.toArray
        })
        val probabilityCol = LocalDataColumn(sparkTransformer.getProbabilityCol, rawPredictionCol.data.map(Vectors.dense).map { vector =>
          val raw2probabilityInPlace = cls.getDeclaredMethod("raw2probabilityInPlace", classOf[Vector])
          val res = raw2probabilityInPlace.invoke(sparkTransformer, vector.copy).asInstanceOf[Vector]
          res.toArray
        })
        val predictionCol = LocalDataColumn(sparkTransformer.getPredictionCol, rawPredictionCol.data.map(Vectors.dense).map { vector =>
          val raw2prediction = cls.getMethod("raw2prediction", classOf[Vector])
          raw2prediction.invoke(sparkTransformer, vector.copy)
        })
        localData.withColumn(rawPredictionCol)
          .withColumn(probabilityCol)
          .withColumn(predictionCol)
      case None => localData
    }
  }
}

object LocalRandomForestClassificationModel extends LocalModel[RandomForestClassificationModel] {
  override def load(metadata: Metadata, data: Map[String, Any]): RandomForestClassificationModel = {
    val treesMetadata = metadata.paramMap("treesMetadata").asInstanceOf[Map[String, Any]]
    val trees = treesMetadata map { treeKv =>
      val treeMeta = treeKv._2.asInstanceOf[Map[String, Any]]
      val meta = treeMeta("metadata").asInstanceOf[Metadata]
      LocalDecisionTreeClassificationModel.createTree(
        meta,
        data(treeKv._1).asInstanceOf[Map[String, Any]]
      )
    }
    val ctor = classOf[RandomForestClassificationModel].getDeclaredConstructor(classOf[String], classOf[Array[DecisionTreeClassificationModel]], classOf[Int], classOf[Int])
    ctor.setAccessible(true)
    ctor
      .newInstance(
        metadata.uid,
        trees.to[Array],
        metadata.numFeatures.get.asInstanceOf[java.lang.Integer],
        metadata.numClasses.get.asInstanceOf[java.lang.Integer]
      )
      .setFeaturesCol(metadata.paramMap("featuresCol").asInstanceOf[String])
      .setPredictionCol(metadata.paramMap("predictionCol").asInstanceOf[String])
      .setProbabilityCol(metadata.paramMap("probabilityCol").asInstanceOf[String])
  }

  override implicit def getTransformer(transformer: RandomForestClassificationModel): LocalTransformer[RandomForestClassificationModel] = new LocalRandomForestClassificationModel(transformer)
}