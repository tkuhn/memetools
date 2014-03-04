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
import java.util.HashSet;
import java.util.Set;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class PrepareWosData {

	@Parameter(names = "-v", description = "Write detailed log")
	private boolean verbose = false;

	@Parameter(names = "-rth", description = "Threshold on references (discard publications with less references)")
	private int rth = 0;

	@Parameter(names = "-cth", description = "Threshold on citations (discard publications with less citations)")
	private int cth = 0;

	@Parameter(names = "-d", description = "The directory to read the raw data from")
	private File rawWosDataDir;

	@Parameter(names = "-sg", description = "Skip GML file generation")
	private boolean skipGml = false;

	@Parameter(names = "-sd", description = "Skip data file generation")
	private boolean skipData = false;

	private File logFile;

	public static final void main(String[] args) {
		PrepareWosData obj = new PrepareWosData();
		JCommander jc = new JCommander(obj);
		try {
			jc.parse(args);
		} catch (ParameterException ex) {
			jc.usage();
			System.exit(1);
		}
		obj.run();
	}

	private static String wosFolder = "wos";

	private final static int MAX_WOS_ID = 150000000;

	private IntStringMap titles;
	private Short[] years;
	private IntStringMap references;

	private int docCount;

	private Set<FileVisitOption> walkFileTreeOptions;

	public PrepareWosData() {
	}

	public void run() {
		init();
		try {
			readData();
			writeDataFile();
			writeGmlFile();
		} catch (Throwable th) {
			log(th);
			System.exit(1);
		}
		log("Finished");
	}

	private void init() {
		logFile = new File(MemeUtils.getLogDir(), "prepare-wos.log");
		log("==========");
		log("Starting...");

		titles = new IntStringMap(MAX_WOS_ID);
		years = new Short[MAX_WOS_ID];
		references = new IntStringMap(MAX_WOS_ID);

		docCount = 0;

		walkFileTreeOptions = new HashSet<FileVisitOption>();
		walkFileTreeOptions.add(FileVisitOption.FOLLOW_LINKS);
	}

	private void readData() throws IOException {
		if (rawWosDataDir == null) {
			rawWosDataDir = new File(MemeUtils.getRawDataDir(), wosFolder);
		}
		log("Reading files from " + rawWosDataDir + " ...");
		Files.walkFileTree(rawWosDataDir.toPath(), walkFileTreeOptions, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
				if (path.toString().endsWith(".txt")) {
					readData(path);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		titles.freeze();
		references.freeze();
		log("Number of documents: " + docCount);
	}

	private void readData(Path path) throws IOException {
		log("Reading file to collect IDs: " + path);
		BufferedReader reader = new BufferedReader(new FileReader(path.toFile()), 64*1024);
		int errors = 0;
		String line;
		while ((line = reader.readLine()) != null) {
			WosEntry entry;
			if (verbose) {
				entry = new WosEntry(line, logFile);
			} else {
				entry = new WosEntry(line);
			}
			if (!entry.isValid()) {
				errors++;
				continue;
			}
			if (entry.getRefCount() < rth) continue;
			if (entry.getCitCount() < cth) continue;
			docCount++;
			putTitle(entry.getIdInt(), MemeUtils.normalize(entry.getTitle()));
			putYear(entry.getIdInt(), entry.getYear());
			putReferences(entry.getIdInt(), entry.getRef());
		}
		reader.close();
		log("Number of errors: " + errors);
	}

	private void writeDataFile() throws IOException {
		if (skipData) return;
		log("Writing data file...");
		String filename = "wos-T";
		if (cth > 0) filename += "-c" + cth;
		if (rth > 0) filename += "-r" + rth;
		File file = new File(MemeUtils.getPreparedDataDir(), filename + ".txt");
		BufferedWriter wT = new BufferedWriter(new FileWriter(file));
		for (int id1 = 0 ; id1 < MAX_WOS_ID ; id1++) {
			String text = getTitle(id1);
			if (text == null) continue;
			short year = getYear(id1);
			DataEntry e = new DataEntry(getIdString(id1), year, text);
			String refs = getReferences(id1);
			while (!refs.isEmpty()) {
				String id2 = refs.substring(0, 9);
				refs = refs.substring(9);
				if (getTitle(id2) != null) {
					e.addCitedText(getTitle(id2));
				}
			}
			wT.write(e.getLine() + "\n");
		}
		wT.close();
	}

	private void writeGmlFile() throws IOException {
		if (skipGml) return;
		log("Writing GML file...");
		String filename = "wos";
		if (cth > 0) filename += "-c" + cth;
		if (rth > 0) filename += "-r" + rth;
		File file = new File(MemeUtils.getPreparedDataDir(), filename + ".gml");
		BufferedWriter w = new BufferedWriter(new FileWriter(file));
		w.write("graph [\n");
		w.write("directed 1\n");
		for (int id = 0 ; id < MAX_WOS_ID ; id++) {
			String text = getTitle(id);
			if (text == null) continue;
			short year = getYear(id);
			text = " " + text + " ";
			w.write("node [\n");
			w.write("id \"" + getIdString(id) + "\"\n");
			w.write("year \"" + year + "\"\n");
			// TODO Make this general:
			if (text.contains(" quantum ")) w.write("memeQuantum \"y\"\n");
			if (text.contains(" traffic ")) w.write("memeTraffic \"y\"\n");
			if (text.contains(" black hole ")) w.write("memeBlackHole \"y\"\n");
			if (text.contains(" graphene ")) w.write("memeGraphene \"y\"\n");
			w.write("]\n");
		}
		for (int id1 = 0 ; id1 < MAX_WOS_ID ; id1++) {
			String refs = getReferences(id1);
			if (refs == null) continue;
			while (!refs.isEmpty()) {
				String id2Str = refs.substring(0, 9);
				refs = refs.substring(9);
				if (getTitle(id2Str) != null) {
					w.write("edge [\n");
					w.write("source \"" + getIdString(id1) + "\"\n");
					w.write("target \"" + id2Str + "\"\n");
					w.write("]\n");
				}
			}
		}
		w.write("]\n");
		w.close();
	}

	private String getIdString(int id) {
		return String.format("%09d", id);
	}

	private void putTitle(int id, String title) {
		titles.put(id, title);
	}

	private String getTitle(Object id) {
		if (id instanceof Integer) {
			return titles.get((Integer) id);
		}
		return titles.get(Integer.parseInt(id.toString()));
	}

	private void putYear(int id, short year) {
		years[id] = year;
	}

	private short getYear(Object id) {
		if (id instanceof Integer) {
			return years[(Integer) id];
		}
		return years[Integer.parseInt(id.toString())];
	}

	private void putReferences(int id, String ref) {
		references.put(id, ref);
	}

	private String getReferences(Object id) {
		if (id instanceof Integer) {
			return references.get((Integer) id);
		}
		return references.get(Integer.parseInt(id.toString()));
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

	
	// Data format: semicolon-delimited with the following columns:
	//
    // 0 t9 (9 last digits)
    // 1 year
    // 2 documentType (one letter)
    // 3 doi (optional)
    // 4 subject (two letters, optional)
    // 5 iso journal (optional)
    // 6 volume
    // 7 issue
    // 8 pages
    // 9 title
    // 10 noOfAuthors
    // 11.. authors
    // 11+noOfAuthors noOfJournals
    // 12+noOfAuthors... journals (other journal labels)
    // 12+noOfAuthors+noOfJournals summary (optional)
    // 13+noOfAuthors+noOfJournals references (non-delimited t9)
    // 14+noOfAuthors+noOfJournals citations (non-delimited t9)

	static class WosEntry {

		private boolean valid = false;

		private String id;
		private Integer idInt;
		private String title;
		private short year;
		private String ref;
		private String cit;
		private int refCount;
		private int citCount;

		private File logFile;

		String getId() {
			return id;
		}

		int getIdInt() {
			if (idInt == null) {
				idInt = Integer.parseInt(id);
			}
			return idInt;
		}

		String getTitle() {
			return title;
		}

		short getYear() {
			return year;
		}

		String getRef() {
			return ref;
		}

		String getCit() {
			return cit;
		}

		int getRefCount() {
			return refCount;
		}

		int getCitCount() {
			return citCount;
		}

		WosEntry(String line) {
			this(line, null);
		}

		WosEntry(String line, File logFile) {
			this.logFile = logFile;
			String[] parts = line.split(";", -1);
			if (parts.length < 15) {
				log("Invalid line: " + line);
				return;
			}
			id = parts[0];
			if (!id.matches("[0-9]{9}")) {
				log("Invalid ID: " + id);
				return;
			}
			String yearStr = parts[1];
			if (!yearStr.matches("[0-9]{4}")) {
				log("Invalid year: " + yearStr);
				return;
			}
			year = Short.parseShort(yearStr);
			title = parts[9];
			if (title.isEmpty()) {
				log("Empty title for publication: " + id);
				return;
			}
			ref = parts[parts.length-2];
			if (!ref.matches("([0-9]{9})*")) {
				log("Invalid references: " + ref);
				return;
			}
			refCount = ref.length() / 9;
			cit = parts[parts.length-1];
			if (!cit.matches("([0-9]{9})*")) {
				log("Invalid citations: " + cit);
				return;
			}
			citCount = cit.length() / 9;
			valid = true;
		}

		private void log(String text) {
			if (logFile != null) {
				MemeUtils.log(logFile, text);
			}
		}

		boolean isValid() {
			return valid;
		}

	}

}
