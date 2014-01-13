package ch.tkuhn.memetools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class MakeInput {

	public enum Source { aps, pmc };

	@Parameter(names = "-s", description = "Data source; one of: aps, pmc", required = true)
	private Source source;

	@Parameter(names = "-d", description = "Data path")
	private String dataPath = "data";

	private static String apsMetadataFolder = "aps-dataset-metadata";
	private static String apsAbstractFolder = "aps-abstracts";
	private static String apsCitationFile = "aps-dataset-citations/citing_cited.csv";

	private static String pmcListFile = "PMC-ids.csv";
	private static String pmcXmlPath = "pmcoa-xml";

	public static final void main(String[] args) {
		MakeInput obj = new MakeInput();
		JCommander jc = new JCommander(obj);
		try {
			jc.parse(args);
		} catch (ParameterException ex) {
			jc.usage();
			System.exit(1);
		}
		obj.run();
	}

	public MakeInput() {
	}

	public void run() {
		if (source == Source.aps) {
			System.out.println("Making APS input...");
			// TODO
		} else if (source == Source.pmc) {
			System.out.println("Making PMC input...");
			// TODO
		}
	}

}
