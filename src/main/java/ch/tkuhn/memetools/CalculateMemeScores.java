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
		// ...
	}

	public static void printHelp() {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("CalculateMemeScores <options> <inputfile>", options);
	}

}
