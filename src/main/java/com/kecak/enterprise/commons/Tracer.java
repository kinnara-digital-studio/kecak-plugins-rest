package com.kecak.enterprise.commons;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Tracer {
	/**
	 * 
	 * @param childName	: Child Name 
	 * @param parent 	: Parent Node
	 * @return
	 */
	public static Node xmlFindChild(String path, Node parent) {
		int index = path.indexOf(".");
		String myName = path.substring(0, index);
		
		return null;
	}
}
