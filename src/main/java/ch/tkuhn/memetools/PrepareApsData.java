package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class PrepareApsData {

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

	private static String metadataFolder = "aps-dataset-metadata";
	private static String abstractFolder = "aps-abstracts";
	private static String citationFile = "aps-dataset-citations/citing_cited.csv";

	private Map<String,String> titles;
	private Map<String,String> dates;
	private Map<String,String> abstracts;
	private Map<String,List<String>> references;

	private Set<FileVisitOption> walkFileTreeOptions;

	public PrepareApsData() {
	}

	public void run() {
		init();
		try {
			processMetadataDir();
			processAbstractDir();
			processCitationFile();
			writeDataFiles();
			writeGmlFile();
		} catch (IOException ex) {
			log(ex);
			System.exit(1);
		}
		log("Finished");
	}

	private void init() {
		logFile = new File(MemeUtils.getLogDir(), "prepare-aps.log");
		log("==========");
		log("Starting...");

		titles = new HashMap<String,String>();
		dates = new HashMap<String,String>();
		abstracts = new HashMap<String,String>();
		references = new HashMap<String,List<String>>();

		walkFileTreeOptions = new HashSet<FileVisitOption>();
		walkFileTreeOptions.add(FileVisitOption.FOLLOW_LINKS);
	}

	private void processMetadataDir() throws IOException {
		log("Reading metadata...");
		File metadataDir = new File(MemeUtils.getRawDataDir(), metadataFolder);
		Files.walkFileTree(metadataDir.toPath(), walkFileTreeOptions, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
				if (path.toString().endsWith(".xml")) {
					processMetadataFile(path);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		log("Number of documents: " + titles.size());
	}

	private void processMetadataFile(Path path) {
		log("Reading metadata file: " + path);
		try {
			BufferedReader r = new BufferedReader(new FileReader(path.toFile()));
			int errors = 0;
			String doi = null;
			String date = null;
			String line;
			while ((line = r.readLine()) != null) {
				line = line.trim();
				if (line.matches(".*doi=\".*\".*")) {
					String newDoi = line.replaceFirst("^.*doi=\"([^\"]*)\".*$", "$1");
					if (doi != null && !doi.equals(newDoi)) {
						logDetail("ERROR. Two DOI values found for same entry");
						errors++;
					}
					doi = newDoi;
				}
				if (line.matches(".*<issue printdate=\"[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]\".*")) {
					String newDate = line.replaceFirst("^.*<issue printdate=\"([0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9])\".*$", "$1");
					if (date != null && !date.equals(newDate)) {
						logDetail("ERROR. Two year values found for same entry");
						errors++;
					}
					date = newDate;
				}
				if (line.matches(".*<title>.*</title>.*")) {
					String title = line.replaceFirst("^.*<title>(.*)</title>.*$", "$1");
					if (doi == null) {
						logDetail("ERROR. No DOI found for title: " + title);
						errors++;
						continue;
					}
					if (date == null) {
						logDetail("ERROR. No publishing date found for title: " + title);
						errors++;
						continue;
					}
					title = MemeUtils.normalize(title);
					if (!titles.containsKey(doi)) {
						titles.put(doi, title);
						dates.put(doi, date);
						// initialize also references table:
						references.put(doi, new ArrayList<String>());
					} else {
						logDetail("ERROR. Duplicate DOI: " + doi);
						errors++;
					}
					doi = null;
					date = null;
				}
			}
			r.close();
			log("Number of errors: " + errors);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private void processAbstractDir() throws IOException {
		log("Reading abstracts...");
		File abstractDir = new File(MemeUtils.getRawDataDir(), abstractFolder);
		Files.walkFileTree(abstractDir.toPath(), walkFileTreeOptions, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
				if (path.toString().endsWith(".txt")) {
					processAbstractFile(path);
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private void processAbstractFile(Path path) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(path.toFile()));
			String doi = "10.1103/" + path.getFileName().toString().replaceFirst(".txt$", "");
			String text = "";
			String line;
			while ((line = br.readLine()) != null) {
				text += line.trim() + " ";
			}
			abstracts.put(doi, MemeUtils.normalize(text));
			br.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private void processCitationFile() throws IOException {
		log("Reading citations...");
		File file = new File(MemeUtils.getRawDataDir(), citationFile);
		BufferedReader r = new BufferedReader(new FileReader(file));
		String line;
		int invalid = 0;
		int missing = 0;
		int linecount = 0;
		while ((line = r.readLine()) != null) {
			linecount++;
			if (linecount == 1) continue;  // skip header line
			line = line.trim();
			if (line.matches(".*,.*")) {
				String doi1 = line.replaceFirst("^(.*),.*$", "$1");
				String doi2 = line.replaceFirst("^.*,(.*)$", "$1");
				if (!titles.containsKey(doi1)) {
					logDetail("ERROR. Missing publication: " + doi1);
					missing++;
					continue;
				}
				if (!titles.containsKey(doi2)) {
					logDetail("ERROR. Missing publication: " + doi2);
					missing++;
					continue;
				}
				references.get(doi1).add(doi2);
			} else {
				log("ERROR. Invalid line: " + line);
				invalid++;
			}
		}
		r.close();
		log("Invalid lines: " + invalid);
		log("Missing publications: " + missing);
	}

	private void writeDataFiles() throws IOException {
		File fileT = new File(MemeUtils.getPreparedDataDir(), "aps-T.txt");
		File fileTA = new File(MemeUtils.getPreparedDataDir(), "aps-TA.txt");
		int noAbstracts = 0;
		BufferedWriter wT = new BufferedWriter(new FileWriter(fileT));
		BufferedWriter wTA = new BufferedWriter(new FileWriter(fileTA));
		for (String doi1 : titles.keySet()) {
			String text = titles.get(doi1);
			String date = dates.get(doi1);
			DataEntry eT = new DataEntry(doi1, date, text);
			if (abstracts.containsKey(doi1)) text += " " + abstracts.get(doi1);
			DataEntry eTA = new DataEntry(doi1, date, text);
			for (String doi2 : references.get(doi1)) {
				text = titles.get(doi2);
				eT.addCitedText(text);
				if (abstracts.containsKey(doi2)) text += " " + abstracts.get(doi2);
				eTA.addCitedText(text);
			}
			wT.write(eT.getLine() + "\n");
			wTA.write(eTA.getLine() + "\n");
		}
		wT.close();
		wTA.close();
		log("No abstracts: " + noAbstracts);
	}

	private void writeGmlFile() throws IOException {
		File file = new File(MemeUtils.getPreparedDataDir(), "aps.gml");
		BufferedWriter w = new BufferedWriter(new FileWriter(file));
		w.write("graph [\n");
		w.write("directed 1\n");
		for (String doi : titles.keySet()) {
			String year = dates.get(doi).substring(0, 4);
			String journal = doi.replaceFirst("^10\\.[0-9]+/([^.]+).*$", "$1");
			String text = titles.get(doi);
			if (abstracts.containsKey(doi)) text += " " + abstracts.get(doi);
			text = " " + text + " ";
			w.write("node [\n");
			w.write("id \"" + doi + "\"\n");
			w.write("journal \"" + journal + "\"\n");
			w.write("year \"" + year + "\"\n");
			// TODO Make this general:
			if (text.contains(" quantum ")) w.write("memeQuantum \"y\"\n");
			if (text.contains(" traffic ")) w.write("memeTraffic \"y\"\n");
			if (text.contains(" black hole ")) w.write("memeBlackHole \"y\"\n");
			if (text.contains(" graphene ")) w.write("memeGraphene \"y\"\n");
			w.write("]\n");
		}
		for (String doi1 : references.keySet()) {
			for (String doi2 : references.get(doi1)) {
				w.write("edge [\n");
				w.write("source \"" + doi1 + "\"\n");
				w.write("target \"" + doi2 + "\"\n");
				w.write("]\n");
			}
		}
		w.write("]\n");
		w.close();
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

	void logDetail(Object obj) {
		if (verbose) log(obj);
	}

}
