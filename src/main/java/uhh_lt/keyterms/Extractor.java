package uhh_lt.keyterms;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;

import uhh_lt.keyterms.StemmerWrapper.NoStemmer;


public class Extractor {

	private final static Double MINIMUM_KEYNESS_THRESHOLD = 6.63; // 3.84;

	private final static Logger LOGGER = 
			Logger.getLogger(Extractor.class.getName());

	private Options cliOptions;

	private Dictionary comparison;

	private int nKeyterms;
	private String language;
	private boolean concatMultiWordUnits;
	private double diceThreshold = 0.4;

	public Extractor() {
		super();
	}

	public Extractor(String language, Integer nKeyterms) throws IOException {
		super();
		initialize(language, nKeyterms);
	}

	public String getLanguage() {
		return language;
	}


	public void setLanguage(String language) {
		this.language = language;
	}


	public int getnKeyterms() {
		return nKeyterms;
	}

	public void setnKeyterms(int nKeyterms) {
		this.nKeyterms = nKeyterms;
	}

	public void log(Level level, String message) {
		LOGGER.log(level, message);
	}



	private TreeMap<String, Double> getKeyness(Dictionary target) {

		long c = target.getTotalCounts();
		long d = comparison.getTotalCounts();

		// compute significance
		TreeMap<String, Double> significances = new TreeMap<String, Double>();
		Set<String> candidates = target.getVocabulary();
		candidates = filterKeytermCandidates(candidates);
		for (String type : candidates) {
			long a = target.getStemFrequency(type);
			long b = comparison.getStemFrequency(type);
			Double significance = computeLogLikelihood(a, b, c, d);
			significances.put(type, significance);
		}

		return(significances);
	}


	private TreeMap<String, Double> getKeyterms(Dictionary target, Document document) {

		TreeMap<String, Double> candidates = getKeyness(target);

		// top n words + minimum keyness filter
		TreeMap<String, Double> keyterms = new TreeMap<String, Double>();
		for (Map.Entry<String, Double> candidate : candidates.entrySet()) {
			if (candidate.getValue() >= MINIMUM_KEYNESS_THRESHOLD) {
				keyterms.put(target.getTypeFromStem(candidate.getKey()), candidate.getValue());
			}
		}


		// concatenate MWU
		if (this.concatMultiWordUnits) {
			keyterms = concatMultiWords(keyterms, target, document);
		}

		// sort
		keyterms = sortMapByValue(keyterms);

		return keyterms;

	}



	private TreeMap<String, Double> concatMultiWords(TreeMap<String, Double> keyterms, Dictionary target, Document document) {
		Token prevToken = null;
		TreeMultiset<NGram> ngrams = TreeMultiset.create();
		Character currentSeparator = ' ';
		List<NGram> activeNGrams = new ArrayList<NGram>();
		for (Token token : document) {
			if (prevToken != null) {
				if (token.getValue().equals("-")) {
					currentSeparator = '-';
					continue;
				}
			}
			String type = target.getTypeFromStem(token.getStem());
			if (keyterms.containsKey(type)) {

				NGram ngram = new NGram();
				activeNGrams.add(ngram);
				for (NGram ng : activeNGrams) {
					ng.append(token, type, keyterms.get(type), currentSeparator);
				}
				for (NGram ng : activeNGrams) {
					try {
						ngrams.add(ng.clone());
					} catch (CloneNotSupportedException e) {
						e.printStackTrace();
					}
				}
			} else {
				activeNGrams = new ArrayList<NGram>();
				currentSeparator = ' ';
			}
			prevToken = token;
		}

		// filter multiwords by Dice
		TreeMap<String, Double> keyMultiWords = new TreeMap<String, Double>();
		for (NGram ng : ngrams.elementSet()) {
			Double dice = dice(ngrams.count(ng), ng.getPartialCounts(target));
			if (dice >= this.diceThreshold) {
				Double maxKeyness = ng.keyness();
				LOGGER.log(Level.FINEST, "MWU detected: " + ng + " (" + maxKeyness + ")");
				keyMultiWords.put(ng.toString(), maxKeyness);
			}
		}

		// clean multiwords (longest match)
		Set<String> mwus = keyMultiWords.keySet();
		Set<String> mwusToRemove = new HashSet<String>();
		for (String mwu : mwus) {
			if (longerMatchExists(mwu, mwus)) {
				mwusToRemove.add(mwu);
			}
		}
		for (String mwu : mwusToRemove) {
			keyMultiWords.remove(mwu);
			LOGGER.log(Level.FINEST, "Removing " + mwu);
		}
		return keyMultiWords;
	}


