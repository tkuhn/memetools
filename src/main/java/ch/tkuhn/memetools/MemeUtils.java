package ch.tkuhn.memetools;

import java.io.IOException;
import java.io.Writer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemeUtils {

	private MemeUtils() {}  // no instances allowed

	public static Map<String,Boolean> getTerms(String text, int grams, Map<String,?> filter) {
		Map<String,Boolean> terms = new HashMap<String,Boolean>();
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

}
