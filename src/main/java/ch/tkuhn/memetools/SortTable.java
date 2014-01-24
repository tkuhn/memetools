package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.supercsv.io.CsvListReader;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class SortTable {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-r", description = "Sort in reversed order")
	private boolean reversed = false;

	@Parameter(names = "-c", description = "Index or name of column to sort")
	private String colIndexOrName = "0";

	@Parameter(names = "-u", description = "Upper threshold value")
	private Double uThreshold;

	@Parameter(names = "-l", description = "Lower threshold value")
	private Double lThreshold;

	public static final void main(String[] args) {
		SortTable obj = new SortTable();
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

	private List<List<String>> content;
	private List<Pair<Integer,Double>> sortList;

	public SortTable() {
	}

	public void run() throws IOException {
		init();
		BufferedReader r = new BufferedReader(new FileReader(inputFile), 64*1024);
		CsvListReader csvReader = new CsvListReader(r, MemeUtils.getCsvPreference());
		List<String> header = csvReader.read();
		int col;
		if (colIndexOrName.matches("[0-9]+")) {
			col = Integer.parseInt(colIndexOrName);
		} else {
			col = header.indexOf(colIndexOrName);
		}
		content.add(header);
		List<String> line;
		while ((line = csvReader.read()) != null) {
			double value = Double.parseDouble(line.get(col));
			if (uThreshold != null && value > uThreshold) continue;
			if (lThreshold != null && value < lThreshold) continue;
			sortList.add(Pair.of(content.size(), value));
			content.add(line);
		}
		csvReader.close();
		Collections.sort(sortList, new Comparator<Pair<Integer,Double>>() {
			@Override
			public int compare(Pair<Integer,Double> o1, Pair<Integer,Double> o2) {
				if (reversed) {
					return Double.compare(o2.getRight(), o1.getRight());
				} else {
					return Double.compare(o1.getRight(), o2.getRight());
				}
			}
		});
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile), 64*1024);
		writer.write(content.get(0) + "\n");
		for (Pair<Integer,Double> p : sortList) {
			writer.write(content.get(p.getLeft()) + "\n");
		}
		writer.close();
	}

	private void init() {
		if (outputFile == null) {
			outputFile = new File(inputFile.getPath().replaceAll("\\..*$", "") + "-sorted.csv");
		}
		content = new ArrayList<List<String>>();
		sortList = new ArrayList<Pair<Integer,Double>>();
	}

}
