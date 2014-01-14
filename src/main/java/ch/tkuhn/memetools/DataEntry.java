package ch.tkuhn.memetools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataEntry {

	public static final String SEP = "  ";

	private String doi;
	private String date;
	private String text;
	private List<String> citedText;

	public DataEntry(String doi, String date, String text, List<String> citedText) {
		this.doi = doi;
		this.date = date;
		this.text = text;
		this.citedText = citedText;
	}

	public DataEntry(String doi, String date, String text) {
		this(doi, date, text, new ArrayList<String>());
	}

	public DataEntry(String line) {
		String[] splitline = line.split("  ");
		if (splitline.length < 3) {
			throw new RuntimeException("Invalid line: " + line);
		}
		doi = splitline[0];
		date = splitline[1];
		text = splitline[2];
		citedText = new ArrayList<String>();
		for (int i = 3 ; i < splitline.length ; i++) {
			citedText.add(splitline[i]);
		}
	}

	public String getDoi() {
		return doi;
	}

	public String getDate() {
		return date;
	}

	public int getYear() {
		return Integer.parseInt(date.substring(0, 4));
	}

	public String getText() {
		return text;
	}

	public List<String> getCitedText() {
		return citedText;
	}

	public void addCitedText(String t) {
		citedText.add(t);
	}

	public String getLine() {
		String line = doi + SEP + date + SEP + text;
		for (String c : citedText) {
			line += SEP + c;
		}
		return line;
	}

	public void recordTerms(Map<String,Integer> map, Map<String,Integer> stickingMap, Map<String,Integer> sparkingMap, Object filter) {
		Map<String,Boolean> processed = new HashMap<String,Boolean>();
		String allCited = " ";
		for (String c : citedText) allCited += "  " + c;
		allCited += " ";
		String[] tokens = text.trim().split(" ");
		for (int p1 = 0 ; p1 < tokens.length ; p1++) {
			String term = " ";
			for (int p2 = p1 ; p2 < tokens.length ; p2++) {
				term += tokens[p2] + " ";
				String t = term.trim();
				if (ignoreTerm(t, filter)) continue;
				if (processed.containsKey(t)) continue;
				processed.put(t, true);
				if (map != null) {
					increaseMapEntry(map, t);
				}
				if (stickingMap != null && allCited.contains(term)) {
					increaseMapEntry(stickingMap, t);
				}
				if (sparkingMap != null && !allCited.contains(term)) {
					increaseMapEntry(sparkingMap, t);
				}
			}
		}
	}

	public void recordCitedTerms(Map<String,Integer> map, Object filter) {
		Map<String,Boolean> processed = new HashMap<String,Boolean>();
		for (String cited : citedText) {
			String[] tokens = cited.trim().split(" ");
			for (int p1 = 0 ; p1 < tokens.length ; p1++) {
				String term = " ";
				for (int p2 = p1 ; p2 < tokens.length ; p2++) {
					term += tokens[p2] + " ";
					String t = term.trim();
					if (ignoreTerm(t, filter)) continue;
					if (processed.containsKey(t)) continue;
					processed.put(t, true);
					increaseMapEntry(map, t);
				}
			}
		}
	}

	private static void increaseMapEntry(Map<String,Integer> map, String key) {
		if (map.containsKey(key)) {
			map.put(key, map.get(key) + 1);
		} else {
			map.put(key, 1);
		}
	}

	@SuppressWarnings("unchecked")
	private static boolean ignoreTerm(String term, Object filter) {
		if (filter == null) {
			return false;
		} else if (filter instanceof Map) {
			return !((Map<String,?>) filter).containsKey(term);
		} else if (filter instanceof String) {
			return !((String) filter).contains(" " + term + " ");
		} else {
			throw new RuntimeException("Unrecognized filter: " + filter);
		}
	}

}
