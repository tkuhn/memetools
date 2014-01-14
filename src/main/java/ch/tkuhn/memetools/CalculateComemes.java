package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import au.com.bytecode.opencsv.CSVReader;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class CalculateComemes {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-m", required = true, description = "Meme file")
	private String memeFileName;

	@Parameter(names = "-c", description = "Number of memes to consider (from top of meme file)")
	private int count = 100;

	public static final void main(String[] args) {
		CalculateComemes obj = new CalculateComemes();
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

	private int grams = 1;

	private Map<String,Integer> memes;
	private Map<String,HashMap<String,Integer>> co;
	private Map<String,Integer> mst;
	private int nxx;

	private void run() {
		memes = new HashMap<String,Integer>();
		co = new HashMap<String,HashMap<String,Integer>>();
		mst = new HashMap<String,Integer>();
		nxx = 0;

		System.out.println("Reading " + count + " memes from " + memeFileName);
		try {
			CSVReader reader = new CSVReader(new FileReader(memeFileName));
			String [] row;
			int c = -1;
			while ((row = reader.readNext()) != null) {
				c = c + 1;
				if (c == 0) continue;
				if (c > count) break;
				String meme = row[1];
				int l = meme.trim().split("\\s+").length;
				if (l > grams) grams = l;
				memes.put(meme, c);
			}
			reader.close();
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}

		for (String meme1 : memes.keySet()) {
			co.put(meme1, new HashMap<String,Integer>());
			mst.put(meme1, 0);
			for (String meme2 : memes.keySet()) {
				co.get(meme1).put(meme2, 0);
			}
		}

		int errors = 0;
		try {
			System.out.println("Counting memes...");
			BufferedReader reader = new BufferedReader(new FileReader(inputFile));
			String line;
			while ((line = reader.readLine()) != null) {
				DataEntry d = new DataEntry(line);
				nxx = nxx + 1;
				Map<String,Boolean> citingTerms = getFilteredTerms(d.getText());
				Map<String,Boolean> citedTerms = new HashMap<>();
				for (String s : d.getCitedText()) {
					collectFilteredTerms(s, citedTerms);
				}
				for (String meme1 : memes.keySet()) {
					boolean meme1Stick = false;
					if (citedTerms.containsKey(meme1) && citingTerms.containsKey(meme1)) {
						mst.put(meme1, mst.get(meme1) + 1);
						meme1Stick = true;
					}
					for (String meme2 : citedTerms.keySet()) {
						if (meme1.compareTo(meme2) >= 0) continue;
						boolean meme2Stick = false;
						if (citedTerms.containsKey(meme2) && citingTerms.containsKey(meme2)) {
							meme2Stick = true;
						}
						if (meme1Stick && meme2Stick) {
							co.get(meme1).put(meme2, co.get(meme1).get(meme2) + 1);
						}
					}
				}
			}
			reader.close();
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		System.out.println("Number of errors: " + errors);
		System.out.println("Calculating comeme scores and writing CSV file...");
		try {
			Writer csvWriter = new BufferedWriter(new FileWriter(getOutputFile(inputFile, "csv")));
			Writer gmlWriter = new BufferedWriter(new FileWriter(getOutputFile(inputFile, "gml")));
			gmlWriter.write("graph [\n");
			gmlWriter.write("directed 0\n");
			for (String meme : memes.keySet()) {
				gmlWriter.write("node [ id " + memes.get(meme) + " label \"" + meme.replaceAll("\"", "\\\"") + "\" ]\n");
			}
			for (String meme1 : memes.keySet()) {
				Object[] row = new Object[count+1];
				row[0] = meme1;
				int i = 0;
				for (String meme2 : memes.keySet()) {
					i = i + 1;
					if (meme1.equals(meme2)) {
						row[i] = 1;
					} else {
						int n11 = getValue(co, meme1, meme2);
						int n1x = mst.get(meme1);
						int n0x = nxx - n1x;
						int nx1 = mst.get(meme2);
						int nx0 = nxx - nx1;
						int n10 = n1x - n11;
						int n01 = nx1 - n11;
						int n00 = nxx - n11 - n10 -n01;
						double v = 0;
						//v = (double) n11 / ( n01 + n10 + n11 );
						// Phi coefficient:
						double np = (double) n1x * (double) n0x * (double) nx1 * (double) nx0;
						if (np != 0) {
							v = ( ((double) n11 * (double) n00) - ((double) n10 * (double) n01) ) / Math.sqrt(np);
						}
						row[i] = v;
						if (meme1.compareTo(meme2) < 0) {
							gmlWriter.write("edge [ source " + memes.get(meme1) +
									" target " + memes.get(meme2) +
									" value " + MemeUtils.formatNumber(v) + " ]\n");
						}
					}
				}
				MemeUtils.writeCsvLine(csvWriter, row);
			}
			gmlWriter.write("]\n");
			csvWriter.close();
			gmlWriter.close();
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}

	private Map<String,Boolean> getFilteredTerms(String text) {
		return MemeUtils.getTerms(text, grams, memes);
	}

	private void collectFilteredTerms(String text, Map<String,Boolean> terms) {
		MemeUtils.collectTerms(text, grams, memes, terms);
	}

	private File getOutputFile(File inputFile, String ext) {
		String basename = inputFile.getName().replaceAll("\\..*$", "");
		String filename = "files/cm-" + basename + "-c" + count + "." + ext;
		return new File(filename);
	}

	private int getValue(Map<String,HashMap<String,Integer>> map, String meme1, String meme2) {
		if (meme1.compareTo(meme2) < 0) {
			return map.get(meme1).get(meme2);
		}
		return map.get(meme2).get(meme1);
	}

}
