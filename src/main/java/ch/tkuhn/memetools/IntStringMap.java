package ch.tkuhn.memetools;

import java.util.ArrayList;
import java.util.List;

public class IntStringMap {

	private static final int MAX_POS = 2000000000;

	private List<String> data = new ArrayList<String>();
	private long[] startPos;
	private int[] length;

	private StringBuilder sb = new StringBuilder();

	private boolean frozen = false;

	public IntStringMap(int size) {
		startPos = new long[size];
		length = new int[size];
		sb.append(" ");
	}

	public void put(int key, String value) {
		if (frozen) {
			throw new RuntimeException("Frozen");
		}
		long s = MAX_POS*data.size() + sb.length();
		int l = value.length();
		sb.append(value);
		startPos[key] = s;
		length[key] = l;
		if (sb.length() > MAX_POS) {
			data.add(sb.toString());
			sb = new StringBuilder();
		}
	}

	public String get(int key) {
		if (!frozen) {
			throw new RuntimeException("Not yet frozen");
		}
		long s = startPos[key];
		if (s == 0) return null;
		int l = length[key];
		int subpos = (int) (s % MAX_POS);
		return data.get((int) (s / MAX_POS)).substring(subpos, subpos + l);
	}

	public void freeze() {
		frozen = true;
		data.add(sb.toString());
		sb = null;
	}

}
