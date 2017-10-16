package com.capitalone.dashboard.collector;

import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XMLToJSONConvertor {

	public static final int PRETTY_PRINT_INDENT_FACTOR = 4;
	public static final String TEST_XML_STRING = "<?xml version=\"1.0\" ?><test attrib=\"moretest\">Turn this to JSON</test>";

	private static final Logger LOGGER = LoggerFactory.getLogger(XMLToJSONConvertor.class);

	public static String convertXmlToJSON(String text) {

		org.json.JSONObject xmlJSONObj = XML.toJSONObject(text);
		String json = xmlJSONObj.toString(PRETTY_PRINT_INDENT_FACTOR);
		LOGGER.debug(json);
		return json;

	}

}
