package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.supercsv.io.CsvListReader;
import org.supercsv.io.CsvListWriter;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class CalculateHistory {

	@Parameter(description = "chronologically-sorted-input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-w", description = "Window size", required = true)
	private int windowSize;

	@Parameter(names = "-s", description = "Step size", required = true)
	private int stepSize;

	private int stepsPerWindow;

	@Parameter(names = "-t", description = "File with terms", required = true)
	private File termsFile;

	@Parameter(names = "-tcol", description = "Index or name of column to read terms (if term file is in CSV format)")
	private String termCol = "TERM";

	@Parameter(names = "-c", description = "Only use first c terms")
	private int termCount = -1;

	@Parameter(names = "-m", description = "Metrics to use (comma separated): 'ms' = meme score, " +
			"'ps' = propagation score, 'st' = sticking factor, 'sp' = sparking factor, 'af' = absolute frequency")
	private String metrics = "ms";

	@Parameter(names = "-d", description = "Set delta parameter (controlled noise level)")
	private int delta = 1;

	@Parameter(names = "-v", description = "Write detailed log")
	private boolean verbose = false;

	private File logFile;

	public static final void main(String[] args) {
		CalculateHistory obj = new CalculateHistory();
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
		if (obj.stepSize <= 0 || obj.windowSize <= obj.stepSize) {
			System.err.println("ERROR: Step size has to be positive and smaller than window size");
			jc.usage();
			System.exit(1);
		}
		if (obj.windowSize % obj.stepSize != 0) {
			System.err.println("ERROR: Window size must be a multiple of step size");
			jc.usage();
			System.exit(1);
		}
		obj.inputFile = new File(obj.parameters.get(0));
		obj.run();
	}

	private MemeScorer[] ms;
	private List<String> terms;

	private List<CsvListWriter> csvWriters;

	public void run() {
		init();
		try {
			readTerms();
			processAndWriteData();
		} catch (Throwable th) {
			log(th);
			System.exit(1);
		}
		log("Finished");
	}

	private void init() {
		logFile = new File(MemeUtils.getLogDir(), getOutputFileName(metrics) + ".log");
		log("==========");

		stepsPerWindow = windowSize / stepSize;
		ms = new MemeScorer[stepsPerWindow];
		ms[0] = new MemeScorer(MemeScorer.GIVEN_TERMLIST_MODE);
		for (int i = 1 ; i < stepsPerWindow ; i++) {
			ms[i] = new MemeScorer(ms[0], MemeScorer.GIVEN_TERMLIST_MODE);
		}
		terms = new ArrayList<String>();

		csvWriters = new ArrayList<CsvListWriter>();
	}

	private void readTerms() throws IOException {
		log("Reading terms from " + termsFile + " ...");
		if (termsFile.toString().endsWith(".csv")) {
			readTermsCsv();
		} else {
			readTermsTxt();
		}
		log("Number of terms: " + terms.size());
	}

	private void readTermsTxt() throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(termsFile));
		String line;
		while ((line = reader.readLine()) != null) {
			String term = MemeUtils.normalize(line);
			ms[0].addTerm(term);
			terms.add(term);
			if (termCount >= 0 && terms.size() >= termCount) {
				break;
			}
		}
		reader.close();
	}

	private void readTermsCsv() throws IOException {
		BufferedReader r = new BufferedReader(new FileReader(termsFile));
		CsvListReader csvReader = new CsvListReader(r, MemeUtils.getCsvPreference());
		List<String> header = csvReader.read();
		int col;
		if (termCol.matches("[0-9]+")) {
			col = Integer.parseInt(termCol);
		} else {
			col = header.indexOf(termCol);
		}
		List<String> line;
		while ((line = csvReader.read()) != null) {
			String term = MemeUtils.normalize(line.get(col));
			ms[0].addTerm(term);
			terms.add(term);
			if (termCount >= 0 && terms.size() >= termCount) {
				break;
			}
		}
		csvReader.close();
	}

	private void processAndWriteData() throws IOException {
		log("Processing data from " + inputFile + " and writing result to output files...");
		BufferedReader reader = new BufferedReader(new FileReader(inputFile));
		for (String metric : metrics.split(",")) {
			File outputFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName(metric) + ".csv");
			BufferedWriter w = new BufferedWriter(new FileWriter(outputFile));
			csvWriters.add(new CsvListWriter(w, MemeUtils.getCsvPreference()));
		}

		List<String> outputHeader = new ArrayList<String>();
		outputHeader.add("DATE");
		for (String term : terms) outputHeader.add(term);
		for (CsvListWriter csvWriter : csvWriters) {
			csvWriter.write(outputHeader);
		}

		int entryCount = 0;
		int bin = 0;
		String inputLine;
		while ((inputLine = reader.readLine()) != null) {
			logProgress(entryCount);
			DataEntry d = new DataEntry(inputLine);
			ms[bin].recordTerms(d);
			entryCount++;
			bin = (entryCount % windowSize) / stepSize;
			if (entryCount % stepSize == 0) {
				// Current bin is full
				if (entryCount >= windowSize) {
					// All bins are full
					writeLine(d.getDate());
				}
				logDetail("Start new bin " + bin + " (at entry " + entryCount + ")");
				ms[bin].clear();
			}
		}
		log(((entryCount - windowSize) / stepSize + 1) + " output entries written");
		log((entryCount % stepSize) + " final input entries ignored");
		reader.close();
		for (CsvListWriter csvWriter : csvWriters) {
			csvWriter.close();
		}
	}

	private void writeLine(String date) throws IOException {
		List<List<String>> outputLines = new ArrayList<List<String>>();
		String[] metricsArray = metrics.split(",");
		for (int i = 0 ; i < metricsArray.length ; i++) {
			List<String> outputLine = new ArrayList<String>();
			outputLine.add(date);
			outputLines.add(outputLine);
		}
		for (String term : terms) {
			int mmVal = 0, mVal = 0, xmVal = 0, xVal = 0, fVal = 0;
			for (MemeScorer m : ms) {
				mmVal += m.getMM(term);
				mVal += m.getM(term);
				xmVal += m.getXM(term);
				xVal += m.getX(term);
				fVal += m.getF(term);
			}
			double[] v = MemeScorer.calculateMemeScoreValues(mmVal, mVal, xmVal, xVal, fVal, delta);
			for (int i = 0 ; i < metricsArray.length ; i++) {
				String metric = metricsArray[i];
				if (metric.equals("st")) {
					outputLines.get(i).add(v[0] + "");
				} else if (metric.equals("sp")) {
					outputLines.get(i).add(v[1] + "");
				} else if (metric.equals("ps")) {
					outputLines.get(i).add(v[2] + "");
				} else if (metric.equals("ms")) {
					outputLines.get(i).add(v[3] + "");
				} else if (metric.equals("af")) {
					outputLines.get(i).add(fVal + "");
				}
			}
		}
		for (int i = 0 ; i < metricsArray.length ; i++) {
			csvWriters.get(i).write(outputLines.get(i));
		}
	}

	private String getOutputFileName(String metric) {
		String basename = inputFile.getName().replaceAll("\\..*$", "");
		basename = basename.replace("-chronologic", "");
		String filename = "hi-" + metric + "-" + basename + "-d" + delta + "-w" + windowSize + "-s" + stepSize;
		return filename;
	}

	private void logProgress(int p) {
		if (p % 100000 == 0) log(p + "...");
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

	private void logDetail(Object obj) {
		if (verbose) log(obj);
	}

}
