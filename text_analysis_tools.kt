package sample

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.CharArraySet
import org.apache.lucene.analysis.de.GermanAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import java.io.File
import java.io.IOException
import java.io.StringReader
import kotlin.math.log2
import kotlin.math.max

class TextAnalysisTools {
    companion object {
        //html tools
        fun getStarsFromHTMLLine(line: String): String {
            for (token in line.split(" "))
                if (token.contains("data-review-rating")) {
                    if (!token.split("=")[1].split("\"")[1].contains("+"))
                        return token.split("=")[1].split("\"")[1] + ".0"
                }
            return ""
        }
        fun getTitleFromHTMLLine(line: String) = " " + line.split(">")[1].split("<")[0].trim()
        fun lineContainsTitle(line: String) = line.split(">")[0].trim() == "<h4 class=\"pl_headline50 pl_my25\""
        fun lineContainsStars(line: String) = line.contains("data-review-rating")
        fun checkBeginningOfTextContent(line: String) = line.contains("cr-review-text")
        fun getContentFromHTMLLine(line: String) = " ${line.trim().drop(1).dropLast(7)}\n"
        fun createDataFromSingleHTMLFile(inputFile: File): String {
            var textContentIndicator = false
            val contentFromSingleHTML = StringBuilder()
            inputFile.forEachLine {
                try {
                    if (TextAnalysisTools.lineContainsStars(it))
                        contentFromSingleHTML.append(TextAnalysisTools.getStarsFromHTMLLine(it))
                    if (TextAnalysisTools.lineContainsTitle(it))
                        contentFromSingleHTML.append(TextAnalysisTools.getTitleFromHTMLLine(it))
                    if (textContentIndicator) {
                        contentFromSingleHTML.append(TextAnalysisTools.getContentFromHTMLLine(it))
                        textContentIndicator = false
                    }
                    textContentIndicator = TextAnalysisTools.checkBeginningOfTextContent(it)
                } catch (e: IndexOutOfBoundsException) {
                    println(e.message)
                }
            }
            return contentFromSingleHTML.toString()
        }

        fun starsOfReview(review: String) = review.split(" ")[0].toDouble()
        fun contentOfReview(review: String) = if (review.split(" ").size > 1)
            review.split(Regex("\\s"), 2)[1] else ""

        @JvmStatic
        fun dataFileSummary(file: File): Pair<Int, Int> {
            var numberOfPositiveDocs = 0
            var numberOfNegativeDocs = 0
            file.forEachLine {
                val review = Review(it)
                if (review.isContentNotEmpty) {
                    if (review.isPositive()) numberOfPositiveDocs++
                    if (review.isNegative()) numberOfNegativeDocs++
                }
            }
            return Pair(numberOfPositiveDocs, numberOfNegativeDocs)
        }

        fun starsToClass(stars: Double) = if (stars == 1.0 || stars == 2.0) -1
        else if (stars == 4.0 || stars == 5.0) 1 else 0

        fun minMaxNormalization(value: Double, min: Double, max: Double, l: Double, r: Double) =
            ((r - l) * ((value - min) / (max - min))) + l

        //normalize a text using GermanAnalyzer
        fun tokenizeWithGermanAnalyzer(
            input: String, analyzer: Analyzer = GermanAnalyzer(),
            fieldName: String = "word"
        ): String {
            val tokens = StringBuilder()
            try {
                analyzer.tokenStream(fieldName, StringReader(input)).use { tokenStream ->
                    tokenStream.reset() // required
                    while (tokenStream.incrementToken()) {
                        tokens.append(tokenStream.getAttribute(CharTermAttribute::class.java).toString())
                        tokens.append(" ")
                    }

                    tokenStream.close()
                }
            } catch (e: IOException) {
                println("IOException in SentimentAnalyzer.Opinion")
            }
            return tokens.toString().trim()
        }

        //remove tokens containing symbols other than letters from doc
        fun cleanText(content: String) = content.split(
            " "
        ).filter {
            isWordClean(it)
        }.joinToString(" ").trim().ifEmpty { "" }

        fun cleanAndTokenizeText(content: String) = tokenizeWithGermanAnalyzer(content).split(
            " "
        ).filter {
            isWordClean(it)
        }.joinToString(" ").trim().ifEmpty { "" }

        //check if word does not contain special symbols
        fun isWordClean(word: String) =
            !word.contains("[0-9!\\\"#\$%&'()*+,-./:;\\\\\\\\<=>?@\\\\[\\\\]^_`{|}~]".toRegex())

        class Review(text: String) {
            private val stars = starsOfReview(text)
            val numericalClassification = starsToClass(stars)
            val content = contentOfReview(text)
            private val normalizedContent = tokenizeWithGermanAnalyzer(content)
            private val normalizedContentInclusiveNegation =
                tokenizeWithGermanAnalyzer(content, germanAnalyzerWithNegation())
            val cleanedContent = cleanText(normalizedContent)
            val cleanedContentInclusiveNegation = cleanText(
                normalizedContentInclusiveNegation
            )
            private val combinations = findCombinations(cleanedContentInclusiveNegation)
            val isContentNotEmpty = cleanedContent.isNotEmpty()
            fun containsWord(word: String) = normalizedContent.contains(word)
            fun containsCombination(combination: List<String>) = combinations.contains(combination)
            fun isPositive() = numericalClassification == 1
            fun isNegative() = numericalClassification == -1
        }

        //find all possible combinations in a text
        fun findCombinations(text: String): List<List<String>> =
            createCombinationsOfTwoWords(createSegments(text)) +
                    createCombinationsOfThreeWords((createSegments(text)))

        //find possible combinations in a file
        fun findCombinationsInFile(
            inputPath: String, minPMI: Double,
            minFrequency: Int
        ): List<Combination> {
            val stringCombinations = buildStringCombinationsFromFile(inputPath)
            val stringCombinationsOfTwoWords = stringCombinations.first
            val stringCombinationsOfThreeWords = stringCombinations.second
            val combinationList = constructCombinationList(
                stringCombinationsOfTwoWords,
                stringCombinationsOfThreeWords, inputPath, minPMI, minFrequency
            )
            val pmiVectors = calculatePMIVectors(combinationList)
            normalizeCombinations(combinationList, pmiVectors.first, pmiVectors.second)
            return combinationList
        }

        //construct the content combinations file
        fun createContentOfCombinationsFile(combinations: List<Combination>): String {
            val content = StringBuilder()
            for (combination in combinations)
                content.append(combination.write())
            return content.toString()
        }

        private fun calculatePMIVectors(combinations: List<Combination>): Pair<List<Double>, List<Double>> {
            val positivePMIVector = mutableListOf<Double>()
            val negativePMIVector = mutableListOf<Double>()
            for (combination in combinations) {
                positivePMIVector.add(combination.positivePMI)
                negativePMIVector.add(combination.negativePMI)
            }
            return Pair(positivePMIVector, negativePMIVector)
        }

        private fun normalizeCombinations(
            combinations: List<Combination>,
            positivePMIVector: List<Double>,
            negativePMIVector: List<Double>
        ) {
            val maxPositivePMI = positivePMIVector.maxOrNull()
            val minPositivePMI = positivePMIVector.minOrNull()
            val maxNegativePMI = negativePMIVector.maxOrNull()
            val minNegativePMI = negativePMIVector.minOrNull()
            for (combination in combinations)
                combination.normalizePMI(minPositivePMI!!, maxPositivePMI!!, minNegativePMI!!, maxNegativePMI!!)
        }


        private fun buildStringCombinationsFromFile(inputPath: String): Pair<List<List<String>>,
                List<List<String>>> {
            val stringCombinationsOfTwoWords = mutableListOf<List<String>>()
            val stringCombinationsOfThreeWords = mutableListOf<List<String>>()
            File(inputPath).forEachLine {
                stringCombinationsOfTwoWords += createCombinationsOfTwoWords(
                    createSegments(
                        Review(it).cleanedContentInclusiveNegation
                    )
                )
                stringCombinationsOfThreeWords += createCombinationsOfThreeWords(
                    createSegments(
                        Review(it).cleanedContentInclusiveNegation
                    )
                )
            }
            return Pair(
                stringCombinationsOfTwoWords.distinct(),
                stringCombinationsOfThreeWords.distinct()
            )
        }

        private fun createCombinationsOfTwoWords(segments: List<List<String>>): List<List<String>> {
            val combinations = mutableListOf<List<String>>()
            for (segment in segments)
                if (segment.size > 1)
                    for (i in 1 until segment.size)
                        combinations.add(listOf(segment[0], segment[i]))
            return combinations.distinct()
        }

        private fun createCombinationsOfThreeWords(segments: List<List<String>>): List<List<String>> {
            val combinations = mutableListOf<List<String>>()
            for (segment in segments)
                if (segment.size > 2)
                    for (j in 1 until segment.size)
                        for (k in j + 1 until segment.size)
                            combinations.add(listOf(segment[0], segment[j], segment[k]))
            return combinations.distinct()
        }

        private fun constructCombinationList(
            combinationsOfTwoWords: List<List<String>>,
            combinationsOfThreeWords: List<List<String>>,
            inputPath: String, minPMI: Double, minFrequency: Int
        ): List<Combination> {
            val dataSummary = dataFileSummary(File(inputPath))
            val docsOfFile = docsOfFile(inputPath)
            val combinationElementsOfTwoWords = listStringToCombinationList(
                combinationsOfTwoWords, docsOfFile, dataSummary, minPMI, minFrequency
            )
            val combinationElementsOfThreeWords = listStringToCombinationList(
                combinationsOfThreeWords, docsOfFile, dataSummary, minPMI, minFrequency
            )
            return combinationElementsOfTwoWords + combinationElementsOfThreeWords
        }

        private fun listStringToCombinationList(
            combinations: List<List<String>>,
            docsOfFile: List<Review>, dataSummary: Pair<Int, Int>, minPMI: Double,
            minFrequency: Int
        ): List<Combination> {
            val listOfCombinations = mutableListOf<Combination>()
            val numberOfPositiveDocs = dataSummary.first
            val numberOfNegativeDocs = dataSummary.second
            val totalNumberOfDocs = numberOfPositiveDocs + numberOfNegativeDocs
            for (combination in combinations) {
                val docsOfCombination = docsOfCombination(combination, docsOfFile)
                val positiveDocsContainingCombination = docsOfCombination.first
                val negativeDocsContainingCombination = docsOfCombination.second
                val numberOfDocsContainingCombination = positiveDocsContainingCombination +
                        negativeDocsContainingCombination
                val positivePMI = calculatePositivePMI(
                    numberOfDocsContainingCombination,
                    numberOfPositiveDocs, totalNumberOfDocs, positiveDocsContainingCombination
                )
                val negativePMI = calculateNegativePMI(
                    numberOfDocsContainingCombination,
                    numberOfNegativeDocs, totalNumberOfDocs, negativeDocsContainingCombination
                )
                val probabilityPresentConditionPos = positiveDocsContainingCombination / (1.0 * numberOfPositiveDocs)
                val probabilityPresentConditionNegative =
                    negativeDocsContainingCombination / (1.0 * numberOfNegativeDocs)
                val combinationListElement = Combination(
                    combination,
                    positivePMI,
                    negativePMI,
                    frequency = numberOfDocsContainingCombination,
                    numberOfNegativeDocsContainingCombination = negativeDocsContainingCombination,
                    numberOfPositiveDocsContainingCombination = positiveDocsContainingCombination,
                    probabilityPresentConditionPos = probabilityPresentConditionPos,
                    probabilityPresentConditionNegative = probabilityPresentConditionNegative
                )
                combinationListElement.isRelevant = isRelevant(combinationListElement, minPMI, minFrequency)
                listOfCombinations.add(combinationListElement)
            }
            return listOfCombinations
        }

        //check if a combination relevant
        private fun isRelevant(combination: Combination, minPMI: Double, minFrequency: Int) =
            (max(combination.positivePMI, combination.negativePMI) >= minPMI) &&
                    (combination.frequency >= minFrequency)

        //check if a word relevant
        fun isRelevant(domainWord: DomainWord, minPMI: Double, minWordFrequency: Int) =
            (max(domainWord.positivePMI, domainWord.negativePMI) >= minPMI) &&
                    (domainWord.wordFrequency >= minWordFrequency)

        //create segments of size 5 from a text
        private fun createSegments(text: String) = text.split(" ").windowed(5, 1)
        private fun textContainsCombination(text: String, combination: List<String>): Boolean {
            for (possibleCombination in createCombinationsOfTwoWords(createSegments(text)))
                if (combination == possibleCombination) return true
            for (possibleCombination in createCombinationsOfThreeWords(createSegments(text)))
                if (combination == possibleCombination) return true
            return false
        }

        fun splitTextToStringList(text: String) = text.split(" ")

        //create normalized training data
        private fun splitDataIntoWordsAndDocs(inputPath: String): Pair<List<String>,
                MutableList<Review>> {
            val words = mutableListOf<String>()
            val docs = mutableListOf<Review>()
            File(inputPath).forEachLine {
                val review = Review(it)
                if (review.isContentNotEmpty) {
                    docs.add(review)
                    for (word in review.cleanedContent.split(" "))
                        words.add(word)
                }
            }
            return Pair(words.distinct(), docs)
        }

        fun docsOfFile(inputPath: String): List<Review> {
            val docs = mutableListOf<Review>()
            File(inputPath).forEachLine {
                val review = Review(it)
                if (review.isContentNotEmpty)
                    docs.add(review)
            }
            return docs
        }

        //number of positive and negative docs containing a combination
        private fun docsOfCombination(
            combination: List<String>,
            docs: List<Review>
        ): Pair<Int, Int> {
            var numberOfNegativeDocsContainingCombination = 0
            var numberOfPositiveDocsContainingCombination = 0
            for (doc in docs) {
                if (textContainsCombination(
                        doc.cleanedContentInclusiveNegation,
                        combination
                    )
                ) {
                    if (doc.numericalClassification == 1)
                        numberOfPositiveDocsContainingCombination++
                    if (doc.numericalClassification == -1)
                        numberOfNegativeDocsContainingCombination++
                }
            }
            //Laplace Correction for the bayes classifier to avoid zero probabilities
            if (numberOfNegativeDocsContainingCombination == 0) numberOfNegativeDocsContainingCombination++
            if (numberOfPositiveDocsContainingCombination == 0) numberOfPositiveDocsContainingCombination++
            return Pair(numberOfPositiveDocsContainingCombination, numberOfNegativeDocsContainingCombination)
        }

        //number of positive and negative docs containing a word
        fun docsOfWord(
            word: String, docs: List<Review>,
            classifierConfig: ClassifierConfig
        ): Pair<Int, Int> {
            var numberOfNegativeDocsContainingWord = 0
            var numberOfPositiveDocsContainingWord = 0
            for (doc in docs) {
                if (doc.containsWord(word)) {
                    if (doc.numericalClassification == 1)
                        numberOfPositiveDocsContainingWord++
                    if (doc.numericalClassification == -1)
                        numberOfNegativeDocsContainingWord++
                }
            }
            //Laplace Correction for the bayes classifier to avoid zero probabilities
            if (classifierConfig == ClassifierConfig.BAYES) {
                if (numberOfNegativeDocsContainingWord == 0) numberOfNegativeDocsContainingWord++
                if (numberOfPositiveDocsContainingWord == 0) numberOfPositiveDocsContainingWord++
            }
            return Pair(numberOfPositiveDocsContainingWord, numberOfNegativeDocsContainingWord)
        }

        //create a word list and a polarity vector
        fun stringWordsToDomainWords(
            words: List<String>, docs: List<Review>,
            totalNegativeDocs: Int, totalPositiveDocs: Int,
            classifierConfig: ClassifierConfig
        ): Pair<List<DomainWord>, List<Double>> {
            val domainWords = mutableListOf<DomainWord>()
            val polarityVector = mutableListOf<Double>()
            for (word in words) {
                val docsOfWord = docsOfWord(
                    word, docs,
                    classifierConfig
                )
                val numberOfNegativeDocsContainingWord = docsOfWord.second
                val numberOfPositiveDocsContainingWord = docsOfWord.first
                val polarityInfo = calculatePolarity(
                    totalNegativeDocs, totalPositiveDocs,
                    numberOfNegativeDocsContainingWord, numberOfPositiveDocsContainingWord
                )
                val wordFrequency = numberOfNegativeDocsContainingWord + numberOfPositiveDocsContainingWord
                val domainWord = DomainWord(
                    word, polarityInfo.polarity, positivePMI = polarityInfo.positivePMI,
                    negativePMI = polarityInfo.negativePMI,
                    wordFrequency = wordFrequency,
                    numberOfPositiveDocsContainingWord = numberOfPositiveDocsContainingWord,
                    numberOfNegativeDocsContainingWord = numberOfNegativeDocsContainingWord
                )
                domainWords.add(domainWord)
                polarityVector.add(polarityInfo.polarity)
            }
            return Pair(domainWords, polarityVector)
        }

        fun createDomainWords(
            inputPath: String,
            classifierConfig: ClassifierConfig
        ):
                Pair<List<DomainWord>, List<Double>> {
            val normalizedContent = splitDataIntoWordsAndDocs(inputPath)
            val dataFileSummary = dataFileSummary(File(inputPath))
            val totalPositiveDocs = dataFileSummary.first
            val totalNegativeDocs = dataFileSummary.second
            val words = normalizedContent.first
            val domainWordsAnPolarityVector = stringWordsToDomainWords(
                words,
                docs = normalizedContent.second, totalNegativeDocs, totalPositiveDocs,
                classifierConfig
            )
            return Pair(
                domainWordsAnPolarityVector.first,
                domainWordsAnPolarityVector.second
            )
        }


        private fun calculatePolarity(
            numberOfNegativeDocs: Int, numberOfPositiveDocs: Int,
            numberOfNegativeDocsContainingWord: Int, numberOfPositiveDocsContainingWord: Int
        ): PolarityInfo {
            val numberOfDocsContainingWord = numberOfNegativeDocsContainingWord + numberOfPositiveDocsContainingWord
            val totalNumberOfDocs = numberOfPositiveDocs + numberOfNegativeDocs
            val positivePMIValue = calculatePositivePMI(
                numberOfDocsContainingWord, numberOfPositiveDocs,
                totalNumberOfDocs, numberOfPositiveDocsContainingWord
            )
            val negativePMIValue = calculateNegativePMI(
                numberOfDocsContainingWord, numberOfNegativeDocs, totalNumberOfDocs,
                numberOfNegativeDocsContainingWord
            )
            val polarity = positivePMIValue - negativePMIValue
            return PolarityInfo(polarity, positivePMIValue, negativePMIValue)
        }

        private fun calculateNegativePMI(
            numberOfDocsContainingFeature: Int, numberOfNegativeDocs: Int,
            totalNumberOfDocs: Int, numberOfNegativeDocsContainingFeature: Int
        ) =
            if (numberOfNegativeDocsContainingFeature != 0) calculatePMI(
                probabilityOfFeature(numberOfDocsContainingFeature, totalNumberOfDocs),
                probabilityOfNegativeDocs(numberOfNegativeDocs, totalNumberOfDocs),
                probabilityNeg(numberOfNegativeDocsContainingFeature, totalNumberOfDocs)
            ) else 0.0

        private fun calculatePositivePMI(
            numberOfDocsContainingFeature: Int, numberOfPositiveDocs: Int,
            totalNumberOfDocs: Int, numberOfPositiveDocsContainingFeature: Int
        ) =
            if (numberOfPositiveDocsContainingFeature != 0) calculatePMI(
                probabilityOfFeature(
                    numberOfDocsContainingFeature, totalNumberOfDocs
                ), probabilityOfPositiveDocs(numberOfPositiveDocs, totalNumberOfDocs), probabilityPos(
                    numberOfPositiveDocsContainingFeature, totalNumberOfDocs
                )
            ) else 0.0

        private fun calculatePMI(
            probabilityOfFeature: Double, probabilityDocs: Double,
            probabilityFeatureInDocs: Double
        ): Double {
            return log2(probabilityFeatureInDocs * 1.0 / (probabilityOfFeature * probabilityDocs))
        }

        private fun probabilityOfNegativeDocs(
            totalNumberOfNegativeDocs: Int,
            totalNumberOfDocs: Int
        ) =
            totalNumberOfNegativeDocs * 1.0 / totalNumberOfDocs

        private fun probabilityOfPositiveDocs(totalNumberOfPositiveDocs: Int, totalNumberOfDocs: Int) =
            totalNumberOfPositiveDocs * 1.0 / totalNumberOfDocs

        private fun probabilityPos(
            numberOfPositiveDocsContainingFeature: Int,
            totalNumberOfDocs: Int
        ) =
            numberOfPositiveDocsContainingFeature * 1.0 / totalNumberOfDocs

        private fun probabilityNeg(
            numberOfNegativeDocsContainingFeature: Int,
            totalNumberOfDocs: Int
        ) =
            numberOfNegativeDocsContainingFeature * 1.0 / totalNumberOfDocs

        private fun probabilityOfFeature(numberOfDocsContainingFeature: Int, totalNumberOfDocs: Int) =
            (numberOfDocsContainingFeature * 1.0) / totalNumberOfDocs

        fun createLineInDomainLexicon(domainWord: DomainWord): String {
            return "${domainWord.word} ${domainWord.polarity}\n"
        }

        fun createLineOfFeatureAsSingleWord(
            domainWord: DomainWord,
            totalPositive: Int = 1,
            totalNegative: Int = 1
        ): String {
            val probabilityPresentConditionPositive =
                domainWord.numberOfPositiveDocsContainingWord * 1.0 / totalPositive
            val probabilityPresentConditionNegative =
                domainWord.numberOfNegativeDocsContainingWord * 1.0 / totalNegative
            return "${domainWord.word}#${domainWord.numberOfPositiveDocsContainingWord}#${domainWord.numberOfNegativeDocsContainingWord}#" +
                    "$probabilityPresentConditionPositive#$probabilityPresentConditionNegative#${domainWord.positivePMI}#${domainWord.negativePMI}\n"
        }

        private data class PolarityInfo(val polarity: Double, val positivePMI: Double, val negativePMI: Double)
        data class DomainWord(
            val word: String, var polarity: Double,
            var positivePMI: Double = 0.0, var negativePMI: Double = 0.0,
            var wordFrequency: Int = 0,
            var isRelevant: Boolean = true,
            var numberOfPositiveDocsContainingWord: Int = 0,
            var numberOfNegativeDocsContainingWord: Int = 0
        )

        data class Combination(
            val words: List<String>, var positivePMI: Double,
            var negativePMI: Double, val frequency: Int,
            var isRelevant: Boolean = false, val numberOfPositiveDocsContainingCombination: Int,
            val numberOfNegativeDocsContainingCombination: Int, val probabilityPresentConditionPos: Double,
            val probabilityPresentConditionNegative: Double
        )
        //Feature files (combination and singleWords) have the following format
        //0:words#1:posDocs#2:negDocs#3:p(f|pos)#4:P(f|neg)#5:posPMI#6:negPMI
        private fun Combination.write() =
            "${this.words.toRowOfWords()}#${this.numberOfPositiveDocsContainingCombination}" +
                    "#${this.numberOfNegativeDocsContainingCombination}#${this.probabilityPresentConditionPos}#" +
                    "${this.probabilityPresentConditionNegative}#${this.positivePMI}" +
                    "#${this.negativePMI}\n"

        private fun Combination.normalizePMI(
            minPositivePMI: Double, maxPositivePMI: Double,
            minNegativePMI: Double, maxNegativePMI: Double
        ) {
            if (this.positivePMI > 0.0) this.positivePMI =
                minMaxNormalization(this.positivePMI, 0.0, maxPositivePMI!!, 0.0, 1.0)
            if (this.negativePMI > 0.0) this.negativePMI =
                minMaxNormalization(this.negativePMI, 0.0, maxNegativePMI!!, 0.0, 1.0)
            if (this.positivePMI < 0.0) this.positivePMI =
                minMaxNormalization(this.positivePMI, minPositivePMI!!, 0.0, -1.0, 0.0)
            if (this.negativePMI < 0.0) this.negativePMI =
                minMaxNormalization(this.negativePMI, minNegativePMI!!, 0.0, -1.0, 0.0)
        }

        fun DomainWord.normalizePolarity(maxPol: Double, minPol: Double) {
            if (this.polarity > 0.0)
                this.polarity = minMaxNormalization(this.polarity, 0.0, maxPol!!, 0.0, 1.0)
            if (this.polarity < 0.0)
                this.polarity = minMaxNormalization(this.polarity, minPol!!, 0.0, -1.0, 0.0)
        }

        fun DomainWord.normalizePMI(
            minPositivePMI: Double, maxPositivePMI: Double,
            minNegativePMI: Double, maxNegativePMI: Double
        ) {
            if (this.positivePMI > 0.0) this.positivePMI =
                minMaxNormalization(this.positivePMI, 0.0, maxPositivePMI!!, 0.0, 1.0)
            if (this.negativePMI > 0.0) this.negativePMI =
                minMaxNormalization(this.negativePMI, 0.0, maxNegativePMI!!, 0.0, 1.0)
            if (this.positivePMI < 0.0) this.positivePMI =
                minMaxNormalization(this.positivePMI, minPositivePMI!!, 0.0, -1.0, 0.0)
            if (this.negativePMI < 0.0) this.negativePMI =
                minMaxNormalization(this.negativePMI, minNegativePMI!!, 0.0, -1.0, 0.0)
        }
        //join a list of words to a single text
        private fun List<String>.toRowOfWords() = this.joinToString(" ").trim()

        //lucene german analyzer that does not filter out negation
        fun germanAnalyzerWithNegation(): GermanAnalyzer {
            val newStopWordsList = listOf(
                "denn",
                "daß",
                "muss",
                "allem",
                "allen",
                "dem",
                "den",
                "aller",
                "alles",
                "der",
                "des",
                "über",
                "ihnen",
                "andere",
                "meinem",
                "durch",
                "manchem",
                "manchen",
                "anderm",
                "andern",
                "meines",
                "am",
                "an",
                "anderr",
                "anders",
                "doch",
                "welches",
                "jene",
                "denselben",
                "wollen",
                "meinen",
                "wirst",
                "dasselbe",
                "ein",
                "hatte",
                "sollte",
                "seine",
                "unter",
                "mancher",
                "mir",
                "mit",
                "so",
                "während",
                "anderem",
                "anderen",
                "meiner",
                "dieselben",
                "anderes",
                "einen",
                "einer",
                "dazu",
                "musste",
                "jenem",
                "jenen",
                "da",
                "derer",
                "manches",
                "jener",
                "jenes",
                "weil",
                "desselben",
                "wird",
                "die",
                "bei",
                "hab",
                "ist",
                "sind",
                "dir",
                "deinem",
                "deinen",
                "du",
                "zum",
                "deiner",
                "deines",
                "zur",
                "um",
                "viel",
                "hat",
                "könnte",
                "welche",
                "unse",
                "derselbe",
                "er",
                "es",
                "das",
                "gewesen",
                "aber",
                "auf",
                "ich",
                "habe",
                "damit",
                "mein",
                "eines",
                "aus",
                "einigen",
                "wieder",
                "ander",
                "indem",
                "zwar",
                "einiges",
                "einmal",
                "sie",
                "diese",
                "dann",
                "vom",
                "von",
                "wo",
                "vor",
                "soll",
                "sehr",
                "eure",
                "alle",
                "werde",
                "weiter",
                "was",
                "und",
                "würde",
                "jedem",
                "jeden",
                "oder",
                "jeder",
                "sein",
                "uns",
                "jede",
                "demselben",
                "mich",
                "haben",
                "manche",
                "bin",
                "dessen",
                "bis",
                "wenn",
                "sondern",
                "solche",
                "euch",
                "jedes",
                "ihrem",
                "ihren",
                "ihrer",
                "ihres",
                "unsem",
                "unsen",
                "im",
                "in",
                "unser",
                "unses",
                "ihm",
                "ihn",
                "wollte",
                "ihr",
                "wir",
                "anderer",
                "sich",
                "dort",
                "würden",
                "derselben",
                "welcher",
                "meine",
                "warst",
                "ohne",
                "für",
                "nach",
                "weg",
                "man",
                "eine",
                "euer",
                "solchen",
                "machen",
                "solches",
                "dein",
                "hier",
                "wie",
                "sonst",
                "hinter",
                "zwischen",
                "nun",
                "nur",
                "hin",
                "einem",
                "einigem",
                "waren",
                "ihre",
                "jetzt",
                "einiger",
                "kann",
                "auch",
                "war",
                "dieselbe",
                "werden",
                "einig",
                "hatten",
                "dieser",
                "als",
                "selbst",
                "dich",
                "einige",
                "können",
                "gegen",
                "seinem",
                "deine",
                "solchem",
                "also",
                "solcher",
                "zu",
                "will",
                "ob",
                "welchem",
                "welchen",
                "etwas",
                "diesem",
                "diesen",
                "seinen",
                "dieses",
                "seiner",
                "seines",
                "noch",
                "eurem",
                "euren",
                "ins",
                "eurer",
                "eures",
                "dies",
                "bist"
            )
            val newStopSet = CharArraySet(newStopWordsList, true)
            return GermanAnalyzer(newStopSet)
        }

        fun germanAnalyzerWithoutStopWords() = GermanAnalyzer(CharArraySet(0, false))
        data class OrderedWord(val word: String, val order: Int, val pos: String)
        data class OpinionWord(
            val word: String, val polarity: Double = 0.0, val pos: String = ""
        )

        fun createOrderedWordsFromText(text: String): List<OrderedWord> =
            POSParser.parseText(text).mapIndexedNotNull { index, opinionWord ->
                createOrderedWord(index, opinionWord)
            }

        //extract a word with order >= 1
        private fun createOrderedWord(index: Int, opinionWord: OpinionWord): OrderedWord? {
            val tokenizedWord = tokenizeWithGermanAnalyzer(opinionWord.word, germanAnalyzerWithoutStopWords())
            return if (tokenizedWord.isNotBlank()) OrderedWord(
                tokenizedWord, index + 1,
                opinionWord.pos
            ) else null
        }

        //create neighbourhood of a word
        fun scopeOfWord(wordOrder: Int, orderedWords: List<OrderedWord>): List<OrderedWord> {
            return if (orderedWords.size < 2) orderedWords
            else when (wordOrder) {
                1 -> orderedWords.drop(1).cutForScopeAfter()
                orderedWords.last().order -> orderedWords.dropLast(1).cutForScopeBefore()
                else -> cutWordAtPositionFromList(orderedWords, wordOrder)
            }

        }
        //maximal 4 words before oder after target

        private fun List<OrderedWord>.cutForScopeAfter(): List<OrderedWord> {
            return if (this.size > 4)
                this.take(4)
            else this
        }

        private fun List<OrderedWord>.cutForScopeBefore(): List<OrderedWord> {
            return if (this.size > 4)
                this.takeLast(4)
            else this
        }
        //cut a word in a specific position from a list of words
        private fun cutWordAtPositionFromList(
            contextWords: List<OrderedWord>,
            wordPosition: Int
        ): List<OrderedWord> {
            val firstPart = mutableListOf<OrderedWord>()
            val secondPart = mutableListOf<OrderedWord>()
            for (parsedWord in contextWords) {
                if (parsedWord.order < wordPosition) firstPart.add(parsedWord)
                if (parsedWord.order > wordPosition) secondPart.add(parsedWord)
            }
            return firstPart.cutForScopeBefore() + secondPart.cutForScopeAfter()
        }
        //list of shifters
        private val shifters = listOf(
            createShifter("nicht", TargetOfShifter.everything),
            createShifter("aufhor"), createShifter("aufhort"), createShifter("aufhorst"),
            createShifter("aufgehort"), createShifter("beend"), createShifter("beend"), createShifter("abflauen"),
            createShifter("bewältigen"), createShifter("nie"), createShifter("nix"),
            createShifter("keinsterweise"), createShifter("keinerweise"), createShifter("niemals"),
            createShifter("nichts"), createShifter("trotzen"), createShifter("ohne"),
            createShifter("kein", TargetOfShifter.everything), createShifter("einzigartig", TargetOfShifter.everything),
            createShifter("weniger", TargetOfShifter.everything), createShifter("kaum"),
            createShifter("keinesfalls"), createShifter("ebensowenig")
        )
        //list of modal verbs
        private val modalWords = listOf(
            tokenizeWithGermanAnalyzer("darf"),
            tokenizeWithGermanAnalyzer("durf"),
            tokenizeWithGermanAnalyzer("durft"),
            tokenizeWithGermanAnalyzer("konn"),
            tokenizeWithGermanAnalyzer("konnt"),
            tokenizeWithGermanAnalyzer("kann"),
            tokenizeWithGermanAnalyzer("sollen")
        )
        //list of intensifiers
        private val intensifiers = listOf(
            createIntensifier("besonders", TargetOfIntensifier.everything, "adv"),
            createIntensifier("ständig"), createIntensifier("völlig"), createIntensifier(
                "steigern",
                TargetOfIntensifier.nouns, "verben"
            ), createIntensifier(
                "sehr",
                TargetOfIntensifier.everything, "adv"
            ), createIntensifier("äusserst"),
            createIntensifier("äußerst"), createIntensifier("enorm"), createIntensifier("erheblich"),
            createIntensifier("bleiben", TargetOfIntensifier.nouns, "verben"), createIntensifier("exzeptionell"),
            createIntensifier("viel", TargetOfIntensifier.everything, "adv"),
            createIntensifier("ziemlich", TargetOfIntensifier.everything, "adv"),
            createIntensifier("zusätzlich"),
            createIntensifier("maximal"), createIntensifier("extrem"), createIntensifier("intensiv"),
            createIntensifier("unvermindert"), createIntensifier("groß"),
            createIntensifier("gross"), createIntensifier("recht"), createIntensifier("tatsächlich"),
            createIntensifier("unglaublich"), createIntensifier("wirklich"), createIntensifier("richtig"),
            createIntensifier("verdammt")
        )

        enum class TargetOfIntensifier { everything, nouns }
        enum class TargetOfShifter { everything, nouns }

        data class Intensifier(val word: String, val pos: String, val targets: TargetOfIntensifier)
        data class Shifter(val word: String, val targets: TargetOfShifter)

        private fun createIntensifier(
            word: String, target: TargetOfIntensifier =
                TargetOfIntensifier.everything, pos: String = "adj"
        ) = Intensifier(
            tokenizeWithGermanAnalyzer(word, germanAnalyzerWithoutStopWords()),
            pos,
            target
        )


        private fun createShifter(word: String, target: TargetOfShifter = TargetOfShifter.nouns) = Shifter(
            tokenizeWithGermanAnalyzer(
                word,
                germanAnalyzerWithoutStopWords()
            ), target
        )

        fun List<OrderedWord>?.containIntensifiers(targetPOS: String): Boolean {
            for (intensifier in intensifiers) {
                val intensifierInsideScope = this?.contains(intensifier)
                if (intensifierInsideScope != null) {
                    if (intensifierInsideScope.contained)
                        if (intensifier.pos == intensifierInsideScope.orderedAnnotatedWord!!.pos)
                            if (intensifier.targets == TargetOfIntensifier.nouns)
                                return when (targetPOS) {
                                    "nomen" -> true
                                    else -> false
                                }
                            else if (targetPOS == "adj" || targetPOS == "nomen") return true
                }
            }
            return false
        }

        private fun List<OrderedWord>?.contains(intensifier: Intensifier):
                IntensifierInsideScope {
            this?.forEach { parsedOrderedWord ->
                if (parsedOrderedWord.word == intensifier.word)
                    return IntensifierInsideScope(parsedOrderedWord, true)
            }
            return IntensifierInsideScope(null, false)
        }

        private data class IntensifierInsideScope(
            val orderedAnnotatedWord: OrderedWord?,
            val contained: Boolean
        )

        private fun List<OrderedWord>?.contains(shifter: Shifter): Boolean {
            this?.forEach { parsedOrderedWord ->
                if (parsedOrderedWord.word == shifter.word)
                    return true
            }
            return false
        }

        private fun List<OrderedWord>?.contains(word: String): Boolean {
            this?.forEach { parsedOrderedWord ->
                if (parsedOrderedWord.word == word)
                    return true
            }
            return false
        }

        fun List<OrderedWord>?.containModal(): Boolean {
            for (modal in modalWords)
                if (this.contains(modal))
                    return true
            return false
        }

        fun List<OrderedWord>?.containShifter(targetPOS: String): Boolean {
            for (shifter in shifters)
                if (this.contains(shifter))
                    if (shifter.targets == TargetOfShifter.nouns)
                        return when (targetPOS) {
                            "nomen" -> true
                            else -> false
                        }
                    else if (targetPOS == "nomen" || targetPOS == "adj") return true
            return false
        }

        //to check for intensifier, shifter and modals use the functions in this way:
        //createScope(...).containIntensifiers() ...
        class Opinion(opinion: String) {
            companion object {
                private const val noAlphaBehindHyphen = "((?<!\\p{Alpha})-)"
                private const val noAlphaAheadHyphen = "(-(?!(\\p{Alpha})))"
                private const val punctuationExceptHyphen = "[\\p{Punct}&&[^-]]"

                private fun String.markUpSentenceBreaks() = this.replace(
                    Regex(
                        punctuationExceptHyphen + "|" +
                                noAlphaBehindHyphen + "|" + noAlphaAheadHyphen
                    ), "#"
                )

                private fun String.removeHTMLNewLine() = this.replace("<br>", "")
                private fun String.removeFirstWhiteSpace() = this.replace(Regex("^\\s"), "")
                private fun String.extractSentencesFromOpinion() =
                    this.removeHTMLNewLine().markUpSentenceBreaks().removeFirstWhiteSpace().split("#")
            }

            val opinionSentences = opinion.extractSentencesFromOpinion()
            private val cleanedOpinion = cleanText(opinion)
            private val tokenizedOpinion = tokenizeWithGermanAnalyzer(cleanedOpinion)
            private val tokenizedOpinionWords = splitTextToStringList(tokenizedOpinion)

            private fun listPolarities() = tokenizedOpinionWords.map {
                IndexSearcherTools.searchIndexWithPOS(it).polarity
            }

            fun meanPolarity() = listPolarities().average()
            fun numberOfPositiveWords() = listPolarities().count { it > 0 }
            fun numberOfNegativeWords() = listPolarities().count { it < 0 }
            fun numberOfNeutralWords() = listPolarities().count { it == 0.0 }
        }

        data class OpinionInfo(
            val result: Int, val positiveWords: Int,
            val negativeWords: Int, val neutralWords: Int, val polarity: Double
        )

        fun representTextAsVector(text: String, definitionVector: List<Feature>): List<Int> {
            val words = TextAnalysisTools.splitTextToStringList(
                TextAnalysisTools.cleanAndTokenizeText(text)
            )
            //just combinations of 2 or 3 words
            val combinations = TextAnalysisTools.findCombinations(text)
            return createVectorFromCombination(words, combinations, definitionVector)
        }

        fun createVectorFromCombination(
            singleWords: List<String>, combinations: List<List<String>>,
            definitionVector: List<Feature>
        ): List<Int> {
            val representationVector = mutableListOf<Int>()

            for (feature in definitionVector)
                if (isFeaturePresent(feature, singleWords, combinations))
                    representationVector.add(1)
                else representationVector.add(0)
            return representationVector
        }

        fun isFeaturePresent(feature: Feature, singleWords: List<String>, combinations: List<List<String>>): Boolean {
            for (word in singleWords)
                if (feature.combination.size == 1 && feature.combination[0] == word)
                    return true
            for (combination in combinations)
                if (feature.combination == combination)
                    return true
            return false
        }

        fun probabilityOfVectorInNegativeClass(
            vector: List<Int>, definitionVector: List<Feature>,
            probabilityOfNegativeDocs: Double
        ): Double {
            var probabilityConditionNegClass = 1.0
            for ((vectorIndex, feature) in definitionVector.withIndex()) {
                probabilityConditionNegClass *= if (vector[vectorIndex] == 1) {
                    feature.probabilityPresentConditionNegClass

                } else {
                    feature.probabilityNotPresentConditionNegClass
                }
            }
            return probabilityConditionNegClass * probabilityOfNegativeDocs

        }

        fun probabilityOfVectorInPositiveClass(
            vector: List<Int>, definitionVector: List<Feature>,
            probabilityOfPositiveDocs: Double
        ): Double {
            var probabilityConditionPosClass = 1.0
            for ((vectorIndex, feature) in definitionVector.withIndex()) {
                probabilityConditionPosClass *= if (vector[vectorIndex] == 1) {
                    feature.probabilityPresentConditionPosClass

                } else {
                    feature.probabilityNotPresentConditionPosClass

                }
            }
            return probabilityConditionPosClass * probabilityOfPositiveDocs
        }

        fun probabilitiesOfFile(file: File): Pair<Double, Double> {
            val docsOfFile = dataFileSummary(file)
            val positiveDocs = docsOfFile.first
            val negativeDocs = docsOfFile.second
            val totalDocs = positiveDocs + negativeDocs
            val probabilityOfPositiveDocs = positiveDocs / (1.0 * totalDocs)
            val probabilityOfNegativeDocs = negativeDocs / (1.0 * totalDocs)
            return Pair(probabilityOfPositiveDocs, probabilityOfNegativeDocs)
        }

    }
}

