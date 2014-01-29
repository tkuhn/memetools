package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

	@Parameter(names = "-m", description = "Metric to use: 'ms' = meme score")
	private String metric = "ms";

	@Parameter(names = "-n", description = "Set n parameter")
	private int n = 3;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

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

	private CsvListWriter csvWriter;

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
		logFile = new File(MemeUtils.getLogDir(), getOutputFileName() + ".log");
		log("==========");

		if (outputFile == null) {
			outputFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + ".csv");
		}

		stepsPerWindow = windowSize / stepSize;
		ms = new MemeScorer[stepsPerWindow];
		ms[0] = new MemeScorer(false);
		for (int i = 1 ; i < stepsPerWindow ; i++) {
			ms[i] = new MemeScorer(ms[0]);
		}
		terms = new ArrayList<String>();
	}

	private void readTerms() throws IOException {
		log("Reading terms from " + termsFile + " ...");
		BufferedReader reader = new BufferedReader(new FileReader(termsFile));
		String line;
		while ((line = reader.readLine()) != null) {
			String term = MemeUtils.normalize(line);
			ms[0].addTerm(term);
			terms.add(term);
		}
		reader.close();
		log("Number of terms: " + terms.size());
	}

	private void processAndWriteData() throws IOException {
		log("Processing data from " + inputFile + " and writing result to " + outputFile + " ...");
		BufferedReader reader = new BufferedReader(new FileReader(inputFile));
		BufferedWriter w = new BufferedWriter(new FileWriter(outputFile));
		csvWriter = new CsvListWriter(w, MemeUtils.getCsvPreference());

		List<String> outputHeader = new ArrayList<String>();
		outputHeader.add("DATE");
		for (String term : terms) outputHeader.add(term);
		csvWriter.write(outputHeader);

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
		csvWriter.close();
	}

	private void writeLine(String date) throws IOException {
		List<String> outputLine = new ArrayList<String>();
		outputLine.add(date);
		for (String term : terms) {
			int mmVal = 0, mVal = 0, xmVal = 0, xVal = 0, fVal = 0;
			for (MemeScorer m : ms) {
				mmVal += m.getMM(term);
				mVal += m.getM(term);
				xmVal += m.getXM(term);
				xVal += m.getX(term);
				fVal += m.getF(term);
			}
			double[] v = MemeScorer.calculateMemeScoreValues(mmVal, mVal, xmVal, xVal, fVal, n);
			outputLine.add(v[3] + "");
		}
		csvWriter.write(outputLine);
	}

	private String getOutputFileName() {
		String basename = inputFile.getName().replaceAll("\\..*$", "");
		basename = basename.replace("-chronologic", "");
		String filename = "hi-" + metric + "-" + basename + "-w" + windowSize + "-s" + stepSize;
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
