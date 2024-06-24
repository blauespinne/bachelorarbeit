//this file includes the functionality of teh graphical use interface
package sample;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import kotlin.Pair;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;


public class Controller {
    @FXML
    TextArea opinionTextArea = new TextArea();

    @FXML
    TextArea evaluationTextArea = new TextArea();

    @FXML
    TextField pmiTextField = new TextField();

    @FXML
    TextField frequencyTextField = new TextField();

    @FXML
    TextField lexiconTextField = new TextField();

    @FXML
    TextField trainingDataTextField = new TextField();

    @FXML
    TextField domainSpecificIndexTextField = new TextField();

    @FXML
    TextField featuresTextField = new TextField();

    @FXML
    TextField generalIndexTextField = new TextField();

    @FXML
    TextField testDataTextField = new TextField();

    @FXML
    TextField treeTaggerTextField = new TextField();

    @FXML
    TextField positiveWordsTextField = new TextField();

    @FXML
    TextField negativeWordsTextField = new TextField();

    @FXML
    TextField neutralWordsTextField = new TextField();

    @FXML
    TextField polarityTextField = new TextField();

    @FXML
    TextField classificationTextField = new TextField();

    @FXML
    RadioButton primitiveNoPolarityChoice = new RadioButton();

    @FXML
    RadioButton primitiveWithPolarityChoice = new RadioButton();

    @FXML
    RadioButton domainSpecificChoice = new RadioButton();

    @FXML
    RadioButton naiveBayes = new RadioButton();

    @FXML
    Button initialisationButton = new Button();

    @FXML
    Button deleteButton = new Button();

    @FXML
    Button classificationButton = new Button();

    @FXML
    RadioButton ruleBasedChoice = new RadioButton();

    @FXML
    CheckBox ruleBasedEval = new CheckBox();

    @FXML
    CheckBox polAdj = new CheckBox();

    @FXML
    CheckBox modal = new CheckBox();

    @FXML
    CheckBox intensifiers = new CheckBox();

    @FXML
    CheckBox negation = new CheckBox();

    @FXML
    CheckBox lexiconPOS = new CheckBox();

    //to be invoked to classify a text
    public void onCharacterEntered() {
        //entries for initializing
        boolean polAdjChecked = polAdj.isSelected();
        boolean modalChecked = modal.isSelected();
        boolean intensifiersChecked = intensifiers.isSelected();
        boolean negationChecked = negation.isSelected();
        boolean lexiconPOSChecked = lexiconPOS.isSelected();
        int minFrequency = Integer.parseInt(frequencyTextField.getText());
        double minPMI = Double.parseDouble(pmiTextField.getText());
        String featuresPath = featuresTextField.getText();

        //calculate number of docs
        Pair<Integer, Integer> docsOfFile = TextAnalysisTools.dataFileSummary(new File(
                featuresPath + "balanced_trainingData.txt"
        ));
        int positiveDocs = docsOfFile.getFirst();
        int negativeDocs = docsOfFile.getSecond();
        int totalDocs = positiveDocs + negativeDocs;
        double probabilityOfPositiveDocs = positiveDocs / (1.0 * totalDocs);
        double probabilityOfNegativeDocs = negativeDocs / (1.0 * totalDocs);

        //invoke the chosed classifier
        String opinionText = opinionTextArea.getText();
        TextAnalysisTools.Companion.OpinionInfo opinionInfo = new TextAnalysisTools.Companion.OpinionInfo(0, 0, 0, 0, 0.0);
        if (primitiveNoPolarityChoice.isSelected())
            opinionInfo = PrimitiveClassifier.primitiveNoPolarityAnalyzer(opinionText);
        else if (primitiveWithPolarityChoice.isSelected())
            opinionInfo = PrimitiveClassifier.primitivePolarityAnalyzer(opinionText);
        else if (ruleBasedChoice.isSelected())
            opinionInfo = RuleBasedClassifier.ruleBasedClassifier(opinionText, new RuleBasedClassifierOptions(
                    !lexiconPOSChecked, !negationChecked, !polAdjChecked, !intensifiersChecked, !modalChecked
            ));
        else if (naiveBayes.isSelected())
            opinionInfo = BayesClassifier.bayesClassifier(opinionText, FeatureSelection.createDefinitionVector(minPMI, minFrequency)
                    , probabilityOfPositiveDocs, probabilityOfNegativeDocs);
        else if (domainSpecificChoice.isSelected())
            opinionInfo = DomainSpecificClassifier.domainSpecificClassifier(opinionText);

        //present the result of the classification
        switch (opinionInfo.getResult()) {
            case 1:
                classificationTextField.setText("positiv");
                break;
            case -1:
                classificationTextField.setText("negativ");
                break;
            default:
                classificationTextField.setText("neutral");
        }
        positiveWordsTextField.setText(Integer.toString(opinionInfo.getPositiveWords()));
        negativeWordsTextField.setText((Integer.toString(opinionInfo.getNegativeWords())));
        neutralWordsTextField.setText(Integer.toString(opinionInfo.getNeutralWords()));
        polarityTextField.setText(Double.toString(opinionInfo.getPolarity()));
    }

    public void emptyTextArea() {
        opinionTextArea.setText("");
    }

