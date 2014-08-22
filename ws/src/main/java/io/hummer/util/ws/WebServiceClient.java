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

import io.hummer.util.Configuration;
import io.hummer.util.coll.CollectionsUtil;
import io.hummer.util.log.LogUtil;
import io.hummer.util.misc.ExceptionsUtil;
import io.hummer.util.misc.PerformanceInterceptor;
import io.hummer.util.misc.PerformanceInterceptor.EventType;
import io.hummer.util.net.SSLContextInitializer;
import io.hummer.util.par.Parallelization;
import io.hummer.util.persist.IDocumentCache;
import io.hummer.util.persist.IDocumentCache.CacheEntry;
import io.hummer.util.str.StringUtil;
import io.hummer.util.ws.request.*;
import io.hummer.util.xml.XMLUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.net.BindException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.wsdl.Definition;
import javax.wsdl.Port;
import javax.wsdl.WSDLException;
import javax.wsdl.extensions.http.HTTPBinding;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.extensions.soap12.SOAP12Address;
import javax.wsdl.extensions.soap12.SOAP12Binding;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.soap.SOAPBinding;

import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Logger;
import org.w3c.css.sac.CSSException;
import org.w3c.css.sac.CSSParseException;
import org.w3c.css.sac.ErrorHandler;
import org.w3c.dom.Element;

import com.gargoylesoftware.htmlunit.AlertHandler;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.IncorrectnessListener;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.ScriptException;
import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptErrorListener;
import com.ibm.wsdl.Constants;

/**
 * Generic Web service client class which is capable of performing 
 * different types of requests:
 * 
 * - Simple HTTP GET
 * - HTTP POST with custom request body
 * - SOAP (SOAP messages via HTTP POST)
 * 
 * @author Waldemar Hummer
 */
public class WebServiceClient {

	private String endpointURL;
	private QName serviceName;
	private QName portName;
	private Service service;
	private XMLUtil xmlUtil = new XMLUtil();
	private StringUtil strUtil = new StringUtil();
	private List<Element> eprParamsAndProps = new LinkedList<Element>();
	private IDocumentCache cache = new IDocumentCache.DocumentCache();

	private static final int CONNECT_TIMEOUT_MS = 1000*5;
	private static final int READ_TIMEOUT_MS = 1000*60*3;
	private static final int READ_TIMEOUT_VERYLONG_MS = 1000*60*60;
	private static final int READ_TIMEOUT_HTTP_GET_MS = 1000*7;
	private static final int READ_TIMEOUT_HTTP_GET_VERYLONG_MS = 1000*60*60;
	private static Map<EndpointReference, WebServiceClient> clientCache = new HashMap<EndpointReference, WebServiceClient>();
	private static final Logger logger = LogUtil.getLogger(WebServiceClient.class);
	public static final int REQUEST_RETRIES = 0; // TODO: make configurable (?)
	private static final SortedMap<String,AtomicLong> lastRequestedHosts = new TreeMap<String,AtomicLong>();
	
	static {
		try {
			SSLContextInitializer.init();
		} catch (Exception e) {
			logger.warn("Unexpected error.", e);
		}
	}
	
	protected WebServiceClient(EndpointReference epr) {
		setRelevantAttributes(epr);
	}
	
