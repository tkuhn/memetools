package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.supercsv.io.CsvListReader;
import org.supercsv.io.CsvListWriter;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class ApplyRegex {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-r", description = "Regular expression to match", required = true)
	private String regex;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-c", description = "Index or name of column to match")
	private String colIndexOrName = "TERM";

	@Parameter(names = "-n", description = "Name of the new column")
	private String newColName = "REGEX";

	public static final void main(String[] args) {
		ApplyRegex obj = new ApplyRegex();
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
		try {
			obj.run();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public ApplyRegex() {
	}

	public void run() throws IOException {
		init();
		BufferedReader r = new BufferedReader(new FileReader(inputFile), 64*1024);
		CsvListReader csvReader = new CsvListReader(r, MemeUtils.getCsvPreference());
		BufferedWriter w = new BufferedWriter(new FileWriter(outputFile), 64*1024);
		CsvListWriter csvWriter = new CsvListWriter(w, MemeUtils.getCsvPreference());
		List<String> line = csvReader.read();
		int col;
		if (colIndexOrName.matches("[0-9]+")) {
			col = Integer.parseInt(colIndexOrName);
		} else {
			col = line.indexOf(colIndexOrName);
		}
		line.add(newColName);
		csvWriter.write(line);
		while ((line = csvReader.read()) != null) {
			if (line.get(col).matches(regex)) {
				line.add("1");
			} else {
				line.add("0");
			}
			csvWriter.write(line);
		}
		csvReader.close();
		csvWriter.close();
	}

	private void init() {
		if (outputFile == null) {
			outputFile = new File(inputFile.getPath().replaceAll("\\..*$", "") + "-regex.csv");
		}
	}

}
