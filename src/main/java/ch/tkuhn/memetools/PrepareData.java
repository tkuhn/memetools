package ch.tkuhn.memetools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.Date;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class PrepareData {

	public enum Source { aps, pmc };

	@Parameter(names = "-s", description = "Data source; one of: aps, pmc", required = true)
	private Source source;

	@Parameter(names = "-r", description = "Path to read raw data")
	private String rawDataPath = "data";

	@Parameter(names = "-p", description = "Path to write prepared data")
	private String preparedDataPath = "input";

	private File logFile;

	public static final void main(String[] args) {
		PrepareData obj = new PrepareData();
		JCommander jc = new JCommander(obj);
		try {
			jc.parse(args);
		} catch (ParameterException ex) {
			jc.usage();
			System.exit(1);
		}
		obj.run();
	}

	public PrepareData() {
	}

	public void run() {
		logFile = new File(preparedDataPath + "/" + source + ".log");
		if (logFile.exists()) logFile.delete();

		if (source == Source.aps) {
			log("Preparing APS data...");
			PrepareApsData.run(this);
		} else if (source == Source.pmc) {
			log("Preparing PMC data...");
			PreparePmcData.run(this);
		}
	}

	String getRawDataPath() {
		return rawDataPath;
	}

	String getPreparedDataPath() {
		return preparedDataPath;
	}

	public static String normalize(String text) {
		text = " " + text.toLowerCase() + " ";
		text = text.replaceAll("[.,:;] ", " ");
		text = text.replaceAll("[^a-z0-9\\-()\\[\\]{}_^.,:;/\\'|+]+", " ");
		text = text.replaceAll("[^a-z0-9\\-()\\[\\]{}_^.,:;/\\'|+]+", " ");
		return text.trim();
	}

	void log(Object text) {
		try {
		    PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)));
		    out.println(new Timestamp(new Date().getTime()) + " " + text);
		    out.close();
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
	}

}
