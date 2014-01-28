package ch.tkuhn.memetools;

import java.util.HashMap;
import java.util.Map;

public class MemeScorer {

	private boolean screenMode;

	private Map<String,Integer> f;

	private int t;

	private Map<String,Integer> mm;
	private Map<String,Integer> m;
	private Map<String,Integer> xm;

	private Map<String,Boolean> termBeginnings;

	private Map<String,Boolean> terms;

	public MemeScorer(boolean screenMode) {
		this.screenMode = screenMode;
		f = new HashMap<String,Integer>();
		t = 0;
		mm = new HashMap<String,Integer>();
		m = new HashMap<String,Integer>();
		xm = new HashMap<String,Integer>();
		termBeginnings = new HashMap<String,Boolean>();
		if (!screenMode) {
			terms = new HashMap<String,Boolean>();
		}
	}

	public Map<String,Integer> getF() {
		return f;
	}

	public int getF(String term) {
		return f.get(term);
	}

	public double getRelF(String term) {
		return (double) f.get(term) / t;
	}

	public int getT() {
		return t;
	}

	public Map<String,Integer> getMM() {
		return mm;
	}

	public int getMM(String term) {
		return mm.get(term);
	}

	public Map<String,Integer> getM() {
		return m;
	}

	public int getM(String term) {
		return m.get(term);
	}

	public Map<String,Integer> getXM() {
		return xm;
	}

	public int getXM(String term) {
		return xm.get(term);
	}

	public int getX(String term) {
		return t - m.get(term);
	}

	public double[] calculateMemeScoreValues(String term, int n) {
		return calculateMemeScoreValues(getMM(term), getM(term), getXM(term), getX(term), getRelF(term), n);
	}

	public static double[] calculateMemeScoreValues(int mmVal, int mVal, int xmVal, int xVal, double rfVal, int n) {
		double stick = (double) mmVal / (mVal + n);
		double spark = (double) (xmVal + n) / (xVal + n);
		double ps = stick / spark;
		double ms = ps * rfVal;
		return new double[] {stick, spark, ps, ms};
	}

	public void screenTerms(DataEntry d) {
		if (!screenMode) {
			throw new RuntimeException("Not in screen mode");
		}
		recordStickingTerms(d);
	}

	private void recordStickingTerms(DataEntry d) {
		Map<String,Boolean> processed = new HashMap<String,Boolean>();
		String allCited = "";
		for (String c : d.getCitedText()) allCited += "  " + c.trim();
		allCited += " ";
		String[] tokens = d.getText().trim().split(" ");
		for (int p1 = 0 ; p1 < tokens.length ; p1++) {
			String pre = "   ";
			if (p1 > 0) pre = " " + tokens[p1-1];
			String term = " ";
			for (int p2 = p1 ; p2 < tokens.length ; p2++) {
				term += tokens[p2] + " ";
				String t = term.trim();
				String post = "   ";
				if (p2 < tokens.length-1) post = tokens[p2+1] + " ";
				if (processed.containsKey(t)) continue;
				if (allCited.contains(term)) {
					int c = countOccurrences(allCited, term);
					if (countOccurrences(allCited, pre + term) < c && countOccurrences(allCited, term + post) < c) {
						increaseMapEntry(mm, t);
						processed.put(t, true);
					} else if (screenMode) {
						termBeginnings.put(t, true);
					}
				} else {
					break;
				}
			}
		}
	}

	public void addTerm(String term) {
		if (screenMode) {
			throw new RuntimeException("In screen mode");
		}
		terms.put(term, true);
		String[] parts = term.split(" ");
		String beginning = "";
		for (int i = 0 ; i < term.length()-1 ; i++) {
			beginning += " " + parts[i];
			beginning = beginning.trim();
			termBeginnings.put(beginning, true);
		}
	}

	public void fixTerms() {
		for (String w : mm.keySet()) {
			f.put(w, 0);
			m.put(w, 0);
			xm.put(w, 0);
			if (mm.get(w) == null) {
				mm.put(w, 0);
			}
		}
	}

	public void recordTerms(DataEntry d) {
		t++;
		if (!screenMode) {
			recordStickingTerms(d);
		}
		// Record terms from citing article:
		Map<String,Boolean> processed = new HashMap<String,Boolean>();
		String allCited = "";
		for (String c : d.getCitedText()) allCited += "  " + c.trim();
		allCited += " ";
		String[] tokens = d.getText().trim().split(" ");
		for (int p1 = 0 ; p1 < tokens.length ; p1++) {
			String term = " ";
			for (int p2 = p1 ; p2 < tokens.length ; p2++) {
				term += tokens[p2] + " ";
				String s = term.trim();
				if (ignoreTermsStartingWith(s)) break;
				if (ignoreTerm(s)) continue;
				if (processed.containsKey(s)) continue;
				processed.put(s, true);
				increaseMapEntry(f, s);
				if (!allCited.contains(term)) {
					increaseMapEntry(xm, s);
				}
			}
		}
		// Record terms from cited article:
		processed = new HashMap<String,Boolean>();
		for (String cited : d.getCitedText()) {
			tokens = cited.trim().split(" ");
			for (int p1 = 0 ; p1 < tokens.length ; p1++) {
				String term = " ";
				for (int p2 = p1 ; p2 < tokens.length ; p2++) {
					term += tokens[p2] + " ";
					String s = term.trim();
					if (ignoreTermsStartingWith(s)) break;
					if (ignoreTerm(s)) continue;
					if (processed.containsKey(s)) continue;
					processed.put(s, true);
					increaseMapEntry(m, s);
				}
			}
		}
	}

	private boolean ignoreTermsStartingWith(String term) {
		if (screenMode) {
			return !mm.containsKey(term) && !termBeginnings.containsKey(term);
		} else {
			return !terms.containsKey(term) && !termBeginnings.containsKey(term);
		}
	}

	private boolean ignoreTerm(String term) {
		if (screenMode) {
			return !mm.containsKey(term);
		} else {
			return !terms.containsKey(term);
		}
	}

	private static void increaseMapEntry(Map<String,Integer> map, String key) {
		if (map.containsKey(key)) {
			map.put(key, map.get(key) + 1);
		} else {
			map.put(key, 1);
		}
	}

	private static int countOccurrences(String string, String subString) {
		int c = 0;
		int p = -1;
		while ((p = string.indexOf(subString, p+1)) > -1) c++;
		return c;
	}

}
