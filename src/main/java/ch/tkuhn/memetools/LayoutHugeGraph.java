package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class LayoutHugeGraph {

	@Parameter(description = "gexf-input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-d", description = "The directory to read the raw data from")
	private File rawWosDataDir;

	@Parameter(names = "-v", description = "Write detailed log")
	private boolean verbose = false;

	private File logFile;

	public static final void main(String[] args) {
		LayoutHugeGraph obj = new LayoutHugeGraph();
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

	private static String wosFolder = "wos";

	private Map<String,Pair<Double,Double>> positions;

	private BufferedWriter writer;

	public LayoutHugeGraph() {
	}

	public void run() {
		try {
			init();
			readBasePositions();
			// TODO;
			writer.close();
		} catch (Throwable th) {
			log(th);
			System.exit(1);
		}
		log("Finished");
	}

	private void init() throws IOException {
		logFile = new File(MemeUtils.getLogDir(), getOutputFileName() + ".log");
		log("==========");
		log("Starting...");

		positions = new HashMap<String,Pair<Double,Double>>();
		if (outputFile == null) {
			outputFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + ".csv");
		}
		if (rawWosDataDir == null) {
			rawWosDataDir = new File(MemeUtils.getRawDataDir(), wosFolder);
		}

		writer = new BufferedWriter(new FileWriter(outputFile));
	}

	private static final String idPattern = "^\\s*<node id=\"([0-9]{9})\".*$";
	private static final String coordPattern = "^\\s*<viz:position x=\"(.*?)\" y=\"(.*?)\".*$";

	private void readBasePositions() throws IOException {
		log("Reading base positions from gext file: " + inputFile);
		BufferedReader reader = new BufferedReader(new FileReader(inputFile), 64*1024);
		int errors = 0;
		String line;
		String id = null;
		while ((line = reader.readLine()) != null) {
			if (line.matches(idPattern)) {
				if (id != null) {
					errors++;
					logDetail("No coordinates found for: " + id);
				}
				id = line.replaceFirst(idPattern, "$1");
			} else if (line.matches(coordPattern)) {
				if (id == null) {
					errors++;
					logDetail("No ID found for coordinates: " + line);
				} else {
					double posX = Double.parseDouble(line.replaceFirst(coordPattern, "$1"));
					double posY = Double.parseDouble(line.replaceFirst(coordPattern, "$2"));
					addPosition(id, posX, posY);
					id = null;
				}
			}
		}
		if (id != null) {
			errors++;
			logDetail("No coordinates found for: " + id);
		}
		reader.close();
		log("Number of errors: " + errors);
	}

	private void addPosition(String id, double posX, double posY) throws IOException {
		positions.put(id, Pair.of(posX, posY));
		writer.write(id + "," + posX + "," + posY + "\n");
	}

	private String getOutputFileName() {
		return "la-" + inputFile.getName().replaceAll("\\..*$", "");
	}

	private void logDetail(Object obj) {
		if (verbose) log(obj);
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

}
