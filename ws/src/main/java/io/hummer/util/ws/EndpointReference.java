/*
 * Project 'WS-Aggregation':
 * http://www.infosys.tuwien.ac.at/prototype/WS-Aggregation/
 *
 * Copyright 2010 Vienna University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.hummer.util.ws;

import io.hummer.util.ws.AbstractNode;
import io.hummer.util.xml.XMLUtil;

import java.io.Serializable;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.namespace.QName;

import org.w3c.dom.Element;

@XmlRootElement(name="EndpointReference", namespace=EndpointReference.NS_WS_ADDRESSING)
public class EndpointReference implements Serializable {
	private static final long serialVersionUID = 6799123345155368837L;

	public static final String NS_WS_ADDRESSING = "http://schemas.xmlsoap.org/ws/2004/08/addressing";

	private static final XMLUtil xmlUtil = new XMLUtil();

	@XmlElement(name="Address", namespace=NS_WS_ADDRESSING)
	private String Address;
	@XmlElement(name="ServiceName", namespace=NS_WS_ADDRESSING, type=EPRServiceName.class)
	private EPRServiceName ServiceName;
	@XmlElement(name="feature")
	private String feature;
	@XmlElement(name="ReferenceParameters", namespace=NS_WS_ADDRESSING)
	private ReferenceParamsOrProps ReferenceParameters;
	@XmlElement(name="ReferenceProperties", namespace=NS_WS_ADDRESSING)
	private ReferenceParamsOrProps ReferenceProperties;
	
	@XmlRootElement(name="To", namespace=EndpointReference.NS_WS_ADDRESSING)
	public static class WSAddressingTo {
		public WSAddressingTo() {}
		public WSAddressingTo(String to) {
			value = to;
		}
		@XmlValue
		public String value;
	}
	
	@XmlRootElement(name="ParamOrProps", namespace=EndpointReference.NS_WS_ADDRESSING)
	public static class ReferenceParamsOrProps {
		@XmlAnyElement
		private List<Object> elements = new LinkedList<Object>();

		public Element getByName(QName q) {
			return getByName(q.getNamespaceURI(), q.getLocalPart());
		}
		public Element getByName(String localPart) {
			return getByName(null, localPart);
		}
		public Element getByName(String namespace, String localPart) {
			for(Object o : elements) {
				if(o instanceof Element) {
					Element e = (Element)o;
					if(localPart != null && e.getLocalName() != null && localPart.equals(e.getLocalName())) {
						if(namespace == null || (e.getNamespaceURI() != null && 
									namespace.equals(e.getNamespaceURI()))) {
							return e;
						}
					}
				} else {
					throw new RuntimeException();
				}
			}
			return null; 
		}
		
		@XmlTransient
		public List<Object> getElements() {
			return elements;
		}
		public void setElements(List<Object> elements) {
			this.elements = elements;
		}
	}
	
	@XmlRootElement(name="ServiceName", namespace=EndpointReference.NS_WS_ADDRESSING)
	public static class EPRServiceName {
		@XmlAttribute(name="PortName")
		private String PortName;
		@XmlValue
		private QName ServiceName;
		
		public EPRServiceName() {}
		public EPRServiceName(QName ServiceName, String portName) {
			this.ServiceName = ServiceName;
			this.PortName = portName;
		}

		@XmlTransient
		public String getPortName() {
			return PortName;
		}
		@XmlTransient
		public QName getServiceName() {
			return ServiceName;
		}
		public void setPortName(String portName) {
			PortName = portName;
		}
		public void setServiceName(QName serviceName) {
			ServiceName = serviceName;
		}
		@Override
		public int hashCode() {
			int hc = 0;
			if(ServiceName != null)
				hc += ServiceName.hashCode();
			if(PortName != null)
				hc += PortName.hashCode();
			return hc;
		}
		@Override
		public boolean equals(Object o) {
			if(o == null)
				return getServiceName() == null && getPortName() == null;
			if(!(o instanceof EPRServiceName))
				return false;
			EPRServiceName other = (EPRServiceName)o;
			boolean eq = true;
			if(getPortName() != null)
				eq &= getPortName().equals(other.getPortName());
			if(getServiceName() != null)
				eq &= getServiceName().equals(other.getServiceName());
			return eq;
		}
		@Override
		public String toString() {
			return ServiceName + ":" + PortName;
		}
	}

	/**
	 * default constructor required by JAXB, should not be used by the programmer..
	 */
	@Deprecated
	public EndpointReference() {
	}

	public EndpointReference(Element element) throws Exception {
		if(!element.getTagName().contains("EndpointReference")) {
			element = xmlUtil.changeRootElementName(element, 
					"wsaNS1:EndpointReference xmlns:wsaNS1=\"" + NS_WS_ADDRESSING + "\"");
		}
		
		EndpointReference epr = xmlUtil.toJaxbObject(EndpointReference.class, element, true);
		ServiceName = new EPRServiceName();
		if(epr.ServiceName != null) 
			ServiceName = epr.ServiceName;
		if(epr.feature != null) 
			feature = epr.feature;
		if(epr.Address != null) 
			Address = epr.Address;
		if(epr.ReferenceParameters != null) {
			ReferenceParameters = new ReferenceParamsOrProps();
			ReferenceParameters.elements.addAll(epr.ReferenceParameters.elements);
		}
		if(epr.ReferenceProperties != null) {
			ReferenceProperties = new ReferenceParamsOrProps();
			ReferenceProperties.elements.addAll(epr.ReferenceProperties.elements);
		}
	}
	
	public List<Element> getAllReferenceParameters() {
		if(ReferenceParameters == null)
			return new LinkedList<Element>();
		List<Element> list = new LinkedList<Element>();
		for(Object o : ReferenceParameters.elements)
			list.add((Element)o);
		return list;
	}
	public List<Element> getAllReferenceProperties() {
		if(ReferenceProperties == null)
			return new LinkedList<Element>();
		List<Element> list = new LinkedList<Element>();
		for(Object o : ReferenceProperties.elements)
			list.add((Element)o);
		return list;
	}
	public Element getPropOrParamByName(QName name) {
		if(ReferenceParameters != null) {
			Element e = ReferenceParameters.getByName(name);
			if(e != null)
				return e;
		}
		if(ReferenceProperties != null) {
			Element e = ReferenceProperties.getByName(name);
			if(e != null)
				return e;
		}
		return null;
	}
	public void addReferenceParameter(Element e) {
		if(ReferenceParameters == null)
			ReferenceParameters = new ReferenceParamsOrProps();
		ReferenceParameters.elements.add(e);
	}
	public void addReferenceProperty(Element e) {
		if(ReferenceProperties == null)
			ReferenceProperties = new ReferenceParamsOrProps();
		ReferenceProperties.elements.add(e);
	}

	public boolean containsAllPropsAndParams(EndpointReference epr) {
		List<Element> allMine = new LinkedList<Element>();
		allMine.addAll(getAllReferenceParameters());
		allMine.addAll(getAllReferenceProperties());
		List<Element> allTheirs = new LinkedList<Element>();
		allTheirs.addAll(epr.getAllReferenceParameters());
		allTheirs.addAll(epr.getAllReferenceProperties());
		for(Element theirs : allTheirs) {
			boolean contained = false;
			for(Element mine : allMine) {
				if(elementsEqual(theirs, mine)) {
					contained = true;
					break;
				}
			}
			if(!contained)
				return false;
		}
		return true;
	}
	private boolean elementsEqual(Element e1, Element e2) {
		if(!e1.getLocalName().equals(e2.getLocalName()))
			return false;
		if(e1.getTextContent() != null && !e1.getTextContent().equals(e2.getTextContent()))
			return false;
		return true;
	}

	public EndpointReference(URL location) {
		Address = location.toExternalForm();
	}

	public EndpointReference(String element) throws Exception {
		this(xmlUtil.toElement(element));
	}

	public EndpointReference(EndpointReference epr) throws Exception {
		this(xmlUtil.toElement(epr));
	}

	@XmlTransient
	public String getAddress() {
		if(Address != null)
			return Address;
		return null;
	}

	@XmlTransient
	public String getPortName() {
		if(ServiceName != null && ServiceName.PortName != null)
			return ServiceName.PortName;
		return null;
	}

	@XmlTransient
	public EPRServiceName getServiceName() {
		return ServiceName;
	}
	
	public void setAddress(String address) {
		Address = address;
	}
	public void setServiceName(EPRServiceName serviceName) {
		ServiceName = serviceName;
	}
	
	public Element toElement() throws Exception {
		return xmlUtil.toElement(this);
	}

	@XmlTransient
	public String getFeature() {
		return feature;
	}
	public void setFeature(String feature) {
		this.feature = feature;
	}
	
	@XmlTransient
	public ReferenceParamsOrProps getReferenceParameters() {
		return ReferenceParameters;
	}
	public void setReferenceParameters(ReferenceParamsOrProps referenceParameters) {
		ReferenceParameters = referenceParameters;
	}

	@XmlTransient
	public ReferenceParamsOrProps getReferenceProperties() {
		return ReferenceProperties;
	}
	public void setReferenceProperties(ReferenceParamsOrProps referenceProperties) {
		ReferenceProperties = referenceProperties;
	}
	

	@Override
	public String toString() {
		try {
			Element el = toElement();
			if(el == null)
				return null;
			return xmlUtil.toString(el);
		} catch (Exception e) {
			return null;
		}
	}
	public String toString(String elementName) throws Exception {
		return xmlUtil.toString(toElement(elementName));
	}
	public Element toElement(String elementName) throws Exception {
		Element el = toElement();
		return xmlUtil.changeRootElementName(el, elementName);
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof AbstractNode)
			o = ((AbstractNode)o).getEPR();
		if(!(o instanceof EndpointReference)) {
			return false;
		}
		EndpointReference epr = (EndpointReference)o;
		boolean eq = true;
		if(getAddress() != null)
			eq &= getAddress().equals(epr.getAddress());
		if(getPortName() != null)
			eq &= getPortName().equals(epr.getPortName());
		if(getServiceName() != null)
			eq &= getServiceName().equals(epr.getServiceName());
		eq &= containsAllPropsAndParams(epr);
		eq &= epr.containsAllPropsAndParams(this);
		return eq;
	}
	
	@Override
	public int hashCode() {
		int hc = 0;
		if(getAddress() != null)
			hc += getAddress().hashCode();
		if(getPortName() != null)
			hc += getPortName().hashCode();
		if(getServiceName() != null)
			hc += getServiceName().hashCode();
		return hc;
	}
	
	/*public static void main(String[] args) throws Exception {
		RequestInput i = new RequestInput();
		EndpointReference epr = new EndpointReference(
				"<wsa:EndpointReference xmlns:wsa=\"" + NS_WS_ADDRESSING + "\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<wsa:Address>address</wsa:Address>" +
					"<wsa:ServiceName PortName=\"AggregatorNodePort\">" +
						"tns:AggregatorNodeService" +
					"</wsa:ServiceName>" +
					"<wsa:ReferenceParameters1>" +
					"</wsa:ReferenceParameters1>" +
					"<wsa:ReferenceParameters>" +
						"<elements>bar</elements>" +
					"</wsa:ReferenceParameters>" +
					"<wsa:ReferenceProperties>" +
						"<elements>bar</elements>" +
					"</wsa:ReferenceProperties>" +
				"</wsa:EndpointReference>");
		i.serviceEPR = epr;
		System.out.println(epr);
		epr.addReferenceParameter(at.ac.tuwien.infosys.util.Util.getInstance().toElement("<elements>foo</elements>"));
		System.out.println(epr);
		System.out.println(epr.toString("foobar"));
		epr = new EndpointReference(at.ac.tuwien.infosys.util.Util.getInstance().toElement(epr));
		System.out.println(epr);
		System.out.println(epr.getReferenceParameters());
		System.out.println(at.ac.tuwien.infosys.util.Util.getInstance().toString(epr));
		System.out.println(at.ac.tuwien.infosys.util.Util.getInstance().toString(i));
		System.out.println(at.ac.tuwien.infosys.util.Util.getInstance().toString(epr.getServiceName()));
		System.out.println(epr.getServiceName().getPortName());
	}*/
	
}
