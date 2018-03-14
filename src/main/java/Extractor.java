import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Extractor {
	
	private final static Logger LOGGER = Logger.getLogger(Extractor.class.getName());

	private TypeCounter target;
	private TypeCounter comparison;
	
	private String language;

	public Extractor(String language) {
		super();
		this.language = language;
		ConsoleHandler handler = new ConsoleHandler();
		handler.setLevel(Level.ALL);
		handler.setFormatter(new SimpleFormatter());
		LOGGER.addHandler(handler);
		LOGGER.setLevel(Level.ALL);
	}

	public TypeCounter loadLccFile() {
		File file = new File("resources/" + this.language + ".tsv");
		TypeCounter counter = new TypeCounter(this.language);
		int lineCounter = 0;
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = br.readLine()) != null) {
				lineCounter++;
				String[] entry = line.split("\t");
				if (entry.length == 2) {
					counter.addToken(entry[0], Long.parseLong(entry[1]));
				}
			}
		} catch (FileNotFoundException e) {
			System.err.println("Could not find file " + file);
		} catch (IOException e) {
			System.err.println("Could not read file " + file);
		} catch (ArrayIndexOutOfBoundsException e) {
			System.err.println("File " + file + " malformed at line " + lineCounter);
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
			System.err.println("Could not find file " + file);
		} catch (IOException e) {
			System.err.println("Could not read file " + file);
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
		Pattern wordbounds = Pattern.compile("[\\w-']+|[^\\w-' ]",
				Pattern.UNICODE_CHARACTER_CLASS);
		Matcher matcher = wordbounds.matcher(sequence);
		List<String> matchList = new ArrayList<String>();
		while (matcher.find()) {
			matchList.add(matcher.group(0)); // add match to the list
		}
		return matchList;
	}



	public static void main(String[] args) {

		Extractor extractor = new Extractor("deu");
		extractor.comparison = extractor.loadLccFile();
		extractor.comparison.process();

		extractor.target = extractor.loadTxt(new File("resources/deu_sample.txt"));
		extractor.target.process(extractor.comparison);
		
		extractor.getKeywords();

	}

	private void getKeywords() {
		
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
		
        System.out.println(significances);
        
        
        // use wikipedia lists
        
        // filter stop word list: top 250 terms
        // normalize sentence beginnings: convert UC-beginning to LC-beginning if there is more frequent LC-term in comparison
        // collapse terms with same stem (keep shorter term)
        
        
        
		
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
	
	

	static class ValueComparator implements Comparator<String>{
		HashMap<String, Double> map = new HashMap<String, Double>();
		public ValueComparator(Map<String, Double> map){
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
	
	public static TreeMap<String, Double> sortMapByValue(Map<String, Double> map){
		Comparator<String> comparator = new ValueComparator(map);
		TreeMap<String, Double> result = new TreeMap<String, Double>(comparator);
		result.putAll(map);
		return result;
	}



}
