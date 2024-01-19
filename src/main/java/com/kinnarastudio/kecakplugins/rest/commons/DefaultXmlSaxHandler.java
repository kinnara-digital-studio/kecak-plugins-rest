package com.kinnarastudio.kecakplugins.rest.commons;

import java.util.regex.Pattern;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public abstract class DefaultXmlSaxHandler extends DefaultHandler {
	private Pattern recordPattern;
	
	private String currentPath = "";
	private String currentQName = "";
	private boolean isRecordPathFound = false;
	
	/**
	 * @param recordPattern
	 * @param valuePattern
	 * @param labelPattern
	 * @param rowSet : output parameter, the record set being built
	 */
	public DefaultXmlSaxHandler(Pattern recordPattern) {
		this.recordPattern = recordPattern;
	}
	
	@Override
	public final void startElement(String uri, String localName, String qName, Attributes attributes)
			throws SAXException {
		currentQName = qName; 
		currentPath += "." + qName;
		if(recordPattern.matcher(currentPath).find()) {
			isRecordPathFound = true;
			onOpeningTag(qName);
		}
	}
	
	@Override
	public final void characters(char[] ch, int start, int length) throws SAXException {
		if(isRecordPathFound) {
			// record path found
			onTagContent(currentQName, currentPath, new String(ch, start, length).trim());
		}
	}
	
	@Override
	public final void endElement(String uri, String localName, String qName) throws SAXException {
		if(isRecordPathFound && recordPattern.matcher(currentPath).find()) {
			onClosingTag(qName);
		}
		currentPath = currentPath.replaceAll("(\\." + qName + "$)|(^" + qName + "$)", "");
	}
	
	protected abstract void onOpeningTag(String recordQName);
	protected abstract void onTagContent(String recordQName, String path, String content);
	protected abstract void onClosingTag(String recordQName);
}
