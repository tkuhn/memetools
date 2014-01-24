package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import au.com.bytecode.opencsv.CSVParser;
import au.com.bytecode.opencsv.CSVWriter;

public class MemeUtils {

	private MemeUtils() {}  // no instances allowed

	private static final String logDirName = "log";
	private static final String rawDataDirName = "data";
	private static final String preparedDataDirName = "input";
	private static final String outputDataDirName = "files";

	private static File logDir;
	private static File rawDataDir;
	private static File preparedDataDir;
	private static File outputDataDir;

	private static CSVParser csvParser;

	public static File getLogDir() {
		if (logDir == null) {
			logDir = new File(logDirName);
			if (!logDir.exists()) logDir.mkdir();
		}
		return logDir;
	}

	public static File getRawDataDir() {
		if (rawDataDir == null) {
			rawDataDir = new File(rawDataDirName);
			if (!rawDataDir.exists()) rawDataDir.mkdir();
		}
		return rawDataDir;
	}

	public static File getPreparedDataDir() {
		if (preparedDataDir == null) {
			preparedDataDir = new File(preparedDataDirName);
			if (!preparedDataDir.exists()) preparedDataDir.mkdir();
		}
		return preparedDataDir;
	}

	public static File getOutputDataDir() {
		if (outputDataDir == null) {
			outputDataDir = new File(outputDataDirName);
			if (!outputDataDir.exists()) outputDataDir.mkdir();
		}
		return outputDataDir;
	}

	private static DecimalFormat df = new DecimalFormat("0.##########");

	public static String formatNumber(Number n) {
		return df.format(n);
	}

	public static void writeCsvLine(Writer writer, Object[] line) throws IOException {
		CSVWriter csvWriter = getCsvWriter(writer);
		String[] s = new String[line.length];
		for (int i = 0 ; i < line.length ; i++) {
			s[i] = line[i] + "";
		}
		csvWriter.writeNext(s);
	}

	public static void writeCsvLine(Writer writer, List<?> line) throws IOException {
		CSVWriter csvWriter = getCsvWriter(writer);
		String[] s = new String[line.size()];
		for (int i = 0 ; i < line.size() ; i++) {
			s[i] = line.get(i) + "";
		}
		csvWriter.writeNext(s);
	}

	private static CSVWriter getCsvWriter(Writer writer) {
		return new CSVWriter(writer, ',', '"', '\\', "\n");
	}

	public static String[] readCsvLine(BufferedReader reader) throws IOException {
		if (!reader.ready()) return null;
		String line = reader.readLine();
		return readCsvLine(line);
	}

	public static String[] readCsvLine(String line) throws IOException {
		if (csvParser == null) {
			csvParser = new CSVParser(',', '"', '\\');
		}
		return csvParser.parseLine(line);
	}

	public static List<String> readCsvLineAsList(BufferedReader reader) throws IOException {
		return new ArrayList<String>(Arrays.asList(readCsvLine(reader)));
	}

	public static List<String> readCsvLineAsList(String line) throws IOException {
		return new ArrayList<String>(Arrays.asList(readCsvLine(line)));
	}

	public static String normalize(String text) {
		text = " " + text.toLowerCase() + " ";
		text = text.replaceAll("([.,:;!?]) ", " $1 ");
		text = text.replaceAll("\\s+", " ");
		return text.trim();
	}

	public static void log(File logFile, Object obj) {
		try {
		    PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)));
		    String timestamp = new Timestamp(new Date().getTime()).toString();
		    if (obj instanceof Throwable) {
		    	((Throwable) obj).printStackTrace(out);
		    } else {
			    timestamp = timestamp.replaceFirst("\\.[0-9]*$", "");
			    out.println(timestamp + " " + obj);
		    }
		    out.close();
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
	}

}
