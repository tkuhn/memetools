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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.supercsv.io.CsvListReader;
import org.supercsv.io.CsvListWriter;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class CalculateCommData {

	@Parameter(description = "chronologically-sorted-input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file for community data")
	private File outputFile;

	@Parameter(names = "-t", description = "File with terms", required = true)
	private File termsFile;

	@Parameter(names = "-tcol", description = "Index or name of column to read terms (if term file is in CSV format)")
	private String termCol = "TERM";

	@Parameter(names = "-c", description = "Only use first c terms")
	private int termCount = -1;

	@Parameter(names = "-m", description = "Community map file", required = true)
	private File communityMapFile;

	private File logFile;

	public static final void main(String[] args) {
		CalculateCommData obj = new CalculateCommData();
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

	private Map<String,String> communityMap;
	private List<String> communitySequence;
	private Map<String,Integer> commFreq;
	private List<String> terms;
	private Map<String,Long> timeSum;

	private Map<String,Integer> termFreq;
	private Map<String,Map<String,Integer>> termCommFreq;

	private Map<String,List<Pair<String,Float>>> commTopMemes;

	public void run() {
		init();
		try {
			readTerms();
			readCommunityMap();
			readData();
			analyzeData();
			writeOutput();
		} catch (Throwable th) {
			log(th);
			System.exit(1);
		}
		log("Finished");
	}

	private void init() {
		logFile = new File(MemeUtils.getLogDir(), getOutputFileName() + ".log");
		log("==========");

		terms = new ArrayList<String>();
		communityMap = new HashMap<String,String>();
		communitySequence = new ArrayList<String>();
		commFreq = new HashMap<String,Integer>();
		timeSum = new HashMap<String,Long>();

		termFreq = new HashMap<String,Integer>();
		termCommFreq = new HashMap<String,Map<String,Integer>>();

		commTopMemes = new HashMap<String,List<Pair<String,Float>>>();
	}

	private void readCommunityMap() throws IOException {
		log("Reading communities from " + communityMapFile + " ...");
		BufferedReader reader = new BufferedReader(new FileReader(communityMapFile));
		String line;
		while ((line = reader.readLine()) != null) {
			String[] parts = line.split(" ");
			String c = parts[1];
			communityMap.put(parts[0], c);
			if (!communitySequence.contains(c)) {
				communitySequence.add(c);
			}
			commFreq.put(c, 0);
			timeSum.put(c, 0l);
			commTopMemes.put(c, new ArrayList<Pair<String,Float>>());
		}
		reader.close();
		Collections.sort(communitySequence, new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				Integer i1 = Integer.parseInt(o1);
				Integer i2 = Integer.parseInt(o2);
				return i1.compareTo(i2);
			}
		});
		log("Number of communities: " + communitySequence.size());
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
			termFreq.put(term, 0);
			termCommFreq.put(term, new HashMap<String,Integer>());
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
			termFreq.put(term, 0);
			termCommFreq.put(term, new HashMap<String,Integer>());
			if (termCount >= 0 && terms.size() >= termCount) {
				break;
			}
		}
		csvReader.close();
	}

	private void readData() throws IOException {
		log("Processing data from " + inputFile + "...");
		BufferedReader reader = new BufferedReader(new FileReader(inputFile));
		String inputLine;
		int entryCount = 0;
		while ((inputLine = reader.readLine()) != null) {
			logProgress(entryCount);
			entryCount++;
			DataEntry d = new DataEntry(inputLine);
			String c = communityMap.get(d.getId());
			if (c == null) continue;
			commFreq.put(c, commFreq.get(c) + 1);
			timeSum.put(c, timeSum.get(c) + entryCount);
			String text = " " + d.getText() + " ";
			for (String term : terms) {
				if (text.contains(" " + term + " ")) {
					termFreq.put(term, termFreq.get(term) + 1);
					if (termCommFreq.get(term).containsKey(c)) {
						termCommFreq.get(term).put(c, termCommFreq.get(term).get(c) + 1);
					} else {
						termCommFreq.get(term).put(c, 1);
					}
				}
			}
			
		}
		reader.close();
	}

	private void analyzeData() {
		for (String term : terms) {
			int tf = termFreq.get(term);
			for (String comm : communitySequence) {
				int cf = commFreq.get(comm);
				int tcf = 0;
				if (termCommFreq.get(term).containsKey(comm)) {
					tcf = termCommFreq.get(term).get(comm);
				}
				if (tf > 0 && cf > 0 && tcf > 0) {
					float prec = (float) tcf / tf;
					float rec = (float) tcf / cf;
					float score = 2 * prec * rec / (prec + rec);
					commTopMemes.get(comm).add(Pair.of(term, score));
				}
			}
		}
	}

	private void writeOutput() throws IOException {
		log("Writing result to output file...");
		if (outputFile == null) {
			outputFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + ".csv");
		}
		CsvListWriter csvWriter = new CsvListWriter(new BufferedWriter(new FileWriter(outputFile)), MemeUtils.getCsvPreference());

		csvWriter.write("COMM-ID", "TIME-AVG", "MEME1", "FSCORE1", "MEME2", "FSCORE2", "...");

		for (String comm : communitySequence) {
			List<Object> row = new ArrayList<Object>();
			row.add(comm);
			row.add(timeSum.get(comm) / commFreq.get(comm));
			List<Pair<String,Float>> memes = new ArrayList<Pair<String,Float>>(commTopMemes.get(comm));
			Collections.sort(memes, new Comparator<Pair<String,Float>>() {
				@Override
				public int compare(Pair<String,Float> o1, Pair<String,Float> o2) {
					return o2.getRight().compareTo(o1.getRight());
				}
			});
			int n = 0;
			for (Pair<String,Float> m : memes) {
				n++;
				if (n > 3 && m.getRight() < 0.05) break;
				row.add(m.getLeft());
				row.add(m.getRight());
			}
			csvWriter.write(row);
		}

		csvWriter.close();
	}

	private String getOutputFileName() {
		String basename = inputFile.getName().replaceAll("\\..*$", "");
		basename = basename.replaceFirst("-chronologic", "");
		basename = basename.replaceFirst("-TA?", "");
		String filename = "cm-" + basename;
		return filename;
	}

	private void logProgress(int p) {
		if (p % 100000 == 0) log(p + "...");
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

}
