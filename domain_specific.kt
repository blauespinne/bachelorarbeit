package sample

class DomainSpecificClassifier{
    companion object{
        //classify using a domain specific lexicon
        @JvmStatic
        fun domainSpecificClassifier(opinion: String): TextAnalysisTools.Companion.OpinionInfo {
            val documentPolarity = TextAnalysisTools.splitTextToStringList(
                TextAnalysisTools.cleanAndTokenizeText(opinion)
            ).map {
                domainSpecificPolarity(it)
            }.average()

            return TextAnalysisTools.Companion.OpinionInfo(
                if (documentPolarity < 0.0) -1 else 1, 0, 0, 0, documentPolarity
            )

        }
        private fun domainSpecificPolarity(word: String) = IndexSearcherTools.searchDomainSpecificIndex(word)
    }
}