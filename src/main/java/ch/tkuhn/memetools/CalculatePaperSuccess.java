package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.supercsv.io.CsvListReader;
import org.supercsv.io.CsvListWriter;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class CalculatePaperSuccess {

	@Parameter(description = "chronologically-sorted-input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-t", description = "File with terms", required = true)
	private File termsFile;

	@Parameter(names = "-tcol", description = "Index or name of column to read terms (if term file is in CSV format)")
	private String termCol = "TERM";

	@Parameter(names = "-d", description = "Set delta parameter (controlled noise level)")
	private int delta = 3;

	private File logFile;

	public static final void main(String[] args) {
		CalculatePaperSuccess obj = new CalculatePaperSuccess();
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

	private MemeScorer ms;
	private List<String> terms;
	private CsvListWriter csvWriter;
	private BufferedReader reader;
	private Map<String,String> cpyMapKeys;
	private Map<String,Long> cpyLastDay;
	private Map<String,Long> cpyPaperDays;
	private Map<String,Integer> cpyPaperCount;
	private Map<String,Integer> cpyCitationCount;

	public CalculatePaperSuccess() {
	}

	public void run() {
		init();
		try {
			readTerms();
			processEntries();
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
		ms = new MemeScorer(MemeScorer.GIVEN_TERMLIST_MODE);
		terms = new ArrayList<String>();
		cpyMapKeys = new HashMap<String,String>();
		cpyLastDay = new HashMap<String,Long>();
		cpyPaperDays = new HashMap<String,Long>();
		cpyPaperCount = new HashMap<String,Integer>();
		cpyCitationCount = new HashMap<String,Integer>();
	}

	private void readTerms() throws IOException {
		log("Reading terms from " + termsFile + " ...");
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
			ms.addTerm(term);
			terms.add(term);
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
			ms.addTerm(term);
			terms.add(term);
		}
		csvReader.close();
	}

	private void processEntries() throws IOException {
		try {
			log("Processing entries and writing CSV file...");
			Writer w = new BufferedWriter(new FileWriter(outputFile));
			csvWriter = new CsvListWriter(w, MemeUtils.getCsvPreference());
			csvWriter.write("ID", "JOURNAL-C/PY", "FIRSTAUTHOR-C/PY", "AUTHOR-MAX-C/PY", "TOP-MS-" + delta);

			reader = new BufferedReader(new FileReader(inputFile));
			int progress = 0;
			String line;
			while ((line = reader.readLine()) != null) {
				progress++;
				logProgress(progress);
				DataEntry d = new DataEntry(line);

				// Calculate C/PY values
				long thisDay = getDayCount(d.getDate());
				String doi = d.getId();
				String[] authList = d.getAuthors().split(" ");
				String journal = PrepareApsData.getJournalFromDoi(doi);
				String journalKey = "J:" + journal;
				double journalCpy = updateCpyData(journalKey, thisDay);
				double firstAuthorCpy = -2.0;
				double authorMaxCpy = -2.0;
				for (String author : authList) {
					String authorKey = "A:" + author;
					double authorCpy = updateCpyData(authorKey, thisDay);
					if (firstAuthorCpy == -2.0) {
						firstAuthorCpy = authorCpy;
					}
					if (authorCpy > authorMaxCpy) {
						authorMaxCpy = authorCpy;
					}
				}

				// Calculate meme scores
				List<String> memes = new ArrayList<String>();
				ms.recordTerms(d, memes);
				double topMs = 0;
				for (String meme : memes) {
					double thisMs = ms.calculateMemeScoreValues(meme, delta)[3];
					if (thisMs > topMs) topMs = thisMs;
				}

				csvWriter.write(doi, journalCpy, firstAuthorCpy, authorMaxCpy, topMs);

				addCpyPaper(journalKey);
				String cpyKeys = journalKey;
				for (String author : authList) {
					String authorKey = "A:" + author;
					addCpyPaper(authorKey);
					cpyKeys += " " + authorKey;
				}
				String[] citList = d.getCitations().split(" ");
				for (String cit : citList) {
					// Ignore citations that are not backwards in time:
					if (!cpyMapKeys.containsKey(cit)) continue;
					for (String k : cpyMapKeys.get(cit).split(" ")) {
						addCpyCitation(k);
					}
				}
				cpyMapKeys.put(doi, cpyKeys);
			}
		} finally {
			if (csvWriter != null) csvWriter.close();
			if (reader != null) reader.close();
		}
	}

	private double updateCpyData(String key, long thisDay) {
		Long lastDay = cpyLastDay.get(key);
		if (lastDay == null) lastDay = 0l;
		long dayDiff = thisDay - lastDay;
		Integer paperCount = cpyPaperCount.get(key);
		if (paperCount == null) paperCount = 0;
		Integer citationCount = cpyCitationCount.get(key);
		if (citationCount == null) citationCount = 0;
		Long paperDays = cpyPaperDays.get(key);
		if (paperDays == null) paperDays = 0l;
		paperDays = paperDays + paperCount*dayDiff;
		cpyPaperDays.put(key, paperDays);
		cpyLastDay.put(key, thisDay);
		if (paperDays == 0) return -1.0;
		return (double) citationCount/(paperDays/365.0);
	}

	private void addCpyPaper(String key) {
		Integer paperCount = cpyPaperCount.get(key);
		if (paperCount == null) paperCount = 0;
		cpyPaperCount.put(key, paperCount + 1);
	}

	private void addCpyCitation(String key) {
		Integer citationCount = cpyCitationCount.get(key);
		if (citationCount == null) citationCount = 0;
		cpyCitationCount.put(key, citationCount + 1);
	}

	private static long getDayCount(String date) {
		return TimeUnit.DAYS.convert(parseDate(date).getTime(), TimeUnit.MILLISECONDS);
	}

	private static final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

	private static Date parseDate(String s) {
		try {
			return formatter.parse(s);
		} catch (ParseException ex) {
			throw new RuntimeException(ex);
		}
	}

	private String getOutputFileName() {
		return "su-" + inputFile.getName().replaceAll("-chronologic", "").replaceAll("\\..*$", "");
	}

	private void logProgress(int p) {
		if (p % 100000 == 0) log(p + "...");
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

}
