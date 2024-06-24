package sample

import sample.TextAnalysisTools.Companion.probabilityOfVectorInNegativeClass
import sample.TextAnalysisTools.Companion.probabilityOfVectorInPositiveClass
import sample.TextAnalysisTools.Companion.representTextAsVector

data class Feature(
    val combination: List<String>,
    val probabilityPresentConditionPosClass: Double = 0.0,
    val probabilityPresentConditionNegClass: Double = 0.0,
    val probabilityNotPresentConditionNegClass: Double =
        1 - probabilityPresentConditionNegClass,
    val probabilityNotPresentConditionPosClass: Double =
        1 - probabilityPresentConditionPosClass
)


class BayesClassifier {

    companion object {
        //classify using naive bayes classifier
        @JvmStatic
        fun bayesClassifier(
            input: String, definitionVector: List<Feature>, probabilityOfPositiveDocs: Double,
            probabilityOfNegativeDocs: Double
        ): TextAnalysisTools.Companion.OpinionInfo {
            val inputVector = representTextAsVector(input, definitionVector)
            return TextAnalysisTools.Companion.OpinionInfo(
                if (probabilityOfVectorInNegativeClass(inputVector, definitionVector, probabilityOfNegativeDocs) <
                    probabilityOfVectorInPositiveClass(inputVector, definitionVector, probabilityOfPositiveDocs)
                )
                    1 else -1, 0, 0, 0, 0.0
            )
        }
    }

}

