package sample

import org.annolab.tt4j.TokenHandler
import org.annolab.tt4j.TreeTaggerWrapper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream

class POSParser {
    companion object {
        var treeTaggerModelFile = ""
        //"\\users\\TreeTagger\\lib\\german.par:iso8859-1"

        //provide the path of the german module of treetagger
        @JvmStatic
        fun initializePOSParser(treeTaggerModelPath: String){
            this.treeTaggerModelFile = "${treeTaggerModelPath}german.par:iso8859-1"
            println("Pos Parser initialized")
        }

        //extract words with pos from a string
        fun parseText(text: String): List<TextAnalysisTools.Companion.OpinionWord> {
            var listOfWordsWithPOS = listOf<TextAnalysisTools.Companion.OpinionWord>()
            val treeTaggerWrapper = TreeTaggerWrapper<String>()
            try {
                treeTaggerWrapper.setModel(this.treeTaggerModelFile)
                val outputAsStringList = outputOfTreeTaggerAsList(text, treeTaggerWrapper)
                listOfWordsWithPOS = convertOutPutStringListToListOpinionWord(outputAsStringList)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            treeTaggerWrapper.destroy()
            return listOfWordsWithPOS
        }
        //convert output of TreeTagger to a list of string
        private fun outputOfTreeTaggerAsList(text: String, treeTaggerWrapper: TreeTaggerWrapper<String>): List<String> {
            val byteArrayOutputStream = ByteArrayOutputStream()
            val printStream = PrintStream(byteArrayOutputStream)
            treeTaggerWrapper.handler = TokenHandler { token, pos, _ -> printStream.println("$token $pos") }
            treeTaggerWrapper.process(TextAnalysisTools.splitTextToStringList(text))
            return convertByteArrayOutPutOfTreeTaggerToStringList(byteArrayOutputStream)
        }


        private fun convertByteArrayOutPutOfTreeTaggerToStringList(byteArrayOutputStream: ByteArrayOutputStream) =
            byteArrayOutputStream.toString().replace(Regex("\\s"), "#").replace("##", "#").split("#")
                .filter { it.isNotBlank() }

        private fun convertOutPutStringListToListOpinionWord(outputOfTreeTagger: List<String>): List<TextAnalysisTools.Companion.OpinionWord> {
            val listOfWords = mutableListOf<TextAnalysisTools.Companion.OpinionWord>()
            val wordsAndPOS = outputOfTreeTagger.windowed(2, 2)
            for (window in wordsAndPOS)
                if (window.size == 2) listOfWords.add(
                    TextAnalysisTools.Companion.OpinionWord(
                        window[0], pos = posAdapter(window[1])
                    )
                )
            return listOfWords
        }
        //reduce the output set of TreeTagger auf das Set {verben, nomen, adj, adv}
        private fun posAdapter(input: String): String = when (input) {
            "ADJA", "ADJD" -> "adj"
            "ADV" -> "adv"
            "NN" -> "nomen"
            "VVFIN", "VVIMP", "VVINF", "VVIZU", "VVPP", "VAFIN",
            "VAIMP", "VAINF", "VAPP", "VMFIN", "VMINF", "VMPP" -> "verben"
            "PTKVZ" -> "verbzusatz"
            else -> input
        }

    }

}
