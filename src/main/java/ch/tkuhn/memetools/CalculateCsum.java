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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class CalculateCsum {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-c", description = "Column index or name", required = true)
	private String column = "";

	@Parameter(names = "-n", description = "Number of rows to read")
	private int number = 1000;

	@Parameter(names = "-s", description = "Skip first s rows")
	private int skip = 0;

	public static final void main(String[] args) {
		CalculateCsum obj = new CalculateCsum();
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

	public CalculateCsum() {
	}

	public void run() throws IOException {
		init();
		BufferedReader r = new BufferedReader(new FileReader(inputFile), 64*1024);
		CsvListReader csvReader = new CsvListReader(r, MemeUtils.getCsvPreference());
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
		List<String> header = csvReader.read();
		int colIndex;
		if (column.matches("[0-9]+")) {
			colIndex = Integer.parseInt(column);
		} else {
			colIndex = header.indexOf(column);
		}
		List<String> line;
		int c = 0;
		int csum = 0;
		while ((line = csvReader.read()) != null) {
			c++;
			if (skip >= c) continue;
			if (c > number + skip) break;
			String valStr = line.get(colIndex);
			if (!valStr.matches("0|1")) {
				csvReader.close();
				writer.close();
				throw new RuntimeException("Illegal value (must be 0 or 1): " + valStr);
			}
			int val = Integer.parseInt(valStr);
			csum += val;
			writer.write(((double) csum / c) + "\n");
		}
		csvReader.close();
		writer.close();
	}

	private void init() {
		if (outputFile == null) {
			outputFile = new File(inputFile.getPath().replaceAll("\\..*$", "") + "-cs.csv");
		}
	}

}
