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
package io.hummer.util.ws.request;

import io.hummer.util.xml.XMLUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.w3c.dom.Element;

@XmlRootElement
public class InvocationResult {

	private Map<String,List<String>> httpHeaders = new HashMap<String, List<String>>();
	/** The result of the invocation, possibly converted to an XML element or similar. */
	private Object result;
	/** The original, unmodified result received from the invocation. */
	private Object verbatimResult;
	@XmlTransient
	private XMLUtil xmlUtil = new XMLUtil();

	public InvocationResult(Object result) {
		this.result = result;
	}
	public InvocationResult(Object result, Object verbatimResult) {
		this.result = result;
		this.verbatimResult = verbatimResult;
	}
	
	/** required by JAXB, should not be used by the programmer.. */
	@Deprecated
	public InvocationResult() { }
	
	public Element getResultAsElement() {
		if(result instanceof Element)
			return (Element)result;
		try {
			return xmlUtil.toElement((String)result);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public Element getResponseBody() {
		Element e = (Element)result;
		List<Element> children = xmlUtil.getChildElements(e, "Body");
		if(children.size() > 0)
			e = children.get(0);
		List<Element> l = xmlUtil.getChildElements(e);
		if(l.isEmpty())
			return null;
		return l.get(0);
	}

	public List<String> getHeader(String name) {
		return httpHeaders.get(name);
	}
	public Map<String,List<String>> getHeaders() {
		return httpHeaders;
	}
	public Object getResult() {
		return result;
	}
	public void setResult(Object result) {
		this.result = result;
	}
	public Object getVerbatimResult() {
		return verbatimResult;
	}
	public void setVerbatimResult(Object verbatimResult) {
		this.verbatimResult = verbatimResult;
	}
}
