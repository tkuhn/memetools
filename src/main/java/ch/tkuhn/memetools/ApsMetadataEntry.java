package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class ApsMetadataEntry {

	private String id;
	private String date;
	private Map<String,String> title;
	private List<Map<String,Object>> authors;
	@SerializedName("abstract")
	private Map<String,String> abstractText;

	public static ApsMetadataEntry load(File file) throws FileNotFoundException {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(file));
			return new Gson().fromJson(br, ApsMetadataEntry.class);
		} finally {
			try {
				if (br != null) br.close();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	public String getId() {
		return id;
	}

	public String getDate() {
		return date;
	}

	public List<String> getNormalizedAuthors() {
		List<String> nAuthors = new ArrayList<String>();
		if (authors != null) {
			for (Map<String,Object> m : authors) {
				if (!m.containsKey("type") || !"Person".equals(m.get("type")) || !m.containsKey("firstname") ||
						!m.containsKey("surname")) {
					nAuthors.add(null);
					continue;
				}
				String f = m.get("firstname").toString().toLowerCase();
				String s = m.get("surname").toString().toLowerCase();
				if (f.isEmpty() || s.isEmpty()) {
					nAuthors.add(null);
					continue;
				}
				nAuthors.add(f.substring(0, 1) + "." + s);
			}
		}
		return nAuthors;
	}

	public String getNormalizedTitle() {
		if (title == null || !title.containsKey("value")) return null;
		String n = title.get("value");
		if (n == null) return null;
		if (title.containsKey("format") && title.get("format").startsWith("html")) {
			n = preprocessHtml(n);
		}
		return MemeUtils.normalize(n);
	}

	public String getNormalizedAbstract() {
		if (abstractText == null || !abstractText.containsKey("value")) return null;
		String n = abstractText.get("value");
		if (n == null) return null;
		if (abstractText.containsKey("format") && abstractText.get("format").startsWith("html")) {
			n = preprocessHtml(n);
		}
		return MemeUtils.normalize(n);
	}

	public String preprocessHtml(String htmlText) {
		htmlText = htmlText.replaceAll("</?[a-z]+ ?.*?/?>", "");
		return htmlText;
	}

}
