package eu.deustotech.rdf2subdue.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.RDFReader;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.vocabulary.RDF;

class ModelLoader extends Thread {

	Thread t;
	
	Model model;
	File fileEntry;
	String uri;
	
	public ModelLoader(Model model, File fileEntry, String uri) {
		this.model = model;
		this.fileEntry = fileEntry;
		this.uri = uri;
	}
	
	@Override
	public void run() {
		RDFReader reader = model.getReader();
		InputStream in;
		try {
			in = new FileInputStream(fileEntry.toString());
			reader.read(this.model, in, this.uri);
			in.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
}

public class RDF2Subdue {
	
	//private static String INPUT_DIR = "/home/mikel/doctorado/src/rdf2subdue/models.bak";
	//private static String NAMESPACE_URI = "http://acm/";
	//private static String OUTPUT_FILE = "/home/mikel/doctorado/src/java/rdf2subdue-java/RDF2Subdue/graphs/acm.g";
	//private static String TDB_DIR = "tdb";
	
	public static void main(String args[]) {
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd - HH:mm:ss");
		Logger logger = LoggerFactory.getLogger(RDF2Subdue.class);
		
		//Loading Props
		Properties configFile = new Properties();
		InputStream in;
		try {
			in = new FileInputStream(args[0]);
			configFile.load(in);
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		String inputDir = configFile.getProperty("INPUT_DIR");
		String namespaceURI = configFile.getProperty("NAMESPACE_URI");
		String outputDir = configFile.getProperty("OUTPUT_DIR");
		String tdbDir = configFile.getProperty("TDB_DIR");
		
		// Loading model
		logger.info(String.format("[%s] Loading model...", sdf.format(System.currentTimeMillis())));
		//Model model = ModelFactory.createDefaultModel();
		Dataset dataset = TDBFactory.createDataset(tdbDir);
		Model model = dataset.getDefaultModel();
		model.add(loadModel(inputDir, namespaceURI));
		logger.info(String.format("[%s] Model loaded!", sdf.format(System.currentTimeMillis())));
		
		//Retrieving subjects
		logger.info(String.format("[%s] Retrieving subjects", sdf.format(System.currentTimeMillis())));
		List<Resource> subjects = model.listSubjectsWithProperty(RDF.type).toList();
		logger.info(String.format("[%s] %s subjects found!", sdf.format(System.currentTimeMillis()), subjects.size()));
		
		//Retrieving objects
		logger.info(String.format("[%s] Retrieving objects", sdf.format(System.currentTimeMillis())));
		List<RDFNode> objects = model.listObjects().toList();
		objects.removeAll(subjects);
		logger.info(String.format("[%s] %s Objects found!", sdf.format(System.currentTimeMillis()), objects.size()));
		
		logger.info(String.format("[%s] Generationg and writing nodes to output file...", sdf.format(System.currentTimeMillis())));
		List<RDFNode> nodes = new ArrayList<RDFNode>();
		BufferedWriter out = null;
		try {
			FileWriter fstream = new FileWriter(outputDir);
			out = new BufferedWriter(fstream);
			int i = 1;
			for (RDFNode subject : subjects) {
				List<RDFNode> types = model.listObjectsOfProperty(subject.asResource(), RDF.type).toList();
				String type = types.get(0).toString();
				out.write(String.format("v %s \"%s\"\n", i, type));
				nodes.add(subject);
				i++;
			}
			for (RDFNode object : objects) {
				String type = "";
				if (object.isLiteral()) {
					type = "Literal";
				} else {
					type = "URI";
				}
				out.write(String.format("v %s \"%s\"\n", i, type));
				nodes.add(object);
				i++;
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		logger.info(String.format("[%s] Generating and writing edges to output file...", sdf.format(System.currentTimeMillis())));
		for (RDFNode subject : subjects) {
			int origin = nodes.indexOf(subject) + 1;
			List<Statement> statementList = model.listStatements(subject.asResource(), (Property)null, (RDFNode)null).toList();
			for (Statement stmt : statementList) {
				int destination;
				if (!stmt.getPredicate().equals(RDF.type)) {
					destination = nodes.indexOf(stmt.getObject()) + 1;
					try {
						out.write(String.format("e %s %s \"%s\"\n", origin, destination, stmt.getPredicate().toString()));
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		
		try {
			out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		model.close();
		logger.info(String.format("[%s] Finished!", sdf.format(System.currentTimeMillis())));
	}

	private static Model loadModel(String inputDir, String namespaceURI) {
		Model model = ModelFactory.createDefaultModel();
		List<ModelLoader> modelPool = new ArrayList<ModelLoader>();
		
		File folder = new File(inputDir);
		for (File fileEntry : folder.listFiles()) {
			
			Model tempModel = ModelFactory.createDefaultModel();
			ModelLoader ml = new ModelLoader(tempModel, fileEntry, namespaceURI);
			ml.start();
			
			modelPool.add(ml);
			
		}
		
		for (ModelLoader ml : modelPool) {
			try {
				ml.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		for (ModelLoader ml : modelPool) {
			model.add(ml.model);
		}
		
		return model;		
	}

}
