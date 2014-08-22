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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Element;

/**
 * TODO: Merge with com.ibm.haifa.tecos.jaxb.InvocationRequest from TeCoS project..!
 * 
 * @author whummer
 */
public class InvocationRequest {

	public final RequestType type;
	public final Object body;
	public final List<Element> soapHeaders;
	public final List<String> httpHeaders;
	public boolean cache = false;
	public boolean timeout = true;

	public InvocationRequest(RequestType type, Object requestBody) {
		this(type, requestBody, new LinkedList<Element>(), new LinkedList<String>());
	}
	@SuppressWarnings("all")
	public InvocationRequest(RequestType type, Object requestBody, List<?> headers) {
		this(type, requestBody, headers, new LinkedList<String>());
	}
	@SuppressWarnings("all")
	public InvocationRequest(RequestType type, Object requestBody, List<?> headers1, List<?> headers2) {
		this.body = requestBody;
		this.type = type;
		this.soapHeaders = new LinkedList<Element>();
		this.httpHeaders = new LinkedList<String>();
		for(List<?> headers : Arrays.asList(headers1, headers2)) {
			if(headers != null && headers.size() > 0) {
				if(headers.get(0) instanceof Element)
					soapHeaders.addAll((List<Element>)headers);
				else if(headers.get(0) instanceof String)
					httpHeaders.addAll((List<String>)headers);
				else
					throw new RuntimeException("Unexpected type of headers list, expected Strings or Elements: " + headers);
			}
		}
	}

	public Element getBodyAsElement() throws Exception {
		return getAsElement(body);
	}
	private Element getAsElement(Object o) throws Exception {
		if(o instanceof Element)
			return (Element)o;
		if(o instanceof List<?> && ((List<?>)o).size() == 1) {
			Object o1 = ((List<?>)o).get(0);
			return getAsElement(o1);
		}
		if(o instanceof String)
			return new XMLUtil().toElement((String)o);
		throw new Exception("Cannot convert to Element: " + o);
	}
	
	@Override
	public int hashCode() {
		int code = 0;
		//if(target != null) code += target.hashCode();
		if(body != null) code += body.hashCode();
		if(soapHeaders != null) code += soapHeaders.hashCode();
		if(httpHeaders != null) code += httpHeaders.hashCode();
		if(type != null) code += type.hashCode();
		return code;
	}

	@Override
	public boolean equals(Object o) {
		if(!(o instanceof InvocationRequest))
			return false;
		InvocationRequest r = (InvocationRequest)o;
		boolean eq = true;
		//if(target != null) eq &= target.equals(r.target);
		if(body != null) eq &= body.equals(r.body);
		if(soapHeaders != null) eq &= soapHeaders.equals(r.soapHeaders);
		if(httpHeaders != null) eq &= httpHeaders.equals(r.httpHeaders);
		if(type != null) eq &= type.equals(r.type);
		return eq;
	}
	
}