	protected WebServiceClient(URL wsdlLocation, QName serviceName, QName port)
	{
		service = Service.create(wsdlLocation, serviceName);
		if(port != null)
			portName = port;
		else
			portName = service.getPorts().next();
		try {
			Port servicePort = (Port)getSingleWSDLService(wsdlLocation.toExternalForm()).getPorts().values().iterator().next();
			endpointURL = getAddressFromWSDLPort(servicePort);
		} catch (Exception e) {
			//e.printStackTrace();
			//endpointURL = service.getServiceName().toString();
			throw new RuntimeException(e);
		}
	}
	private void setRelevantAttributes(EndpointReference epr) {
		try {
			this.eprParamsAndProps.addAll(epr.getAllReferenceParameters());
			this.eprParamsAndProps.addAll(epr.getAllReferenceProperties());
			if(this.eprParamsAndProps.size() > 0) {
				if(logger.isDebugEnabled()) logger.trace("Reference Params/Props: " + this.eprParamsAndProps);
			}
			
			this.endpointURL = epr.getAddress();
			String binding = SOAPBinding.SOAP11HTTP_BINDING;
			try {
				this.serviceName = epr.getServiceName().getServiceName();
				this.portName = new QName(epr.getPortName());
				service = Service.create(this.serviceName);
				service.addPort(portName, binding, this.endpointURL);
			} catch (Exception e) {
				if(endpointURL.contains("wsdl") || endpointURL.contains("WSDL")) {
					// assume that the given URL is a WSDL URL
					String wsdlURL = endpointURL;
					javax.wsdl.Service wsdlService = getSingleWSDLService(wsdlURL);
					this.serviceName = wsdlService.getQName();
					javax.wsdl.Port port = getDefaultWSDLPort(wsdlService);
					if(port instanceof SOAP12Binding)
						binding = SOAPBinding.SOAP12HTTP_BINDING;
					this.portName = new QName(serviceName.getNamespaceURI(), port.getName());
					this.endpointURL = getAddressFromWSDLPort(port);
					service = Service.create(new URL(wsdlURL), this.serviceName);
				}
			}
		} catch (WSDLException e) {
			throw new RuntimeException("Unable to create Web service client from WSDL.", e);
		} catch (ConnectException e) {
			throw new RuntimeException("Unable to create Web service client.", e);
		} catch (Exception e) {
			// swallow
			logger.info("Error initializing Web service client.", e);
		}
	}
	
	public static WebServiceClient getClient(EndpointReference epr) throws Exception {
		return getClient(epr, false);
	}
	public static WebServiceClient getClient(EndpointReference epr, boolean doCache) throws Exception {
		WebServiceClient client = null;
		if(doCache) {
			synchronized (clientCache) {
				client = clientCache.get(epr);
			}
		}
		if(client != null)
			return client;
		client = createClient(epr);
		if(doCache) {
			clientCache.put(epr, client);
		}
		return client;
	}
	public static WebServiceClient getClient(URL wsdlLocation, QName serviceName) throws Exception {
		WebServiceClient client = new WebServiceClient(wsdlLocation, serviceName, null);
		return client;
	}
	
	private static WebServiceClient createClient(EndpointReference epr) throws Exception {
		return new WebServiceClient(epr);
	}
	
	/**
	 * Performs a standard HTTP_GET invocation to the 
	 * endpoint URL that this client is configured with.
	 */
	public InvocationResult invoke() throws Exception {
		return invoke(new InvocationRequest(RequestType.HTTP_GET, null));
	}

	public InvocationResult invoke(InvocationRequest request) throws Exception {
		long before = System.currentTimeMillis();
		InvocationResult r = invoke(request, REQUEST_RETRIES);
		if(logger.isDebugEnabled()) logger.debug(request.type + " request took " + (System.currentTimeMillis() - before) + "ms");
		return r;
	}
	
