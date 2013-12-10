package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class CalculateMemeScores {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-g", description = "Use X-grams")
	private int grams = 1;

	@Parameter(names = "-y", description = "Calculate scores for given year")
	private Integer year;

	@Parameter(names = "-ys", description = "Calculate scores for time period with given start year")
	private Integer yearStart;

	@Parameter(names = "-ye", description = "Calculate scores for time period with given end year")
	private Integer yearEnd;

	@Parameter(names = "-n", description = "Set n parameter")
	private int n = 3;

	public static final void main(String[] args) {
		CalculateMemeScores obj = new CalculateMemeScores();
		JCommander jc = new JCommander(obj);
		try {
			jc.parse(args);
		} catch (ParameterException ex) {
			jc.usage();
			System.exit(1);
		}
		if (obj.parameters.size() != 1) {
			System.err.println("ERROR: Exactly one main argument is needed");
			jc.usage();
			System.exit(1);
		}
		obj.inputFile = new File(obj.parameters.get(0));
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
				String[] splitline = (line + " ").split("\\|\\|\\|");
				String citing = splitline[0];
				if (citing.matches("[0-9][0-9][0-9][0-9] .*")) {
					int y = Integer.parseInt(citing.substring(0, 4));
					if (!considerYear(y)) continue;
					citing = citing.substring(5);
				}
				nt = nt + 1;
				Map<String,Boolean> citingTerms = getTerms(citing);
				String cited = splitline[1];
				Map<String,Boolean> citedTerms = getTerms(cited);
				for (String term : citingTerms.keySet()) {
					if (!citedTerms.containsKey(term)) continue;
					if (emm.containsKey(term)) {
						emm.put(term, emm.get(term) + 1);
						emm.put(term, 1);
					}
				}
			}
			reader.close();
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		System.out.println("Total number of documents: " + nt);
		System.out.println("Number of unique terms with meme score > 0: " + emm.size());

		for (String w : emm.keySet()) {
			nm.put(w, 0);
			emx.put(w, 0);
			exm.put(w, 0);
		}
		int errors = 0;
		try {
			System.out.println("Counting terms...");
			BufferedReader reader = new BufferedReader(new FileReader(inputFile));
			String line;
			while ((line = reader.readLine()) != null) {
				String[] splitline = (line + " ").split("\\|\\|\\|");
				if (splitline.length != 2) {
					errors = errors + 1;
					continue;
				}
				String citing = splitline[0];
				if (line.substring(0, 5).matches("[0-9][0-9][0-9][0-9] ")) {
					int y = Integer.parseInt(line.substring(0, 4));
					if (!considerYear(y)) continue;
					citing = citing.substring(5);
				}
				Map<String,Boolean> citingTerms = getFilteredTerms(citing);
				String cited = splitline[1];
				Map<String,Boolean> citedTerms = getFilteredTerms(cited);
				et = et + 1;
				for (String term : citedTerms.keySet()) {
					if (!citingTerms.containsKey(term)) {
						emx.put(term, emx.get(term) + 1);
					}
				}
				for (String term : citingTerms.keySet()) {
					nm.put(term, nm.get(term) + 1);
					if (!citedTerms.containsKey(term)) {
						exm.put(term, exm.get(term) + 1);
					}
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
			Writer w = new BufferedWriter(new FileWriter(getOutputFile(inputFile)));
			MemeUtils.writeCsvLine(w, new Object[] {"MEME SCORE", "TERM", "ABS. FREQUENCY", "REL. FREQUENCY", "MM", "M",
					"STICKING", "XM", "X", "SPARKING", "PROPAGATION SCORE"});
			for (String term : nm.keySet()) {
				int em = emm.get(term) + emx.get(term);
				int ex = et - em;
				double stick = (double) emm.get(term) / (em + n);
				double spark = (double) (exm.get(term) + n) / (ex + n);
				double ps = stick / spark;
				int absFq = nm.get(term);
				double relFq = (double) absFq / nt;
				double ms = ps * relFq;
				MemeUtils.writeCsvLine(w, new Object[] { ms, term, absFq, relFq, emm.get(term), em,
						stick, exm.get(term), ex, spark, ps });
			}
			w.close();
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}

	private Map<String,Boolean> getTerms(String text) {
		return MemeUtils.getTerms(text, grams);
	}

	private Map<String,Boolean> getFilteredTerms(String text) {
		return MemeUtils.getTerms(text, grams, emm);
	}

	private File getOutputFile(File inputFile) {
		String basename = inputFile.getName().replaceAll("\\..*$", "");
		String filename = "files/ms-" + basename + "-g" + grams + "-n" + n;
		if (year != null) {
			filename += "-y" + year;
		} else if (yearStart != null || yearEnd != null) {
			filename += "-y";
			if (yearStart != null) filename += yearStart;
			filename += "TO";
			if (yearEnd != null) filename += yearEnd;
		}
		filename += ".csv";
		return new File(filename);
	}

	private boolean considerYear(int y) {
		if (year != null && y != year) return false;
		if (yearStart != null && y < yearStart) return false;
		if (yearEnd != null && y > yearEnd) return false;
		return true;
	}

}
