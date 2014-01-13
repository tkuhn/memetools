package ch.tkuhn.memetools;

public class PreparePmcData {

	public static void run(PrepareData superProcess) {
		PreparePmcData obj = new PreparePmcData(superProcess);
		obj.run();
	}


	private static String pmcListFile = "PMC-ids.csv";
	private static String pmcXmlPath = "pmcoa-xml";

	private PrepareData s;

	public PreparePmcData(PrepareData superProcess) {
		this.s = superProcess;
	}

	public void run() {
		log("Starting...");
		// TODO;
	}

	private void log(String text) {
		s.log(text);
	}

}