	public InvocationResult invoke(InvocationRequest request, int retries) throws Exception {
		
		Map<String, String> httpHeaders = extractHeaders(request.httpHeaders);
		int connectTimeoutMS = CONNECT_TIMEOUT_MS;
		int requestTimeoutMS = request.timeout ? READ_TIMEOUT_MS : READ_TIMEOUT_VERYLONG_MS;
		if(request.body != null) {
			if(request.type == RequestType.SOAP || request.type == RequestType.SOAP11) {
				try {
					return doInvokeSOAP((Element)request.getBodyAsElement(), request.soapHeaders, 
							retries, SOAPConstants.SOAP_1_1_PROTOCOL, connectTimeoutMS, requestTimeoutMS);					
				} catch (Exception e) {
					logger.debug("SOAP invocation failed: " + new ExceptionsUtil().getAllCauses(e));
					throw e;
				}
			}
			if(request.type == RequestType.SOAP12) {
				try {
					return doInvokeSOAP((Element)request.getBodyAsElement(), request.soapHeaders, 
							retries, SOAPConstants.SOAP_1_2_PROTOCOL, connectTimeoutMS, requestTimeoutMS);
				} catch (Exception e) {
					throw e;
				}
			} else if(request.type == RequestType.HTTP_GET) {
				requestTimeoutMS = request.timeout ? READ_TIMEOUT_HTTP_GET_MS : READ_TIMEOUT_HTTP_GET_VERYLONG_MS;
				if(logger.isDebugEnabled()) logger.debug("Request body: " + request.body);
				Object c = request.body;
				if(!(c instanceof String) && c != null) {
					logger.warn("Unexpected tyle " + c.getClass() + " of input body content: ");
					if(c instanceof Element) {
						xmlUtil.print((Element)c);
					} else {
						System.out.println(c);
					}
				}
				return doInvokeGET((String)c, httpHeaders, retries, 
						connectTimeoutMS, requestTimeoutMS, request.cache);
			} else if(request.type == RequestType.HTTP_POST)
				return doInvokePOST(request.body, httpHeaders, retries);
		} else {
			requestTimeoutMS = request.timeout ? READ_TIMEOUT_HTTP_GET_MS : READ_TIMEOUT_HTTP_GET_VERYLONG_MS;
			return doInvokeGET("", httpHeaders, retries, connectTimeoutMS, 
					requestTimeoutMS, request.cache);
		}
		return null;
	}
	
	private void pauseToAvoidSpamming() throws Exception {
		long minIntervalMS = 5000;
		long otherwiseSleepMS = 1500;
		long maxStoredHosts = 20;
		
		String host = new URL(this.endpointURL).getHost();
		synchronized (lastRequestedHosts) {
			if(!lastRequestedHosts.containsKey(host)) {
				lastRequestedHosts.put(host, new AtomicLong(System.currentTimeMillis()));
				return;
			}
		}
		AtomicLong time = lastRequestedHosts.get(host);
		synchronized (time) {
			if((System.currentTimeMillis() - time.get()) < minIntervalMS) {
				logger.info("Sleeping some time to avoid spamming host '" + host + "'");
				Thread.sleep(otherwiseSleepMS);
				time.set(System.currentTimeMillis());
			}
		}
		if(lastRequestedHosts.size() > maxStoredHosts) {
			new CollectionsUtil().removeKeyWithSmallestValue(lastRequestedHosts);
		}
	}

	private Map<String, String> extractHeaders(List<String> in) {
		Map<String, String> result = new HashMap<String, String>();
		
		for(String l : in) {
			int colon = l.indexOf(":");
			if(colon > 0) {
				String key = l.substring(0, colon).trim();
				String value = l.substring(colon + 1).trim();
				result.put(key,value);
			}
		}
		
		return result;
	}
	