	private boolean longerMatchExists(String candidate, Set<String> testSet) {
		for (String c : testSet) {
			if (c.matches(candidate + "[ -].+") || c.matches(".+[ -]" + candidate)) return true;
		}
		return false;
	}

	private Double dice(long nCooc, long... parts) {
		int n = parts.length;
		double numerator = (double) n * nCooc;
		double denominator = .0;
		for (Long p : parts) {
			denominator += p;
		}
		return numerator / denominator;
	}



	private Set<String> filterKeytermCandidates(Set<String> candidates) {
		
		// only apply filter to languages with available stemmer
		if (this.comparison.getStemmer().getStemmer() instanceof NoStemmer) {
			return candidates;
		}

		HashSet<String> filteredSet = new HashSet<String>();
		for (String candidate : candidates) {
			// filter out single chars
			if (candidate.length() < 2) {
				LOGGER.log(Level.FINEST, "Removed (candidate.length() < 2): " + candidate);
				continue;
			}
			// filter out terms with no alpha chars
			if (candidate.replaceAll("[^A-Za-z]", "").length() < 1) {
				LOGGER.log(Level.FINEST, "Removed (candidate.replaceAll(\"[^A-Za-z]\", \"\").length() < 1): " + candidate);
				continue;
			};
			// filter out terms with two or more special chars
			if (candidate.replaceAll("[\\p{L}-]", "").length() > 1) {
				LOGGER.log(Level.FINEST, "Removed (candidate.replaceAll([\\\\p{L}-]).length() > 1): " + candidate);
				continue;
			};
			// filter out stopwords
			if (comparison.isStopword(candidate)) {
				LOGGER.log(Level.FINEST, "Removed (comparison.isStopword(candidate)): " + candidate);
				continue;
			};
			filteredSet.add(candidate);
		}

		return filteredSet;
	}

	private Double computeLogLikelihood(long a, long b, long c, long d) {

		double e1 = c * (a + b) / (double) (c + d);
		double e2 = d * (a + b) / (double) (c + d);
		double t1 = a == 0 ? .0 : a * Math.log(a / e1);
		double t2 = b == 0 ? .0 : b * Math.log(b / e2);
		double ll = a == 0 ? .0 : 2 * (t1 + t2);

		double relA = a / (double) c;
		double relB = b / (double) d;

		if (relA < relB) {
			ll = ll * -1;
		}

		return ll;
	}



	static class DoubleValueComparator implements Comparator<String>{
		HashMap<String, Double> map = new HashMap<String, Double>();
		public DoubleValueComparator(Map<String, Double> map){
			this.map.putAll(map);
		}
		@Override
		public int compare(String s1, String s2) {
			if(map.get(s1) > map.get(s2)) {
				return -1;
			} else {
				return 1;
			}
		}
	}

	private static TreeMap<String, Double> sortMapByValue(Map<String, Double> map){
		Comparator<String> comparator = new DoubleValueComparator(map);
		TreeMap<String, Double> result = new TreeMap<String, Double>(comparator);
		result.putAll(map);
		return result;
	}


