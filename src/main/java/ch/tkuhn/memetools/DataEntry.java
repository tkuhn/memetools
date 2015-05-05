package ch.tkuhn.memetools;

import java.util.ArrayList;
import java.util.List;

public class DataEntry {

	public static final String SEP = "  ";

	private Object id;
	private Object date;
	private String authors;
	private String text;
	private List<String> citedText;

	public DataEntry(Object id, Object date, String text, List<String> citedText) {
		this(id, date, null, text, citedText);
	}

	public DataEntry(Object id, Object date, String authors, String text, List<String> citedText) {
		this.id = id;
		this.date = date;
		this.authors = authors;
		this.text = text;
		this.citedText = citedText;
	}

	public DataEntry(Object id, Object date, String text) {
		this(id, date, text, null, new ArrayList<String>());
	}

	public DataEntry(Object id, Object date, String authors, String text) {
		this(id, date, text, authors, new ArrayList<String>());
	}

	public DataEntry(String line) {
		String[] splitline = line.split("  ", -1);
		int l = splitline.length;
		int tpos = 2;
		if (l < 3) {
			throw new RuntimeException("Invalid line: " + line);
		}
		date = splitline[0];
		id = splitline[1];
		if (splitline[2].startsWith(" ")) {
			authors = splitline[2].substring(1);
			tpos = 3;
		}
		text = splitline[tpos];
		citedText = new ArrayList<String>();
		for (int i = tpos+1 ; i < splitline.length ; i++) {
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

	public String getAuthors() {
		return authors;
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
		String line;
		if (authors == null) {
			line = date + SEP + id + SEP + text;
		} else {
			line = date + SEP + id + SEP + " " + authors + SEP + text;
		}
		for (String c : citedText) {
			line += SEP + c;
		}
		return line;
	}

}
