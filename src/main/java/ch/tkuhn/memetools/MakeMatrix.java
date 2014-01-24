package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class MakeMatrix {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-t", description = "Columns with text (comma-separated)")
	private String textColums = "";

	public static final void main(String[] args) {
		MakeMatrix obj = new MakeMatrix();
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

	public MakeMatrix() {
	}

	public void run() throws IOException {
		init();
		BufferedReader reader = new BufferedReader(new FileReader(inputFile), 64*1024);
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile), 64*1024);
		String headerLine = reader.readLine();
		List<String> header = MemeUtils.readCsvLineAsList(headerLine);
		boolean[] textual = new boolean[header.size()];
		for (String t : textColums.split(",")) {
			if (t.isEmpty()) continue;
			if (t.matches("[0-9]+")) {
				textual[Integer.parseInt(t)] = true;
			} else {
				textual[header.indexOf(t)] = true;
			}
		}
		String line;
		int c = 0;
		while ((line = reader.readLine()) != null) {
			c++;
			String[] entries = MemeUtils.readCsvLine(line);
			for (int i = 0 ; i < entries.length ; i++) {
				if (textual[i]) entries[i] = c + "";
			}
			MemeUtils.writeCsvLine(writer, entries);
		}
		reader.close();
		writer.close();
	}

	private void init() {
		if (outputFile == null) {
			outputFile = new File(inputFile.getPath().replaceAll("\\..*$", "") + "-m.csv");
		}
	}

}
