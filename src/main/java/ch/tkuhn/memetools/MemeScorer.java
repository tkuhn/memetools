package ch.tkuhn.memetools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MemeScorer {

	public static final int FAST_SCREEN_MODE = 0;
	public static final int DECOMPOSED_SCREEN_MODE = 1;
	public static final int GIVEN_TERMLIST_MODE = 2;

	private int mode;

	private Map<String,Integer> f;

	private int t;

	private Map<String,Integer> mm;
	private Map<String,Integer> m;
	private Map<String,Integer> xm;

	private Map<String,Boolean> termBeginnings;

	private Map<String,Boolean> terms;

	public MemeScorer(int mode) {
		init();
		this.mode = mode;
		termBeginnings = new HashMap<String,Boolean>();
		if (mode != FAST_SCREEN_MODE) {
			terms = new HashMap<String,Boolean>();
		}
	}

	public MemeScorer(MemeScorer termShareObject, int mode) {
		init();
		this.mode = mode;
		terms = termShareObject.terms;
		termBeginnings = termShareObject.termBeginnings;
	}

	private void init() {
		f = new HashMap<String,Integer>();
		t = 0;
		mm = new HashMap<String,Integer>();
		m = new HashMap<String,Integer>();
		xm = new HashMap<String,Integer>();
	}

	public void clear() {
		f.clear();
		t = 0;
		mm.clear();
		m.clear();
		xm.clear();
	}

	public Map<String,Boolean> getTerms() {
		return terms;
	}

	public Map<String,Integer> getF() {
		return f;
	}

	public int getF(String term) {
		if (!f.containsKey(term)) return 0;
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
		if (!mm.containsKey(term)) return 0;
		return mm.get(term);
	}

	public Map<String,Integer> getM() {
		return m;
	}

	public int getM(String term) {
		if (!m.containsKey(term)) return 0;
		return m.get(term);
	}

	public Map<String,Integer> getXM() {
		return xm;
	}

	public int getXM(String term) {
		if (!xm.containsKey(term)) return 0;
		return xm.get(term);
	}

	public int getX(String term) {
		if (!m.containsKey(term)) return t;
		return t - m.get(term);
	}

	public double[] calculateMemeScoreValues(String term, int delta) {
		return calculateMemeScoreValues(getMM(term), getM(term), getXM(term), getX(term), getF(term), delta);
	}

	public static double[] calculateMemeScoreValues(int mmVal, int mVal, int xmVal, int xVal, int fVal, int delta) {
		int t = xVal + mVal;
		double rfVal = (double) fVal / t;
		double stick = (double) mmVal / (mVal + delta);
		double spark = (double) (xmVal + delta) / (xVal + delta);
		double ps = stick / spark;
		double ms = rfVal * ps;
		return new double[] {stick, spark, ps, ms};
	}

	public double[] calculateMemeScoreValues(String term, int delta, float gamma) {
		return calculateMemeScoreValues(getMM(term), getM(term), getXM(term), getX(term), getF(term), delta, gamma);
	}

	public static double[] calculateMemeScoreValues(int mmVal, int mVal, int xmVal, int xVal, int fVal, int delta, float gamma) {
		int t = xVal + mVal;
		double rfVal = (double) fVal / t;
		double stick = (double) mmVal / (mVal + delta);
		double spark = (double) (xmVal + delta) / (xVal + delta);
		double ps = stick / spark;
		double ms = Math.pow(rfVal, 2*gamma) * Math.pow(ps, 2*(1-gamma));
		return new double[] {stick, spark, ps, ms};
	}

	public void screenTerms(DataEntry d) {
		screenTerms(d, null);
	}

	public void screenTerms(DataEntry d, Set<String> collectTerms) {
		if (mode != FAST_SCREEN_MODE && mode != DECOMPOSED_SCREEN_MODE) {
			throw new RuntimeException("Not in screen mode");
		}
		recordStickingTerms(d, true, collectTerms);
	}

	private void recordStickingTerms(DataEntry d, boolean screening, Set<String> collectTerms) {
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
				if (!screening) {
					if (ignoreTermsStartingWith(t)) break;
					if (ignoreTerm(t)) continue;
				}
				String post = "   ";
				if (p2 < tokens.length-1) post = tokens[p2+1] + " ";
				if (processed.containsKey(t)) continue;
				if (allCited.contains(term)) {
					int c = countOccurrences(allCited, term);
					if (countOccurrences(allCited, pre + term) < c && countOccurrences(allCited, term + post) < c) {
						if (mode == DECOMPOSED_SCREEN_MODE && screening) {
							terms.put(t, true);
						} else {
							increaseMapEntry(mm, t);
						}
						if (collectTerms != null) collectTerms.add(t);
						processed.put(t, true);
					} else if (screening) {
						termBeginnings.put(t, true);
					}
				} else {
					break;
				}
			}
		}
	}

	public void addTerm(String term) {
		if (mode != GIVEN_TERMLIST_MODE) {
			throw new RuntimeException("In screen mode");
		}
		terms.put(term, true);
		String[] parts = term.split(" ");
		String beginning = "";
		for (int i = 0 ; i < parts.length-1 ; i++) {
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
		recordTerms(d, null);
	}

	public void recordTerms(DataEntry d, List<String> collectMemesList) {
		t++;
		if (mode != FAST_SCREEN_MODE) {
			recordStickingTerms(d, false, null);
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
				if (collectMemesList != null) {
					collectMemesList.add(s);
				}
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
		if (mode == FAST_SCREEN_MODE) {
			return !mm.containsKey(term) && !termBeginnings.containsKey(term);
		} else {
			return !terms.containsKey(term) && !termBeginnings.containsKey(term);
		}
	}

	private boolean ignoreTerm(String term) {
		if (mode == FAST_SCREEN_MODE) {
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
