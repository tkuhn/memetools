package ch.tkuhn.memetools;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class CalculateMemeScores {

	private static Options options;

	static {
		options = new Options();
		options.addOption("g", true, "Use <arg>-grams");
		options.addOption("y", true, "Calculate scores for year <arg>");
		options.addOption("n", true, "Set n parameter to <arg>");
	}

	public static final void main(String[] args) {
		CommandLine cmd = null;
		try {
			cmd = (new GnuParser()).parse(options, args);
		} catch (ParseException ex) {
			System.err.println("ERROR: " + ex.getMessage());
			printHelp();
			System.exit(1);
		}
		if (cmd.getArgList().size() == 0) {
			System.err.println("ERROR: Specify input file");
			printHelp();
			System.exit(1);
		}
		if (cmd.getArgList().size() > 1) {
			System.err.println("ERROR: Too many arguments");
			printHelp();
			System.exit(1);
		}
		String gramString = cmd.getOptionValue("g", "1");
		int gram = 1;
		if (gramString.matches("[1-9]")) {
			gram = Integer.parseInt(gramString);
		} else {
			System.err.println("ERROR: -g has to be an integer between 1 and 9");
			printHelp();
			System.exit(1);
		}
		String nString = cmd.getOptionValue("g", "1");
		int n = 1;
		if (nString.matches("[0-9]{1,3}")) {
			n = Integer.parseInt(nString);
		} else {
			System.err.println("ERROR: -g has to be an integer between 0 and 999");
			printHelp();
			System.exit(1);
		}
		String yearString = cmd.getOptionValue("y");
		Integer year = null;
		if (yearString != null) {
			if (yearString.matches("[0-9]{1,4}")) {
				year = Integer.parseInt(yearString);
			} else {
				System.err.println("ERROR: -y has to be an integer between 0 and 9999");
				printHelp();
				System.exit(1);
			}
		}

		CalculateMemeScores c = new CalculateMemeScores(gram, n, year);
		c.run();
	}

	public static void printHelp() {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("CalculateMemeScores <options> <inputfile>", options);
	}

	private int gram;
	private int n;
	private Integer year;

	public CalculateMemeScores(int gram, int n, Integer year) {
		this.gram = gram;
		this.n = n;
		this.year = year;
	}

	public CalculateMemeScores(int gram, int n) {
		this(gram, n, null);
	}

	public void run() {
		// ...
	}

}
