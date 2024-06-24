package sample

import org.apache.lucene.analysis.de.GermanAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.nio.file.Paths

data class IndexWriterOptions(val directory: Directory, val indexWriterConfig: IndexWriterConfig)
enum class IndexClass { GENERAL, DOMAINSPECIFIC }

class IndexBuilder {
    companion object {
        private lateinit var lexiconPath: String
        private lateinit var lexiconFileName: String
        private lateinit var domainSpecificLexiconPath: String
        private lateinit var domainSpecificLexiconFileName: String
        private lateinit var lexiconFile: String
        private lateinit var domainSpecificLexiconFile: String
        private lateinit var indexWriter: IndexWriter
        private lateinit var domainSpecificIndexWriter: IndexWriter

        //initialize location of lexicon file and index path
        @JvmStatic
        fun initializeIndexBuilderOptions(
            generalIndexWriter: IndexWriter,
            lexiconPath: String,
            lexiconFileName: String,
            domainSpecificIndexWriter: IndexWriter,
            domainSpecificLexiconPath: String,
            domainSpecificLexiconFileName: String
        ) {
            println("IndexBuilder initialized")
            this.lexiconPath = lexiconPath
            this.lexiconFileName = lexiconFileName
            this.lexiconFile = "${Companion.lexiconPath}${Companion.lexiconFileName}.txt"
            indexWriter = generalIndexWriter

            this.domainSpecificLexiconPath = domainSpecificLexiconPath
            this.domainSpecificLexiconFileName = domainSpecificLexiconFileName
            this.domainSpecificLexiconFile =
                "${domainSpecificLexiconPath}${Companion.domainSpecificLexiconFileName}.txt"
            this.domainSpecificIndexWriter = domainSpecificIndexWriter
        }

        //build index from a list of OpinionWord
        //ignore pos if the lexicon includes no pos entries
        @JvmStatic
        fun buildIndex(
            words: List<TextAnalysisTools.Companion.OpinionWord>, ignorePOS: Boolean,
            indexClass: IndexClass
        ) {
            val writer = if (indexClass == IndexClass.GENERAL) indexWriter
            else domainSpecificIndexWriter

            if (ignorePOS)
                for (word in words)
                    writer.addDocument(word)
            else
                for (word in words)
                    writer.addDocumentWithPOS(word)
            indexWriter.close()
            println("index were successfully built!")
        }

        private fun IndexWriter.addDocument(opinionWord: TextAnalysisTools.Companion.OpinionWord) {
            val doc = createDocument(opinionWord)
            addDocument(doc)
        }

        private fun IndexWriter.addDocumentWithPOS(opinionWord: TextAnalysisTools.Companion.OpinionWord) {
            val doc = createDocumentWithPOS(opinionWord)
            addDocument(doc)
        }

        private fun createDocument(opinionWord: TextAnalysisTools.Companion.OpinionWord) = Document().also {
            it.add(TextField("word", opinionWord.word, Field.Store.NO))
            it.add(StringField("polarity", opinionWord.polarity.toString(), Field.Store.YES))
        }

        private fun createDocumentWithPOS(opinionWord: TextAnalysisTools.Companion.OpinionWord) = Document().also {
            it.add(TextField("word", opinionWord.word, Field.Store.NO))
            it.add(StringField("polarity", opinionWord.polarity.toString(), Field.Store.YES))
            it.add(StringField("POS", opinionWord.pos, Field.Store.YES))
        }

        //build a list of OpinionWord from the domain specific lexicon
        @JvmStatic
        fun readDomainSpecificLexicon(): List<TextAnalysisTools.Companion.OpinionWord> {
            val words = mutableListOf<TextAnalysisTools.Companion.OpinionWord>()
            File(domainSpecificLexiconFile).forEachLine {
                val wordAndPolarity = it.split(" ")
                val word = TextAnalysisTools.Companion.OpinionWord(wordAndPolarity[0], wordAndPolarity[1].toDouble())
                words.add(word)
            }
            return words
        }

        //build a list of OpinionWord from PolArt lexicon
        @JvmStatic
        fun readPolArtLexicon(): List<TextAnalysisTools.Companion.OpinionWord> {
            val words = mutableListOf<TextAnalysisTools.Companion.OpinionWord>()
            File(lexiconFile).forEachLine {
                if (convertPolArtLineToOpinionWord(it) != null) words.add(
                    convertPolArtLineToOpinionWord(it)!!
                )
            }
            return words
        }

        private fun convertPolArtLineToOpinionWord(line: String): TextAnalysisTools.Companion.OpinionWord? {
            val elements = line.split(" ")
            val word = elements[0]
            val pos = elements[2]
            val polElements = elements[1].split("=")
            return when (polElements[0]) {
                "POS" -> TextAnalysisTools.Companion.OpinionWord(word, polElements[1].toDouble(), pos)
                "NEG" -> TextAnalysisTools.Companion.OpinionWord(word, -1.0 * polElements[1].toDouble(), pos)
                else -> null
            }
        }
    }
}