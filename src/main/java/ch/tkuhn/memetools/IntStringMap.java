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
		int l = value.length();
		if (sb.length() + l >= MAX_POS) {
			data.add(sb.toString());
			sb = new StringBuilder();
		}
		long s = MAX_POS*data.size() + sb.length();
		sb.append(value);
		if (s < 0) {
			throw new RuntimeException("Negative start position");
		}
		if (l < 0) {
			throw new RuntimeException("Negative length");
		}
		startPos[key] = s;
		length[key] = l;
	}

	public String get(int key) {
		if (!frozen) {
			throw new RuntimeException("Not yet frozen");
		}
		long s = startPos[key];
		if (s < 0) {
			throw new RuntimeException("Negative start position");
		}
		if (s == 0) return null;
		int l = length[key];
		if (l < 0) {
			throw new RuntimeException("Negative length");
		}
		int subpos = (int) (s % MAX_POS);
		int subposEnd = subpos + l;
		if (subpos < 0) {
			throw new RuntimeException("Negative sub-position start");
		}
		if (subposEnd < 0) {
			throw new RuntimeException("Negative sub-position end");
		}
		return data.get((int) (s / MAX_POS)).substring(subpos, subposEnd);
	}

	public void freeze() {
		frozen = true;
		data.add(sb.toString());
		sb = null;
	}

}
