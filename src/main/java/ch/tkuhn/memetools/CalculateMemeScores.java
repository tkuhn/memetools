package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class CalculateMemeScores {

	@Parameter(description = "files", required = true)
	private List<String> inputFiles = new ArrayList<>();

	@Parameter(names = "-g", description = "Use <arg>-grams")
	private int grams = 1;

	@Parameter(names = "-y", description = "Calculate scores for year <arg>")
	private Integer year;

	@Parameter(names = "-n", description = "Set n parameter to <arg>")
	private int n = 1;

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
	private Map<String,Integer> nm = new HashMap<>();

	public CalculateMemeScores() {
	}

	public void run() {
		for (String inputFile : inputFiles) {
			run(inputFile);
		}
	}

	public void run(String inputFile) {
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
