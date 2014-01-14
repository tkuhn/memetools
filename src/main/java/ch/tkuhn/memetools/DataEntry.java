package ch.tkuhn.memetools;

import java.util.ArrayList;
import java.util.List;

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

}
