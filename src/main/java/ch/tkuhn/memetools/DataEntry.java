package ch.tkuhn.memetools;

import java.util.ArrayList;
import java.util.List;

public class DataEntry {

	public static final String SEP = "  ";
	public static final String AUTHORS_MARKER = "A:";
	public static final String CITATIONS_MARKER = "C:";

	private Object id;
	private Object date;
	private String text;
	private List<String> citedText;
	private String authors;
	private String citations;

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
		int l = splitline.length;
		int tpos = 2;
		if (l < 3) {
			throw new RuntimeException("Invalid line: " + line);
		}
		date = splitline[0];
		id = splitline[1];
		while (splitline[tpos].matches("[A-Z]:.*")) {
			if (splitline[tpos].startsWith(AUTHORS_MARKER)) {
				authors = splitline[tpos].substring(AUTHORS_MARKER.length());
			} else if (splitline[tpos].startsWith(CITATIONS_MARKER)) {
				citations = splitline[tpos].substring(CITATIONS_MARKER.length());
			} else {
				break;
			}
			tpos++;
		}
		text = splitline[tpos];
		citedText = new ArrayList<String>();
		for (int i = tpos+1 ; i < splitline.length ; i++) {
			citedText.add(splitline[i]);
		}
	}

	protected void setAuthors(String authors) {
		this.authors = authors;
	}

	protected void setCitations(String citations) {
		this.citations = citations;
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

	public String getCitations() {
		return citations;
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
		String line = date + SEP + id;
		if (authors != null) {
			line += SEP + AUTHORS_MARKER + authors;
		}
		if (citations != null) {
			line += SEP + CITATIONS_MARKER + citations;
		}
		line += SEP + text;
		for (String c : citedText) {
			line += SEP + c;
		}
		return line;
	}

}
