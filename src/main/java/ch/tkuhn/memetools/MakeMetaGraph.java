package ch.tkuhn.memetools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.tkuhn.vilagr.GraphIterator;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class MakeMetaGraph {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

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
	private Map<String,Map<String,Integer>> typeEdges = new HashMap<String,Map<String,Integer>>();

	public MakeMetaGraph() {
	}

	public void run() throws IOException {
		init();

		log("Calculating meta-graph...");
		GraphIterator ei = new GraphIterator(inputFile, new GraphIterator.GraphHandler() {

			@Override
			public void handleNode(String nodeId, Map<String,String> atts) throws Exception {
				String type = atts.get(typeAtt);
				nodeTypes.put(nodeId, type);
				if (typeCount.containsKey(type)) {
					typeCount.put(type, typeCount.get(type) + 1);
				} else {
					typeCount.put(type, 1);
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

		log("Writing GML file...");
		BufferedWriter w = new BufferedWriter(new FileWriter(outputFile));
		w.write("graph [\n");
		w.write("directed 1\n");
		for (String t : typeCount.keySet()) {
			w.write("node [\n");
			w.write("id \"" + t + "\"\n");
			w.write("weight " + typeCount.get(t) + "\n");
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

	private void init() {
		logFile = new File(MemeUtils.getLogDir(), "make-meta-graph.log");
		if (outputFile == null) {
			String filename = inputFile.getPath().replaceAll("\\..*$", "") + "-meta";
			outputFile = new File(filename + ".gml");
		}
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

}
