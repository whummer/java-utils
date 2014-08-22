package io.hummer.util.xml;

import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

public class JAXBTypes {

	@XmlRootElement
	public static class ObjectList {
		@XmlElement(name="item")
		public List<? extends Object> items = new LinkedList<Object>();
		public ObjectList() {}
		public ObjectList(List<? extends Object> items) {
			this.items = items;
		}
		
	}
	
}
