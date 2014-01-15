package ch.tkuhn.memetools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.Date;

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
			if (o instanceof String) {
				String s = (String) o;
				if (s.matches(".*[,\"].*")) {
					s = "\"" + s.replaceAll("\"", "\\\"") + "\"";
				}
				writer.write(s);
			} else {
				writer.write(df.format(o));
			}
		}
		writer.write("\n");
	}

	public static String normalize(String text) {
		text = " " + text.toLowerCase() + " ";
		text = text.replaceAll("[.,:;] ", " ");
		text = text.replaceAll("[^a-z0-9\\-()\\[\\]{}_^.,:;/\\'|+]+", " ");
		text = text.replaceAll("[^a-z0-9\\-()\\[\\]{}_^.,:;/\\'|+]+", " ");
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
