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

import org.supercsv.io.CsvListReader;
import org.supercsv.io.CsvListWriter;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class DetectJournalTerms {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-t", description = "File to load the terms from")
	private File termFile;

	@Parameter(names = "-c", description = "Index or name of term file column")
	private String colIndexOrName = "TERM";

	@Parameter(names = "-s", description = "Threshold for journal size")
	private int journalSizeThreshold = 0;

	private File logFile;

	public static final void main(String[] args) {
		DetectJournalTerms obj = new DetectJournalTerms();
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
		} catch (Throwable th) {
			obj.log(th);
			System.exit(1);
		}
	}

	private Map<String,Boolean> terms;
	private Map<String,Integer> journalSizes;
	private Map<String,Map<String,Integer>> freqMap;
	private Map<String,Double> freqMax;
	private Map<String,Double> freqMin;

	public DetectJournalTerms() {
	}

	public void run() throws IOException {
		init();

		loadTerms();

		int t = 0;
		log("Extracting terms from input file: " + inputFile);
		BufferedReader reader = new BufferedReader(new FileReader(inputFile));
		String line;
		while ((line = reader.readLine()) != null) {
			t++;
			logProgress(t);
			DataEntry d = new DataEntry(line);
			recordNgrams(d);
		}
		reader.close();
		log("Total number of documents: " + t);

		calculateMinMax();

		log("Writing output...");
		t = 0;
		File csvFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + ".csv");
		Writer w = new BufferedWriter(new FileWriter(csvFile));
		CsvListWriter csvWriter = new CsvListWriter(w, MemeUtils.getCsvPreference());
		csvWriter.write("TERM", "MAX-ABS-DIFF", "MAX-REL-DIFF");
		for (String term : terms.keySet()) {
			logProgress(t);
			t++;
			double absFreqDiff = freqMax.get(term) - freqMin.get(term);
			double relFreqDiff = freqMax.get(term) / freqMin.get(term);
			csvWriter.write(term, absFreqDiff, relFreqDiff);
		}
		csvWriter.close();
		log("Finished");
	}

	private void init() {
		if (outputFile == null) {
			outputFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + ".csv");
		}
		logFile = new File(MemeUtils.getLogDir(), getOutputFileName() + ".log");
		log("==========");
		
		terms = new HashMap<String, Boolean>();
		journalSizes = new HashMap<String, Integer>();
		freqMap = new HashMap<String, Map<String,Integer>>();
		freqMax = new HashMap<String, Double>();
		freqMin = new HashMap<String, Double>();
	}

	private void loadTerms() throws IOException {
		log("Loading terms from " + termFile + "...");
		BufferedReader r = new BufferedReader(new FileReader(termFile));
		CsvListReader csvReader = new CsvListReader(r, MemeUtils.getCsvPreference());
		List<String> firstLine = csvReader.read();
		int col;
		if (colIndexOrName.matches("[0-9]+")) {
			col = Integer.parseInt(colIndexOrName);
		} else {
			col = firstLine.indexOf(colIndexOrName);
		}
		List<String> l;
		while ((l = csvReader.read()) != null) {
			terms.put(l.get(col), true);
		}
		csvReader.close();
		log("Number of terms loaded: " + terms.size());
	}

	private void calculateMinMax() {
		for (String journal : freqMap.keySet()) {
			Map<String,Integer> termMap = freqMap.get(journal);
			int size = journalSizes.get(journal);
			log("Journal " + journal + " (" + size + ")");
			for (String t : terms.keySet()) {
				double freq = 1.0 / journalSizes.get(journal);  // Frequency should never be zero
				if (termMap.containsKey(t)) {
					freq = (double) termMap.get(t) / size;
				}
				if (!freqMin.containsKey(t) || freqMin.get(t) > freq) {
					freqMin.put(t, freq);
				}
				if (!freqMax.containsKey(t) || freqMax.get(t) < freq) {
					freqMax.put(t, freq);
				}
			}
			
		}
	}

	private String getOutputFileName() {
		return "jd-" + inputFile.getName().replaceAll("\\..*$", "") + "-s" + journalSizeThreshold;
	}

	private void recordNgrams(DataEntry d) {
		Map<String,Boolean> processed = new HashMap<String,Boolean>();
		String journal = PrepareApsData.getJournalFromDoi(d.getId());
		if (journalSizes.containsKey(journal)) {
			journalSizes.put(journal, journalSizes.get(journal) + 1);
		} else {
			journalSizes.put(journal, 1);
		}
		String[] tokens = d.getText().trim().split(" ");
		for (int p1 = 0 ; p1 < tokens.length ; p1++) {
			String term = "";
			for (int p2 = p1 ; p2 < tokens.length ; p2++) {
				term += " " + tokens[p2];
				term = term.trim();
				if (!terms.containsKey(term)) break;
				if (processed.containsKey(term)) continue;
				processed.put(term, true);
				Map<String,Integer> termMap;
				if (freqMap.containsKey(journal)) {
					termMap = freqMap.get(journal);
				} else {
					termMap = new HashMap<String,Integer>();
					freqMap.put(journal, termMap);
				}
				if (termMap.containsKey(term)) {
					termMap.put(term, termMap.get(term) + 1);
				} else {
					termMap.put(term, 1);
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
