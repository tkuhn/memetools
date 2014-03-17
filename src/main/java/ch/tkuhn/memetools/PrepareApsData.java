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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.supercsv.io.CsvListReader;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class PrepareApsData {

	@Parameter(names = "-v", description = "Write detailed log")
	private boolean verbose = false;

	@Parameter(names = "-t", description = "File with terms")
	private File termsFile;

	@Parameter(names = "-tcol", description = "Index or name of column to read terms (if term file is in CSV format)")
	private String termCol = "TERM";

	@Parameter(names = "-c", description = "Only use first c terms")
	private int termCount = -1;

	@Parameter(names = "-sg", description = "Skip GML file generation")
	private boolean skipGml = false;

	@Parameter(names = "-sd", description = "Skip data file generation")
	private boolean skipData = false;

	@Parameter(names = "-sdt", description = "Skip generation of title-only data file")
	private boolean skipDataT = false;

	@Parameter(names = "-sdta", description = "Skip generation of title+abstract data file")
	private boolean skipDataTA = false;

	@Parameter(names = "-r", description = "Randomize graph (for baseline analysis)")
	private boolean randomize = false;

	@Parameter(names = "-rw", description = "Randomize graph within time window (keeping time structure mostly intact)")
	private int randomizeTimeWindow = 0;

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

	private Map<String,String> randomizedDois;

	private List<String> terms;

	private Set<FileVisitOption> walkFileTreeOptions;

	public PrepareApsData() {
	}

	public void run() {
		init();
		try {
			processMetadataDir();
			processAbstractDir();
			processCitationFile();
			readTerms();
			writeDataFiles();
			writeGmlFile();
		} catch (Throwable th) {
			log(th);
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

		terms = null;

		walkFileTreeOptions = new HashSet<FileVisitOption>();
		walkFileTreeOptions.add(FileVisitOption.FOLLOW_LINKS);

		if (randomizeTimeWindow > 0) {
			randomize = true;
		}

		if (randomize) {
			randomizedDois = new HashMap<String,String>();
		}
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

	private void readTerms() throws IOException {
		if (termsFile == null) return;
		log("Reading terms from " + termsFile + " ...");
		terms = new ArrayList<String>();
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
			terms.add(term);
			if (termCount >= 0 && terms.size() >= termCount) {
				break;
			}
		}
		csvReader.close();
	}

	private void writeDataFiles() throws IOException {
		if (skipData) return;
		if (randomize) {
			randomizeDois();
		}
		log("Writing data files...");
		String fileSuffix = ".txt";
		if (randomize) fileSuffix = "-randomized.txt";
		File fileT = new File(MemeUtils.getPreparedDataDir(), "aps-T" + fileSuffix);
		File fileTA = new File(MemeUtils.getPreparedDataDir(), "aps-TA" + fileSuffix);
		int noAbstracts = 0;
		BufferedWriter wT = null;
		BufferedWriter wTA = null;
		if (!skipDataT) wT = new BufferedWriter(new FileWriter(fileT));
		if (!skipDataTA) wTA = new BufferedWriter(new FileWriter(fileTA));
		for (String doi1 : titles.keySet()) {
			List<String> refs = references.get(doi1);
			if (randomize) doi1 = randomizedDois.get(doi1);
			String text = titles.get(doi1);
			String date = dates.get(doi1);
			DataEntry eT = new DataEntry(doi1, date, text);
			if (abstracts.containsKey(doi1)) text += " " + abstracts.get(doi1);
			DataEntry eTA = new DataEntry(doi1, date, text);
			for (String doi2 : refs) {
				if (randomize) doi2 = randomizedDois.get(doi2);
				text = titles.get(doi2);
				eT.addCitedText(text);
				if (abstracts.containsKey(doi2)) text += " " + abstracts.get(doi2);
				eTA.addCitedText(text);
			}
			if (!skipDataT) wT.write(eT.getLine() + "\n");
			if (!skipDataTA) wTA.write(eTA.getLine() + "\n");
		}
		if (!skipDataT) wT.close();
		if (!skipDataTA) wTA.close();
		log("No abstracts: " + noAbstracts);
	}

	private void randomizeDois() {
		log("Randomizing DOIs...");
		if (randomizeTimeWindow > 0) {
			List<String> dateDoiList = new ArrayList<String>(titles.size());
			for (String doi : titles.keySet()) {
				String date = dates.get(doi);
				if (date == null) date = "";
				dateDoiList.add(date + " " + doi);
			}
			Collections.sort(dateDoiList);
			List<String> dois = new ArrayList<String>(randomizeTimeWindow);
			for (String dateDoi : dateDoiList) {
				dois.add(dateDoi.split(" ", -1)[1]);
				if (dois.size() >= randomizeTimeWindow) {
					randomizeDois(dois);
				}
			}
			randomizeDois(dois);
		} else {
			List<String> doisOut = new ArrayList<String>(titles.keySet());
			Collections.shuffle(doisOut);
			int i = 0;
			for (String doiIn : titles.keySet()) {
				randomizedDois.put(doiIn, doisOut.get(i));
				i++;
			}
		}
	}

	private void randomizeDois(List<String> dois) {
		List<String> doisShuffled = new ArrayList<String>(dois);
		Collections.shuffle(doisShuffled);
		int i = 0;
		for (String doiIn : dois) {
			randomizedDois.put(doiIn, doisShuffled.get(i));
			i++;
		}
		dois.clear();
	}

	private void writeGmlFile() throws IOException {
		if (skipGml || randomize) return;
		log("Writing GML file...");
		File file = new File(MemeUtils.getPreparedDataDir(), "aps.gml");
		BufferedWriter w = new BufferedWriter(new FileWriter(file));
		w.write("graph [\n");
		w.write("directed 1\n");
		if (terms != null) {
			for (int i = 0; i < terms.size(); i++) {
				w.write("comment \"meme" + i + ": " + terms.get(i).replace("\"", "") + "\"\n");
			}
		}
		for (String doi : titles.keySet()) {
			String year = dates.get(doi).substring(0, 4);
			String journal = doi.replaceFirst("^10\\.[0-9]+/([^.]+).*$", "$1");
			w.write("node [\n");
			w.write("id \"" + doi + "\"\n");
			w.write("journal \"" + journal + "\"\n");
			w.write("year \"" + year + "\"\n");
			if (terms != null) {
				String text = titles.get(doi);
				if (abstracts.containsKey(doi)) text += " " + abstracts.get(doi);
				text = " " + text + " ";
				for (int i = 0; i < terms.size(); i++) {
					if (text.contains(" " + terms.get(i) + " ")) {
						w.write("meme" + i + " 1\n");
					}
				}
			}
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

	private void logDetail(Object obj) {
		if (verbose) log(obj);
	}

}
