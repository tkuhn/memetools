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

	@Parameter(names = "-t", description = "File with terms", required = true)
	private File termsFile;

	@Parameter(names = "-tcol", description = "Index or name of column to read terms (if term file is in CSV format)")
	private String termCol = "TERM";

	@Parameter(names = "-y", description = "Number of years for which to count article citations")
	private int citationYears = 3;

	@Parameter(names = "-d", description = "Set delta parameter (controlled noise level)")
	private int delta = 3;

	@Parameter(names = "-r", description = "Relative frequency threshold")
	private double relFreqThreshold = 0.15;

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

	private File outputTempFile, outputFile, outputMatrixFile;

	private MemeScorer ms;
	private List<String> terms;
	private BufferedReader reader;
	private Map<String,Long> paperDates;
	private Map<String,Integer> paperCitations;
	private Map<String,String> cpyMapKeys;
	private Map<String,Long> cpyLastDay;
	private Map<String,Long> cpyPaperDays;
	private Map<String,Integer> cpyPaperCount;
	private Map<String,Integer> cpyCitationCount;
	private long lastDay;

	public CalculatePaperSuccess() {
	}

	public void run() {
		init();
		try {
			readTerms();
			processEntries();
			writeSuccessColumn();
		} catch (Throwable th) {
			log(th);
			System.exit(1);
		}
		log("Finished");
	}

	private void init() {
		logFile = new File(MemeUtils.getLogDir(), getOutputFileName() + ".log");
		log("==========");

		outputTempFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + "-temp.csv");
		outputFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + ".csv");
		outputMatrixFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + "-matrix.csv");

		ms = new MemeScorer(MemeScorer.GIVEN_TERMLIST_MODE);
		terms = new ArrayList<String>();
		paperDates = new HashMap<String,Long>();
		paperCitations = new HashMap<String,Integer>();
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
		CsvListWriter csvWriter = null;
		try {
			log("Processing entries and writing CSV file...");
			Writer w = new BufferedWriter(new FileWriter(outputTempFile));
			csvWriter = new CsvListWriter(w, MemeUtils.getCsvPreference());
			csvWriter.write("ID", "JOURNAL-C/PY", "FIRSTAUTHOR-C/PY", "AUTHOR-MAX-C/PY", "TOP-MS-" + delta, "TOP-MS-" + delta + "-MEME");

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
				paperDates.put(doi, thisDay);
				paperCitations.put(doi, 0);
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
				String topMeme = "";
				for (String meme : memes) {
					if (ms.getRelF(meme) > relFreqThreshold) continue;  // ignore frequent terms
					double thisMs = ms.calculateMemeScoreValues(meme, delta)[3];
					if (thisMs > topMs) {
						topMs = thisMs;
						topMeme = meme;
					}
				}

				csvWriter.write(doi, journalCpy, firstAuthorCpy, authorMaxCpy, topMs, topMeme);

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
					long date = paperDates.get(cit);
					if (thisDay < date + 365*citationYears) {
						paperCitations.put(cit, paperCitations.get(cit) + 1);
					}
				}
				cpyMapKeys.put(doi, cpyKeys);
				lastDay = thisDay;
			}
		} finally {
			if (csvWriter != null) csvWriter.close();
			if (reader != null) reader.close();
		}
	}

	private void writeSuccessColumn() throws IOException {
		BufferedReader r = new BufferedReader(new FileReader(outputTempFile));
		CsvListReader csvReader = new CsvListReader(r, MemeUtils.getCsvPreference());
		Writer w = new BufferedWriter(new FileWriter(outputFile));
		CsvListWriter csvWriter = new CsvListWriter(w, MemeUtils.getCsvPreference());
		Writer wm = new BufferedWriter(new FileWriter(outputMatrixFile));
		CsvListWriter csvMatrixWriter = new CsvListWriter(wm, MemeUtils.getCsvPreference());

		// Process header:
		List<String> line = csvReader.read();
		line.add("CITATIONS-" + citationYears + "Y");
		csvWriter.write(line);
		line.remove(line.size()-2);
		line.remove(0);
		csvMatrixWriter.write(line);

		while ((line = csvReader.read()) != null) {
			String doi = line.get(0);
			long date = paperDates.get(doi);
			boolean completeRow = false;
			if (lastDay >= date + 365*citationYears) {
				line.add(paperCitations.get(doi).toString());
				completeRow = true;
			} else {
				line.add("-1");
			}
			csvWriter.write(line);
			if (completeRow) {
				line.remove(line.size()-2);
				line.remove(0);
				csvMatrixWriter.write(line);
			}
		}
		csvWriter.close();
		csvMatrixWriter.close();
		csvReader.close();
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
		return (citationCount * 365.0) / (paperDays + 1);
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
