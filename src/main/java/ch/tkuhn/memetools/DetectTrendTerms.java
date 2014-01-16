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

public class DetectTrendTerms {

	@Parameter(description = "chronologically-sorted-input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-d", description = "Number of documents per period")
	private int docsPerPeriod = 10000;

	@Parameter(names = "-t", description = "File to load the terms from")
	private File termFile;

	@Parameter(names = "-c", description = "Index or name of term file column")
	private String colIndexOrName = "TERM";

	private File logFile;

	public static final void main(String[] args) {
		DetectTrendTerms obj = new DetectTrendTerms();
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
		try {
			obj.run();
		} catch (IOException ex) {
			obj.log(ex);
		}
	}

	private Map<String,Boolean> terms;
	private Map<String,Double> absFreqCh;
	private Map<String,Double> relFreqCh;
	private Map<String,Integer> thisCount;
	private Map<String,Integer> lastCount;

	private int docsInPeriodCount;

	public DetectTrendTerms() {
	}

	public void run() throws IOException {
		init();
		log("==========");
		
		terms = new HashMap<String, Boolean>();
		absFreqCh = new HashMap<String,Double>();
		relFreqCh = new HashMap<String,Double>();
		thisCount = new HashMap<String,Integer>();

		log("Loading terms from " + termFile + "...");
		BufferedReader termReader = new BufferedReader(new FileReader(termFile));
		List<String> l = MemeUtils.readCsvLine(termReader);
		int col;
		if (colIndexOrName.matches("[0-9]+")) {
			col = Integer.parseInt(colIndexOrName);
		} else {
			col = l.indexOf(colIndexOrName);
		}
		while ((l = MemeUtils.readCsvLine(termReader)) != null) {
			terms.put(l.get(col), true);
		}
		termReader.close();
		log("Number of terms loaded: " + terms.size());

		int t = 0;
		docsInPeriodCount = 0;
		log("Extracting terms from input file: " + inputFile);
		BufferedReader reader = new BufferedReader(new FileReader(inputFile));
		String line;
		while ((line = reader.readLine()) != null) {
			if (docsInPeriodCount >= docsPerPeriod) {
				finishPeriod();
			}
			t++;
			logProgress(t);
			DataEntry d = new DataEntry(line);
			recordNgrams(d);
			docsInPeriodCount++;
		}
		finishPeriod();
		reader.close();
		log("Total number of documents: " + t);

		log("Writing output...");
		t = 0;
		File csvFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + ".csv");
		Writer writer = new BufferedWriter(new FileWriter(csvFile));
		MemeUtils.writeCsvLine(writer, new Object[] {"TERM", "MAX-ABS-CHANGE", "MAX-REL-CHANGE"});
		for (String term : terms.keySet()) {
			logProgress(t);
			t++;
			MemeUtils.writeCsvLine(writer, new Object[] { term, absFreqCh.get(term), relFreqCh.get(term) });
		}
		writer.close();
		log("Finished");
	}

	private void init() {
		if (outputFile == null) {
			outputFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + ".csv");
		}
		logFile = new File(MemeUtils.getLogDir(), getOutputFileName() + ".log");
	}

	private void finishPeriod() {
		if (lastCount != null) {
			for (String t : terms.keySet()) {
				double lc = 0;
				if (lastCount.containsKey(t)) lc = lastCount.get(t);
				double rlc = lc / docsInPeriodCount;
				double tc = 0;
				if (thisCount.containsKey(t)) tc = thisCount.get(t);
				double rtc = (double) tc / docsInPeriodCount;
				double a = rtc - rlc;
				if (!absFreqCh.containsKey(t) || absFreqCh.get(t) < a) {
					absFreqCh.put(t, a);
				}
				double r = rtc / rlc;
				if (!relFreqCh.containsKey(t) || relFreqCh.get(t) < r) {
					relFreqCh.put(t, r);
				}
			}
		}
		lastCount = thisCount;
		thisCount = new HashMap<String,Integer>();
		docsInPeriodCount = 0;
	}

	private String getOutputFileName() {
		String basename = inputFile.getName().replaceAll("\\..*$", "");
		String filename = "frch-" + basename + "-d" + docsPerPeriod;
		return filename;
	}

	private void recordNgrams(DataEntry d) {
		Map<String,Boolean> processed = new HashMap<String,Boolean>();
		String[] tokens = d.getText().trim().split(" ");
		for (int p1 = 0 ; p1 < tokens.length ; p1++) {
			String term = "";
			for (int p2 = p1 ; p2 < tokens.length ; p2++) {
				term += " " + tokens[p2];
				term = term.trim();
				if (!terms.containsKey(term)) break;
				if (processed.containsKey(term)) continue;
				processed.put(term, true);
				if (thisCount.containsKey(term)) {
					thisCount.put(term, thisCount.get(term) + 1);
				} else {
					thisCount.put(term, 0);
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
