package ch.tkuhn.memetools;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.supercsv.io.CsvListReader;

import ch.tkuhn.vilagr.GraphIterator;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class MakeMetaGraph {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-l", description = "Label file")
	private File labelFile;

	@Parameter(names = "-g", description = "Output file (in GML format)")
	private File outputFile;

	@Parameter(names = "-m", description = "Output file for type map")
	private File typeMapFile;

	@Parameter(names = "-t", description = "Name of type attribute that partitions the graph")
	private String typeAtt = "type";

	@Parameter(names = "-i", description = "Ignore citations within the same type")
	private boolean ignoreWithinCitations = false;

	private File logFile;

	public static final void main(String[] args) {
		MakeMetaGraph obj = new MakeMetaGraph();
		JCommander jc = new JCommander(obj);
		try {
			jc.parse(args);
		} catch (ParameterException ex) {
			jc.usage();
			System.exit(1);
		}
		if (obj.parameters.size() != 1) {
			System.err.println("ERROR: Exactly one main argument is needed");
			jc.usage();
			System.exit(1);
		}
		obj.inputFile = new File(obj.parameters.get(0));
		try {
			obj.run();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private Map<String,String> nodeTypes = new HashMap<String,String>();
	private Map<String,Integer> typeCount = new HashMap<String,Integer>();
	private Map<String,Pair<Float,Float>> typeCoords = new HashMap<String,Pair<Float,Float>>();
	private Map<String,Color> typeColors = new HashMap<String,Color>();
	private Map<String,Map<String,Integer>> typeEdges = new HashMap<String,Map<String,Integer>>();
	private Map<String,String> labelMap = new HashMap<String,String>();

	public MakeMetaGraph() {
	}

	public void run() throws IOException {
		init();

		final BufferedWriter typeMapWriter;
		if (typeMapFile != null) {
			typeMapWriter = new BufferedWriter(new FileWriter(typeMapFile));
		} else {
			typeMapWriter = null;
		}

		log("Calculating meta-graph...");
		GraphIterator ei = new GraphIterator(inputFile, new GraphIterator.GraphHandler() {

			@Override
			public void handleNode(String nodeId, Pair<Float,Float> coords, Color color, Map<String,String> atts) throws Exception {
				String type = atts.get(typeAtt);
				nodeTypes.put(nodeId, type);
				if (typeCount.containsKey(type)) {
					typeCount.put(type, typeCount.get(type) + 1);
				} else {
					typeCount.put(type, 1);
				}
				if (typeCoords.containsKey(type)) {
					Pair<Float,Float> c = typeCoords.get(type);
					typeCoords.put(type, Pair.of(c.getLeft() + coords.getLeft(), c.getRight() + coords.getRight()));
				} else  {
					typeCoords.put(type, coords);
				}
				if (color != null) {
					typeColors.put(type, color);
				}
				if (typeMapWriter != null) {
					typeMapWriter.write(nodeId + " " + type + "\n");
				}
			}

			@Override
			public void handleEdge(String nodeId1, String nodeId2) throws Exception {
				String type1 = nodeTypes.get(nodeId1);
				if (type1 == null) {
					throw new RuntimeException("No type found for node: " + type1);
				}
				String type2 = nodeTypes.get(nodeId2);
				if (type2 == null) {
					throw new RuntimeException("No type found for node: " + type2);
				}
				if (!typeEdges.containsKey(type1)) {
					typeEdges.put(type1, new HashMap<String,Integer>());
				}
				Map<String,Integer> m = typeEdges.get(type1);
				if (m.containsKey(type2)) {
					m.put(type2, m.get(type2) + 1);
				} else {
					m.put(type2, 1);
				}
			}

		});
		ei.run();
		if (typeMapWriter != null) {
			typeMapWriter.close();
		}

		if (outputFile != null) {
			log("Writing GML file...");
			BufferedWriter w = new BufferedWriter(new FileWriter(outputFile));
			w.write("graph [\n");
			w.write("directed 1\n");
			List<String> nodes = new ArrayList<String>(typeCount.keySet());
			Collections.sort(nodes, new Comparator<String>() {
				@Override
				public int compare(String o1, String o2) {
					return typeCount.get(o2) - typeCount.get(o1);
				}
			});
			for (String t : nodes) {
				int count = typeCount.get(t);
				w.write("node [\n");
				w.write("id \"" + t + "\"\n");
				w.write("weight " + count + "\n");
				w.write("graphics [\n");
				float x = typeCoords.get(t).getLeft() / count;
				float y = typeCoords.get(t).getRight() / count;
				w.write("center [ x " + x + " y " + y + " ]\n");
				w.write("w " + Math.sqrt(count) + "\n");
				w.write("h " + Math.sqrt(count) + "\n");
				Color color = typeColors.get(t);
				if (color != null) {
					w.write("fill \"#" + String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()) + "\"\n");
				}
				w.write("]\n");
				if (labelMap.containsKey(t)) {
					w.write("label \"" + labelMap.get(t) + "\"\n");
				}
				w.write("]\n");
			}
			for (String t1 : typeEdges.keySet()) {
				for (String t2 : typeEdges.get(t1).keySet()) {
					if (ignoreWithinCitations && t1.equals(t2)) {
						continue;
					}
					int c = typeEdges.get(t1).get(t2);
					w.write("edge [\n");
					w.write("source \"" + t1 + "\"\n");
					w.write("target \"" + t2 + "\"\n");
					w.write("weight " +c + "\n");
					w.write("]\n");
				}
			}
			w.write("]\n");
			w.close();
		}
	}

	private void init() {
		logFile = new File(MemeUtils.getLogDir(), "make-meta-graph.log");
		if (labelFile != null) {
			try {
				readLabels();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	private void readLabels() throws IOException {
		BufferedReader r = new BufferedReader(new FileReader(labelFile));
		CsvListReader csvReader = new CsvListReader(r, MemeUtils.getCsvPreference());
		List<String> line;
		while ((line = csvReader.read()) != null) {
			labelMap.put(line.get(0), line.get(1));
		}
		csvReader.close();
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

}
