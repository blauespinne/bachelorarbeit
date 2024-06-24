package sample

import sample.TextAnalysisTools.Companion.containIntensifiers
import sample.TextAnalysisTools.Companion.containModal
import sample.TextAnalysisTools.Companion.containShifter

data class RuleBasedClassifierOptions(
    val ignorePOS: Boolean = false, val ignoreNegation: Boolean = false,
    val ignorePolAdj: Boolean = false,
    val ignoreIntensifiers: Boolean = false, val ignoreModals: Boolean = false
)

class RuleBasedClassifier {
    companion object {
        //divide the result through 4 because 4 is the maximum value
        private fun calculateDocumentPolarityFromWordsOfSentences(
            wordsOfSentences: List<List<TextAnalysisTools.Companion.OrderedWord>>,
            ruleBasedClassifierOptions: RuleBasedClassifierOptions
        ) = wordsOfSentences.map {
            calculatePolarityFromWords(
                it,
                ruleBasedClassifierOptions
            )
        }.average() / 4.0

        private fun calculatePolarityFromWords(
            words: List<TextAnalysisTools.Companion.OrderedWord>,
            ruleBasedClassifierOptions: RuleBasedClassifierOptions
        ) =
            words.map {
                polarityFromLexicon(
                    it.word, it.pos,
                    ruleBasedClassifierOptions.ignorePOS
                ).applyNegationRule(
                    it, words, ruleBasedClassifierOptions.ignoreNegation
                ).applyPolAdjRule(it.pos, ruleBasedClassifierOptions.ignorePolAdj).applyIntensifierRule(
                    it, words, ruleBasedClassifierOptions.ignoreIntensifiers
                ).applyModalRule(it, words, ruleBasedClassifierOptions.ignoreModals)
            }.average()

        @JvmStatic
        fun ruleBasedClassifier(
            opinion: String,
            ruleBasedClassifierOptions: RuleBasedClassifierOptions =
                RuleBasedClassifierOptions()
        ): TextAnalysisTools.Companion.OpinionInfo {
            //create sentences
            val opinionTools = TextAnalysisTools.Companion.Opinion(opinion)
            val wordsOfSentences = opinionTools.opinionSentences.filter { it.isNotBlank() }.map {
                TextAnalysisTools.createOrderedWordsFromText(it)
            }
            val documentPolarity = calculateDocumentPolarityFromWordsOfSentences(
                wordsOfSentences,
                ruleBasedClassifierOptions
            )
            return TextAnalysisTools.Companion.OpinionInfo(
                if (documentPolarity > 0.0)
                    1 else -1, 0, 0, 0,
                documentPolarity
            )

        }


        //Feature Modals
        private fun Double.applyModalRule(
            parsedWord: TextAnalysisTools.Companion.OrderedWord,
            orderedWords: List<TextAnalysisTools.Companion.OrderedWord>, ignore: Boolean = false
        ) =
            if (ignore) this else if (TextAnalysisTools.scopeOfWord(parsedWord.order, orderedWords)
                    .containModal()
            ) 0.0 else this

        //Feature Intensifier
        private fun Double.applyIntensifierRule(
            parsedWord: TextAnalysisTools.Companion.OrderedWord,
            orderedWords: List<TextAnalysisTools.Companion.OrderedWord>, ignore: Boolean = false
        ): Double =
            if (ignore) this else if (TextAnalysisTools.scopeOfWord(parsedWord.order, orderedWords).containIntensifiers(
                    parsedWord.pos
                )
            ) this * 2.0 else this

        //Feature negation
        private fun Double.applyNegationRule(
            parsedWord: TextAnalysisTools.Companion.OrderedWord,
            orderedWords: List<TextAnalysisTools.Companion.OrderedWord>, ignoreNegation: Boolean = false
        ): Double {
            return if (ignoreNegation) this
            else
                if (TextAnalysisTools.scopeOfWord(parsedWord.order, orderedWords).containShifter(
                        parsedWord.pos
                    )
                )
                    when {
                        this < 0.0 -> this + 1.3
                        this > 0.0 -> this - 1.3
                        else -> this
                    }
                else this
        }

        //Feature PolAdj
        private fun Double.applyPolAdjRule(pos: String, ignore: Boolean = false) =
            if (ignore) this else if (pos == "adj") 2 * this else this

        //move to class lexical word
        //achieving word sense disambiguation using pre-defined pos in lexicon
        private fun polarityFromLexicon(
            word: String, pos: String,
            ignorePOS: Boolean = false
        ): Double {
            val opinionWordResult = IndexSearcherTools.searchIndexWithPOS(word)
            return if (ignorePOS) opinionWordResult.polarity
            else if (opinionWordResult.polarity != 0.0)
                if (opinionWordResult.pos != pos) 0.0
                else opinionWordResult.polarity
            else opinionWordResult.polarity
        }
    }

}