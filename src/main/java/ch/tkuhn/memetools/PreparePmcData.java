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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class PreparePmcData {

	private File logFile;

	public static final void main(String[] args) {
		PreparePmcData obj = new PreparePmcData();
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
	private static String pmcXmlPath = "pmcoa-xml";

	private static String idlinePattern = "^([^,]*,|\"[^\"]*\",){7}([^,]*|\"[^\"]*\"),(PMC[0-9]+),([0-9]*),([^,]*|\"[^\"]*\"),([^,]*|\"[^\"]*\")$";

	private Map<String,String> idMap;
	private Map<String,String> titles;
	private Map<String,String> dates;
	private Map<String,String> abstracts;
	private Map<String,List<String>> references;

	private int progress;

	private int idMissing;
	private int duplicateIds;
	private int titleMissing;
	private int abstractMissing;
	private int refNotFound;
	private int dateMissing;

	private Set<FileVisitOption> walkFileTreeOptions;

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

		progress = 0;

		idMissing = 0;
		duplicateIds = 0;
		titleMissing = 0;
		abstractMissing = 0;
		refNotFound = 0;
		dateMissing = 0;

		walkFileTreeOptions = new HashSet<FileVisitOption>();
		walkFileTreeOptions.add(FileVisitOption.FOLLOW_LINKS);
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
		}
		r.close();
	}

	private void processXmlFiles() throws IOException {
		File dir = new File(MemeUtils.getRawDataDir(), pmcXmlPath);
		log("Reading files from " + dir + " ...");
		Files.walkFileTree(dir.toPath(), walkFileTreeOptions, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
				if (path.toString().endsWith(".nxml")) {
					progress++;
					logProgress();
					processXmlFile(path);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		log("IDs missing: " + idMissing);
		log("Duplicate IDs: " + duplicateIds);
		log("Titles missing: " + titleMissing);
		log("Date missing: " + dateMissing);
		log("Abstracts missing (ignored): " + abstractMissing);
		log("Reference not found (ignored): " + refNotFound);
		log("Number of documents: " + titles.size());
	}

	private static final String idPattern = "<article-id[^>]* pub-id-type=\"pmc\"[^>]*>(.*?)</article-id>";
	private static final String titlePattern = "<article-title[^>]*>(.*?)</article-title>";
	private static final String abstractPattern = "<abstract[^>]*>(.*?)</abstract>";
	private static final String datePattern = "<pub-date[^>]*><day>([0-3]?[0-9])</day><month>([0-1]?[0-9])</month><year>([0-9][0-9][0-9][0-9])</year></pub-date>";
	private static final String refPattern = "<pub-id[^>]* pub-id-type=\"(pmc|doi|pmid)\"[^>]*>(.*?)</pub-id>";

	private void processXmlFile(Path path) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(path.toFile()), 64*1024);
		StringBuffer content = new StringBuffer();
		String line;
		while ((line = reader.readLine()) != null) {
			content.append(line + " ");
		}
		reader.close();
		String c = content.toString();
		if (!c.matches(idPattern)) {
			idMissing++;
			return;
		}
		String pmcid = "PMC" + c.replaceFirst("^.*?" + idPattern + ".*$", "$1");
		if (titles.containsKey(pmcid)) {
			log("ERROR. Duplicate ID: " + pmcid);
			duplicateIds++;
			return;
		}
		if (!c.matches(titlePattern)) {
			titleMissing++;
			return;
		}
		String title = c.replaceFirst("^.*?" + titlePattern + ".*$", "$1");
		String date = null;
		Matcher dateMatcher = Pattern.compile(datePattern).matcher(c);
		while (dateMatcher.find()) {
			String day = dateMatcher.group(1);
			if (day.length() < 2) day = "0" + day;
			String month = dateMatcher.group(2);
			if (month.length() < 2) month = "0" + day;
			String year = dateMatcher.group(3);
			String d = year + "-" + month + "-" + day;
			if (date == null || d.compareTo(date) < 0) {
				date = d;
			}
		}
		if (date == null) {
			dateMissing++;
			return;
		}
		titles.put(pmcid, MemeUtils.normalize(title));
		dates.put(pmcid, date);
		if (c.matches(abstractPattern)) {
			String abs = c.replaceFirst("^.*?" + abstractPattern + ".*$", "$1");
			abstracts.put(pmcid, MemeUtils.normalize(abs));
		} else {
			abstractMissing++;
		}
		List<String> cited = new ArrayList<String>();
		Matcher refMatcher = Pattern.compile(refPattern).matcher(c);
		while (refMatcher.find()) {
			String type = refMatcher.group(1);
			String id = refMatcher.group(2);
			String citedPmcid = null;
			if (type.equals("pmc")) {
				citedPmcid = "PMC" + id;
			} else if (idMap.containsKey(id)) {
				citedPmcid = idMap.get(id);
			} else {
				refNotFound++;
			}
			if (citedPmcid != null && !cited.contains(citedPmcid)) {
				cited.add(citedPmcid);
			}
		}
		references.put(pmcid, cited);
	}

	private void writeDataFiles() throws IOException {
		File fileT = new File(MemeUtils.getPreparedDataDir(), "pmc-T.txt");
		File fileTA = new File(MemeUtils.getPreparedDataDir(), "pmc-TA.txt");
		BufferedWriter wT = new BufferedWriter(new FileWriter(fileT));
		BufferedWriter wTA = new BufferedWriter(new FileWriter(fileTA));
		for (String pmcid1 : titles.keySet()) {
			String text = titles.get(pmcid1);
			String date = dates.get(pmcid1);
			DataEntry eT = new DataEntry(pmcid1, date, text);
			if (abstracts.containsKey(pmcid1)) text += " " + abstracts.get(pmcid1);
			DataEntry eTA = new DataEntry(pmcid1, date, text);
			for (String pmcid2 : references.get(pmcid1)) {
				text = titles.get(pmcid2);
				eT.addCitedText(text);
				if (abstracts.containsKey(pmcid2)) text += " " + abstracts.get(pmcid2);
				eTA.addCitedText(text);
			}
			wT.write(eT.getLine() + "\n");
			wTA.write(eTA.getLine() + "\n");
		}
		wT.close();
		wTA.close();
	}

	private void writeGmlFile() throws IOException {
		File file = new File(MemeUtils.getPreparedDataDir(), "pmc.gml");
		BufferedWriter w = new BufferedWriter(new FileWriter(file));
		w.write("graph [\n");
		w.write("directed 1\n");
		for (String pmcid : titles.keySet()) {
			String year = dates.get(pmcid).substring(0, 4);
			String text = titles.get(pmcid);
			if (abstracts.containsKey(pmcid)) text += " " + abstracts.get(pmcid);
			text = " " + text + " ";
			w.write("node [\n");
			w.write("id \"" + pmcid + "\"\n");
			w.write("year \"" + year + "\"\n");
			// TODO Make this general:
			if (text.contains(" quantum ")) w.write("memeQuantum \"y\"\n");
			if (text.contains(" traffic ")) w.write("memeTraffic \"y\"\n");
			if (text.contains(" black hole ")) w.write("memeBlackHole \"y\"\n");
			if (text.contains(" graphene ")) w.write("memeGraphene \"y\"\n");
			w.write("]\n");
		}
		for (String pmcid1 : references.keySet()) {
			for (String pmcid2 : references.get(pmcid1)) {
				w.write("edge [\n");
				w.write("source \"" + pmcid1 + "\"\n");
				w.write("target \"" + pmcid2 + "\"\n");
				w.write("]\n");
			}
		}
		w.write("]\n");
		w.close();
	}

	private void log(Object text) {
		MemeUtils.log(logFile, text);
	}

	private void logProgress() {
		if (progress % 100000 == 0) log(progress + "...");
	}

}
