# Statistical keyterm extraction for multiple languages

Simple key term extraction using statistical log likelihood comparison with
reference data from Wikipedia.

If possible, Porter stemming is applied to unify vocabulary before key term 
extraction. 

# Usage

Compile the maven project to receive an executable `jar` file.

```
mvn package
```

The `target`-folder should now contain the file `lt-keyterms.jar`.

Use the keyterm extractor according to the help info: `java -jar target/lt-keyterms.jar -h`

```
usage: lt-keyterms <options> [file1 [file2 file3 ...]]
 -h,--help             Display help information
 -l,--language <arg>   ISO-639-3 language code (default: eng)
 -n,--number <arg>     Number of key terms to extract (default: 25)
 -v,--verbose          Output debug information
```

You can give one or more file as arguments:

```
java -jar target/lt-keyterms.jar -l deu src/test/resources/deu_sample.txt
```

Alternatively, you can pass in text from the standard input:

```
java -jar target/lt-keyterms.jar -l fra < src/test/resources/fra_sample.txt
```

Or use Unix-style piping:

```
echo "This is a really complex test scenario." | java -jar target/lt-keyterms.jar -l eng 
```

# Supported languages

* english
* danish
* german
* dutch
* finnish
* french
* hungarian
* italian
* norwegian
* portuguese
* romanian
* russian
* spanish
* swedish
* turkish