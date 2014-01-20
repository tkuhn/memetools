package ch.tkuhn.memetools;

import java.io.File;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class PrepareWosData {

	private File logFile;

	public static final void main(String[] args) {
		PrepareWosData obj = new PrepareWosData();
		JCommander jc = new JCommander(obj);
		try {
			jc.parse(args);
		} catch (ParameterException ex) {
			jc.usage();
			System.exit(1);
		}
		obj.run();
	}

	// Data format: semicolon-delimited with the following columns:
	//
    // 0 t9 (9 last digits)
    // 1 year
    // 2 documentType (one letter)
    // 3 doi (optional)
    // 4 subject (two letters, optional)
    // 5 iso journal (optional)
    // 6 volume
    // 7 issue
    // 8 pages
    // 9 title
    // 10 noOfAuthors
    // 11.. authors
    // 11+noOfAuthors noOfJournals
    // 12+noOfAuthors... journals (other journal labels)
    // 12+noOfAuthors+noOfJournals summary (optional)
    // 13+noOfAuthors+noOfJournals references (non-delimited t9)
    // 14+noOfAuthors+noOfJournals citations (non-delimited t9)

	public PrepareWosData() {
	}

	public void run() {
		init();
		log("Finished");
	}

	private void init() {
		logFile = new File(MemeUtils.getLogDir(), "prepare-aps.log");
		log("==========");
		log("Starting...");
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

}