	private InvocationResult doInvokeSOAP(Element request, List<Element> headers, int retries, 
			String protocol, int connectTimeoutMS, int requestTimeoutMS) throws Exception {
		if(retries < 0)
			throw new Exception("Invocation to " + endpointURL + " failed: " + xmlUtil.toString(request));
		
		// if the service is null here because the WSDL is unavailable, 
		// create a plain HTTP POST invocation..
		if(service == null) {
			return doInvokePOST(request, new HashMap<String, String>(), retries);
		}
		
		Dispatch<SOAPMessage> dispatch = null;
		synchronized (service) {
			dispatch = service.createDispatch(portName, SOAPMessage.class, Service.Mode.MESSAGE);			
		}

		dispatch.getRequestContext().put("com.sun.xml.ws.connect.timeout", connectTimeoutMS);
		dispatch.getRequestContext().put("com.sun.xml.ws.request.timeout", requestTimeoutMS);
		dispatch.getRequestContext().put("com.sun.xml.internal.ws.connect.timeout", connectTimeoutMS);
		dispatch.getRequestContext().put("com.sun.xml.internal.ws.request.timeout", requestTimeoutMS);
		dispatch.getRequestContext().put(MessageContext.HTTP_REQUEST_HEADERS, 
				Collections.singletonMap("Connection", Collections.singletonList("close")));

		try {
			SOAPMessage message = createSOAPMessage(request, headers, protocol);
			String tmpID = PerformanceInterceptor.event(EventType.START_SEND_INVOCATION);
			SOAPMessage response = dispatch.invoke(message);
			PerformanceInterceptor.event(EventType.FINISH_SEND_INVOCATION, tmpID);
			InvocationResult result = new InvocationResult(response.getSOAPBody());
			return result;
		} catch (Exception e) {
			if(!(e.getCause() instanceof ThreadDeath)) {
				//logger.warn("Exception in invocation; to: " + endpointURL + "; " + e);
			}
			
			if(retries <= 0) {
				throw new Exception("Invocation to " + endpointURL + " failed: " + xmlUtil.toString(request), e);
			}
			if(e instanceof BindException || e.getCause() instanceof BindException) {
				long sleep = 200L + (long)(Math.random() * 800);
				logger.warn("Cannot bind to (client) port, sleeping " + sleep);
				Thread.sleep(sleep);
			}
			request = xmlUtil.clone(request);
			return doInvokeSOAP(request, headers, retries - 1, protocol, connectTimeoutMS, requestTimeoutMS);
		} finally {
			//PerformanceInterceptor.event(EventType.FINISH_INVOCATION, eID);
			//System.out.println(eID + " finished " + endpointURL);
		}
	}
	
