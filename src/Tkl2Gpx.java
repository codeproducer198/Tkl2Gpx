import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.latitude.gpsmaster.tkl.TKLHandler;
import com.latitude.gpsmaster.tkl.TKLSessionSummary;
import com.latitude.gpsmaster.tkl.TKLTrackPoint;

public class Tkl2Gpx {
	// set this value if you have pauses in your track. put here your storing
	// interval of your GPS position in seconds
	// (my is every 4 seconds a pos is stored)
	private static final int PAUSE_TIME_INDICATOR = 4 * 1000;

	// creater is set as root attribute
	private static final String CREATOR = "Mapjack GPS Watch";

	private static final String LINE_BREAK = "\n";

	private static final String NS_GPXTPX = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1";

	private final Path input;
	private final Path outputFolder;
	private int filecount = 0;
	private boolean hasHR;

	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			System.err.println("Usage: tkl2gpx <filename or folder> <output-folder>");
			return;
		}

		Path input = Paths.get(args[0]);
		Tkl2Gpx t2g = new Tkl2Gpx(input, args[1]);

		if (Files.isDirectory(input)) {
			Files.walk(input).filter(Files::isRegularFile).filter(p -> p.toFile().getName().endsWith(".tkl"))
					.forEach(p -> t2g.convert(p));
			System.out.println("Process " + t2g.filecount + " files.");
		} else {
			t2g.convert(input);
		}
	}

	public Tkl2Gpx(Path input, String outputFolder) {
		this.input = input;
		this.outputFolder = Paths.get(outputFolder).toAbsolutePath();

		if (!Files.isDirectory(Paths.get(outputFolder))) {
			throw new RuntimeException(outputFolder + " is not a folder!");
		}
	}

	public void convert(Path file) {
		try {
			System.out.println("Processing " + file.toFile().getAbsoluteFile());

			String newFileName = file.toFile().getName() + ".gpx";

			// convert the data
			TKLHandler tkl = new TKLHandler(file.toFile().getAbsolutePath());
			ArrayList<TKLTrackPoint> trackPtList = tkl.getTrackPointList();

			if (trackPtList.size() == 0) {
				System.out.println("This file is empty");
			}

			hasHR = tkl.getSessionSummary().getMaxHR() != 0;

			// Java bullshit
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			docFactory.setNamespaceAware(true);
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

			// root elements
			Document doc = docBuilder.newDocument();

			Element rootElement = doc.createElement("gpx");
			rootElement.setAttribute("xmlns", "http://www.topografix.com/GPX/1/1");
			rootElement.setAttribute("xmlns:gpxtpx", NS_GPXTPX);
			doc.appendChild(rootElement);

			rootElement.setAttribute("creator", CREATOR);

			// meta data
			Element meta = doc.createElement("metadata");
			rootElement.appendChild(meta);

			addElement(doc, meta, "name", newFileName);
			addElement(doc, meta, "time", trackPtList.get(0).getGPXDateTimeString());
			addElement(doc, meta, "desc", getSummary(tkl));

			// GPS trace
			Element trk = doc.createElement("trk");
			rootElement.appendChild(trk);

			// create first segment
			Element trkseg = doc.createElement("trkseg");
			trk.appendChild(trkseg);

			long lastTime = trackPtList.get(0).getDateTime();

			int i = 0;
			int pause = 0;
			for (TKLTrackPoint tp : trackPtList) {
				long time = tp.getDateTime();
				if (time - lastTime > PAUSE_TIME_INDICATOR) {
					trkseg = doc.createElement("trkseg");
					trk.appendChild(trkseg);

					pause++;
				}
				lastTime = time;

				Element tpt = doc.createElement("trkpt");
				trkseg.appendChild(tpt);

				// Position
				tpt.setAttribute("lat", tp.getLatitude() + "");
				tpt.setAttribute("lon", tp.getLongitude() + "");

				// number
				addElement(doc, tpt, "desc", ++i);

				// hight
				Element ele = doc.createElement("ele");
				ele.appendChild(doc.createTextNode(tp.getAltitude(true) + ""));
				tpt.appendChild(ele);

				// Time
				addElement(doc, tpt, "time", tp.getGPXDateTimeString());

				// Sat
				addElement(doc, tpt, "sat", tp.getSatelliteNumber());

				tpt.appendChild(createExtensions(doc, tp));
			}

			System.out.println("Found " + pause + " pause.");

			// full input
			String fullInput = file.toAbsolutePath().toString();
			String inputFolder = input.toAbsolutePath().toString();
			Path sepPath = Paths.get(fullInput.replace(inputFolder, "")).getParent();
			File out;
			if (sepPath != null) {
				out = new File(outputFolder + "/" + sepPath + "/" + newFileName);
			} else {
				out = new File(outputFolder + "/" + newFileName);
			}

			// More java bullshit
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			transformerFactory.setAttribute("indent-number", 1);
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			DOMSource source = new DOMSource(doc);
			createFolder(out);

			StreamResult result = new StreamResult(out);
			transformer.transform(source, result);

			System.out.println("Finished " + out.getAbsolutePath());

			filecount++;
		} catch (ParserConfigurationException | TransformerException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void createFolder(File out) throws IOException {
		File folder = out.getParentFile();

		if (!folder.exists()) {
			Files.createDirectories(folder.toPath());
		}
	}

	private Element addElement(Document doc, Element parent, String elementName, int value) {
		Element e = doc.createElement(elementName);
		e.appendChild(doc.createTextNode(String.valueOf(value)));
		parent.appendChild(e);

		return e;
	}

	private Element addElement(Document doc, Element parent, String elementName, String value) {
		Element e = doc.createElement(elementName);
		e.appendChild(doc.createTextNode(value));
		parent.appendChild(e);

		return e;
	}

	private Element addElement(Document doc, Element parent, String elementName, float value, int scale) {
		Element e = doc.createElement(elementName);
		e.appendChild(doc.createTextNode(Float.toString(round(value, scale))));
		parent.appendChild(e);

		return e;
	}

	private Element createHr(Document doc, int heartRate) {
		Element tpe = doc.createElement("gpxtpx:TrackPointExtension");

		Element e = doc.createElement("gpxtpx:hr");
		e.appendChild(doc.createTextNode(String.valueOf(heartRate)));
		tpe.appendChild(e);

		return tpe;
	}

	private Element createExtensions(Document doc, TKLTrackPoint tp) {
		Element extension = doc.createElement("extensions");

		if (hasHR) {
			extension.appendChild(createHr(doc, tp.getHeartRate()));
		}
		addElement(doc, extension, "distance", tp.getTotalDistance() / 1000f, 2);
		addElement(doc, extension, "speed", tp.getSpeed(), 1);

		return extension;
	}

	private float round(float value, int scale) {
		return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).floatValue();
	}

	private String getSummary(TKLHandler tkl) {
		StringBuilder builder = new StringBuilder();

		TKLSessionSummary s = tkl.getSessionSummary();

		builder.append("Anzahl Runden              : " + s.getNumberOfLaps() + LINE_BREAK);
		builder.append("Trainingszeit (Std)        : " + s.getWorkoutTimeString() + LINE_BREAK);
		builder.append("Gesamtdistanz (km)         : " + round(s.getDistance() / 1000, 2) + LINE_BREAK);
		builder.append("Pace AVG (min/km)          : " + s.getPaceString() + LINE_BREAK);
		builder.append("Geschwindigkeit AVG (km/h) : " + round(s.getAverageSpeed(), 1) + LINE_BREAK);
		builder.append("Geschwindigkeit MAX (km/h) : " + round(s.getMaxSpeed(), 1) + LINE_BREAK);
		builder.append("Kalorien (kcal)            : " + round(s.getCalories(), 1) + LINE_BREAK);
		if (hasHR) {
			builder.append("Herzfrequenz MIN           : " + s.getMinHRString() + LINE_BREAK);
			builder.append("Herzfrequenz AVG           : " + s.getHeartRateString() + LINE_BREAK);
			builder.append("Herzfrequenz MAX           : " + s.getMaxHRString() + LINE_BREAK);
		} else {
			builder.append("Herzfrequenz               : nicht aufgezeichnet "+ LINE_BREAK);
		}

		return builder.toString();
	}
}
