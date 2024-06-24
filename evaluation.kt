package sample

import java.io.File

data class Evaluation(
    val rightPositive: Int, val rightNegative: Int, val sensitivity: Double, val specificity: Double,
    val accuracy: Double
)

enum class Classifier {
    PRIMITIVENOPOLARITY, PRIMITIVEWITHPOLARITY, RULEBASED, NAIVEBAYES, DOMAINSPECIFIC
}

class Evaluator {
    companion object {
        //evaluate test data using all classifiers
        @JvmStatic
        fun evaluateWithAllClassifiers(
            testDataPath: String,
            minPMI: Double = 0.0,
            minFrequency: Int = 0,
            includeRuleBased: Boolean
        ): String {
            val docsOfTestDataFile =
                TextAnalysisTools.dataFileSummary(File("${testDataPath}balanced_testData.txt"))
            val positiveEntries = docsOfTestDataFile.first
            val negativeEntries = docsOfTestDataFile.second
            val probabilityOfDocsInTrainingData = TextAnalysisTools.probabilitiesOfFile(
                File("${testDataPath}balanced_testData.txt")
            )
            val probabilityOfPositiveDocs = probabilityOfDocsInTrainingData.first
            val probabilityOfNegativeDocs = probabilityOfDocsInTrainingData.second

            val evaluation = StringBuilder()
            for (classifier in Classifier.values())
                if (!includeRuleBased && classifier == Classifier.RULEBASED) continue
                else
                    evaluation.append(
                        evaluationToString(
                            evaluate(
                                TextAnalysisTools.docsOfFile("${testDataPath}balanced_testData.txt"),
                                classifier, positiveEntries, negativeEntries,
                                RuleBasedClassifierOptions(
                                    true, false, false, false,
                                    true
                                ), minPMI, minFrequency,
                                probabilityOfPositiveDocs, probabilityOfNegativeDocs
                            ),
                            classifier, positiveEntries, negativeEntries
                        )
                    )
            return evaluation.toString()
        }

        fun evaluationToString(
            evaluation: Evaluation,
            classifier: Classifier,
            positiveEntries: Int,
            negativeEntries: Int
        ) = "Analyse mit $classifier\n" +
                "Richtig positive Tupeln: ${evaluation.rightPositive} aus $positiveEntries\n" +
                "Richtig negative Tupeln: ${evaluation.rightNegative} aus $negativeEntries\n" +
                "Sensitivität: ${evaluation.sensitivity}\n" +
                "Spezifizität: ${evaluation.specificity}\n" +
                "Genauigkeit: ${evaluation.accuracy}\n\n"

        fun evaluate(
            testData: List<TextAnalysisTools.Companion.Review>,
            classifier: Classifier,
            positiveEntries: Int,
            negativeEntries: Int,
            ruleBasedClassifierOptions: RuleBasedClassifierOptions = RuleBasedClassifierOptions(),
            minPMI: Double = 0.0,
            minFrequency: Int = 0,
            probabilityOfPositiveDocs: Double = 0.0,
            probabilityOfNegativeDocs: Double = 0.0
        ): Evaluation {
            val rightTuples =
                determineRightTuples(
                    testData, ruleBasedClassifierOptions, classifier, minPMI, minFrequency, probabilityOfPositiveDocs,
                    probabilityOfNegativeDocs
                )
            val rightPositive = rightTuples.first
            val rightNegative = rightTuples.second
            val sensitivity = rightPositive * 1.0 / positiveEntries
            val specificity = rightNegative * 1.0 / negativeEntries
            val accuracy = (rightPositive + rightNegative) * 1.0 / (positiveEntries + negativeEntries)
            return Evaluation(rightPositive, rightNegative, sensitivity, specificity, accuracy)
        }

        private fun determineRightTuples(
            testData: List<TextAnalysisTools.Companion.Review>,
            ruleBasedClassifierOptions: RuleBasedClassifierOptions,
            classifier: Classifier, minPMI: Double, minFrequency: Int, probabilityOfPositiveDocs: Double,
            probabilityOfNegativeDocs: Double
        ): Pair<Int, Int> {
            return when (classifier) {
                Classifier.RULEBASED -> determineRightTuplesFromRuleBased(testData, ruleBasedClassifierOptions)
                Classifier.DOMAINSPECIFIC -> determineRightTuplesFromDomainSpecific(testData)
                Classifier.NAIVEBAYES -> determineRightTuplesFromBayes(
                    testData, minPMI, minFrequency,
                    probabilityOfPositiveDocs, probabilityOfNegativeDocs
                )
                else -> determineRightTuplesFromPrimitive(testData, classifier)
            }

        }

        private fun determineRightTuplesFromPrimitive(
            testData: List<TextAnalysisTools.Companion.Review>,
            classifier: Classifier
        ): Pair<Int, Int> {
            var rightPositive = 0
            var rightNegative = 0
            if (classifier == Classifier.PRIMITIVENOPOLARITY)
                for (document in testData)
                    if (PrimitiveClassifier.primitiveNoPolarityAnalyzer(document.content).result
                        == document.numericalClassification
                    )
                        if (document.numericalClassification == 1)
                            rightPositive++
                        else
                            rightNegative++

            if (classifier == Classifier.PRIMITIVEWITHPOLARITY)
                for (document in testData)
                    if (PrimitiveClassifier.primitivePolarityAnalyzer(document.content).result ==
                        document.numericalClassification
                    )
                        if (document.numericalClassification == 1)
                            rightPositive++
                        else
                            rightNegative++


            return Pair(rightPositive, rightNegative)
        }

        private fun determineRightTuplesFromRuleBased(
            testData: List<TextAnalysisTools.Companion.Review>,
            ruleBasedClassifierOptions: RuleBasedClassifierOptions
        ): Pair<Int, Int> {
            var rightPositive = 0
            var rightNegative = 0
            for (document in testData)
                if (RuleBasedClassifier.ruleBasedClassifier(
                        document.content,
                        ruleBasedClassifierOptions
                    ).result == document.numericalClassification
                )
                    if (document.numericalClassification == 1)
                        rightPositive++
                    else
                        rightNegative++

            return Pair(rightPositive, rightNegative)

        }

        private fun determineRightTuplesFromDomainSpecific(
            testData: List<TextAnalysisTools.Companion.Review>
        ): Pair<Int, Int> {
            var rightPositive = 0
            var rightNegative = 0
            for (document in testData)
                if (DomainSpecificClassifier.domainSpecificClassifier(
                        document.content
                    ).result == document.numericalClassification
                )
                    if (document.numericalClassification == 1)
                        rightPositive++
                    else
                        rightNegative++
            return Pair(rightPositive, rightNegative)

        }

        private fun determineRightTuplesFromBayes(
            testData: List<TextAnalysisTools.Companion.Review>,
            minPMI: Double, minFrequency: Int, probabilityOfPositiveDocs: Double,
            probabilityOfNegativeDocs: Double
        ): Pair<Int, Int> {
            var rightPositive = 0
            var rightNegative = 0
            val definitionVector = FeatureSelection.createDefinitionVector(minPMI, minFrequency)
            for (document in testData)
                if (BayesClassifier.bayesClassifier(
                        document.content, definitionVector,
                        probabilityOfPositiveDocs, probabilityOfNegativeDocs
                    ).result == document.numericalClassification
                )
                    if (document.numericalClassification == 1)
                        rightPositive++
                    else
                        rightNegative++
            return Pair(rightPositive, rightNegative)
        }
    }
}