    //initialize values of paths for training and indexes
    //and TreeTagger
    public void initialize() throws IOException {
        String generalLexiconFile = lexiconTextField.getText();
        String trainingDataPath = trainingDataTextField.getText();
        String domainSpecificIndexPath = domainSpecificIndexTextField.getText();
        String generalIndexPath = generalIndexTextField.getText();
        String featuresPath = featuresTextField.getText();
        String treeTaggerPath = treeTaggerTextField.getText();

        double minPMI = 0.0;
        int minFrequency = 0;
        try {
            minPMI = Double.parseDouble(pmiTextField.getText());
            minFrequency = Integer.parseInt(frequencyTextField.getText());
        } catch (NumberFormatException e) {
            minPMI = 0.0;
            minFrequency = 0;
        }

        //initialize tree tagger
        POSParser.initializePOSParser(treeTaggerPath);

        //initialize features
        FeatureSelection.initializeFeatureOptions(trainingDataPath, "balanced_trainingData", featuresPath,
                minPMI, minFrequency);

        //initialize general and domain specific index
        try {
            IndexReader ir = DirectoryReader.open(FSDirectory.open(Paths.get(generalIndexPath)));
            IndexSearcher is = new IndexSearcher(ir);
            IndexReader dir = DirectoryReader.open(FSDirectory.open(Paths.get(domainSpecificIndexPath)));
            IndexSearcher dis = new IndexSearcher(dir);

            IndexSearcherTools.refreshConfig(is, dis);
        } catch (IOException ignored) {

        }


    }

    //evaluate test data
    public void evaluate() {
        double minPMI = Double.parseDouble(pmiTextField.getText());
        int minFrequency = Integer.parseInt(frequencyTextField.getText());
        String testDataPath = testDataTextField.getText();
        String featuresPath = featuresTextField.getText();
        File testDataFolder = new File(testDataPath);
        int numberOfTestFiles = testDataFolder.listFiles().length;

        //create text file of reviews from html files
        DataFactory.createData(new DataFactoryContext(
                testDataPath, "td", "html", featuresPath,
                "testData", "txt", numberOfTestFiles, DataSource.OTTO
        ));

        //evaluate test data
        evaluationTextArea.setText(
                Evaluator.evaluateWithAllClassifiers(featuresPath, minPMI, minFrequency,
                        ruleBasedEval.isSelected()));

    }

    //create text file from html files, domain lexicon, indexes
    //and training data and select features for naive bayes
    public void createData() throws IOException {
        initialize();
        String generalLexiconFile = lexiconTextField.getText();
        String trainingDataPath = trainingDataTextField.getText();
        String domainSpecificIndexPath = domainSpecificIndexTextField.getText();
        String generalIndexPath = generalIndexTextField.getText();
        String featuresPath = featuresTextField.getText();

        double minPMI = 0.0;
        int minFrequency = 0;
        try {
            minPMI = Double.parseDouble(pmiTextField.getText());
            minFrequency = Integer.parseInt(frequencyTextField.getText());
        } catch (NumberFormatException ignored) {

        }

        //create trainingData.txt from html files
        File trainingDataFolder = new File(trainingDataPath);
        int numberOfTrainingFiles = trainingDataFolder.listFiles().length;
        DataFactory.createData(new DataFactoryContext(
                trainingDataPath, "td", "html", featuresPath,
                "trainingData", "txt", numberOfTrainingFiles, DataSource.OTTO
        ));

        //initialize index builder
        IndexWriter generalIndexWriter = null;
        IndexWriter domainSpecificIndexWriter = null;
        try {
            IndexWriterOptions iwo = new IndexWriterOptions(FSDirectory.open(Paths.get(generalIndexPath)),
                    new IndexWriterConfig(new GermanAnalyzer()));
            IndexWriterOptions domIwo = new IndexWriterOptions(FSDirectory.open(Paths.get(domainSpecificIndexPath)),
                    new IndexWriterConfig(new GermanAnalyzer()));
            generalIndexWriter = new IndexWriter(iwo.getDirectory(), iwo.getIndexWriterConfig());
            domainSpecificIndexWriter = new IndexWriter(domIwo.getDirectory(), domIwo.getIndexWriterConfig());

            //create general index from lexicon.txt
            IndexBuilder.initializeIndexBuilderOptions(generalIndexWriter, generalLexiconFile,
                    "lexicon", domainSpecificIndexWriter, featuresPath,
                    "domainLexicon");
            IndexBuilder.buildIndex(IndexBuilder.readPolArtLexicon(), false,
                    IndexClass.GENERAL);
            generalIndexWriter.close();

            //Create domain specific lexicon from training data
            DomainLexicon.createDomainLexicon(featuresPath + "balanced_trainingData.txt", featuresPath,
                    "domainLexicon",
                    minFrequency, minPMI, ClassifierConfig.DOMAIN);

            // create domain specific Index from domainLexicon.txt
            IndexBuilder.buildIndex(IndexBuilder.readDomainSpecificLexicon(), true,
                    IndexClass.DOMAINSPECIFIC);
            domainSpecificIndexWriter.close();

            //create features (combinations.txt and singleWords.txt) from trainingData.txt
            FeatureSelection.initializeFeatureOptions(featuresPath, "balanced_trainingData", featuresPath,
                    minPMI, minFrequency);
            FeatureSelection.createFeaturesFromTrainingData();

        } catch (IOException ignore) {

        }

        //initialize index searcher
        try {
            IndexReader ir = DirectoryReader.open(FSDirectory.open(Paths.get(generalIndexPath)));
            IndexSearcher is = new IndexSearcher(ir);
            IndexReader dir = DirectoryReader.open(FSDirectory.open(Paths.get(domainSpecificIndexPath)));
            IndexSearcher dis = new IndexSearcher(dir);

            IndexSearcherTools.refreshConfig(is, dis);
        } catch (IOException e) {
            System.out.println("Exception while initializing index searcher tools");
            e.printStackTrace();
        }


    }

}
