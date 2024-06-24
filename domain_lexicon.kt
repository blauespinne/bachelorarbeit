package sample

import sample.TextAnalysisTools.Companion.normalizePMI
import sample.TextAnalysisTools.Companion.normalizePolarity
import java.io.File

//BAYES: creates the features of single words
//DOMAIN: creates a domain specific lexicon
enum class ClassifierConfig { BAYES, DOMAIN }

class DomainLexicon {
    companion object {
        private fun normalizeValues(
            domainWords: List<TextAnalysisTools.Companion.DomainWord>,
            polarityVector: List<Double>, minPMI: Double, minWordFrequency: Int,
            positivePMI: List<Double>, negativePMI: List<Double>
        ) {
            val maxPol = polarityVector.maxOrNull()
            val minPol = polarityVector.minOrNull()
            val maxPositivePMI = positivePMI.maxOrNull()
            val minPositivePMI = positivePMI.minOrNull()
            val maxNegativePMI = negativePMI.maxOrNull()
            val minNegativePMI = negativePMI.minOrNull()
            for (word in domainWords) {
                word.normalizePolarity(maxPol!!, minPol!!)
                word.normalizePMI(minPositivePMI!!, maxPositivePMI!!, minNegativePMI!!, maxNegativePMI!!)
                word.isRelevant = TextAnalysisTools.isRelevant(word, minPMI, minWordFrequency)
            }
        }

        private fun buildContentOfLexicon(
            domainWords: List<TextAnalysisTools.Companion.DomainWord>,
            classifierConfig: ClassifierConfig, inputPath: String
        ): String {
            val stringBuilder = StringBuilder()
            val dataFileSummary = TextAnalysisTools.dataFileSummary(File(inputPath))
            val totalPositiveDocs = dataFileSummary.first
            val totalNegativeDocs = dataFileSummary.second
            for (word in domainWords) if (word.isRelevant) {
                if (classifierConfig == ClassifierConfig.BAYES) stringBuilder.append(
                    TextAnalysisTools.createLineOfFeatureAsSingleWord(
                        word,
                        totalPositiveDocs,
                        totalNegativeDocs
                    )
                )
                else stringBuilder.append(TextAnalysisTools.createLineInDomainLexicon(word))
            }
            return stringBuilder.toString()
        }

        private fun createPMIVectors(domainWords: List<TextAnalysisTools.Companion.DomainWord>): Pair<List<Double>, List<Double>> {
            val positivePMI = mutableListOf<Double>()
            val negativePMI = mutableListOf<Double>()
            for (domainWord in domainWords) {
                positivePMI.add(domainWord.positivePMI)
                negativePMI.add(domainWord.negativePMI)
            }
            return Pair(positivePMI, negativePMI)
        }

        //creates a domain specific lexicon
        @JvmStatic
        fun createDomainLexicon(
            inputPath: String, outputPath: String, outputFileName: String, minWordFrequency: Int = 2,
            minPMI: Double = 0.0, classifierConfig: ClassifierConfig
        ) {
            val domainWordsAndPolarityVector = TextAnalysisTools.createDomainWords(inputPath, classifierConfig)
            val domainWords = domainWordsAndPolarityVector.first
            val polarityVector = domainWordsAndPolarityVector.second
            val pmiVectors = createPMIVectors(domainWords)
            normalizeValues(
                domainWords, polarityVector, minPMI, minWordFrequency,
                pmiVectors.first, pmiVectors.second
            )
            val lexicon = buildContentOfLexicon(domainWords, classifierConfig, inputPath)
            File("$outputPath$outputFileName.txt").writeText(lexicon)
        }
    }
}
