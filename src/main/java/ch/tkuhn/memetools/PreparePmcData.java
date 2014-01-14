package ch.tkuhn.memetools;

import java.io.File;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class PreparePmcData {

	@Parameter(names = "-r", description = "Path to read raw data")
	private String rawDataPath = "data";

	@Parameter(names = "-p", description = "Path to write prepared data")
	private String preparedDataPath = "input";

	@Parameter(names = "-v", description = "Write detailed log")
	private boolean verbose = false;

	private File logFile;

	public static final void main(String[] args) {
		PrepareApsData obj = new PrepareApsData();
		JCommander jc = new JCommander(obj);
		try {
			jc.parse(args);
		} catch (ParameterException ex) {
			jc.usage();
			System.exit(1);
		}
		obj.run();
	}

	private static String pmcListFile = "PMC-ids.csv";
	private static String pmcXmlPath = "pmcoa-xml";

	public PreparePmcData() {
	}

	public void run() {
		log("Starting...");
		// TODO;
	}

	private void log(Object text) {
		MemeUtils.log(logFile, text);
	}

	void logDetail(Object text) {
		if (verbose) log(text);
	}

}
