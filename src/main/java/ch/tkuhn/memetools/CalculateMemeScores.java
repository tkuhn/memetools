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

	@Parameter(names = "-y", description = "Calculate scores for given year")
	private Integer year;

	@Parameter(names = "-ys", description = "Calculate scores for time period with given start year")
	private Integer yearStart;

	@Parameter(names = "-ye", description = "Calculate scores for time period with given end year")
	private Integer yearEnd;

	@Parameter(names = "-n", description = "Set n parameter")
	private int n = 3;

	private File logFile;

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

	private Map<String,Integer> nm;

	private int et;
	private Map<String,Integer> emm;
	private Map<String,Integer> em;
	private Map<String,Integer> exm;

	public CalculateMemeScores() {
	}

	public void run() {
		logFile = new File(MemeUtils.getLogDir(), getOutputFileName() + ".log");
		if (logFile.exists()) logFile.delete();

		nm = new HashMap<String,Integer>();
		et = 0;
		emm = new HashMap<String,Integer>();
		em = new HashMap<String,Integer>();
		exm = new HashMap<String,Integer>();

		try {
			log("Extracting terms from input file: " + inputFile);
			BufferedReader reader = new BufferedReader(new FileReader(inputFile));
			String line;
			while ((line = reader.readLine()) != null) {
				DataEntry d = new DataEntry(line);
				if (!considerYear(d.getYear())) continue;
				et++;
				recordStickingTerms(d, emm);
			}
			reader.close();
		} catch (IOException ex) {
			log(ex);
			System.exit(1);
		}
		log("Total number of documents: " + et);
		log("Number of unique terms with meme score > 0: " + emm.size());

		for (String w : emm.keySet()) {
			nm.put(w, 0);
			em.put(w, 0);
			exm.put(w, 0);
		}
		int errors = 0;
		try {
			log("Counting terms...");
			BufferedReader reader = new BufferedReader(new FileReader(inputFile));
			String line;
			while ((line = reader.readLine()) != null) {
				DataEntry d = new DataEntry(line);
				if (!considerYear(d.getYear())) continue;
				recordCitedTerms(d, em, emm);
				recordTerms(d, nm, exm, emm);
			}
			reader.close();
		} catch (IOException ex) {
			log(ex);
			System.exit(1);
		}
		log("Number of errors: " + errors);
		log("Calculating meme scores and writing CSV file...");
		try {
			File csvFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + ".csv");
			Writer w = new BufferedWriter(new FileWriter(csvFile));
			MemeUtils.writeCsvLine(w, new Object[] {"MEME SCORE", "TERM", "ABS. FREQUENCY", "REL. FREQUENCY", "MM", "M",
					"STICKING", "XM", "X", "SPARKING", "PROPAGATION SCORE"});
			for (String term : nm.keySet()) {
				int ex = et - em.get(term);
				double stick = (double) emm.get(term) / (em.get(term) + n);
				double spark = (double) (exm.get(term) + n) / (ex + n);
				double ps = stick / spark;
				int absFq = nm.get(term);
				double relFq = (double) absFq / et;
				double ms = ps * relFq;
				MemeUtils.writeCsvLine(w, new Object[] { ms, term, absFq, relFq, emm.get(term), em.get(term),
						stick, exm.get(term), ex, spark, ps });
			}
			w.close();
		} catch (IOException ex) {
			log(ex);
			System.exit(1);
		}
		log("Finished");
	}

	private String getOutputFileName() {
		String basename = inputFile.getName().replaceAll("\\..*$", "");
		String filename = "ms-" + basename + "-n" + n;
		if (year != null) {
			filename += "-y" + year;
		} else if (yearStart != null || yearEnd != null) {
			filename += "-y";
			if (yearStart != null) filename += yearStart;
			filename += "TO";
			if (yearEnd != null) filename += yearEnd;
		}
		return filename;
	}

	private boolean considerYear(int y) {
		if (year != null && y != year) return false;
		if (yearStart != null && y < yearStart) return false;
		if (yearEnd != null && y > yearEnd) return false;
		return true;
	}

	private void recordStickingTerms(DataEntry d, Map<String,Integer> stickingMap) {
		Map<String,Boolean> processed = new HashMap<String,Boolean>();
		String allCited = "";
		for (String c : d.getCitedText()) allCited += "  " + c.trim();
		allCited += " ";
		String[] tokens = d.getText().trim().split(" ");
		for (int p1 = 0 ; p1 < tokens.length ; p1++) {
			String pre = "   ";
			if (p1 > 0) pre = " " + tokens[p1-1];
			String term = " ";
			for (int p2 = p1 ; p2 < tokens.length ; p2++) {
				term += tokens[p2] + " ";
				String t = term.trim();
				String post = "   ";
				if (p2 < tokens.length-1) post = tokens[p2+1] + " ";
				if (processed.containsKey(t)) continue;
				if (stickingMap != null && allCited.contains(term)) {
					int c = countOccurrences(allCited, term);
					if (countOccurrences(allCited, pre + term) < c && countOccurrences(allCited, term + post) < c) {
						increaseMapEntry(stickingMap, t);
						processed.put(t, true);
					}
				}
			}
		}
	}

	private void recordTerms(DataEntry d, Map<String,Integer> map, Map<String,Integer> sparkingMap, Map<String,?> filter) {
		Map<String,Boolean> processed = new HashMap<String,Boolean>();
		String allCited = "";
		for (String c : d.getCitedText()) allCited += "  " + c.trim();
		allCited += " ";
		String[] tokens = d.getText().trim().split(" ");
		for (int p1 = 0 ; p1 < tokens.length ; p1++) {
			String term = " ";
			for (int p2 = p1 ; p2 < tokens.length ; p2++) {
				term += tokens[p2] + " ";
				String t = term.trim();
				if (!filter.containsKey(t)) continue;
				if (processed.containsKey(t)) continue;
				processed.put(t, true);
				if (map != null) {
					increaseMapEntry(map, t);
				}
				if (sparkingMap != null && !allCited.contains(term)) {
					increaseMapEntry(sparkingMap, t);
				}
			}
		}
	}

	private int countOccurrences(String string, String subString) {
		int c = 0;
		int p = -1;
		while ((p = string.indexOf(subString, p+1)) > -1) c++;
		return c;
	}

	private void recordCitedTerms(DataEntry d, Map<String,Integer> map, Map<String,?> filter) {
		Map<String,Boolean> processed = new HashMap<String,Boolean>();
		for (String cited : d.getCitedText()) {
			String[] tokens = cited.trim().split(" ");
			for (int p1 = 0 ; p1 < tokens.length ; p1++) {
				String term = " ";
				for (int p2 = p1 ; p2 < tokens.length ; p2++) {
					term += tokens[p2] + " ";
					String t = term.trim();
					if (!filter.containsKey(t)) continue;
					if (processed.containsKey(t)) continue;
					processed.put(t, true);
					increaseMapEntry(map, t);
				}
			}
		}
	}

	private static void increaseMapEntry(Map<String,Integer> map, String key) {
		if (map.containsKey(key)) {
			map.put(key, map.get(key) + 1);
		} else {
			map.put(key, 1);
		}
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

}
