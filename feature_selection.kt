package sample

import java.io.File
import kotlin.math.max

class FeatureSelection {
    companion object {
        private var inputPath = ""
        private var inputFileName = ""
        private var featuresPath = ""
        private var minPMI = 0.0
        private var minFrequency = 0
        var trainingFile = "${inputPath}${inputFileName}.txt"
        private var combinationsFile = "${featuresPath}combinations.txt"
        private var singleWordsFile = "${featuresPath}singleWords.txt"

        @JvmStatic
        fun isInitialized() = (inputPath == "")

        //initialize necessary paths for creating features
        @JvmStatic
        fun initializeFeatureOptions(
            inputPath: String, inputFileName: String, featuresPath: String,
            minPMI: Double, minFrequency: Int
        ) {
            println("features initialized")
            this.inputPath = inputPath
            this.inputFileName = inputFileName
            this.trainingFile = "${Companion.inputPath}${Companion.inputFileName}.txt"
            this.featuresPath = featuresPath
            this.combinationsFile = "${Companion.featuresPath}combinations.txt"
            this.singleWordsFile = "${Companion.featuresPath}singleWords.txt"
            this.minPMI = minPMI
            this.minFrequency = minFrequency
        }

        //create features of naive bayes from training data
        @JvmStatic
        fun createFeaturesFromTrainingData() {
            println("creating combinations")
            createCombinationsFile(
                trainingFile, combinationsFile,
                minPMI, minFrequency
            )
            println("creating single words")
            DomainLexicon.createDomainLexicon(
                trainingFile, featuresPath, "singleWords", minFrequency,
                minPMI, ClassifierConfig.BAYES
            )
        }

        private fun createCombinationsFile(inputPath: String, outputPath: String, minPMI: Double, minFrequency: Int) {
            val combinations = TextAnalysisTools.findCombinationsInFile(inputPath, minPMI, minFrequency)
            val content = TextAnalysisTools.createContentOfCombinationsFile(combinations)
            File(outputPath).writeText(content)
        }

        //definition vector is used to convert a text into a tuple or vector
        @JvmStatic
        fun createDefinitionVector(minPMI: Double, minFrequency: Int): List<Feature> {
            val features = mutableListOf<Feature>()
            File(singleWordsFile).forEachLine {
                createFeatureFromLine(it, minPMI, minFrequency)?.let { it1 -> features.add(it1) }
            }
            File(combinationsFile).forEachLine {
                createFeatureFromLine(it, minPMI, minFrequency)?.let { it1 -> features.add(it1) }
            }
            return features
        }

        //convert a line in the text file of combinations or single words to an object of type Feature
        private fun createFeatureFromLine(line: String, minPMI: Double, minFrequency: Int): Feature? {
            val elements = line.split("#")
            val positivePMI = elements[5].toDouble()
            val negativePMI = elements[6].toDouble()
            val frequency = elements[1].toInt() + elements[2].toInt()
            return if (isFeatureRelevant(positivePMI, negativePMI, frequency, minPMI, minFrequency)) Feature(
                elements[0].split(" "), elements[3].toDouble(),
                elements[4].toDouble()
            ) else null
        }

        private fun isFeatureRelevant(
            positivePMI: Double,
            negativePMI: Double,
            frequency: Int,
            minPMI: Double,
            minFrequency: Int
        ) =
            (max(positivePMI, negativePMI) >= minPMI) &&
                    (frequency >= minFrequency)
    }
}