	private SOAPMessage createSOAPMessage(Element request, List<Element> headers, String protocol) throws Exception {
		MessageFactory mf = MessageFactory.newInstance(protocol);
		SOAPMessage message = mf.createMessage();
		SOAPBody body = message.getSOAPBody();

		// check if we have a complete soap:Envelope as request..
		String ns = request.getNamespaceURI();
		if(request.getTagName().contains("Envelope")) {
			if(ns.equals("http://schemas.xmlsoap.org/soap/envelope/"))
				message = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL).createMessage(
						new MimeHeaders(), new ByteArrayInputStream(xmlUtil.toString(request).getBytes()));
			if(ns.equals("http://www.w3.org/2003/05/soap-envelope"))
				message = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL).createMessage(
						new MimeHeaders(), new ByteArrayInputStream(xmlUtil.toString(request).getBytes()));
			
		} else {
			xmlUtil.appendChild(body, request);
		}
		for(Element h : headers) {
			xmlUtil.appendChild(message.getSOAPHeader(), h);
		}
		for(Element h : eprParamsAndProps) {
			xmlUtil.appendChild(message.getSOAPHeader(), h);
		}
		xmlUtil.appendChild(message.getSOAPHeader(), xmlUtil.toElement(
				"<wsa:To xmlns:wsa=\"" + EndpointReference.NS_WS_ADDRESSING + "\">" + endpointURL + "</wsa:To>"));
		message.saveChanges();
		return message;
	}
	
	private InvocationResult doInvokePOST(Object request, Map<String,String> httpHeaders, int retries) throws Exception {
		if(retries < 0)
			throw new Exception("Invocation to " + endpointURL + " failed: " + xmlUtil.toString(request));
		logger.debug("POST request to: " + endpointURL + " with body " + request);
		URL url = new URL(endpointURL);
		URLConnection conn = url.openConnection();
		conn.setDoOutput(true);
		for(String key : httpHeaders.keySet()) {
			conn.setRequestProperty(key, httpHeaders.get(key));
		}
		String theRequest = null;
		if(request instanceof Element) {
			theRequest = xmlUtil.toString((Element)request);
			conn.setRequestProperty("Content-Type", "application/xml");
		} else if(request instanceof String) {
			theRequest = (String)request;
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		}
		BufferedWriter w = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream()));
		theRequest = theRequest.trim();
		w.write(theRequest);
		w.close();
		BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		StringBuilder b = new StringBuilder();
		String temp;
		while((temp = r.readLine()) != null) {
			b.append(temp);
			b.append("\n");
		}
		String originalResult = b.toString();
		String result = originalResult.trim();
		if(!result.startsWith("<")) // wrap non-xml results (e.g., CSV files)
			result = "<doc>" + result + "</doc>";
		Element resultElement = xmlUtil.toElement(result);
		InvocationResult invResult = new InvocationResult(resultElement, originalResult);
		invResult.getHeaders().putAll(conn.getHeaderFields());
		return invResult;
	}
	
	private InvocationResult doInvokeGET(String parameters, Map<String, String> httpHeaders, 
			int retries, long connectTimeoutMS, long readTimeoutMS, boolean doUseCache) throws Exception {
		if(retries < 0)
			throw new Exception("Invocation to " + endpointURL + " failed: " + xmlUtil.toString(parameters));
		
		String host = new URL(endpointURL).getHost();
		if(!lastRequestedHosts.containsKey(host)) {
			lastRequestedHosts.put(host, new AtomicLong());
		}
		Object lockForTargetHost = lastRequestedHosts.get(host);

		parameters = parameters.trim();
		String urlString = endpointURL;
		if(!strUtil.isEmpty(parameters)) {
			String separator = endpointURL.contains("?") ? "&" : "?";
			urlString = endpointURL + separator + parameters;
		}

		if(doUseCache) {
			/** retrieve result from document cache */
			CacheEntry existing = cache.get(urlString);
			if(existing != null && !strUtil.isEmpty(existing.value)) {
				String valueShort = (String)existing.value;
				if(valueShort.length() > 200)
					valueShort = valueShort.substring(0, 200) + "...";
				AtomicReference<Element> eRef = new AtomicReference<Element>();
				Parallelization.warnIfNoResultAfter(eRef, "! Client could not convert element (" + existing.value.length() + " bytes) within 15 seconds: " + valueShort, 15*1000);
				Parallelization.warnIfNoResultAfter(eRef, "! Client could not convert element (" + existing.value.length() + " bytes) within 40 seconds: " + valueShort, 40*1000);
				Element e = xmlUtil.toElement((String)existing.value);
				eRef.set(e);
				logger.info("Result exists in cache for URL " + urlString + " - " + e + " - " + this.xmlUtil.toString().length());
				return new InvocationResult(e);
			}
		}

		pauseToAvoidSpamming();
		

		URL url = new URL(urlString);
		URLConnection c = url.openConnection();
		c.setConnectTimeout((int)connectTimeoutMS);
		c.setReadTimeout((int)readTimeoutMS);
		logger.info("Retrieving data from service using GET: " + url);

		String tmpID = PerformanceInterceptor.event(EventType.START_HTTP_GET);
		for(String key : httpHeaders.keySet()) {
			c.setRequestProperty(key, httpHeaders.get(key));
		}
		StringBuilder b = new StringBuilder();
		synchronized (lockForTargetHost) {
			try {
				BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
				String temp;
				while((temp = r.readLine()) != null) {
					b.append(temp);
					b.append("\n");
				}
			} catch (Exception e) {
				logger.info("Could not GET page with regular URLConnection, trying HtmlUnit..: " + e);
				b = getPageUsingHtmlUnit(urlString, httpHeaders, readTimeoutMS, null);
			}
		}


		PerformanceInterceptor.event(EventType.FINISH_HTTP_GET, tmpID);
		
		String tmpID1 = PerformanceInterceptor.event(EventType.START_RESPONSE_TO_XML);
		String result = b.toString().trim();
		if(!result.startsWith("<") || !result.endsWith(">")) { // wrap non-xml results (e.g., CSV files)
			StringBuilder sb = new StringBuilder("<doc><![CDATA[");
			sb.append(result);
			sb.append("]]></doc>");
			result = sb.toString();
		}
		
		String tmpID2 = PerformanceInterceptor.event(EventType.START_STRING_TO_XML);
		Element resultElement = xmlUtil.toElement(result);
		PerformanceInterceptor.event(EventType.FINISH_STRING_TO_XML, tmpID2);

		if(doUseCache) {
			/** put result element to document cache */
			cache.putWithoutWaiting(urlString, xmlUtil.toString(resultElement, true));
		}
		
		InvocationResult invResult = new InvocationResult(resultElement);
		PerformanceInterceptor.event(EventType.FINISH_RESPONSE_TO_XML, tmpID1);

		return invResult;
	}
	
	private static StringBuilder getPageUsingHtmlUnit(String urlString, Map<String, String> httpHeaders, 
			long timeoutMS, String proxyHost) throws Exception {
		LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog"); 
		System.getProperties().put("org.apache.commons.logging.simplelog.defaultlog", "WARN");
		// try to make request using HtmlUnit..
		System.out.println("Making request to " + urlString + " using HtmlUnit WebClient..");
		WebClient client = getWebClient(timeoutMS);
		if(proxyHost != null) {
			client.getProxyConfig().setProxyHost(proxyHost);
		}
		if(httpHeaders != null) {
			for(String name: httpHeaders.keySet()) {
				client.addRequestHeader(name, httpHeaders.get(name));
			}
		}
		client.setPrintContentOnFailingStatusCode(false);
		Page p = client.getPage(urlString);
		String pageContent = null;
		if(p instanceof HtmlPage) {
			pageContent = ((HtmlPage)p).asXml();
		} else if(p instanceof TextPage) {
			pageContent = ((TextPage)p).getContent();
		} else {
			throw new RuntimeException("Unexpected page result type: " + p);
		}
		StringBuilder b = new StringBuilder(pageContent);
		try {
			p.cleanUp();
			client.closeAllWindows();
		} catch (Exception e2) {
			logger.warn(e2);
		}
		return b;
	}

	public static WebClient getWebClient() {
		return getWebClient(READ_TIMEOUT_MS);
	}
	public static WebClient getWebClient(long timeoutMS) {
		LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog"); 
		System.getProperties().put("org.apache.commons.logging.simplelog.defaultlog", "WARN");
		WebClient client = new WebClient(BrowserVersion.FIREFOX_3_6);
		client.setThrowExceptionOnScriptError(false);
		client.setTimeout((int)timeoutMS);
		client.setThrowExceptionOnFailingStatusCode(false);
		client.setIncorrectnessListener(new IncorrectnessListener() {
			public void notify(String arg0, Object arg1) {}
		});
		client.setCssErrorHandler(new ErrorHandler() {
			public void warning(CSSParseException arg0) throws CSSException {}
			public void fatalError(CSSParseException arg0) throws CSSException {}
			public void error(CSSParseException arg0) throws CSSException {}
		});
		client.setAlertHandler(new AlertHandler() {
			public void handleAlert(Page arg0, String arg1) {}
		});
		client.setJavaScriptErrorListener(new JavaScriptErrorListener() {
			public void timeoutError(HtmlPage arg0, long arg1, long arg2) { }
			public void scriptException(HtmlPage arg0, ScriptException arg1) { }
			public void malformedScriptURL(HtmlPage arg0, String arg1, MalformedURLException arg2) { }
			public void loadScriptError(HtmlPage arg0, URL arg1, Exception arg2) {}
		});
		return client;
	}
	
	public void changeTargetAddress(String newAddress) {
		QName portName = new QName(Configuration.NAMESPACE, "TempPort" + (getNumPorts() + 1));
		synchronized (service) {
			service.addPort(portName, SOAPBinding.SOAP11HTTP_BINDING, newAddress);		
		}
		this.portName = portName;
	}
	
	public int getNumPorts() {
		int c = 0;
		synchronized (service) {
			Iterator<?> i = service.getPorts();
			while(i.hasNext()) {
				i.next();
				c++;
			}
		}
		return c;
	}
	
	public String getEndpointURL() {
		return endpointURL;
	}
	
	public static Definition getWsdlDefinition(String wsdlURL) throws Exception {
		WSDLFactory wsdlFactory = WSDLFactory.newInstance();

		try {
			int timeoutMS = 5*1000; // timeout of 5 seconds..
			Class<?> clazz = Class.forName("sun.net.www.http.HttpClient");
			Method m = clazz.getMethod("New", URL.class, Proxy.class, int.class);
			try {
				Object conn = m.invoke(null, new URL(wsdlURL), null, timeoutMS);
				conn.getClass().getMethod("closeServer").invoke(conn);
			} catch (Exception e) {
				throw new ConnectException("Unable to retrieve WSDL at " + wsdlURL);
			}
		} catch (Exception e) { /* most likely, sun.net.www.http.HttpClient could not be found --> swallow */}

		WSDLReader wsdlReader = wsdlFactory.newWSDLReader();

		wsdlReader.setFeature(Constants.FEATURE_VERBOSE, false);
		wsdlReader.setFeature(Constants.FEATURE_IMPORT_DOCUMENTS, true);

		Definition wsdl = wsdlReader.readWSDL(wsdlURL);
		return wsdl;
	}
	
	public static javax.wsdl.Service getSingleWSDLService(String wsdlURL) throws Exception {
		Definition wsdl = getWsdlDefinition(wsdlURL);
		return getSingleWSDLService(wsdl);
	}

	@SuppressWarnings("all")
	public static javax.wsdl.Service getSingleWSDLService(Definition wsdl) throws Exception {
		if(wsdl.getServices().values().size() != 1) {
			throw new RuntimeException("Tried to guess the single service name from WSDL, but number of available service names is: " + wsdl.getServices().values().size());
		}
		javax.wsdl.Service service = (javax.wsdl.Service)wsdl.getServices().values().iterator().next();
		return service;
	}

	public static Port getDefaultWSDLPort(javax.wsdl.Service service) {
		Port soap11 = null;
		Port soap12 = null;
		Port http = null;
		for(Object o : service.getPorts().values()) {
			Port p = (Port)o;
			for(Object e : p.getBinding().getExtensibilityElements()) {
				if(e instanceof SOAP12Binding) {
					soap12 = p;
				} else if(e instanceof javax.wsdl.extensions.soap.SOAPBinding) {
					soap11 = p;
				} else if(e instanceof HTTPBinding) {
					http = p;
				} else {
					logger.warn("Unexpected binding class: " + e + " : " + e.getClass());
				}
			}
		}
		if(soap12 != null)
			return soap12;
		if(soap11 != null)
			return soap11;
		if(http != null)
			return http;
		if(service.getPorts().size() <= 0)
			throw new RuntimeException("Tried to guess the port name from WSDL, but no suitable port name was found.");
		return (Port)service.getPorts().values().iterator().next();
	}
	
	public static String getAddressFromWSDLPort(Port p) {
		for(Object e : p.getExtensibilityElements()) {
			if(e instanceof SOAPAddress) {
				SOAPAddress a = (SOAPAddress)e;
				return a.getLocationURI();
			} else if(e instanceof SOAP12Address) {
				SOAP12Address a = (SOAP12Address)e;
				return a.getLocationURI();
			}
		}
		return null;
	}
	
	public String getBindingTransportURIFromWSDLPort(Port p) {
		for(Object e : p.getBinding().getExtensibilityElements()) {
			if(e instanceof javax.wsdl.extensions.soap.SOAPBinding) {
				javax.wsdl.extensions.soap.SOAPBinding b = (javax.wsdl.extensions.soap.SOAPBinding)e;
				return b.getTransportURI();
			}
		}
		return null;
	}
	
	public static void main(String[] args) throws Exception {
		if(args == null || args.length <= 0) {
			System.out.println("Usage: java " + WebServiceClient.class.getName() + " <url>");
			return;
		}
		String proxy = null;
		if(args.length > 1 && args[1] != null && !args[1].trim().equals("")) {
			proxy = args[1];
		}
		System.out.println(getPageUsingHtmlUnit(args[0], null, 5000, proxy));
	}
}
