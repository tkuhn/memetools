package ch.tkuhn.memetools;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

import ch.tkuhn.memetools.PrepareWosData.WosEntry;
import ch.tkuhn.vilagr.GraphIterator;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class LayoutWosGraph {

	@Parameter(description = "gexf-input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-d", description = "The directory to read the raw data from")
	private File rawWosDataDir;

	@Parameter(names = "-v", description = "Write detailed log")
	private boolean verbose = false;

	@Parameter(names = "-n", description = "Noise level (standard deviation of Gaussian distribution)")
	private double noise = 5.0;

	@Parameter(names = "-os", description = "Offset to make all values positive")
	private float offset = 10000;

	private File logFile;

	public static final void main(String[] args) {
		LayoutWosGraph obj = new LayoutWosGraph();
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
		obj.run();
	}

	private static String wosFolder = "wos";

	private float[] pointsX;
	private float[] pointsY;
	private float[] morePointsX;
	private float[] morePointsY;
	private int missingPoints;
	private int additionalPoints;

	private Random random;
	private BufferedWriter writer;
	private Set<FileVisitOption> walkFileTreeOptions;

	public LayoutWosGraph() {
	}

	public void run() {
		try {
			init();
			readBasePoints();
			retrieveMorePoints(10);
			retrieveMorePoints(8);
			retrieveMorePoints(6);
			retrieveMorePoints(4);
			retrieveMorePoints(3);
			retrieveMorePoints(2);
			do {
				retrieveMorePoints(1);
			} while (additionalPoints > 0);
			writer.close();
		} catch (Throwable th) {
			log(th);
			System.exit(1);
		}
		log("Finished");
	}

	private void init() throws IOException {
		logFile = new File(MemeUtils.getLogDir(), getOutputFileName() + ".log");
		log("==========");
		log("Starting...");

		morePointsX = new float[150000000];
		morePointsY = new float[150000000];

		if (outputFile == null) {
			outputFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + ".csv");
		}
		if (rawWosDataDir == null) {
			rawWosDataDir = new File(MemeUtils.getRawDataDir(), wosFolder);
		}

		writer = new BufferedWriter(new FileWriter(outputFile));

		walkFileTreeOptions = new HashSet<FileVisitOption>();
		walkFileTreeOptions.add(FileVisitOption.FOLLOW_LINKS);

		random = new Random(0);
	}

	private void readBasePoints() throws IOException {
		log("Reading base points from gext file: " + inputFile);
		GraphIterator gi = new GraphIterator(inputFile, new GraphIterator.GraphHandler() {

			@Override
			public void handleNode(String nodeId, Pair<Float,Float> coords, Color color, Map<String,String> atts) throws Exception {
				addPosition(nodeId, coords.getLeft() + offset, coords.getRight() + offset);
				
			}

			@Override
			public void handleEdge(String arg0, String arg1) throws Exception {}

		});
		gi.run();
		pointsX = morePointsX;
		pointsY = morePointsY;
		morePointsX = new float[150000000];
		morePointsY = new float[150000000];
	}

	private void retrieveMorePoints(final int minConnections) throws IOException {
		log("Retrieving points from " + rawWosDataDir + " with at least " + minConnections + " connections ...");
		missingPoints = 0;
		additionalPoints = 0;
		Files.walkFileTree(rawWosDataDir.toPath(), walkFileTreeOptions, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
				if (path.toString().endsWith(".txt")) {
					retrieveMorePoints(path, minConnections);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		log("Additional points found: " + additionalPoints);
		log("Points still missing: " + missingPoints);
		for (int i = 0 ; i < morePointsX.length ; i++) {
			pointsX[i] += morePointsX[i];
			morePointsX[i] = 0;
			pointsY[i] += morePointsY[i];
			morePointsY[i] = 0;
		}
	}

	private void retrieveMorePoints(Path path, int minConnections) throws IOException {
		log("Reading file to collect points: " + path);
		BufferedReader reader = new BufferedReader(new FileReader(path.toFile()), 64*1024);
		int errors = 0;
		String line;
		while ((line = reader.readLine()) != null) {
			WosEntry entry;
			if (verbose) {
				entry = new WosEntry(line, logFile);
			} else {
				entry = new WosEntry(line);
			}
			if (!entry.isValid()) {
				errors++;
				continue;
			}
			if (pointsX[entry.getIdInt()] != 0) continue;
			if (morePointsX[entry.getIdInt()] != 0) {
				logDetail("Duplicate id: " + entry.getId());
				errors++;
				continue;
			}
			Set<Integer> neighbors = new HashSet<Integer>();
			String neighborIds = entry.getRef() + entry.getCit();
			while (!neighborIds.isEmpty()) {
				String nId = neighborIds.substring(0, 9);
				int nIdInt = Integer.parseInt(nId);
				neighborIds = neighborIds.substring(9);
				if (pointsX[nIdInt] != 0) {
					neighbors.add(nIdInt);
				}
			}
			if (neighbors.size() >= minConnections) {
				double sumX = 0;
				double sumY = 0;
				for (int n : neighbors) {
					sumX += pointsX[n];
					sumY += pointsY[n];
				}
				final double avgAllX = sumX / neighbors.size();
				final double avgAllY = sumY / neighbors.size();
				double avgX = avgAllX;
				double avgY = avgAllY;
				if (neighbors.size() > 2) {
					// get the 3 points closest to average:
					List<Integer> nlist = new ArrayList<Integer>(neighbors);
					Collections.sort(nlist, new Comparator<Integer>() {
						@Override
						public int compare(Integer o1, Integer o2) {
							double xdiff1 = pointsX[o1] - avgAllX;
							double ydiff1 = pointsY[o1] - avgAllY;
							double dist1 = xdiff1*xdiff1 + ydiff1*ydiff1;
							double xdiff2 = pointsX[o2] - avgAllX;
							double ydiff2 = pointsY[o2] - avgAllY;
							double dist2 = xdiff2*xdiff2 + ydiff2*ydiff2;
							return (int) (dist1 - dist2);
						}
					});
					neighbors.clear();
					for (int i = 0 ; i < 3 ; i++) {
						neighbors.add(nlist.get(i));
					}
					// recalculate average:
					sumX = 0;
					sumY = 0;
					for (int n : neighbors) {
						sumX += pointsX[n];
						sumY += pointsY[n];
					}
					avgX = sumX / neighbors.size();
					avgY = sumY / neighbors.size();
				}
				float posX = (float) (avgX + random.nextGaussian() * noise);
				float posY = (float) (avgY + random.nextGaussian() * noise);
				addPosition(entry, posX, posY);
				additionalPoints++;
			} else {
				missingPoints++;
			}
		}
		reader.close();
		log("Number of errors: " + errors);
	}

	private void addPosition(Object obj, float posX, float posY) throws IOException {
		String id;
		int idInt;
		if (obj instanceof WosEntry) {
			id = ((WosEntry) obj).getId();
			idInt = ((WosEntry) obj).getIdInt();
		} else {
			id = obj.toString();
			idInt = Integer.parseInt(id);
		}
		morePointsX[idInt] = posX;
		morePointsY[idInt] = posY;
		writer.write(id + "," + posX + "," + posY + "\n");
	}

	private String getOutputFileName() {
		return "la-" + inputFile.getName().replaceAll("\\..*$", "");
	}

	private void logDetail(Object obj) {
		if (verbose) log(obj);
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

}
