package ch.tkuhn.memetools;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.supercsv.io.CsvListReader;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class RenderGraph {

	@Parameter(description = "input-file", required = true)
	private List<String> parameters = new ArrayList<String>();

	private File inputFile;

	@Parameter(names = "-o", description = "Output file")
	private File outputFile;

	@Parameter(names = "-d", description = "The directory to read the raw data from")
	private File rawWosDataDir;

	@Parameter(names = "-s", description = "Size of output bitmap")
	private int size = 10000;

	@Parameter(names = "-sc", description = "Scaling factor")
	private float scale = 0.5f;

	@Parameter(names = "-ds", description = "Size of dots")
	private int dotSize = 5;

	private File logFile;

	public static final void main(String[] args) {
		RenderGraph obj = new RenderGraph();
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

	private BufferedImage image;

	public RenderGraph() {
	}

	public void run() {
		try {
			init();
			processNodes();
			processEdges();
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
		image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
	}

	private void processNodes() throws IOException {
		log("Reading base points from gext file: " + inputFile);
		image.getGraphics().setColor(new Color(255,0,0,16));
		BufferedReader r = new BufferedReader(new FileReader(inputFile), 64*1024);
		CsvListReader csvReader = new CsvListReader(r, MemeUtils.getCsvPreference());
		List<String> line;
		while ((line = csvReader.read()) != null) {
			float x = Float.parseFloat(line.get(1));
			float y = Float.parseFloat(line.get(2));
			image.getGraphics().fillOval((int) (x*scale - dotSize/2.0), (int) (y*scale - dotSize/2.0), dotSize, dotSize);
		}
		csvReader.close();
	}

	private void processEdges() {
		// TODO
	}

	private void writeImage() throws IOException {
		log("Writing image to" + outputFile + " ...");
		ImageIO.write(image, "png", outputFile);
	}

	private String getOutputFileName() {
		return "im-" + inputFile.getName().replaceAll("\\..*$", "");
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

}