	private List<String> getConfiguration(String[] args) {
		cliOptions = new Options();

		Option languageOpt = new Option("l", "language", true, "ISO-639-3 language code (default: eng)");
		languageOpt.setRequired(false);
		cliOptions.addOption(languageOpt);

		Option nOpt = new Option("n", "number", true, "Number of key terms to extract (default: 25)");
		nOpt.setRequired(false);
		cliOptions.addOption(nOpt);

		Option mwuOpt = new Option("m", "mwu-off", false, "Disable multi-word concatenation (default: false)");
		mwuOpt.setRequired(false);
		cliOptions.addOption(mwuOpt);

		Option diceOpt = new Option("d", "dice-threshold", true, "Threshold between [0; 1] of dice statistic for multi-word concatenation (default: 0.4)");
		diceOpt.setRequired(false);
		cliOptions.addOption(diceOpt);

		Option verboseOpt = new Option("v", "verbose", false, "Output debug information");
		verboseOpt.setRequired(false);
		cliOptions.addOption(verboseOpt);

		Option helpOpt = new Option("h", "help", false, "Display help information");
		helpOpt.setRequired(false);
		cliOptions.addOption(helpOpt);

		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd;
		List<String> targetFiles = new ArrayList<String>();
		try {
			cmd = parser.parse(cliOptions, args);

			// display help
			if (cmd.hasOption("h")) {
				formatter.printHelp("lt-keyterms <options> [file1 [file2 file3 ...]]", cliOptions);
				System.exit(0);
			}

			// set language
			this.language = cmd.getOptionValue("l") == null ? "eng" : cmd.getOptionValue("l");

			// set verbosity
			if (cmd.hasOption("v")) {
				LOGGER.setLevel(Level.ALL);
			} else {
				LOGGER.setLevel(Level.INFO);
			}

			// set mwu
			if (cmd.hasOption("m")) {
				this.concatMultiWordUnits = false;
			} else {
				this.concatMultiWordUnits = true;
			}

			// set dice threshold
			if (cmd.hasOption("d")) {
				double d = Double.parseDouble(cmd.getOptionValue("d"));
				if (d < 0 || d > 1) throw new NumberFormatException();
			} else {
				this.diceThreshold = 0.4;
			}

			// set target files
			targetFiles = cmd.getArgList();

			// set number of keyterms
			this.nKeyterms =  cmd.getOptionValue("n") == null ? 25 : Integer.parseInt(cmd.getOptionValue("n"));

		} catch (ParseException e) {
			System.out.println(e.getMessage());
			formatter.printHelp("lt-keyterms <options> [file1 [file2 file3 ...]]", cliOptions);
			System.exit(1);
		}

		return targetFiles;

	}


	private void processTargets(List<String> files) {
		for (String f : files) {
			LOGGER.log(Level.INFO, "Extracting keyterms from " + f);
			Document targetDocument = Document.readTextFile(new File(f), this.language);
			targetDocument.normalizeSentenceBeginning(comparison);
			Dictionary target = new Dictionary(this.language, targetDocument);
			System.out.println(formatResult(getKeyterms(target, targetDocument)));
		}
	}


	private void processFromStdin() {
		LOGGER.log(Level.INFO, "No file(s) given. Extracting keyterms from standard input (press CTRL-D to finalize input).");
		StringBuilder sb = new StringBuilder();
		Scanner scanner = new Scanner( System.in );
		while (scanner.hasNextLine()) {
			sb.append(scanner.nextLine()).append("\n");
		}
		scanner.close();
		Document targetDocument = Document.readText(sb.toString(), this.language);
		targetDocument.normalizeSentenceBeginning(comparison);
		Dictionary target = new Dictionary(this.language, targetDocument);
		System.out.println(formatResult(getKeyterms(target, targetDocument)));
	}


	private String formatResult(Map<String, Double> keywords) {
		StringBuilder output = new StringBuilder();
		int kwCounter = 0;
		for (Map.Entry<String, Double> kw : keywords.entrySet()) {
			kwCounter++;
			if (kwCounter > this.nKeyterms) break;
			output.append(kw.getKey()).append("=").append(kw.getValue()).append(System.lineSeparator());
		}
		return output.toString();
	}


	public void initialize(String language, Integer nKeyterms) throws IOException {
		this.language = language;
		this.nKeyterms = nKeyterms;
		this.comparison = new Dictionary(language);
		this.comparison.createFromDictionaryFile();
	}

	public synchronized Set<String> extract(List<String> document) {

		Document targetDocument = new Document(this.language);
		targetDocument.load(document);
		targetDocument.normalizeSentenceBeginning(comparison);
		Dictionary target = new Dictionary(this.language, targetDocument);

		Map<String, Double> keywords = getKeyterms(target, targetDocument);
		return keywords.keySet();
	}





	public static void main(String[] args) {

		Extractor extractor = new Extractor();
		extractor.log(Level.INFO, "Keyterm extraction, Language Technology group (Hamburg University)");
		extractor.log(Level.INFO, "------------------------------------------------------------------");
		List<String> filesToProcess = extractor.getConfiguration(args);

		extractor.comparison = new Dictionary(extractor.language);

		if (filesToProcess.isEmpty()) {
			extractor.processFromStdin();
		} else {
			extractor.processTargets(filesToProcess);
		}

	}

}
