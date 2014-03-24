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

public class CalculateTopHistory {

	@Parameter(description = "chronologically-sorted-input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-w", description = "Window size", required = true)
	private int windowSize;

	@Parameter(names = "-s", description = "Step size", required = true)
	private int stepSize;

	private int stepsPerWindow;

	@Parameter(names = "-n", description = "Set n parameter")
	private int n = 1;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-v", description = "Write detailed log")
	private boolean verbose = false;

	private File logFile;

	public static final void main(String[] args) {
		CalculateTopHistory obj = new CalculateTopHistory();
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
	private List<String> topMemes;
	private List<Double> secondScoreTimeline;
	private List<Double> firstScoreTimeline;
	private List<String> topMemeTimeline;

	public void run() {
		init();
		try {
			extractTerms();
			processData();
			ms = null;  // free for garbage collection
			writeOutput();
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
		ms[0] = new MemeScorer(MemeScorer.DECOMPOSED_SCREEN_MODE);
		for (int i = 1 ; i < stepsPerWindow ; i++) {
			ms[i] = new MemeScorer(ms[0], MemeScorer.DECOMPOSED_SCREEN_MODE);
		}

		topMemes = new ArrayList<String>();
		secondScoreTimeline = new ArrayList<Double>();
		firstScoreTimeline = new ArrayList<Double>();
		topMemeTimeline = new ArrayList<String>();
	}

	private void extractTerms() throws IOException {
		log("Extract terms from " + inputFile + " ...");
		BufferedReader reader = new BufferedReader(new FileReader(inputFile));
		int entryCount = 0;
		String inputLine;
		while ((inputLine = reader.readLine()) != null) {
			logProgress(entryCount);
			DataEntry d = new DataEntry(inputLine);
			ms[0].screenTerms(d);
			entryCount++;
		}
		log("Number of terms extracted: " + ms[0].getTerms().size());
		reader.close();
	}

	private void processData() throws IOException {
		log("Processing data from " + inputFile + " ...");
		BufferedReader reader = new BufferedReader(new FileReader(inputFile));
		int inputEntryCount = 0;
		int outputEntryCount = 0;
		int bin = 0;
		String inputLine;
		while ((inputLine = reader.readLine()) != null) {
			logProgress(inputEntryCount);
			DataEntry d = new DataEntry(inputLine);
			ms[bin].recordTerms(d);
			inputEntryCount++;
			bin = (inputEntryCount % windowSize) / stepSize;
			if (inputEntryCount % stepSize == 0) {
				// Current bin is full
				if (inputEntryCount >= windowSize) {
					// All bins are full
					makeOutputEntry(outputEntryCount);
					outputEntryCount++;
				}
				logDetail("Start new bin " + bin + " (at entry " + inputEntryCount + ")");
				ms[bin].clear();
			}
		}
		log(outputEntryCount + " output entries created");
		log((inputEntryCount % stepSize) + " final input entries ignored");
		reader.close();
	}

	private void makeOutputEntry(int count) {
		String firstTerm = null;
		double firstScore = -1.0;
		double secondScore = -1.0;
		for (String term : ms[0].getTerms().keySet()) {
			int mmVal = 0, mVal = 0, xmVal = 0, xVal = 0, fVal = 0;
			for (MemeScorer m : ms) {
				mmVal += m.getMM(term);
				mVal += m.getM(term);
				xmVal += m.getXM(term);
				xVal += m.getX(term);
				fVal += m.getF(term);
			}
			double score = MemeScorer.calculateMemeScoreValues(mmVal, mVal, xmVal, xVal, fVal, n)[3];
			if (score > firstScore) {
				secondScore = firstScore;
				firstScore = score;
				firstTerm = term;
			} else if (score > secondScore) {
				secondScore = score;
			}
		}
		if (!topMemes.contains(firstTerm)) {
			topMemes.add(firstTerm);
		}
		topMemeTimeline.add(firstTerm);
		firstScoreTimeline.add(firstScore);
		secondScoreTimeline.add(secondScore);
	}

	private void writeOutput() throws IOException {
		log("Writing result to " + outputFile + " ...");
		BufferedWriter w = new BufferedWriter(new FileWriter(outputFile));
		CsvListWriter csvWriter = new CsvListWriter(w, MemeUtils.getCsvPreference());

		List<String> line = new ArrayList<String>();
		line.add("SECOND");
		for (String term : topMemes) line.add(term);
		csvWriter.write(line);

		for (int i = 0 ; i < topMemeTimeline.size() ; i++) {
			line = new ArrayList<String>();
			line.add(secondScoreTimeline.get(i) + "");
			for (String term : topMemes) {
				if (term.equals(topMemeTimeline.get(i))) {
					line.add((firstScoreTimeline.get(i) - secondScoreTimeline.get(i)) + "");
				} else {
					line.add("0");
				}
			}
			csvWriter.write(line);
		}

		csvWriter.close();
	}

	private String getOutputFileName() {
		String basename = inputFile.getName().replaceAll("\\..*$", "");
		basename = basename.replace("-chronologic", "");
		String filename = "hi-topms-" + basename + "-n" + n + "-w" + windowSize + "-s" + stepSize;
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
