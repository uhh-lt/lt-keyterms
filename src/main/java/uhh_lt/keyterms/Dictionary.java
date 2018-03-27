package uhh_lt.keyterms;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Multisets;
import com.google.common.collect.TreeMultiset;
import com.google.common.io.Files;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class Dictionary {

	public static final int STOP_WORD_RANK = 500;
	private final static Logger LOGGER = 
			Logger.getLogger(Extractor.class.getName());

	private Long totalCounts;
	private SortedSet<String> stopwords;
	private HashMap<String, String> stemTypeMapping;
	private StemmerWrapper stemmer;

	private TreeMultiset<String> typeFrequencies;
	private TreeMultiset<String> stemFrequencies;
	private String language;



	public Dictionary(String language) {
		this.language = language;
		stemmer = new StemmerWrapper(language);
		try {
			createFromDictionaryFile();
		} catch (IOException e) {
			System.exit(1);
		}
	}

	public Dictionary(String language, Document document) {
		this.language = language;
		stemmer = new StemmerWrapper(language);
		createFromDocument(document);
	}

	public void createFromDocument(Document document) {
		countVocabulary(document);
		// createStopwordList();
		createStemTypeMapping();
	}

	public void createFromDictionaryFile() throws IOException {
		String filePath = "wordlists/" + this.language + ".tsv";
		loadDictionaryFile(filePath);
		createStopwordList();
		// createStemTypeMapping();
	}

	private String clean(String token) {
		return token.replaceAll("^[^\\p{L}]", "").replaceAll("[^\\p{L}]$", "");
	}

	public StemmerWrapper getStemmer() {
		return stemmer;
	}

	public void countVocabulary(Document document) {

		stemFrequencies = TreeMultiset.create();
		typeFrequencies = TreeMultiset.create();

		// count types
		totalCounts = 0L;
		for (Token token : document) {

			String type = clean(token.getValue());
			if (!type.isEmpty()) {
				String stem = clean(token.getStem());
				stemFrequencies.add(stem);
				typeFrequencies.add(type);
				totalCounts++;
			}

		}

	}

	private void createStopwordList() {
		try {
			
			// try reading stopword list from file system
			String filePath = "wordlists/" + this.language + ".stopwords";
			File stopwordFile = new File(getClass().getResource(filePath).toURI());

			List<String> lines = Files.readLines(stopwordFile, Charset.forName("UTF-8"));
			stopwords = new TreeSet<String>(lines);
		
		} catch (Exception e) {
			
			// use top ranks of reference file, if no stopword file is present
			Iterable<Entry<String>> vocabSorted = 
					Multisets.copyHighestCountFirst(typeFrequencies).entrySet();

			// identify stop words (most frequent terms)
			stopwords = new TreeSet<String>();
			int swCount = 0;
			for (Entry<String> entry : vocabSorted) {
				String stopword = entry.getElement();
				if (++swCount > STOP_WORD_RANK) break;
				if (Character.isLowerCase(stopword.charAt(0))) {
					String variant = Character.toString(stopword.charAt(0)).toUpperCase() + stopword.substring(1);
					// add full word type
					stopwords.add(stopword);
					stopwords.add(variant);
					// add stem
					stopwords.add(stemmer.stem(stopword));
					stopwords.add(stemmer.stem(variant));
				}			
			}
		}
	}


	public boolean isStopword(String type) {
		return stopwords.contains(type);
	}

	public Long getTypeFrequency(String type) {
		return Integer.toUnsignedLong(typeFrequencies.count(type));
	}

	public Long getStemFrequency(String stem) {
		return Integer.toUnsignedLong(stemFrequencies.count(stem));
	}

	public Set<String> getStemVocabulary() {
		return stemFrequencies.elementSet();
	}
	
	public Set<String> getTypeVocabulary() {
		return typeFrequencies.elementSet();
	}
	
	public TreeMap<String, Long> getTypeFrequencies() {
		HashMap<String, Long> counts = new HashMap<String, Long>();
		for (String type : getTypeVocabulary()) {
			counts.put(type, getTypeFrequency(type));
		}
		return sortMapByValue(counts);
	}

	public Long getTotalCounts() {
		return totalCounts;
	}

	public void setTotalCounts(Long totalCounts) {
		this.totalCounts = totalCounts;
	}

	//	public Set<String> getTypes() {
	//		return stemFrequencies.keySet();
	//	}

	private void createStemTypeMapping() {

		stemTypeMapping = new HashMap<String, String>();

		// get all type variants per stem
		HashMap<String, HashSet<String>> stems =  new HashMap<String, HashSet<String>>();
		for (String type : typeFrequencies.elementSet()) {
			String stem = stemmer.stem(type);
			HashSet<String> typeSet = stems.containsKey(stem) ? stems.get(stem) : new HashSet<String>();
			typeSet.add(type);
			stems.put(stem, typeSet);
		}

		// collect best stem-type pair (best = most lemma like)
		for (Map.Entry<String, HashSet<String>> entry : stems.entrySet()) {
			String shortestType = "";
			Integer shortestTypeLength = Integer.MAX_VALUE;

			// TODO: better choose most frequent type instead of shortest one?
			for (String type : entry.getValue()) {
				if (type.length() < shortestTypeLength) {
					shortestTypeLength = type.length();
					shortestType = type;
				} else if (type.length() == shortestTypeLength) {
					if (typeFrequencies.count(type) > typeFrequencies.count(shortestType)) {
						shortestTypeLength = type.length();
						shortestType = type;
					}
				}
			}
			stemTypeMapping.put(entry.getKey(), shortestType);
		}

	}



	private void loadDictionaryFile(String filePath) throws IOException {

		stemFrequencies = TreeMultiset.create();
		typeFrequencies = TreeMultiset.create();

		InputStream stream = getClass().getResourceAsStream(filePath);

		if (stream != null) {
			LOGGER.log(Level.INFO, "Reading reference file: " + filePath);
		} else {
			LOGGER.log(Level.WARNING, "Reference file not found: " + filePath);
		}

		int lineCounter = 0;
		totalCounts = 0L;
		try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
			String line;
			while ((line = br.readLine()) != null) {
				lineCounter++;
				String[] entry = line.split("\t");
				if (entry.length == 2) {

					typeFrequencies.add(entry[0], Integer.parseInt(entry[1]));
					stemFrequencies.add(stemmer.stem(entry[0]), Integer.parseInt(entry[1]));
					totalCounts += Long.parseLong(entry[1]);

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
	}


	public String getTypeFromStem(String stem) {
		return stemTypeMapping.containsKey(stem) ? stemTypeMapping.get(stem) : stem;
	}

	static class LongValueComparator implements Comparator<String>{
		HashMap<String, Long> map = new HashMap<String, Long>();
		public LongValueComparator(Map<String, Long> map){
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

	private static TreeMap<String, Long> sortMapByValue(Map<String, Long> map){
		Comparator<String> comparator = new LongValueComparator(map);
		TreeMap<String, Long> result = new TreeMap<String, Long>(comparator);
		result.putAll(map);
		return result;
	}

}
