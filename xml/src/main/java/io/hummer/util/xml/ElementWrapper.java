package io.hummer.util.xml;

import org.w3c.dom.Element;


public class ElementWrapper {

	public final Element element;
	private String asString;
	private Integer size;
	
	public ElementWrapper(Element e) {
		element = e;
	}

	public Element getElement() {
		return element;
	}

	public int getSize() {
		if(size == null && asString() != null) {
			size = asString().length();
		}
		return size;
	}
	
	public String asString() {
		try {
			if(asString == null) {
				asString = new XMLUtil().toString(element);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return asString;
	}
	
}
