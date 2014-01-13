package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PrepareApsData {

	public static void run(PrepareData superProcess) {
		PrepareApsData obj = new PrepareApsData(superProcess);
		obj.run();
	}

	private static String metadataFolder = "aps-dataset-metadata";
	private static String abstractFolder = "aps-abstracts";
	private static String citationFile = "aps-dataset-citations/citing_cited.csv";

	private PrepareData s;

	private Map<String,String> titles;
	private Map<String,Short> years;
	private Map<String,String> abstracts;

	public PrepareApsData(PrepareData superProcess) {
		this.s = superProcess;
	}

	public void run() {
		log("Starting...");
		titles = new HashMap<String,String>();
		years = new HashMap<String,Short>();
		abstracts = new HashMap<String,String>();
		Set<FileVisitOption> walkFileTreeOptions = new HashSet<FileVisitOption>();
		walkFileTreeOptions.add(FileVisitOption.FOLLOW_LINKS);

		log("Reading metadata...");
		File metadataDir = new File(s.getRawDataPath() + "/" + metadataFolder);
		try {
			Files.walkFileTree(metadataDir.toPath(), walkFileTreeOptions, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
					if (path.toString().endsWith(".xml")) {
						processMetadataFile(path);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		log("Number of documents: " + titles.size());

		log("Reading abstracts...");
		File abstractDir = new File(s.getRawDataPath() + "/" + abstractFolder);
		try {
			Files.walkFileTree(abstractDir.toPath(), walkFileTreeOptions, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
					if (path.toString().endsWith(".txt")) {
						processAbstractFile(path);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		// TODO
	}

	private void processMetadataFile(Path path) {
		log("Reading metadata file: " + path);
		try {
			BufferedReader br = new BufferedReader(new FileReader(path.toFile()));
			String doi = null;
			Short year = null;
			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.matches(".*doi=\".*\".*")) {
					String newDoi = line.replaceFirst("^.*doi=\"([^\"]*)\".*$", "$1");
					if (doi != null && !doi.equals(newDoi)) {
						log("ERROR: Two DOI values found for same entry");
					}
					doi = newDoi;
				}
				if (line.matches(".*<issue printdate=\"[0-9][0-9][0-9][0-9]-.*")) {
					Short newYear = new Short(line.replaceFirst("^.*<issue printdate=\"([0-9][0-9][0-9][0-9])-.*$", "$1"));
					if (year != null && !year.equals(newYear)) {
						log("ERROR: Two year values found for same entry");
					}
					year = newYear;
				}
				if (line.matches(".*<title>.*</title>.*")) {
					String title = line.replaceFirst("^.*<title>(.*)</title>.*$", "$1");
					if (doi == null) {
						log("ERROR: No DOI found for title: " + title);
						continue;
					}
					if (year == null) {
						log("ERROR: No year found for title: " + title);
						continue;
					}
					title = PrepareData.normalize(title);
					if (!titles.containsKey(doi)) {
						titles.put(doi, title);
						years.put(doi, year);
					} else {
						log("ERROR: Duplicate DOI: " + doi);
					}
					doi = null;
					year = null;
				}
			}
			br.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
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
			abstracts.put(doi, PrepareData.normalize(text));
			br.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private void log(Object text) {
		s.log(text);
	}

}
