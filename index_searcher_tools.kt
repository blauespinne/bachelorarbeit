package sample

import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexNotFoundException
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import java.nio.file.Paths

class IndexSearcherTools {

    companion object {
        private const val numberOfHits = 5
        private var suchIndexPath = ""
        private var domainSpecificPath = ""
        private lateinit var indexSearcher: IndexSearcher
        private lateinit var domainIndexSearcher: IndexSearcher

        //initialize general and domain specific index searcher
        @JvmStatic
        fun refreshConfig(indexSearcher: IndexSearcher, domainSpecificIndexSearcher: IndexSearcher) {
            try {
                this.indexSearcher = indexSearcher
                domainIndexSearcher = domainSpecificIndexSearcher
            } catch (ignore: IndexNotFoundException) {

            }

        }

        private fun buildQuery(word: String) = TermQuery(Term("word", word))

        //search a word in the general index
        fun searchIndexWithPOS(word: String): TextAnalysisTools.Companion.OpinionWord {
            val topDocs = indexSearcher.search(buildQuery(word), numberOfHits)
            val results = topDocs.scoreDocs
            return if (results.isNullOrEmpty())
                TextAnalysisTools.Companion.OpinionWord(word, 0.0, "NaN")
            else {
                TextAnalysisTools.Companion.OpinionWord(
                    word, indexSearcher.doc(results[0].doc).get("polarity").toDouble(),
                    indexSearcher.doc(results[0].doc).get("POS")
                )

            }
        }

        //search a word in the domain specific index
        fun searchDomainSpecificIndex(word: String): Double {
            val topDocs = domainIndexSearcher.search(buildQuery(word), numberOfHits)
            val results = topDocs.scoreDocs
            return if (results.isNullOrEmpty())
                0.0
            else
                domainIndexSearcher.doc(results[0].doc).get("polarity").toDouble()
        }

    }
}