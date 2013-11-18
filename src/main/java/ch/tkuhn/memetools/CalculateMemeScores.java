package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import au.com.bytecode.opencsv.CSVReader;

public class CalculateMemeScores {

	private static Options options;

	static {
		options = new Options();
		options.addOption("g", true, "Use <arg>-grams");
		options.addOption("y", true, "Calculate scores for year <arg>");
		options.addOption("n", true, "Set n parameter to <arg>");
	}

	public static final void main(String[] args) {
		CommandLine cmd = null;
		try {
			cmd = (new GnuParser()).parse(options, args);
		} catch (ParseException ex) {
			System.err.println("ERROR: " + ex.getMessage());
			printHelp();
			System.exit(1);
		}
		if (cmd.getArgList().size() == 0) {
			System.err.println("ERROR: Specify input file");
			printHelp();
			System.exit(1);
		}
		if (cmd.getArgList().size() > 1) {
			System.err.println("ERROR: Too many arguments");
			printHelp();
			System.exit(1);
		}
		File inputFile = new File(cmd.getArgs()[0]);
		String gramsString = cmd.getOptionValue("g", "1");
		int grams = 1;
		if (gramsString.matches("[1-9]")) {
			grams = Integer.parseInt(gramsString);
		} else {
			System.err.println("ERROR: -g has to be an integer between 1 and 9");
			printHelp();
			System.exit(1);
		}
		String nString = cmd.getOptionValue("g", "1");
		int n = 1;
		if (nString.matches("[0-9]{1,3}")) {
			n = Integer.parseInt(nString);
		} else {
			System.err.println("ERROR: -g has to be an integer between 0 and 999");
			printHelp();
			System.exit(1);
		}
		String yearString = cmd.getOptionValue("y");
		Integer year = null;
		if (yearString != null) {
			if (yearString.matches("[0-9]{1,4}")) {
				year = Integer.parseInt(yearString);
			} else {
				System.err.println("ERROR: -y has to be an integer between 0 and 9999");
				printHelp();
				System.exit(1);
			}
		}

		CalculateMemeScores c = new CalculateMemeScores(inputFile, grams, n, year);
		c.run();
	}

	public static void printHelp() {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("CalculateMemeScores <options> <inputfile>", options);
	}

	private File inputFile;
	private int grams;
	private int n;
	private Integer year;

	private int nt;
	private Map<String,Integer> nm = new HashMap<>();

	public CalculateMemeScores(File inputFile, int grams, int n, Integer year) {
		this.inputFile = inputFile;
		this.grams = grams;
		this.n = n;
		this.year = year;
	}

	public CalculateMemeScores(File inputFile, int gram, int n) {
		this(inputFile, gram, n, null);
	}

	public void run() {
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
				List<String> words = getTerms(citing);
				Map<String,Boolean> processed = new HashMap<>();
				for (String word : words) {
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
		// ...
	}

	private List<String> getTerms(String text) {
		List<String> terms = new ArrayList<>();
		String[] onegrams = text.split("\\s+");
		List<String> previous = new ArrayList<>();
		for (String t : onegrams) {
			terms.add(t);
			for (int x = 1; x < grams; x++) {
				if (previous.size() > x-1) {
					String term = t;
					for (int y = 0; y < x; y++) {
						term = previous.get(y) + " " + term;
					}
					terms.add(term);
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
