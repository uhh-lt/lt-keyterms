package uhh_lt.keyterms;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;


public class Extractor {

	private final static Logger LOGGER = 
			Logger.getLogger(Extractor.class.getName());

	private Options cliOptions;

	private TypeCounter target;
	private TypeCounter comparison;
	
	private int nKeyterms;
	private String language;

	Pattern wordbounds = Pattern.compile(
			"[\\w-]+|[^\\w-]",
			Pattern.UNICODE_CHARACTER_CLASS
			);

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

	public TypeCounter loadFrequencyFile(String referenceFile) throws IOException {

		// use external reference data
		String filePath = referenceFile;

		// use provided reference data
		if (filePath == null || filePath.isEmpty()) {
			filePath = "wordlists/" + this.language + ".tsv";
		}

		InputStream stream = getClass().getResourceAsStream(filePath);

		if (stream != null) {
			LOGGER.log(Level.INFO, "Reading reference file: {0}", filePath);
		} else {
			LOGGER.log(Level.WARNING, "Reference file not found: {0}", filePath);
		}

		TypeCounter counter = new TypeCounter(this.language);
		int lineCounter = 0;
		try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
			String line;
			while ((line = br.readLine()) != null) {
				lineCounter++;
				String[] entry = line.split("\t");
				if (entry.length == 2) {
					counter.addToken(entry[0], Long.parseLong(entry[1]));
				} else {
					LOGGER.log(Level.SEVERE, "Invalid reference file format at line: " + lineCounter);
				}
			}
		} catch (FileNotFoundException e) {
			throw new IOException("Unknown language code: " + this.language);
		} catch (IOException e) {
			throw new IOException("Could not read file " + filePath);
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new IOException("File " + filePath + " malformed at line " + lineCounter);
		}
		return counter;
	}

	public TypeCounter loadTxt(File file) {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
		} catch (FileNotFoundException e) {
			LOGGER.log(Level.SEVERE, "Could not find file " + file);
			System.exit(1);
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Could not read file " + file);
			System.exit(1);
		}
		return loadTxt(sb.toString());		
	}


	private TypeCounter loadTxt(String string) {
		TypeCounter tokens = new TypeCounter(this.language);
		for (String token : tokenize(string)) {
			tokens.addToken(token);
		}
		return tokens;
	}

	public List<String> tokenize(String sequence) {
		Matcher matcher = wordbounds.matcher(sequence);
		List<String> tokenList = new ArrayList<String>();
		while (matcher.find()) {
			String token = matcher.group(0).trim();
			if (!token.isEmpty()) {
				tokenList.add(token);
			}
		}
		return tokenList;
	}



	private TreeMap<String, Double> getKeywords() {

		long c = target.getTotalCounts();
		long d = comparison.getTotalCounts();

		// compute significance
		TreeMap<String, Double> significances = new TreeMap<String, Double>();
		Set<String> candidates = target.getTypes();
		candidates = filterKeytermCandidates(candidates);
		for (String type : candidates) {
			long a = target.getFrequency(type);
			long b = comparison.getFrequency(type);
			Double significance = computeLogLikelihood(a, b, c, d);
			significances.put(type, significance);
		}

		// sort
		significances = sortMapByValue(significances);

		return(significances);
	}

	private Set<String> filterKeytermCandidates(Set<String> candidates) {

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
			target = loadTxt(new File(f));
			target.process(comparison);
			System.out.println(formatResult(getKeywords()));
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

		target = loadTxt(sb.toString());
		target.process(comparison);
		System.out.println(formatResult(getKeywords()));
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
		this.comparison = loadFrequencyFile(null);
		this.comparison.process();
	}
	
	public synchronized Set<String> extract(List<String> document) {
		this.target = new TypeCounter(this.language);
		for (String token : document) {
			this.target.addToken(token);
		}
		this.target.process(comparison);
		Map<String, Double> keywords = getKeywords();
		return keywords.keySet();
	}


	public static void main(String[] args) {

		Extractor extractor = new Extractor();
		List<String> filesToProcess = extractor.getConfiguration(args);

		try {
			extractor.comparison = extractor.loadFrequencyFile(null);
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
		extractor.comparison.process();

		if (filesToProcess.isEmpty()) {
			extractor.processFromStdin();
		} else {
			extractor.processTargets(filesToProcess);
		}

	}

}
