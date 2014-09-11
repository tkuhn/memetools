package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.supercsv.io.CsvListReader;
import org.supercsv.io.CsvListWriter;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class MakeTermMap {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-s", description = "Cell size (in log scale)")
	private float cellSize = 1;

	@Parameter(names = "-psmin", description = "Minimum on propagation score axis")
	private int psMin = 0;

	@Parameter(names = "-psmax", description = "Maximum on propagation score axis")
	private int psMax = 6;

	@Parameter(names = "-rfmin", description = "Minimum on relative frequency axis")
	private int rfMin = -6;

	@Parameter(names = "-rfmax", description = "Maximum on relative frequency axis")
	private int rfMax = 0;

	@Parameter(names = "-l", description = "Limit on n-grams (0 = no limit)")
	private int ngramLimit = 0;

	public static final void main(String[] args) {
		MakeTermMap obj = new MakeTermMap();
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

	private int psSize, rfSize;
	private String[][] termMap;

	public MakeTermMap() {
	}

	public void run() throws IOException {
		init();
		createTermMap();
		writeTermMap();
	}

	private void createTermMap() throws IOException {
		BufferedReader r = new BufferedReader(new FileReader(inputFile), 64*1024);
		CsvListReader csvReader = new CsvListReader(r, MemeUtils.getCsvPreference());
		List<String> header = csvReader.read();
		int termColIndex = header.indexOf("TERM");
		int rfColIndex = header.indexOf("RELFREQ");
		int psColIndex = header.indexOf("PS-3");
		List<String> line;
		int ignored = 0;
		while ((line = csvReader.read()) != null) {
			String term = line.get(termColIndex);
			double psLog = Math.log10(Double.parseDouble(line.get(psColIndex)));
			double rfLog = Math.log10(Double.parseDouble(line.get(rfColIndex)));
			int psBin = (int) Math.floor((psLog-psMin) / cellSize);
			int rfBin = (int) Math.floor((rfLog-rfMin) / cellSize);
			if (psLog < psMin || psLog > psMax || rfLog < rfMin || rfLog > rfMax) {
				ignored++;
				continue;
			}
			if (ngramLimit > 0 && StringUtils.countMatches(term, " ") >= ngramLimit) {
				if (termMap[psBin][rfBin] == null) {
					termMap[psBin][rfBin] = "(ONLY LONGER PHRASES)";
				}
			} else {
				termMap[psBin][rfBin] = term;
			}
		}
		csvReader.close();
		System.err.println("Ignored: " + ignored);
	}

	private void writeTermMap() throws IOException {
		BufferedWriter w = new BufferedWriter(new FileWriter(outputFile));
		CsvListWriter csvWriter = new CsvListWriter(w, MemeUtils.getCsvPreference());
		for (int rfBin = rfSize-1 ; rfBin >= 0 ; rfBin--) {
			List<String> columns = new ArrayList<String>(psSize);
			for (int psBin = 0 ; psBin < psSize ; psBin++) {
				if (termMap[psBin][rfBin] == null) {
					columns.add("");
				} else {
					columns.add(termMap[psBin][rfBin]);
				}
			}
			csvWriter.write(columns);
		}
		csvWriter.close();
	}

	private void init() {
		if (outputFile == null) {
			String filename = inputFile.getPath().replaceAll("\\..*$", "");
			outputFile = new File(filename + "-termmap.csv");
		}
		psSize = (int) Math.ceil((psMax-psMin) / cellSize);
		rfSize = (int) Math.ceil((rfMax-rfMin) / cellSize);
		termMap = new String[psSize][rfSize];
	}

}
