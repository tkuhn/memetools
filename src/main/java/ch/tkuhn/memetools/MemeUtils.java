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
import java.util.Date;
import java.util.List;

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
		boolean first = true;
		for (Object o : line) {
			if (!first) writer.write(",");
			first = false;
			writeCsvEntry(o, writer);
		}
		writer.write("\n");
	}

	public static void writeCsvLine(Writer writer, Iterable<?> line) throws IOException {
		boolean first = true;
		for (Object o : line) {
			if (!first) writer.write(",");
			first = false;
			writeCsvEntry(o, writer);
		}
		writer.write("\n");
	}

	private static void writeCsvEntry(Object o, Writer writer) throws IOException {
		if (o instanceof String) {
			String s = (String) o;
			if (s.matches(".*[,\"].*")) {
				s = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
			}
			writer.write(s);
		} else {
			writer.write(o + "");
		}
	}

	public static List<String> readCsvLine(BufferedReader reader) throws IOException {
		if (!reader.ready()) return null;
		String line = reader.readLine();
		return readCsvLine(line);
	}

	public static List<String> readCsvLine(String line) throws IOException {
		List<String> entries = new ArrayList<String>();
		while (!line.isEmpty()) {
			if (line.matches("\"([^\"\\\\]|\\\\\"|\\\\\\\\)*\"(,.*)?")) {
				String e = line.replaceFirst("^\"(([^\"\\\\]|\\\\\"|\\\\\\\\)*)\"(,.*)?$", "$1");
				line = line.replaceFirst("^\"([^\"\\\\]|\\\\\"|\\\\\\\\)*\"((,.*)?)$", "$2");
				if (line.startsWith(",")) line = line.substring(1);
				e = e.replace("\\\"", "\"").replace("\\\\", "\\");
				entries.add(e);
			} else if (line.contains(",")) {
				String e = line.substring(0, line.indexOf(','));
				line = line.substring(line.indexOf(',') + 1);
				entries.add(e);
			} else {
				entries.add(line);
				line = "";
			}
		}
		return entries;
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
