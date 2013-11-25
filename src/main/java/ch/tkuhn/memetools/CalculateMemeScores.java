package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import au.com.bytecode.opencsv.CSVWriter;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class CalculateMemeScores {

	@Parameter(description = "files", required = true)
	private List<String> inputFiles = new ArrayList<String>();

	@Parameter(names = "-g", description = "Use X-grams")
	private int grams = 1;

	@Parameter(names = "-y", description = "Calculate scores for given year")
	private Integer year;

	@Parameter(names = "-n", description = "Set n parameter")
	private int n = 1;

	@Parameter(names = "-fth", description = "Frequency threshold")
	private int freqThreshold = 0;

	@Parameter(names = "-nth", description = "Number threshold")
	private int numberThreshold = 2;

	public static final void main(String[] args) {
		CalculateMemeScores obj = new CalculateMemeScores();
		JCommander jc = new JCommander(obj);
		try {
			jc.parse(args);
		} catch (ParameterException ex) {
			jc.usage();
			System.exit(1);
		}
		obj.run();
	}

	private int nt;
	private Map<String,Integer> nm;

	private int et;
	private Map<String,Integer> emm;
	private Map<String,Integer> emx;
	private Map<String,Integer> exm;

	public CalculateMemeScores() {
	}

	public void run() {
		for (String inputFile : inputFiles) {
			run(new File(inputFile));
		}
	}

	public void run(File inputFile) {
		nt = 0;
		nm = new HashMap<String,Integer>();
		et = 0;
		emm = new HashMap<String,Integer>();
		emx = new HashMap<String,Integer>();
		exm = new HashMap<String,Integer>();

		try {
			System.out.println("Extracting terms from input file: " + inputFile);
			BufferedReader reader = new BufferedReader(new FileReader(inputFile));
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.matches(".*\\|\\|\\|.*")) continue;
				String citing = line.split("\\|\\|\\|")[0];
				if (citing.matches("[0-9][0-9][0-9][0-9] .*")) {
					int y = Integer.parseInt(citing.substring(0, 4));
					if (year != null && y != year) continue;
					citing = citing.substring(5);
				}
				nt = nt + 1;
				Map<String,Boolean> words = getTerms(citing);
				Map<String,Boolean> processed = new HashMap<String,Boolean>();
				for (String word : words.keySet()) {
					if (word.length() < 2 || processed.containsKey(word)) continue;
					if (nm.containsKey(word)) {
						nm.put(word, nm.get(word)+1);
					} else {
						nm.put(word, 1);
					}
					processed.put(word, true);
				}
			}
			reader.close();
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		System.out.println("Total number of documents: " + nt);
		System.out.println("Total number of unique terms: " + nm.size());
		for (String v : new ArrayList<String>(nm.keySet())) {
			if (nm.get(v) < freqThreshold * nt || nm.get(v) < numberThreshold) {
				nm.remove(v);
			}
		}

		for (String w : nm.keySet()) {
			emm.put(w, 0);
			emx.put(w, 0);
			exm.put(w, 0);
		}
		int errors = 0;
		try {
			System.out.println("Counting terms...");
			BufferedReader reader = new BufferedReader(new FileReader(inputFile));
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.matches(".*\\|\\|\\|.*")) {
					errors = errors + 1;
					continue;
				}
				String[] splitline = line.split("\\|\\|\\|");
				String citing = splitline[0];
				if (line.substring(0, 5).matches("[0-9][0-9][0-9][0-9] ")) {
					int y = Integer.parseInt(line.substring(0, 4));
					if (year != null && y != year) continue;
					citing = line.substring(5);
				}
				Map<String,Boolean> citingTerms = getTerms(citing);
				String cited = splitline[1];
				Map<String,Boolean> citedTerms = getTerms(cited);
				Map<String,Boolean> processed = new HashMap<String,Boolean>();
				et = et + 1;
				for (String term : citedTerms.keySet()) {
					if (!nm.containsKey(term) || processed.containsKey(term)) continue;
					if (citingTerms.containsKey(term)) {
						emm.put(term, emm.get(term) + 1);
					} else {
						emx.put(term, emx.get(term) + 1);
					}
					processed.put(term, true);
				}
				processed = new HashMap<String,Boolean>();
				for (String term : citingTerms.keySet()) {
					if (!nm.containsKey(term) || processed.containsKey(term)) continue;
					if (!citedTerms.containsKey(term)) {
						exm.put(term, exm.get(term) + 1);
					}
					processed.put(term, true);
				}
			}
			reader.close();
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		System.out.println("Number of errors: " + errors);
		System.out.println("Calculating meme scores and writing CSV file...");
		try {
			String basename = inputFile.getName().replaceAll("\\..*$", "");
			String outputFile = "files/ms-" + basename + "-g" + grams + "-n" + n + "-y" + year + ".csv";
			CSVWriter writer = new CSVWriter(new FileWriter(outputFile), ',', '"', '\\');
			writer.writeNext(new String[] {"MEME SCORE", "TERM", "ABS. FREQUENCY", "REL. FREQUENCY", "MM", "M",
					"STICKING", "XM", "X", "SPARKING", "PROPAGATION SCORE"});
			for (String term : nm.keySet()) {
				int em = emm.get(term) + emx.get(term);
				int ex = et - em;
				double stick = (double) emm.get(term) / (em + n);
				double spark = (double) (exm.get(term) + n) / (ex + n);
				double ps = stick / spark;
				int absFq = nm.get(term);
				double relFq = (double) absFq / nt;
				double ms = ps * (absFq / nt);
				writer.writeNext(new String[] { ms+"", term, absFq+"", relFq+"", emm.get(term)+"", em+"",
						stick+"", exm.get(term)+"", ex+"", spark+"", ps+"" });
			}
			writer.close();
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}

	private Map<String,Boolean> getTerms(String text) {
		Map<String,Boolean> terms = new HashMap<String,Boolean>();
		String[] onegrams = text.split("\\s+");
		List<String> previous = new ArrayList<String>();
		for (String t : onegrams) {
			terms.put(t, true);
			for (int x = 1; x < grams; x++) {
				if (previous.size() > x-1) {
					String term = t;
					for (int y = 0; y < x; y++) {
						term = previous.get(y) + " " + term;
					}
					terms.put(term, true);
				}
			}
			previous.add(0, t);
			while (previous.size() > grams-1) {
				previous.remove(previous.size()-1);
			}
		}
		return terms;
	}

}
