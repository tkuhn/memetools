package ch.tkuhn.memetools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.rio.RDFHandler;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.helpers.RDFHandlerBase;
import org.openrdf.rio.ntriples.NTriplesParser;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class ExtractWikipediaTerms {

	@Parameter(description = "start-categories", required = true)
	private List<String> startCategories = new ArrayList<String>();

	@Parameter(names = "-d", description = "Depth of sub-categories to include (0 = no sub-categories)")
	private int depth = 0;

	private File logFile;

	public static final void main(String[] args) {
		ExtractWikipediaTerms obj = new ExtractWikipediaTerms();
		JCommander jc = new JCommander(obj);
		try {
			jc.parse(args);
		} catch (ParameterException ex) {
			jc.usage();
			System.exit(1);
		}
		try {
			obj.run();
		} catch (Throwable th) {
			obj.log(th);
			System.exit(1);
		}
	}

	private static final URI broader = new URIImpl("http://www.w3.org/2004/02/skos/core#broader");
	private static final URI subject = new URIImpl("http://purl.org/dc/terms/subject");
	private static final URI redirects = new URIImpl("http://dbpedia.org/ontology/wikiPageRedirects");
	private static final URI label = new URIImpl("http://www.w3.org/2000/01/rdf-schema#label");
	private static final String categoryPrefix = "http://dbpedia.org/resource/Category:";

	private Map<String,Boolean> categories = new HashMap<String,Boolean>();
	private Map<String,Boolean> newCategories = new HashMap<String,Boolean>();
	private Map<String,Boolean> terms = new HashMap<String,Boolean>();

	private int labelCount;

	public ExtractWikipediaTerms() {
	}

	public void run() throws Exception {
		logFile = new File(MemeUtils.getLogDir(), "extract-wikipedia.log");
		log("==========");

		for (String sc : startCategories) {
			categories.put(categoryPrefix + sc, true);
		}
		log("Number of start categories: " + categories.size());

		for (int i = 0 ; i < depth ; i++) {
			log("Loading subcategories at depth " + (i+1) + "...");
			handleDbpediaData("skos_categories_en.nt", new RDFHandlerBase() {
				@Override
				public void handleStatement(Statement st) throws RDFHandlerException {
					if (!st.getPredicate().equals(broader)) return;
					if (categories.containsKey(st.getObject().toString())) {
						newCategories.put(st.getSubject().toString(), true);
					}
				}
			});
			for (String s : newCategories.keySet()) {
				categories.put(s, true);
			}
			newCategories.clear();
			log("Number of categories: " + categories.size());
		}

		log("Loading articles...");
		handleDbpediaData("article_categories_en.nt", new RDFHandlerBase() {
			@Override
			public void handleStatement(Statement st) throws RDFHandlerException {
				if (!st.getPredicate().equals(subject)) return;
				if (categories.containsKey(st.getObject().toString())) {
					terms.put(st.getSubject().toString(), true);
				}
			}
		});
		log("Number of terms: " + terms.size());

		log("Loading redirects...");
		handleDbpediaData("redirects_transitive_en.nt", new RDFHandlerBase() {
			@Override
			public void handleStatement(Statement st) throws RDFHandlerException {
				if (!st.getPredicate().equals(redirects)) return;
				if (terms.containsKey(st.getObject().toString())) {
					terms.put(st.getSubject().toString(), true);
				}
			}
		});
		log("Number of terms: " + terms.size());

		log("Retrieving and writing labels...");
		File outputFile = new File(MemeUtils.getOutputDataDir(), "wikipedia-terms.txt");
		final BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
		labelCount = 0;
		handleDbpediaData("labels_en.nt", new RDFHandlerBase() {
			@Override
			public void handleStatement(Statement st) throws RDFHandlerException {
				if (!st.getPredicate().equals(label)) return;
				if (terms.containsKey(st.getSubject().toString())) {
					try {
						writer.write(st.getObject().stringValue() + "\n");
						labelCount++;
					} catch (IOException ex) {
						ex.printStackTrace();
					}
				}
			}
		});
		writer.close();
		log("Number of labels: " + labelCount);

		log("Finished");
	}

	private void handleDbpediaData(String fileName, RDFHandler rdfHandler) throws Exception {
		NTriplesParser subcatParser = new NTriplesParser();
		File subcatFile = new File(MemeUtils.getRawDataDir(), "dbpedia/" + fileName);
		BufferedReader subcatReader = new BufferedReader(new FileReader(subcatFile), 64*1024);
		subcatParser.setRDFHandler(rdfHandler);
		subcatParser.parse(subcatReader, "");
		subcatReader.close();
	}

	private void log(Object obj) {
		MemeUtils.log(logFile, obj);
	}

}
