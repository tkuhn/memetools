package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class CalculateHistory {

	@Parameter(description = "input-file", required = true)
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

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

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
		if (obj.windowSize % obj.stepSize != 0) {
			System.err.println("ERROR: Window size must be a multiple of step size");
			jc.usage();
			System.exit(1);
		}
		obj.inputFile = new File(obj.parameters.get(0));
		obj.run();
	}

	private Map<String,Boolean> terms;

	public void run() {
		init();
		try {
			readTerms();
			// TODO
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
		terms = new HashMap<String,Boolean>();
	}

	private void readTerms() throws IOException {
		log("Reading terms from " + termsFile + " ...");
		BufferedReader reader = new BufferedReader(new FileReader(termsFile));
		String line;
		while ((line = reader.readLine()) != null) {
			terms.put(MemeUtils.normalize(line), true);
		}
		reader.close();
		log("Number of terms: " + terms.size());
	}

	private String getOutputFileName() {
		String basename = inputFile.getName().replaceAll("\\..*$", "");
		String filename = "hi-" + metric + "-" + basename + "-w" + windowSize + "-s" + stepSize;
		return filename;
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

}
