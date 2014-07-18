package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.supercsv.io.CsvListWriter;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class CalculateCommHistory {

	@Parameter(description = "chronologically-sorted-input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-w", description = "Window size", required = true)
	private int windowSize;

	@Parameter(names = "-s", description = "Step size", required = true)
	private int stepSize;

	private int stepsPerWindow;

	@Parameter(names = "-m", description = "File community map", required = true)
	private File communityMapFile;

	@Parameter(names = "-v", description = "Write detailed log")
	private boolean verbose = false;

	private File logFile;

	public static final void main(String[] args) {
		CalculateCommHistory obj = new CalculateCommHistory();
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

	private int[][] count;
	private Map<String,String> communityMap;
	private List<String> communitySequence;

	private CsvListWriter csvWriter;

	public void run() {
		init();
		try {
			readCommunityMap();
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

		stepsPerWindow = windowSize / stepSize;
		communityMap = new HashMap<String,String>();
		communitySequence = new ArrayList<String>();
	}

	private void readCommunityMap() throws IOException {
		log("Reading communities from " + communityMapFile + " ...");
		communitySequence.add("-1");
		BufferedReader reader = new BufferedReader(new FileReader(communityMapFile));
		String line;
		while ((line = reader.readLine()) != null) {
			String[] parts = line.split(" ");
			String c = parts[1];
			communityMap.put(parts[0], c);
			if (!communitySequence.contains(c)) {
				communitySequence.add(c);
			}
		}
		reader.close();
		Collections.sort(communitySequence, new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				Integer i1 = Integer.parseInt(o1);
				Integer i2 = Integer.parseInt(o2);
				return i1.compareTo(i2);
			}
		});
		log("Number of communities: " + communitySequence.size());
	}

	private void processAndWriteData() throws IOException {
		log("Processing data from " + inputFile + " and writing result to output files...");
		BufferedReader reader = new BufferedReader(new FileReader(inputFile));
		File outputFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + ".csv");
		BufferedWriter w = new BufferedWriter(new FileWriter(outputFile));
		csvWriter = new CsvListWriter(w, MemeUtils.getCsvPreference());

		List<String> outputHeader = new ArrayList<String>();
		outputHeader.add("DATE");

		for (String community : communitySequence) {
			outputHeader.add(community);
		}
		csvWriter.write(outputHeader);

		int entryCount = 0;
		int bin = 0;
		count = new int[stepsPerWindow][communitySequence.size()];
		String inputLine;
		while ((inputLine = reader.readLine()) != null) {
			logProgress(entryCount);
			DataEntry d = new DataEntry(inputLine);
			String c = communityMap.get(d.getId());
			if (c == null) c = "-1";
			int i = communitySequence.indexOf(c);
			count[bin][i]++;
			entryCount++;
			bin = (entryCount % windowSize) / stepSize;
			if (entryCount % stepSize == 0) {
				// Current bin is full
				if (entryCount >= windowSize) {
					// All bins are full
					writeLine(d.getDate());
				}
				logDetail("Start new bin " + bin + " (at entry " + entryCount + ")");
				count[bin] = new int[communitySequence.size()];
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
		for (int c = 0 ; c < communitySequence.size() ; c++) {
			int sum = 0;
			for (int[] x : count) {
				sum += x[c];
			}
			outputLine.add(sum + "");
		}
		csvWriter.write(outputLine);
	}

	private String getOutputFileName() {
		String basename = inputFile.getName().replaceAll("\\..*$", "");
		basename = basename.replace("-chronologic", "");
		basename = basename.replaceFirst("-TA?", "");
		String filename = "hi-comm-" + basename + "-w" + windowSize + "-s" + stepSize;
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
