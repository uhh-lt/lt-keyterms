package uhh_lt.keyterms;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.CharacterIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.tartarus.snowball.ext.portugueseStemmer;
import org.tartarus.snowball.ext.romanianStemmer;
import org.tartarus.snowball.ext.russianStemmer;
import org.tartarus.snowball.ext.spanishStemmer;
import org.tartarus.snowball.ext.swedishStemmer;
import org.tartarus.snowball.ext.turkishStemmer;

import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.text.RuleBasedBreakIterator;

public class Document extends ArrayList<Token> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1905147709210286805L;

	private final static Logger LOGGER = 
			Logger.getLogger(Extractor.class.getName());

	Pattern wordbounds = Pattern.compile(
			"[\\w-]+|[^\\w-]",
			Pattern.UNICODE_CHARACTER_CLASS
			);

	private String language;
	private Stemmer stemmer;

	public Document(String language) {
		super();
		this.language = language;
		stemmer = new Stemmer(language);
	}



	public static Document readTextFile(File file, String language) {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line + "\n");
			}
		} catch (FileNotFoundException e) {
			LOGGER.log(Level.SEVERE, "Could not find file " + file);
			System.exit(1);
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Could not read file " + file);
			System.exit(1);
		}
		return readText(sb.toString(), language);		
	}


	public static Document readText(String string, String language) {
		Document document = new Document(language);
		document.load(string);
		return document;
	}



	public void load(String text) {
		load(tokenize(text));
	}

	public void load(List<String> tokens) {
		for (String token : tokens) {
			if (!token.isEmpty()) {
				this.add(new Token(token, stemmer.stem(token)));
			}
		}
	}


	public void normalizeSentenceBeginning(Dictionary referenceCounts) {

		String prevToken = "";
		for (Token token : this) {
			String currentToken = token.getValue();
			if ((prevToken.isEmpty() || prevToken.matches("[\\.\\?\\!]$")) & Character.isUpperCase(currentToken.charAt(0))) {
				String normalizedToken = currentToken.toLowerCase();
				if (referenceCounts.getTypeFrequency(normalizedToken) > referenceCounts.getTypeFrequency(currentToken)) {
					token.setValue(normalizedToken);
					token.setStem(token.getStem().toLowerCase());
				}
			}
			prevToken = currentToken;
		}

	}

	public List<String> tokenizeRegex(String sequence) {
		sequence = sequence.replaceAll("\\r\\n|\\r|\\n", " ");
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


	public List<String> tokenize(String sequence) throws MissingResourceException {

		Locale locale = getIso2LocaleMap().get(this.language);
		if (locale == null) {
			throw new MissingResourceException("Cannot find locale for language code ", Locale.class.getName(), this.language);
		}
		BreakIterator breakIterator = BreakIterator.getWordInstance(locale);
		breakIterator.setText(sequence);

		List<String> tokens = new ArrayList<>();

		int start = breakIterator.first();
		for (int end = breakIterator.next(); end != BreakIterator.DONE; start = end, end = breakIterator.next()) {
			tokens.add(sequence.substring(start, end).trim());
		}

		return tokens;
	}

	public static Map<String, Locale> getIso2LocaleMap() {
		String[] languages = Locale.getISOLanguages();
		Map<String, Locale> localeMap = new HashMap<String, Locale>(languages.length);
		for (String language : languages) {
			Locale locale = new Locale(language);
			localeMap.put(locale.getISO3Language(), locale);
		}
		return localeMap;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Token token : this) {
			sb.append(token.getValue()).append(" ");
		}
		return sb.toString().trim();
	}



}
