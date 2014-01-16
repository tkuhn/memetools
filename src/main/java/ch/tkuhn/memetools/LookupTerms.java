package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class LookupTerms {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-d", description = "Dictionary file", required = true)
	private File dictFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-c", description = "Index or name of column to match")
	private String colIndexOrName = "TERM";

	@Parameter(names = "-n", description = "Name of the new column")
	private String newColName = "LOOKUP";

	@Parameter(names = "-s", description = "Make matching case sensitive")
	private boolean caseSensitive = false;

	public static final void main(String[] args) {
		LookupTerms obj = new LookupTerms();
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

	private Map<String,Boolean> dictTerms;

	public LookupTerms() {
	}

	public void run() throws IOException {
		init();
		loadDictTerms();
		BufferedReader reader = new BufferedReader(new FileReader(inputFile), 64*1024);
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile), 64*1024);
		List<String> line = MemeUtils.readCsvLine(reader);
		int col;
		if (colIndexOrName.matches("[0-9]+")) {
			col = Integer.parseInt(colIndexOrName);
		} else {
			col = line.indexOf(colIndexOrName);
		}
		line.add(newColName);
		MemeUtils.writeCsvLine(writer, line);
		while ((line = MemeUtils.readCsvLine(reader)) != null) {
			if (dictTerms.containsKey(line.get(col))) {
				line.add("1");
			} else {
				line.add("0");
			}
			MemeUtils.writeCsvLine(writer, line);
		}
		reader.close();
		writer.close();
	}

	private void init() {
		if (outputFile == null) {
			outputFile = new File(inputFile.getPath().replaceAll("\\..*$", "") + "-dict.csv");
		}
		dictTerms = new HashMap<String,Boolean>();
	}

	private void loadDictTerms() throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(dictFile), 64*1024);
		String line;
		while ((line = reader.readLine()) != null) {
			if (!caseSensitive) line = line.toLowerCase();
			dictTerms.put(line, true);
		}
		reader.close();
	}

}
