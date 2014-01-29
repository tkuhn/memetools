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

public class CalculateMemeScores {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-y", description = "Calculate scores for given year")
	private Integer year;

	@Parameter(names = "-ys", description = "Calculate scores for time period with given start year")
	private Integer yearStart;

	@Parameter(names = "-ye", description = "Calculate scores for time period with given end year")
	private Integer yearEnd;

	@Parameter(names = "-n", description = "Set n parameter")
	private int n = 3;

	private File logFile;

	public static final void main(String[] args) {
		CalculateMemeScores obj = new CalculateMemeScores();
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

	private boolean appendMode;

	private MemeScorer ms;

	public CalculateMemeScores() {
	}

	public void run() {
		init();
		try {
			if (appendMode) {
				appendTable();
			} else {
				extractTerms();
				countTerms();
				writeTable();
			}
		} catch (Throwable th) {
			log(th);
			System.exit(1);
		}
		log("Finished");
	}

	private void init() {
		appendMode = inputFile.getName().endsWith(".csv");

		logFile = new File(MemeUtils.getLogDir(), getOutputFileName() + ".log");
		log("==========");

		if (outputFile == null) {
			outputFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + ".csv");
		}

		if (!appendMode) {
			ms = new MemeScorer(true);
		}
	}

	private void extractTerms() throws IOException {
		log("Extracting terms from input file: " + inputFile);
		BufferedReader reader = new BufferedReader(new FileReader(inputFile));
		int progress = 0;
		String line;
		while ((line = reader.readLine()) != null) {
			progress++;
			logProgress(progress);
			DataEntry d = new DataEntry(line);
			if (!considerYear(d.getYear())) continue;
			ms.screenTerms(d);
		}
		reader.close();
		log("Total number of documents: " + ms.getT());
		log("Number of unique terms with meme score > 0: " + ms.getMM().size());
		ms.fixTerms();
	}

	private void countTerms() throws IOException {
		int errors = 0;
		try {
			log("Counting terms...");
			BufferedReader reader = new BufferedReader(new FileReader(inputFile));
			int progress = 0;
			String line;
			while ((line = reader.readLine()) != null) {
				progress++;
				logProgress(progress);
				DataEntry d = new DataEntry(line);
				if (!considerYear(d.getYear())) continue;
				ms.recordTerms(d);
			}
			reader.close();
		} catch (IOException ex) {
			log(ex);
			System.exit(1);
		}
		log("Number of errors: " + errors);
	}

	private void writeTable() throws IOException {
		log("Calculating meme scores and writing CSV file...");
		Writer w = new BufferedWriter(new FileWriter(outputFile));
		CsvListWriter csvWriter = new CsvListWriter(w, MemeUtils.getCsvPreference());
		csvWriter.write("TERM", "ABSFREQ", "RELFREQ", "MM", "M",
				"XM", "X", "STICK-" + n, "SPARK-" + n, "PS-" + n, "MS-" + n);
		int progress = 0;
		for (String t : ms.getF().keySet()) {
			progress++;
			logProgress(progress);
			double[] v = ms.calculateMemeScoreValues(t, n);
			csvWriter.write(t, ms.getF(t), ms.getRelF(t), ms.getMM(t), ms.getM(t), ms.getXM(t), ms.getX(t), v[0], v[1], v[2], v[3]);
		}
		csvWriter.close();
	}

	private void appendTable() throws IOException {
		log("Calculating meme scores and appending to CSV file...");
		BufferedReader f = new BufferedReader(new FileReader(inputFile), 64*1024);
		CsvListReader csvReader = new CsvListReader(f, MemeUtils.getCsvPreference());
		BufferedWriter w = new BufferedWriter(new FileWriter(outputFile), 64*1024);
		CsvListWriter csvWriter = new CsvListWriter(w, MemeUtils.getCsvPreference());
		List<String> line = csvReader.read();
		int absFqCol = line.indexOf("ABSFREQ");
		int mmCol = line.indexOf("MM");
		int mCol = line.indexOf("M");
		int xmCol = line.indexOf("XM");
		int xCol = line.indexOf("X");
		line.add("STICK-" + n);
		line.add("SPARK-" + n);
		line.add("PS-" + n);
		line.add("MS-" + n);
		csvWriter.write(line);
		while ((line = csvReader.read()) != null) {
			int mm = Integer.parseInt(line.get(mmCol));
			int m = Integer.parseInt(line.get(mCol));
			int xm = Integer.parseInt(line.get(xmCol));
			int x = Integer.parseInt(line.get(xCol));
			int absFq = Integer.parseInt(line.get(absFqCol));
			double[] v = MemeScorer.calculateMemeScoreValues(mm, m, xm, x, absFq, n);
			for (double d : v) {
				line.add(d + "");
			}
			csvWriter.write(line);
		}
		csvReader.close();
		csvWriter.close();
	}

	private String getOutputFileName() {
		String basename = inputFile.getName().replaceAll("\\..*$", "");
		if (appendMode) {
			return basename + "-a";
		}
		String filename = "ms-" + basename;
		if (year != null) {
			filename += "-y" + year;
		} else if (yearStart != null || yearEnd != null) {
			filename += "-y";
			if (yearStart != null) filename += yearStart;
			filename += "TO";
			if (yearEnd != null) filename += yearEnd;
		}
		return filename;
	}

	private boolean considerYear(int y) {
		if (year != null && y != year) return false;
		if (yearStart != null && y < yearStart) return false;
		if (yearEnd != null && y > yearEnd) return false;
		return true;
	}

	private void logProgress(int p) {
		if (p % 100000 == 0) log(p + "...");
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

}
