# Statistical keyterm extraction for multiple languages

Simple key term extraction for documents using statistical log likelihood 
comparison with reference data from Wikipedia.

If possible, Porter stemming is applied to unify vocabulary before key term 
extraction. 

Sequences of key terms are concatenated to key phrases (multi-word units), 
if they significantly co-occur as neighbors in the target document.

# Usage

Compile the maven project to receive an executable `jar` file.

```
mvn package
```

The `target`-folder should now contain the file `lt-keyterms.jar`.

Use the keyterm extractor according to the help info: `java -jar target/lt-keyterms.jar -h`

```
usage: lt-keyterms <options> [file1 [file2 file3 ...]]
 -d,--dice-threshold <arg>   Threshold between [0; 1] of dice statistic
                             for multi-word concatenation (default: 0.4)
 -f,--frequency              Output frequency list instead of keyness. Use
                             this to create own reference resources.
 -h,--help                   Display help information
 -l,--language <arg>         ISO-639-3 language code (default: eng)
 -m,--mwu-off                Disable multi-word concatenation (default:
                             false)
 -n,--number <arg>           Number of key terms to extract (default: 25)
 -r,--reference <arg>        External reference resource file (file
                             format: 'type\tfrequency', one per line).
 -v,--verbose                Output more log information
```

You can provide one or more file as arguments. Two or more files will be
concatenated into one text corpus before keyterm extraction.

```
java -jar target/lt-keyterms.jar -l deu src/test/resources/deu_sample.txt
```

Alternatively, you can pass in text from the standard input:

```
java -jar target/lt-keyterms.jar -l fra < src/test/resources/fra_sample.txt
```

Or use Unix-style piping:

```
cat src/test/resources/ara_sample.txt | java -jar target/lt-keyterms.jar -l ara 
```

# Supported languages

* arabic (ara)
* chinese (zho)
* danish (dan)
* dutch (nld)
* english (eng)
* finnish (fin)
* french (fra)
* german (deu)
* hungarian (hun)
* italian (ita)
* norwegian (nor)
* portuguese (por)
* romanian (ron)
* russian (rus)
* spanish (spa)
* swedish (swe)
* turkish (tur)

You need another language? Just open an issue in this repository.

# Creating own reference list

Languages which are currently not supported or any project specific reference
resource can also be easily integrated by the following steps:

1. Load a representative language resource, e.g. a 100.000 sentences
corpus compiled by the [Leipzig Corpora Collection](http://wortschatz.uni-leipzig.de/en/download)

2. Run `lt-keyterms` with the frequency mode option and save the result

```
cat my_own_corpus.txt | java -jar target/lt-keyterms.jar -f > my_new_reference.tsv
```

3.  Run `lt-keyterms` with the newly created reference statistics

```
cat my_document.txt | java -jar target/lt-keyterms.jar -r my_new_reference.tsv
```


# Java API

You can use the package via maven. Add this to your `pom.xml`

```
	<repositories>
		<repository>
		    <id>jitpack.io</id>
		    <url>https://jitpack.io</url>
		</repository>
	</repositories>
```

```
	<dependency>
	    <groupId>com.github.uhh-lt</groupId>
	    <artifactId>lt-keyterms</artifactId>
	    <version>-SNAPSHOT</version>
	</dependency>
```

In your Java program, you can use keyterm/keyphrase extraction like this:

```
String myDocument = "领土变更 芬蘭割讓芬蘭灣島嶼、卡累利阿地峽、拉多加湖畔卡累利阿、薩拉、雷巴奇半島以及租借漢科給蘇聯";
Extractor extractor = new Extractor("zho", 5);
Set<String> keyterms = extractor.extractKeyTerms(myDocument);
HashMap<String, Double> keyness = extractor.extractKeyness(myDocument);
```


