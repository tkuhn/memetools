package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.supercsv.io.CsvListReader;
import org.supercsv.io.CsvListWriter;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class CalculatePaperSuccess {

	@Parameter(description = "input-file", required = true)
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
				// TODO write real data:
				csvWriter.write(d.getId(), d.getDate(), d.getAuthors());
			}
		} finally {
			if (csvWriter != null) csvWriter.close();
			if (reader != null) reader.close();
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
