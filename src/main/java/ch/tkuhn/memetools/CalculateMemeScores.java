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

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

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

	private boolean appendMode;

	private Map<String,Integer> nm;

	private int et;
	private Map<String,Integer> emm;
	private Map<String,Boolean> emmp;
	private Map<String,Integer> em;
	private Map<String,Integer> exm;

	public CalculateMemeScores() {
	}

	public void run() {
		init();
		try {
			if (appendMode) {
				appendTable();
			} else {
				extractTerms();
				countTerms();
				writeTable();
			}
		} catch (IOException ex) {
			log(ex);
			System.exit(1);
		}
		log("Finished");
	}

	private void init() {
		appendMode = inputFile.getName().endsWith(".csv");

		logFile = new File(MemeUtils.getLogDir(), getOutputFileName() + ".log");
		log("==========");

		if (outputFile == null) {
			outputFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + ".csv");
		}

		if (!appendMode) {
			nm = new HashMap<String,Integer>();
			et = 0;
			emm = new HashMap<String,Integer>();
			emmp = new HashMap<String,Boolean>();
			em = new HashMap<String,Integer>();
			exm = new HashMap<String,Integer>();
		}
	}

	private void extractTerms() throws IOException {
		log("Extracting terms from input file: " + inputFile);
		BufferedReader reader = new BufferedReader(new FileReader(inputFile));
		int progress = 0;
		String line;
		while ((line = reader.readLine()) != null) {
			progress++;
			logProgress(progress);
			DataEntry d = new DataEntry(line);
			if (!considerYear(d.getYear())) continue;
			et++;
			recordStickingTerms(d);
		}
		reader.close();
		log("Total number of documents: " + et);
		log("Number of unique terms with meme score > 0: " + emm.size());

		// Initialize all maps:
		for (String w : emm.keySet()) {
			nm.put(w, 0);
			em.put(w, 0);
			exm.put(w, 0);
		}
	}

	private void countTerms() throws IOException {
		int errors = 0;
		try {
			log("Counting terms...");
			BufferedReader reader = new BufferedReader(new FileReader(inputFile));
			int progress = 0;
			String line;
			while ((line = reader.readLine()) != null) {
				progress++;
				logProgress(progress);
				DataEntry d = new DataEntry(line);
				if (!considerYear(d.getYear())) continue;
				recordCitedTerms(d);
				recordTerms(d);
			}
			reader.close();
		} catch (IOException ex) {
			log(ex);
			System.exit(1);
		}
		log("Number of errors: " + errors);
	}

	private void writeTable() throws IOException {
		log("Calculating meme scores and writing CSV file...");
		Writer w = new BufferedWriter(new FileWriter(outputFile));
		MemeUtils.writeCsvLine(w, new Object[] {"TERM", "ABSFREQ", "RELFREQ", "MM", "M",
				"XM", "X", "STICK-" + n, "SPARK-" + n, "PS-" + n, "MS-" + n});
		int progress = 0;
		for (String term : nm.keySet()) {
			progress++;
			logProgress(progress);
			int absFq = nm.get(term);
			double relFq = (double) absFq / et;
			int mm = emm.get(term);
			int m = em.get(term);
			int xm = exm.get(term);
			int x = et - m;
			double[] v = calculateMemeScoreValues(mm, m, xm, x, relFq);
			MemeUtils.writeCsvLine(w, new Object[] { term, absFq, relFq, mm, m, xm, x, v[0], v[1], v[2], v[3]});
		}
		w.close();
	}

	private void appendTable() throws IOException {
		log("Calculating meme scores and appending to CSV file...");
		BufferedReader reader = new BufferedReader(new FileReader(inputFile), 64*1024);
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile), 64*1024);
		List<String> line = MemeUtils.readCsvLine(reader);
		int relFq = line.indexOf("RELFREQ");
		int mmCol = line.indexOf("MM");
		int mCol = line.indexOf("M");
		int xmCol = line.indexOf("XM");
		int xCol = line.indexOf("X");
		line.add("STICK-" + n);
		line.add("SPARK-" + n);
		line.add("PS-" + n);
		line.add("MS-" + n);
		MemeUtils.writeCsvLine(writer, line);
		while ((line = MemeUtils.readCsvLine(reader)) != null) {
			int mm = Integer.parseInt(line.get(mmCol));
			int m = Integer.parseInt(line.get(mCol));
			int xm = Integer.parseInt(line.get(xmCol));
			int x = Integer.parseInt(line.get(xCol));
			double[] v = calculateMemeScoreValues(mm, m, xm, x, relFq);
			for (double d : v) {
				line.add(d + "");
			}
			MemeUtils.writeCsvLine(writer, line);
		}
		reader.close();
		writer.close();
	}

	private double[] calculateMemeScoreValues(int mm, int m, int xm, int x, double relFq) {
		double stick = (double) mm / (m + n);
		double spark = (double) xm / (x + n);
		double ps = stick / spark;
		double ms = ps * relFq;
		return new double[] {stick, spark, ps, ms};
	}

	private String getOutputFileName() {
		String basename = inputFile.getName().replaceAll("\\..*$", "");
		if (appendMode) {
			return basename + "-a";
		}
		String filename = "ms-" + basename;
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

	private void recordStickingTerms(DataEntry d) {
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
				if (allCited.contains(term)) {
					int c = countOccurrences(allCited, term);
					if (countOccurrences(allCited, pre + term) < c && countOccurrences(allCited, term + post) < c) {
						increaseMapEntry(emm, t);
						processed.put(t, true);
					} else {
						emmp.put(t, true);
					}
				} else {
					break;
				}
			}
		}
	}

	private void recordTerms(DataEntry d) {
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
				if (!emm.containsKey(t) && !emmp.containsKey(t)) break;
				if (!emm.containsKey(t)) continue;
				if (processed.containsKey(t)) continue;
				processed.put(t, true);
				increaseMapEntry(nm, t);
				if (!allCited.contains(term)) {
					increaseMapEntry(exm, t);
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

	private void recordCitedTerms(DataEntry d) {
		Map<String,Boolean> processed = new HashMap<String,Boolean>();
		for (String cited : d.getCitedText()) {
			String[] tokens = cited.trim().split(" ");
			for (int p1 = 0 ; p1 < tokens.length ; p1++) {
				String term = " ";
				for (int p2 = p1 ; p2 < tokens.length ; p2++) {
					term += tokens[p2] + " ";
					String t = term.trim();
					if (!emm.containsKey(t) && !emmp.containsKey(t)) break;
					if (!emm.containsKey(t)) continue;
					if (processed.containsKey(t)) continue;
					processed.put(t, true);
					increaseMapEntry(em, t);
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

	private void logProgress(int p) {
		if (p % 100000 == 0) log(p + "...");
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

}
