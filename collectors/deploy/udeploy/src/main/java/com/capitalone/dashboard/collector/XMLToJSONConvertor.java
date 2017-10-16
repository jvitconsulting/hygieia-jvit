package com.capitalone.dashboard.collector;

//import java.io.File;
//import java.time.Instant;
//import java.time.OffsetDateTime;
//import java.time.format.DateTimeFormatter;
//import java.time.temporal.TemporalAccessor;
//import java.util.Date;
//import java.util.Scanner;

import org.json.XML;
//import org.json.simple.JSONArray;
//import org.json.simple.JSONObject;
//import org.json.simple.parser.JSONParser;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

public class XMLToJSONConvertor {

	public static final int PRETTY_PRINT_INDENT_FACTOR = 4;
	public static final String TEST_XML_STRING = "<?xml version=\"1.0\" ?><test attrib=\"moretest\">Turn this to JSON</test>";

	//private static final Logger LOGGER = LoggerFactory.getLogger(XMLToJSONConvertor.class);

//	public static void main(String[] args) {
//		try {
//			
//			Scanner scanner = new Scanner(new File("src/main/resources/sample.xml"));
//			String text = scanner.useDelimiter("\\A").next();
//			scanner.close(); // Put this call in a finally block
//			JSONObject parse = (JSONObject) new JSONParser().parse(convertXmlToJSON(text));
//			Object object = parse.get("buildTypes");
//			if (object instanceof JSONObject) {
//				JSONObject jsonObject = (JSONObject) object;
//				JSONObject object2 = (JSONObject) jsonObject.get("buildType");
//			} else if (object instanceof JSONArray) {
//				LOGGER.info("XMLToJSONConvertor.main()");
//			}
//		} catch (Exception je) {
//			LOGGER.error(je.toString());
//		}
//	}

	public static String convertXmlToJSON(String text) {
		org.json.JSONObject xmlJSONObj = XML.toJSONObject(text);
		String jsonPrettyPrintString = xmlJSONObj.toString(PRETTY_PRINT_INDENT_FACTOR);
		 //LOGGER.info(jsonPrettyPrintString);
		return jsonPrettyPrintString;
	}

}
