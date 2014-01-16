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

public class CountNgrams {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-g", description = "Maximal length of n-grams")
	private int g = 1;

	@Parameter(names = "-t", description = "Threshold count")
	private int t = 10;

	@Parameter(names = "-y", description = "Count n-grams for given year")
	private Integer year;

	@Parameter(names = "-ys", description = "Count n-grams for time period with given start year")
	private Integer yearStart;

	@Parameter(names = "-ye", description = "Count n-grams for time period with given end year")
	private Integer yearEnd;

	private File logFile;

	public static final void main(String[] args) {
		CountNgrams obj = new CountNgrams();
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

	private Map<String,Integer> ngrams;

	public CountNgrams() {
	}

	public void run() {
		init();
		log("==========");

		ngrams = new HashMap<String,Integer>();
		int n = 0;

		try {
			log("Extracting terms from input file: " + inputFile);
			BufferedReader reader = new BufferedReader(new FileReader(inputFile));
			String line;
			while ((line = reader.readLine()) != null) {
				n++;
				logProgress(n);
				DataEntry d = new DataEntry(line);
				if (!considerYear(d.getYear())) continue;
				recordNgrams(d);
			}
			reader.close();
		} catch (IOException ex) {
			log(ex);
			System.exit(1);
		}
		log("Total number of documents: " + n);
		log("Number of unique n-grams: " + ngrams.size());

		log("Filtering and writing output...");
		n = 0;
		try {
			File csvFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + ".csv");
			Writer w = new BufferedWriter(new FileWriter(csvFile));
			MemeUtils.writeCsvLine(w, new Object[] {"COUNT", "TERM"});
			for (String term : ngrams.keySet()) {
				logProgress(n);
				int c = ngrams.get(term);
				if (c >= t) {
					n++;
					MemeUtils.writeCsvLine(w, new Object[] { c, term });
				}
			}
			w.close();
		} catch (IOException ex) {
			log(ex);
			System.exit(1);
		}
		log("Number of n-grams after filtering: " + n);
		log("Finished");
	}

	private void init() {
		if (outputFile == null) {
			outputFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + ".csv");
		}
		logFile = new File(MemeUtils.getLogDir(), getOutputFileName() + ".log");
	}

	private String getOutputFileName() {
		String basename = inputFile.getName().replaceAll("\\..*$", "");
		String filename = "fr-" + basename + "-g" + g + "-t" + t;
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

	private void recordNgrams(DataEntry d) {
		String[] tokens = d.getText().trim().split(" ");
		for (int p1 = 0 ; p1 < tokens.length ; p1++) {
			String term = "";
			for (int p2 = p1 ; p2 < tokens.length ; p2++) {
				if (p2 - p1 > g - 1) break;
				term += " " + tokens[p2];
				term = term.trim();
				if (ngrams.containsKey(term)) {
					ngrams.put(term, ngrams.get(term) + 1);
				} else {
					ngrams.put(term, 0);
				}
			}
		}
	}

	private void logProgress(int p) {
		if (p % 100000 == 0) log(p + "...");
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

}
