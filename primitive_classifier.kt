package sample

class PrimitiveClassifier {
    companion object {
        @JvmStatic
        fun primitiveNoPolarityAnalyzer(opinion: String): TextAnalysisTools.Companion.OpinionInfo {
            val opinionTools = TextAnalysisTools.Companion.Opinion(opinion)
            return TextAnalysisTools.Companion.OpinionInfo(
                if (opinionTools.numberOfPositiveWords() > opinionTools.numberOfNegativeWords()) 1 else -1,
                opinionTools.numberOfPositiveWords(), opinionTools.numberOfNegativeWords(),
                opinionTools.numberOfNeutralWords(), opinionTools.meanPolarity()
            )
        }

        @JvmStatic
        fun primitivePolarityAnalyzer(opinion: String): TextAnalysisTools.Companion.OpinionInfo {
            val opinionTools = TextAnalysisTools.Companion.Opinion(opinion)
            return TextAnalysisTools.Companion.OpinionInfo(
                if (opinionTools.meanPolarity() > 0) 1 else -1,
                opinionTools.numberOfPositiveWords(), opinionTools.numberOfNegativeWords(),
                opinionTools.numberOfNeutralWords(), opinionTools.meanPolarity()
            )
        }
    }
}