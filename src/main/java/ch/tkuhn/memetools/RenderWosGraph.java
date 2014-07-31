package ch.tkuhn.memetools;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.tuple.Pair;
import org.supercsv.io.CsvListReader;

import ch.tkuhn.memetools.PrepareWosData.WosEntry;
import ch.tkuhn.vilagr.GraphDrawer;
import ch.tkuhn.vilagr.GraphIterator;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class RenderWosGraph {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-d", description = "The directory to read the raw data from")
	private File rawWosDataDir;

	@Parameter(names = "-sj", description = "File with the subject mappings")
	private File subjFile;

	@Parameter(names = "-cs", description = "Color scheme for subjects: oecdtop|oecdphys")
	private String colorScheme;

	@Parameter(names = "-s", description = "Size of output bitmap")
	private int size = 10000;

	@Parameter(names = "-sc", description = "Scaling factor")
	private float scale = 0.5f;

	@Parameter(names = "-ds", description = "Size of dots")
	private int dotSize = 4;

	@Parameter(names = "-v", description = "Write detailed log")
	private boolean verbose = false;

	@Parameter(names = "-na", description = "Node alpha")
	private float nodeAlpha = 0.065f;

	@Parameter(names = "-ea", description = "Edge alpha")
	private float edgeAlpha = 0.002f;

	private File logFile;

	public static final void main(String[] args) {
		RenderWosGraph obj = new RenderWosGraph();
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

	private Map<String,Byte> subjectMap;
	private byte[] categories;
	private Map<Byte,Color> colorMap;

	private float[] pointsX;
	private float[] pointsY;

	private GraphDrawer graphDrawer;

	private Set<FileVisitOption> walkFileTreeOptions;

	public RenderWosGraph() {
	}

	public void run() {
		try {
			init();
			readSubjects();
			readNodes();
			drawEdges();
			drawNodes();
			writeImage();
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

		if (outputFile == null) {
			outputFile = new File(MemeUtils.getOutputDataDir(), getOutputFileName() + ".png");
		}
		if (rawWosDataDir == null) {
			rawWosDataDir = new File(MemeUtils.getRawDataDir(), wosFolder);
		}

		if (subjFile != null) {
			subjectMap = new HashMap<String,Byte>();
			categories = new byte[150000000];
			colorMap = new HashMap<Byte,Color>();
			colorMap.put((byte) 0, new Color(123, 123, 123, (int) (nodeAlpha * 255)));
			colorMap.put((byte) 1, new Color(255, 0, 0, (int) (nodeAlpha * 255)));
			colorMap.put((byte) 2, new Color(0, 255, 0, (int) (nodeAlpha * 255)));
			colorMap.put((byte) 3, new Color(0, 0, 255, (int) (nodeAlpha * 255)));
			colorMap.put((byte) 4, new Color(255, 255, 0, (int) (nodeAlpha * 255)));
			colorMap.put((byte) 5, new Color(0, 255, 255, (int) (nodeAlpha * 255)));
			colorMap.put((byte) 6, new Color(255, 0, 255, (int) (nodeAlpha * 255)));
		}

		pointsX = new float[150000000];
		pointsY = new float[150000000];

		graphDrawer = new GraphDrawer(size);
		graphDrawer.setTransformation(0, scale, true);
		graphDrawer.setEdgeAlpha(edgeAlpha);
		graphDrawer.setNodeSize(dotSize);

		walkFileTreeOptions = new HashSet<FileVisitOption>();
		walkFileTreeOptions.add(FileVisitOption.FOLLOW_LINKS);
	}

	private void readSubjects() throws IOException {
		if (subjFile == null) return;
		readSubjectMap();
		log("Reading subjects from file: " + subjFile);
		BufferedReader r = new BufferedReader(new FileReader(subjFile), 64*1024);
		int progress = 0;
		String line;
		int[] total = new int[7];
		while ((line = r.readLine()) != null) {
			logProgress(progress);
			progress++;
			if (!line.matches("[0-9]+;[A-Z]+")) continue;
			String[] split = line.split(";", -1);
			int id = Integer.parseInt(split[0]);
			String cats = split[1];
			int[] c = new int[7];
			while (cats.length() > 1) {
				String cat = cats.substring(0, 2);
				cats = cats.substring(2);
				if (subjectMap.containsKey(cat)) {
					c[subjectMap.get(cat)]++;
				}
			}
			byte mainCat = 0;
			int mainCatC = 0;
			for (byte bc = 1; bc < 7; bc++) {
				if (c[bc] > mainCatC) {
					mainCatC = c[bc];
					mainCat = bc;
				} else if (c[bc] == mainCatC) {
					mainCat = 0;
				}
			}
			categories[id] = mainCat;
			total[mainCat]++;
		}
		r.close();
		for (int i = 0; i < 7; i++) {
			log("Nodes with category " + i + ": " + total[i]);
		}
	}

	private void readSubjectMap() throws IOException {
		File subjMapFile = new File(MemeUtils.getPreparedDataDir(), "wos-subjects.csv");
		BufferedReader r = new BufferedReader(new FileReader(subjMapFile), 64*1024);
		CsvListReader csvReader = new CsvListReader(r, MemeUtils.getCsvPreference());
		csvReader.read(); // ignore header
		List<String> line;
		while ((line = csvReader.read()) != null) {
			String wosCat = line.get(0);
			String oecdCat = line.get(5);
			byte oecdTop = Byte.parseByte(oecdCat.substring(0, 1));
			byte cat = 0;
			if ("oecdtop".equals(colorScheme)) {
				cat = oecdTop;
			} else if ("oecdphys".equals(colorScheme)) {
				if (oecdTop == 1 || oecdTop == 4) cat = 3;
				if (oecdTop == 2) cat = 6;
				if (oecdTop == 3) cat = 1;
				if (oecdTop == 5 || oecdTop == 6) cat = 2;
				if ("1.3".equals(oecdCat)) cat = 5;
			}
			subjectMap.put(wosCat, cat);
		}
		csvReader.close();
	}

	private void readNodes() throws IOException {
		log("Processing nodes from input file: " + inputFile);
		GraphIterator gi = new GraphIterator(inputFile, new GraphIterator.GraphHandler() {

			@Override
			public void handleNode(String nodeId, Pair<Float,Float> coords, Color color, Map<String,String> atts) throws Exception {
				int id = Integer.parseInt(nodeId);
				pointsX[id] = coords.getLeft();
				pointsY[id] = coords.getRight();
				
			}

			@Override
			public void handleEdge(String arg0, String arg1) throws Exception {}

		});
		gi.setEdgeHandlingEnabled(false);
		gi.run();
	}

	private void drawNodes() {
		log("Drawing nodes...");
		Color color = new Color(0, 0, 255, (int) (nodeAlpha * 255));
		int progress = 0;
		for (int i = 0 ; i < 150000000 ; i++) {
			logProgress(progress);
			progress++;
			float x = pointsX[i];
			if (pointsX[i] == 0) continue;
			float y = pointsY[i];
			if (categories != null) {
				color = colorMap.get(categories[i]);
			}
			graphDrawer.drawNode(x, y, color);
		}
	}

	private void drawEdges() throws IOException {
		log("Drawing edges from " + rawWosDataDir + " ...");
		Files.walkFileTree(rawWosDataDir.toPath(), walkFileTreeOptions, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
				if (path.toString().endsWith(".txt")) {
					processEdgesFromFile(path);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		graphDrawer.finishEdgeDrawing();
	}

	private void processEdgesFromFile(Path path) throws IOException {
		log("Reading file to collect edges: " + path);
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
			int id1 = entry.getIdInt();
			if (pointsX[id1] == 0) continue;
			String neighborIds = entry.getRef();
			while (!neighborIds.isEmpty()) {
				String nId = neighborIds.substring(0, 9);
				int id2 = Integer.parseInt(nId);
				neighborIds = neighborIds.substring(9);
				if (pointsX[id2] == 0) continue;
				// draw line
				graphDrawer.recordEdge(pointsX[id1], pointsY[id1], pointsX[id2], pointsY[id2]);
			}
		}
		reader.close();
		log("Number of errors: " + errors);
	}

	private void writeImage() throws IOException {
		log("Writing image to" + outputFile + " ...");
		ImageIO.write(graphDrawer.getImage(), "png", outputFile);
	}

	private String getOutputFileName() {
		return "im-" + inputFile.getName().replaceAll("\\..*$", "");
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

	private void logProgress(int p) {
		if (p % 1000000 == 0) log(p + "...");
	}

}
