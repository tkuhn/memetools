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

public class CalculateFMatrices {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-t", description = "File with terms", required = true)
	private File termsFile;

	@Parameter(names = "-tcol", description = "Index or name of column to read terms (if term file is in CSV format)")
	private String termCol = "TERM";

	@Parameter(names = "-c", description = "Use first c terms")
	private int termCount = 100;

	private File logFile;

	public static final void main(String[] args) {
		CalculateFMatrices obj = new CalculateFMatrices();
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

	private List<String> terms;

	public CalculateFMatrices() {
	}

	public void run() {
		init();
		try {
			readTerms();
			processData();
			writeTable();
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
		terms = new ArrayList<String>();
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

	private void processData() throws IOException {
		log("Reading input file: " + inputFile);
		BufferedReader reader = new BufferedReader(new FileReader(inputFile));
		int progress = 0;
		String line;
		while ((line = reader.readLine()) != null) {
			logProgress(progress);
			progress++;
			DataEntry d = new DataEntry(line);
			String articleText = d.getText();
			String[] articleTokens = articleText.split(" ");
			// TODO do something with citing article tokens
			for (String citedArticleText : d.getCitedText()) {
				String[] citedArticleTokens = citedArticleText.split(" ");
				// TODO do something with cited article tokens
			}
			// TODO do something
		}
		reader.close();
	}

	private void writeTable() throws IOException {
		log("Writing CSV file...");
		Writer w = new BufferedWriter(new FileWriter(outputFile));
		CsvListWriter csvWriter = new CsvListWriter(w, MemeUtils.getCsvPreference());
		csvWriter.write("write", "something", "here");
		csvWriter.close();
	}

	private String getOutputFileName() {
		String basename = inputFile.getName().replaceAll("\\..*$", "");
		String filename = "fm-" + basename;
		return filename;
	}

	private void logProgress(int p) {
		if (p % 10000 == 0) log(p + "...");
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

}
