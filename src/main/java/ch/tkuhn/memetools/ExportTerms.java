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
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

import org.supercsv.io.CsvListReader;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

/**
 * Exports (random) terms from a CSV file.
 */
public class ExportTerms {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-n", description = "Number of random terms to extract")
	private int n = 100;

	@Parameter(names = "-d", description = "Allow for duplicates")
	private boolean duplicatesAllowed = false;

	@Parameter(names = "-f", description = "Do not randomize but simply output the first terms")
	private boolean firstTerms = false;

	@Parameter(names = "-c", description = "Index or name of term column")
	private String termColIndexOrName = "TERM";

	@Parameter(names = "-w", description = "Index or name of weight column (equal weights if null)")
	private String weightColIndexOrName = null;

	@Parameter(names = "-s", description = "Random seed")
	private Long randomSeed = null;

	public static final void main(String[] args) {
		ExportTerms obj = new ExportTerms();
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

	private Random random;
	private NavigableMap<Integer,String> map;
	private List<String> list;
	private int totalWeights;
	private Map<String,Boolean> duplicatesMap;
	private int count;

	public void run() throws IOException {
		init();
		readTerms();
		writeRandomTerms();
	}

	private void readTerms() throws IOException {
		BufferedReader r = new BufferedReader(new FileReader(inputFile), 64*1024);
		CsvListReader csvReader = new CsvListReader(r, MemeUtils.getCsvPreference());
		List<String> line = csvReader.read();
		int termCol = getColumnIndex(termColIndexOrName, line);
		int weightCol = getColumnIndex(weightColIndexOrName, line);
		while ((line = csvReader.read()) != null) {
			int weight = 1;
			if (weightCol > -1) weight = Integer.parseInt(line.get(weightCol));
			String term = line.get(termCol);
			if (firstTerms) {
				if (duplicatesMap.containsKey(term)) continue;
				if (count >= n) break;
				count++;
				list.add(term);
				if (!duplicatesAllowed) {
					duplicatesMap.put(term, true);
				}
			} else {
				totalWeights += weight;
				map.put(totalWeights, term);
			}
		}
		csvReader.close();
	}

	private void writeRandomTerms() throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile), 64*1024);
		count = 0;
		while (count < n) {
			String term;
			if (firstTerms) {
				term = list.get(count);
			} else {
				int r = random.nextInt(totalWeights);
				term = map.ceilingEntry(r).getValue();
				if (duplicatesMap.containsKey(term)) continue;
				if (!duplicatesAllowed) {
					duplicatesMap.put(term, true);
				}
			}
			writer.write(term + "\n");
			count++;
		}
		writer.close();
	}

	private void init() {
		if (outputFile == null) {
			String n = inputFile.getName().replaceAll("\\..*$", "");
			if (firstTerms) {
				n += "-first";
			} else {
				n += "-random";
			}
			if (weightColIndexOrName != null) n += "w";
			outputFile = new File(MemeUtils.getOutputDataDir(), n + ".csv");
		}
		if (randomSeed == null) {
			random = new Random();
		} else {
			random = new Random(randomSeed);
		}
		if (firstTerms) {
			list = new ArrayList<String>();
		} else {
			map = new TreeMap<Integer,String>();
			totalWeights = 0;
		}
		duplicatesMap = new HashMap<String,Boolean>();
		count = 0;
	}

	private int getColumnIndex(String colIndexOrName, List<String> header) {
		if (colIndexOrName == null) return -1;
		if (colIndexOrName.matches("[0-9]+")) return Integer.parseInt(colIndexOrName);
		return header.indexOf(colIndexOrName);
	}

}
