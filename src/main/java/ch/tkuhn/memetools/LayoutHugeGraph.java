package ch.tkuhn.memetools;

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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import ch.tkuhn.memetools.PrepareWosData.WosEntry;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class LayoutHugeGraph {

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

	private File logFile;

	public static final void main(String[] args) {
		LayoutHugeGraph obj = new LayoutHugeGraph();
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
	private int missingPoints;

	private Random random;
	private BufferedWriter writer;
	private Set<FileVisitOption> walkFileTreeOptions;

	public LayoutHugeGraph() {
	}

	public void run() {
		try {
			init();
			readBasePoints();
			retrieveMorePoints(3);
			retrieveMorePoints(2);
			retrieveMorePoints(1);
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

		pointsX = new float[150000000];
		pointsY = new float[150000000];

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

	private static final String idPattern = "^\\s*<node id=\"([0-9]{9})\".*$";
	private static final String coordPattern = "^\\s*<viz:position x=\"(.*?)\" y=\"(.*?)\".*$";

	private void readBasePoints() throws IOException {
		log("Reading base points from gext file: " + inputFile);
		BufferedReader reader = new BufferedReader(new FileReader(inputFile), 64*1024);
		int errors = 0;
		String line;
		String id = null;
		while ((line = reader.readLine()) != null) {
			if (line.matches(idPattern)) {
				if (id != null) {
					errors++;
					logDetail("No coordinates found for: " + id);
				}
				id = line.replaceFirst(idPattern, "$1");
			} else if (line.matches(coordPattern)) {
				if (id == null) {
					errors++;
					logDetail("No ID found for coordinates: " + line);
				} else {
					float posX = Float.parseFloat(line.replaceFirst(coordPattern, "$1"));
					float posY = Float.parseFloat(line.replaceFirst(coordPattern, "$2"));
					addPosition(id, posX, posY);
					id = null;
				}
			}
		}
//		points = morePoints;
//		morePoints = new HashMap<String,Point>();
		if (id != null) {
			errors++;
			logDetail("No coordinates found for: " + id);
		}
		reader.close();
		log("Number of errors: " + errors);
	}

	private void retrieveMorePoints(final int minConnections) throws IOException {
		log("Retrieving points from " + rawWosDataDir + " with at least " + minConnections + " connections ...");
		missingPoints = 0;
		Files.walkFileTree(rawWosDataDir.toPath(), walkFileTreeOptions, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
				if (path.toString().endsWith(".txt")) {
					retrieveMorePoints(path, minConnections);
				}
				return FileVisitResult.CONTINUE;
			}
		});
//		log("Additinal points found: " + morePoints.size());
		log("Points still missing: " + missingPoints);
//		points.putAll(morePoints);
//		morePoints.clear();
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
			int idInt = Integer.parseInt(entry.getId());
			if (pointsX[idInt] != 0) continue;
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
				float posX = (float) (sumX / neighbors.size() + random.nextGaussian() * noise);
				float posY = (float) (sumY / neighbors.size() + random.nextGaussian() * noise);
				addPosition(entry.getId(), posX, posY);
			} else {
				missingPoints++;
			}
		}
		reader.close();
		log("Number of errors: " + errors);
	}

	private void addPosition(String id, float posX, float posY) throws IOException {
//		morePoints.put(id, new Point(posX, posY));
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


//	private static class Point {
//
//		float x, y;
//
//		Point(float x, float y) {
//			this.x = x;
//			this.y = y;
//		}
//
//	}

}
