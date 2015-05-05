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
			// TODO write real header:
			//csvWriter.write("ID", "JOURNAL-C/PY", "FIRSTAUTHOR-C/PY", "AUTHOR-MAX-C/PY", "TOP-MS-" + delta);
			csvWriter.write("ID", "DATE", "AUTHORS");

			reader = new BufferedReader(new FileReader(inputFile));
			int progress = 0;
			String line;
			while ((line = reader.readLine()) != null) {
				progress++;
				logProgress(progress);
				DataEntry d = new DataEntry(line);
				ms.recordTerms(d);
				long thisDay = getDayCount(d.getDate());
				String doi = d.getId();
				// TODO write real data:
				csvWriter.write(doi, d.getDate(), d.getAuthors());

				String journal = PrepareApsData.getJournalFromDoi(doi);
				String key = "J:" + journal;
				addCpyPaper(key, thisDay);
				String cpyKeys = key;
				for (String author : d.getAuthors().split(" ")) {
					key = "A:" + author;
					addCpyPaper(key, thisDay);
					cpyKeys += " " + key;
				}
				for (String k : cpyKeys.split(" ")) {
					addCpyPaper(k, thisDay);
				}
				for (String cit : d.getCitations().split(" ")) {
					for (String k : cpyMapKeys.get(cit).split(" ")) {
						addCpyCitation(k, thisDay);
					}
				}
				cpyMapKeys.put(doi, cpyKeys);
			}
		} finally {
			if (csvWriter != null) csvWriter.close();
			if (reader != null) reader.close();
		}
	}

	private void addCpyPaper(String key, long thisDay) {
		long lastDay = cpyLastDay.get(key);
		long dayDiff = thisDay - lastDay;
		int paperCount = cpyPaperCount.get(key);
		long paperDays = cpyPaperDays.get(key);
		cpyPaperDays.put(key, paperDays + paperCount*dayDiff);
		cpyLastDay.put(key, thisDay);
		cpyPaperCount.put(key, paperCount+1);
	}

	private void addCpyCitation(String key, long thisDay) {
		cpyCitationCount.put(key, cpyCitationCount.get(key) + 1);
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
		return "su-" + inputFile.getName().replaceAll("\\..*$", "");
	}

	private void logProgress(int p) {
		if (p % 100000 == 0) log(p + "...");
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

}
