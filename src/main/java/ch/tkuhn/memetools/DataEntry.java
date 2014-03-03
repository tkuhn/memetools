package ch.tkuhn.memetools;

import java.util.ArrayList;
import java.util.List;

public class DataEntry {

	public static final String SEP = "  ";

	private Object id;
	private Object date;
	private String text;
	private List<String> citedText;

	public DataEntry(Object id, Object date, String text, List<String> citedText) {
		this.id = id;
		this.date = date;
		this.text = text;
		this.citedText = citedText;
	}

	public DataEntry(Object id, Object date, String text) {
		this(id, date, text, new ArrayList<String>());
	}

	public DataEntry(String line) {
		String[] splitline = line.split("  ", -1);
		if (splitline.length < 3) {
			throw new RuntimeException("Invalid line: " + line);
		}
		date = splitline[0];
		id = splitline[1];
		text = splitline[2];
		citedText = new ArrayList<String>();
		for (int i = 3 ; i < splitline.length ; i++) {
			citedText.add(splitline[i]);
		}
	}

	public String getId() {
		return id.toString();
	}

	public int getIdInt() {
		if (id instanceof Integer) {
			return (Integer) id;
		}
		return Integer.parseInt(id.toString());
	}

	public String getDate() {
		return date.toString();
	}

	public short getYear() {
		if (date instanceof Short) {
			return (Short) date;
		}
		return Short.parseShort(date.toString().substring(0, 4));
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
		String line = date + SEP + id + SEP + text;
		for (String c : citedText) {
			line += SEP + c;
		}
		return line;
	}

}
