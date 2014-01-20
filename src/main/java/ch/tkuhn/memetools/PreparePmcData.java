package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class PreparePmcData {

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

	private static String pmcIdsFile = "PMC-ids.csv";
//	private static String pmcXmlPath = "pmcoa-xml";

	private static String idlinePattern = "^([^,]*,|\"[^\"]*\",){7}([^,]*|\"[^\"]*\"),(PMC[0-9]+),([0-9]*),([^,]*|\"[^\"]*\"),([^,]*|\"[^\"]*\")$";

	private Map<String,String> idMap;

	public PreparePmcData() {
	}

	public void run() {
		init();
		try {
			makeIdMap();
			processXmlFiles();
			writeDataFiles();
			writeGmlFile();
		} catch (IOException ex) {
			log(ex);
			System.exit(1);
		}
		log("Finished");
	}

	private void init() {
		logFile = new File(MemeUtils.getLogDir(), "prepare-pmc.log");
		log("==========");
		log("Starting...");

		idMap = new HashMap<String,String>();
	}

	private void makeIdMap() throws IOException {
		log("Reading IDs...");
		File file = new File(MemeUtils.getRawDataDir(), pmcIdsFile);
		BufferedReader r = new BufferedReader(new FileReader(file));
		String line;
		while ((line = r.readLine()) != null) {
			line = line.trim();
			if (!line.matches(idlinePattern)) {
				log("Ignoring line: " + line);
				continue;
			}
			String pmcid = line.replaceFirst(idlinePattern, "$3");
			String doi = line.replaceFirst(idlinePattern, "$2");
			if (!doi.isEmpty()) idMap.put(doi, pmcid);
			String pmid = line.replaceFirst(idlinePattern, "$4");
			if (!pmid.isEmpty()) idMap.put(pmid, pmcid);
			log(pmcid + " " + doi + " " + pmid);
		}
		r.close();
	}

	private void processXmlFiles() throws IOException {
		// TODO
	}

	private void writeDataFiles() throws IOException {
		// TODO
	}

	private void writeGmlFile() throws IOException {
		// TODO
	}

	private void log(Object text) {
		MemeUtils.log(logFile, text);
	}

	void logDetail(Object text) {
		if (verbose) log(text);
	}

}
