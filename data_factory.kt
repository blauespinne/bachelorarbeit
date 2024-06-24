package sample

import java.io.File
import java.io.FileNotFoundException
import java.lang.StringBuilder

enum class DataSource { OTTO, AMAZON, EBAY, TRIPADVISOR }
data class DataFactoryContext(
    val inputPath: String = "", val inputFileNamePattern: String = "", val inputFileType: String = "",
    val outputPath: String = "", val outputFileName: String = "",
    val outputFileType: String = "", val numberOfDocuments: Int = 0, val dataSource: DataSource
    = DataSource.OTTO
)

class DataFactory {
    companion object {
        private fun balanceDataContent(inputDataFile: String, numberOfDocs: Int): String {
            var numberOfWrittenPositiveDocs = numberOfDocs
            var numberOfWrittenNegativeDocs = numberOfDocs
            val balancedDataContent = StringBuilder()
            File(inputDataFile).forEachLine {
                val stars = TextAnalysisTools.starsOfReview(it)
                if (TextAnalysisTools.starsToClass(stars) == -1)
                    if (numberOfWrittenNegativeDocs != 0) {
                        balancedDataContent.append(it + "\n")
                        numberOfWrittenNegativeDocs--
                    }

                if (TextAnalysisTools.starsToClass(stars) == 1)
                    if (numberOfWrittenPositiveDocs != 0) {
                        balancedDataContent.append(it + "\n")
                        numberOfWrittenPositiveDocs--
                    }

            }
            return balancedDataContent.toString()
        }

        private fun createBalancedData(inputDataFile: String, balancedDataPath: String) {
            val dataSummary = TextAnalysisTools.dataFileSummary(File(inputDataFile))
            val numberOfPositiveDocs = dataSummary.first
            val numberOfNegativeDocs = dataSummary.second
            val numberOfDocs = kotlin.math.min(numberOfNegativeDocs, numberOfPositiveDocs)
            File(balancedDataPath).writeText(balanceDataContent(inputDataFile, numberOfDocs))
        }
        //converts html files to a text file of reviews
        @JvmStatic
        fun createData(dataFactoryContext: DataFactoryContext) {
            when (dataFactoryContext.dataSource) {
                DataSource.OTTO -> createDataOtto(dataFactoryContext)
                else -> createDataOtto(dataFactoryContext)
            }
            createBalancedData(
                inputDataFile = "${dataFactoryContext.outputPath}" +
                        "${dataFactoryContext.outputFileName}.${dataFactoryContext.outputFileType}",
                balancedDataPath = "${dataFactoryContext.outputPath}balanced_${dataFactoryContext.outputFileName}" +
                        ".${dataFactoryContext.outputFileType}"
            )
        }

        private fun createDataOtto(dataFactoryContext: DataFactoryContext) {
            val content = StringBuilder()
            for (fileCounter in 1..dataFactoryContext.numberOfDocuments)
                try {
                    content.append(
                        TextAnalysisTools.createDataFromSingleHTMLFile(
                            inputFile = File(
                                dataFactoryContext.inputPath +
                                        dataFactoryContext.inputFileNamePattern +
                                        fileCounter +
                                        "." + dataFactoryContext.inputFileType
                            )
                        )
                    )
                } catch (fe: FileNotFoundException) {
                    fe.printStackTrace()
                }
            File(
                dataFactoryContext.outputPath +
                        dataFactoryContext.outputFileName + "." + dataFactoryContext.outputFileType
            ).writeText(
                content.toString()
            )

        }
    }
}