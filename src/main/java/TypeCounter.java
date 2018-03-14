import java.util.TreeMap;

import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.danishStemmer;
import org.tartarus.snowball.ext.dutchStemmer;
import org.tartarus.snowball.ext.englishStemmer;
import org.tartarus.snowball.ext.finnishStemmer;
import org.tartarus.snowball.ext.frenchStemmer;
import org.tartarus.snowball.ext.germanStemmer;
import org.tartarus.snowball.ext.hungarianStemmer;
import org.tartarus.snowball.ext.italianStemmer;
import org.tartarus.snowball.ext.norwegianStemmer;
import org.tartarus.snowball.ext.porterStemmer;
import org.tartarus.snowball.ext.romanianStemmer;
import org.tartarus.snowball.ext.russianStemmer;
import org.tartarus.snowball.ext.spanishStemmer;
import org.tartarus.snowball.ext.swedishStemmer;
import org.tartarus.snowball.ext.turkishStemmer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

public class TypeCounter {

	public static final int STOP_WORD_RANK = 500;

	private LinkedList<Type> tokenList;
	private HashMap<String, Long> frequency;
	private Long totalCounts;
	private HashSet<String> stopwords;
	private HashMap<String, String> collapsedForm;

	private SnowballStemmer stemmer;

	public TypeCounter(String language) {
		super();
		switch (language) {
		case "eng":
			stemmer = new englishStemmer();
			break;
		case "dan":
			stemmer = new danishStemmer();
			break;
		case "deu":
			stemmer = new germanStemmer();
			break;
		case "nld":
			stemmer = new dutchStemmer();
			break;
		case "fin":
			stemmer = new finnishStemmer();
			break;
		case "fra":
			stemmer = new frenchStemmer();
			break;
		case "hun":
			stemmer = new hungarianStemmer();
			break;
		case "ita":
			stemmer = new italianStemmer();
			break;
		case "nor":
			stemmer = new norwegianStemmer();
			break;
		case "por":
			stemmer = new porterStemmer();
			break;
		case "ron":
			stemmer = new romanianStemmer();
			break;
		case "rus":
			stemmer = new russianStemmer();
			break;
		case "spa":
			stemmer = new spanishStemmer();
			break;
		case "swe":
			stemmer = new swedishStemmer();
			break;
		case "tur":
			stemmer = new turkishStemmer();
			break;
		default:
			stemmer = null;
		}

		tokenList = new LinkedList<Type>();
		collapsedForm = new HashMap<String, String>();
	}

	public void addToken(String token, Long count, String pos) {
		tokenList.add(new Type(token, count, pos));
	}

	public void addToken(String token, Long count) {
		addToken(token, count, null);
	}

	public void addToken(String token) {
		addToken(token, 1L, null);
	}
	
	public void process() {
		process(null);
	}
	
	public void process(TypeCounter referenceCounts) {
		if (referenceCounts != null) {
			normalizeSentenceBeginning(referenceCounts);
		}
		countTokens();
		collapseTypes();
	}

	public void countTokens() {
		frequency = new HashMap<String, Long>();
		totalCounts = 0L;
		for (Type t : tokenList) {
			String token = t.getValue();
			frequency.put(token, frequency.containsKey(token) ? frequency.get(token) + t.getCount(): t.getCount());
			totalCounts += t.getCount();
		}
		TreeMap<String, Long> rankedTypes = sortMapByValue(frequency);
		stopwords = new HashSet<String>();
		int swCount = 0;
		for (String stopword : rankedTypes.navigableKeySet()) {
			if (++swCount > STOP_WORD_RANK) break;
			if (Character.isLowerCase(stopword.charAt(0))) {
				stopwords.add(stopword);
				if (stemmer != null) {
					stemmer.setCurrent(stopword);
					stemmer.stem();
					stopwords.add(stemmer.getCurrent());
				}
			}			
		}
	}

	public boolean isStopword(String type) {
		return stopwords.contains(type);
	}

	public Long getFrequency(String type) {
		String lookupType = collapsedForm.containsKey(type) ? collapsedForm.get(type) : type;
		if (frequency.containsKey(lookupType)) {
			return frequency.get(lookupType);
		} else {
			return 0L;
		}
	}

	public Long getTotalCounts() {
		return totalCounts;
	}

	public void setTotalCounts(Long totalCounts) {
		this.totalCounts = totalCounts;
	}

	public Set<String> getTypes() {
		return frequency.keySet();
	}



	static class ValueComparator implements Comparator<String>{
		HashMap<String, Long> map = new HashMap<String, Long>();
		public ValueComparator(Map<String, Long> map) {
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

	public static TreeMap<String, Long> sortMapByValue(Map<String, Long> map){
		Comparator<String> comparator = new ValueComparator(map);
		TreeMap<String, Long> result = new TreeMap<String, Long>(comparator);
		result.putAll(map);
		return result;
	}
	
	
	public void collapseTypes() {
		if (stemmer == null)
			return;
		
		HashMap<String, HashSet<String>> stems =  new HashMap<String, HashSet<String>>();
		for (String type : frequency.keySet()) {
			stemmer.setCurrent(type);
			stemmer.stem();
			String stem = stemmer.getCurrent();
			
			HashSet<String> typeSet = stems.containsKey(stem) ? stems.get(stem) : new HashSet<String>();
			typeSet.add(type);
			stems.put(stem, typeSet);
		}
		
		
		for (Map.Entry<String, HashSet<String>> entry : stems.entrySet()) {
			if (entry.getValue().size() > 1) {
				Integer shortestTypeLength = Integer.MAX_VALUE;
				String shortestType = "";
				Long combinedFrequency = 0L;
				for (String type : entry.getValue()) {
					if (type.length() < shortestTypeLength) {
						shortestTypeLength = type.length();
						shortestType = type;
					}
					combinedFrequency += frequency.get(type);
				}
				for (String type : entry.getValue()) {
					collapsedForm.put(type, shortestType);
					if (type.equals(shortestType)) {
						frequency.put(type, combinedFrequency);
					} else {
						frequency.remove(type);
					}
				}
			} 
		}
		
	}
	
	public void normalizeSentenceBeginning(TypeCounter referenceCounts) {
		
		String prevToken = "";
		for (Type token : tokenList) {
			String currentToken = token.getValue();
			if (prevToken.matches("[\\.\\?!]") & Character.isUpperCase(currentToken.charAt(0))) {
				String normalizedToken = currentToken.toLowerCase();
				if (referenceCounts.getFrequency(normalizedToken) > referenceCounts.getFrequency(currentToken)) {
					token.setValue(normalizedToken);
				}
			}
			prevToken = currentToken;
		}

	}

}
