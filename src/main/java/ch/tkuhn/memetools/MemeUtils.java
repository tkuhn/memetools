package ch.tkuhn.memetools;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemeUtils {

	private MemeUtils() {}  // no instances allowed

	public static final String SEP = "  ";

	public static void collectTerms(String text, int grams, Map<String,?> filter, Map<String,Boolean> terms) {
		String[] onegrams = text.trim().split("\\s+");
		List<String> previous = new ArrayList<String>();
		for (String t : onegrams) {
			if (filter == null || filter.containsKey(t)) {
				terms.put(t, true);
			}
			for (int x = 1; x < grams; x++) {
				if (previous.size() > x-1) {
					String term = t;
					for (int y = 0; y < x; y++) {
						term = previous.get(y) + " " + term;
					}
					if (filter == null || filter.containsKey(term)) {
						terms.put(term, true);
					}
				}
			}
			previous.add(0, t);
			while (previous.size() > grams-1) {
				previous.remove(previous.size()-1);
			}
		}
	}

	public static void collectTerms(String text, int grams, Map<String,Boolean> terms) {
		collectTerms(text, grams, null, terms);
	}

	public static Map<String,Boolean> getTerms(String text, int grams, Map<String,?> filter) {
		Map<String,Boolean> terms = new HashMap<String,Boolean>();
		collectTerms(text, grams, filter, terms);
		return terms;
	}

	public static Map<String,Boolean> getTerms(String text, int grams) {
		return getTerms(text, grams, null);
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

	public static void log(File logFile, Object text) {
		try {
		    PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)));
		    String timestamp = new Timestamp(new Date().getTime()).toString();
		    timestamp = timestamp.replaceFirst("\\.[0-9]*$", "");
		    out.println(timestamp + " " + text);
		    out.close();
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
	}

}
