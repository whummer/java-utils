package io.hummer.util.xml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.UUID;

import javax.jws.WebService;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import net.sf.json.JSON;
import net.sf.json.JSONSerializer;
import net.sf.json.xml.XMLSerializer;
import net.sf.saxon.dom.ElementOverNodeInfo;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;

import org.apache.log4j.Logger;
import org.apache.xml.security.c14n.Canonicalizer;
import org.ccil.cowan.tagsoup.AutoDetector;
import org.ccil.cowan.tagsoup.Parser;
import org.ccil.cowan.tagsoup.XMLWriter;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.tidy.Tidy;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import io.hummer.util.io.IOUtil;
import io.hummer.util.log.LogUtil;
import io.hummer.util.misc.PerformanceInterceptor;
import io.hummer.util.misc.PerformanceInterceptor.EventType;

public class XMLUtil {

	private static final Logger logger = LogUtil.getLogger(XMLUtil.class);
	private static boolean doDtdValidation = false; // TODO make configurable
	private static final ErrorHandler errorHandler = new ErrorHandler() {
		public void warning(SAXParseException exception) throws SAXException {
		}
		public void fatalError(SAXParseException exception) throws SAXException {
		}
		public void error(SAXParseException exception) throws SAXException {
		}
	};
	private static final List<Class<?>> defaultJaxbContextClasses;
	private static JAXBContext defaultJaxbContext;
	private static XMLUtil instance;

	private DocumentBuilderFactory factory;

	static {
		System.setProperty(
				"org.apache.xerces.xni.parser.XMLParserConfiguration",
				"org.apache.xerces.parsers.SoftReferenceSymbolTableConfiguration");
		defaultJaxbContextClasses = new LinkedList<Class<?>>();
		for(String clazz : Arrays
				.asList("at.ac.tuwien.infosys.ws.EndpointReference",

						"at.ac.tuwien.infosys.aggr.request.AbstractInput",
						"at.ac.tuwien.infosys.aggr.request.EventingInput",
						"at.ac.tuwien.infosys.aggr.request.RequestInput",
						"at.ac.tuwien.infosys.aggr.request.AggregationResponse",
						"at.ac.tuwien.infosys.aggr.node.AggregatorNode",
						"at.ac.tuwien.infosys.aggr.node.DataServiceNode",
						"at.ac.tuwien.infosys.aggr.request.AggregationRequest",
						"at.ac.tuwien.infosys.aggr.strategy.Topology",
						"at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceInfo",
						"at.ac.tuwien.infosys.aggr.strategy.StrategyChain",
						"at.ac.tuwien.infosys.aggr.strategy.AggregationStrategy",
						"at.ac.tuwien.infosys.aggr.monitor.ModificationNotification",
						"at.ac.tuwien.infosys.aggr.AggregationClient$CreateTopologyRequest")) {
			try {
				defaultJaxbContextClasses.add(Class.forName(clazz));
			} catch(Throwable e) {
				logger.debug("JAXB context initializer could not load class with name '"
						+ clazz + "'");
			}
		}
		try {
			defaultJaxbContext = JAXBContext
					.newInstance(defaultJaxbContextClasses
							.toArray(new Class[0]));
		} catch(Throwable t) {
			logger.info("Unable to initialize JAXB Context with predefined classes.");
			try {
				defaultJaxbContextClasses.clear();
				defaultJaxbContext = JAXBContext.newInstance();
			} catch(JAXBException e1) {
				throw new RuntimeException(e1);
			}
		}
		org.apache.xml.security.Init.init();
	}

	public XMLUtil() {
		try {
			factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			if(!doDtdValidation) {
				factory.setValidating(false);
				factory.setFeature("http://xml.org/sax/features/validation", false);
				factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
				factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	public static synchronized XMLUtil getInstance() {
		if(instance == null) {
			instance = new XMLUtil();
		}
		return instance;
	}
	
	
	/**
	 * Utility class for Tag Soup XML Parser..
	 * 
	 * @author hummer
	 */
	private final class CharsetDetector implements AutoDetector {
		private String[] charsets;

		public CharsetDetector(String... charsets) {
			this.charsets = charsets;
			if(this.charsets == null)
				this.charsets = new String[0];
		}

		public Reader autoDetectingReader(InputStream i) {
			for(String c : charsets) {
				try {
					return new InputStreamReader(i, c);
				} catch(UnsupportedEncodingException e) { /* swallow */
				}
			}
			// fallback...
			charsets = new String[0];
			return new InputStreamReader(i);
		}
	}

	
	/**
	 * Print an XML element to a given OutputStream.
	 * 
	 * @param e
	 */
	@SuppressWarnings("all")
	public void print(Element e, OutputStream s) {
		if(s == null)
			s = System.out;
		try {
			Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.INDENT, "yes");
			tr.setOutputProperty(OutputKeys.METHOD, "xml");
			tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount",
					"2");
			tr.transform(new DOMSource(e), new StreamResult(s));
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Print an XML element to System.out.
	 * 
	 * @param e
	 */
	public void print(Element e) {
		print(e, System.out);
	}
	
	public String getTextContent(Node n) {
		try {
			return (String) n.getClass().getMethod("getTextContent").invoke(n);
		} catch(Exception e) {
			String total = "";
			NodeList list = n.getChildNodes();
			for(int i = 0; i < list.getLength(); i++) {
				if(list.item(i) instanceof Text) {
					try {
						total += ((Text) list.item(i)).getClass()
								.getMethod("getTextContent")
								.invoke(list.item(i));
					} catch(Exception e1) {
						throw new RuntimeException(e1);
					}
				}
			}
			return total;
		}
	}
	
	public boolean equals(Element r1, Element r2) {
		String s1 = toString(r1);
		String s2 = toString(r2);
		return s1.equals(s2);
	}

	public Document cloneCanonical(Document doc) {
		return cloneCanonical(doc.getDocumentElement()).getOwnerDocument();
	}

	public Element cloneCanonical(Element e) {
		try {
			return toElement(toStringCanonical(e));
		} catch (Exception e2) {
			throw new RuntimeException(e2);
		}
	}
	
	public String toStringCanonical(Element e) {
		try {
			e = clone(e);
			Canonicalizer c14n = Canonicalizer
					.getInstance(Canonicalizer.ALGO_ID_C14N_EXCL_WITH_COMMENTS);
			String result = new String(c14n.canonicalizeSubtree(e, "true"));
			return result;
		} catch (Exception e2) {
			throw new RuntimeException(e2);
		}
	}
	public Element createElement(String tagName) throws Exception {
		Document doc = newDocument();
		synchronized(doc) {
			return doc.createElement(tagName);
		}
	}

	public void insertChildAfter(Node newChild, Node after) {
		newChild = after.getOwnerDocument().importNode(newChild, true);
		Node parent = after.getParentNode();
		Node nextSibling = after.getNextSibling();
		if(nextSibling == null) {
			parent.appendChild(newChild);
		} else {
			parent.insertBefore(newChild, nextSibling);
		}
	}

	public void insertChildBefore(Node newChild, Node before) {
		//newChild = before.getOwnerDocument().importNode(newChild, true);
		Node parent = before.getParentNode();
		parent.insertBefore(newChild, before);
	}

	public List<?> parseListFromXML(Element map) {
		List<Object> result = new LinkedList<Object>();
		for(Element item : getChildElements(map)) {
			List<Element> itemChildren = getChildElements(item);
			if(itemChildren.isEmpty()) {
				result.add(item.getTextContent());
			} else {
				Element c = itemChildren.get(0);
				if(c.getLocalName().equals("map")) {
					result.add(parseMapFromXML(c));
				} else if(c.getLocalName().equals("list")) {
					result.add(parseListFromXML(c));
				}
			}
		}
		return result;
	}
	@SuppressWarnings("all")
	public Map<String,?> parseMapFromXML(Element map) {
		Map<String,Object> result = new HashMap<String,Object>();
		for(Element entry : getChildElements(map)) {
			List<Element> ch = getChildElements(entry);
			String key = ch.get(0).getTextContent();
			Element valueEl = ch.get(1);
			if(getChildElements(valueEl).isEmpty()) {
				Object value = valueEl.getTextContent();
				result.put(key, value);
			} else {
				Element c = getChildElements(valueEl).get(0);
				if(c.getLocalName().equals("map")) {
					result.put(key, parseMapFromXML(c));
				} else if(c.getLocalName().equals("list")) {
					result.put(key, parseListFromXML(c));
				}
			}
		}
		return result;
	}
	public Element dumpMapAsXML(Map<?,?> map) throws Exception {
		return dumpMapAsXML(map, "map");
	}
	public Element dumpMapAsXML(Map<?,?> map, String rootElementName) throws Exception {
		StringBuilder b = new StringBuilder("<" + rootElementName + ">");
		for(Entry<?,?> e : map.entrySet()) {
			b.append("<entry><key><![CDATA[" + e.getKey() + 
					"]]></key><value><![CDATA[" +
					e.getValue() + "]]></value></entry>");
		}
		b.append("</" + rootElementName + ">");
		return toElement(b.toString());
	}

	public Element toElementUsingTagSoup(String string)
			throws Exception {

		Parser reader = new Parser();
		try {
			reader.setFeature(Parser.validationFeature, false);
			reader.setFeature(Parser.ignorableWhitespaceFeature, true);
			reader.setFeature(Parser.externalGeneralEntitiesFeature, true);
			reader.setFeature(Parser.bogonsEmptyFeature, true);
			reader.setProperty(Parser.autoDetectorProperty,
					new CharsetDetector("UTF-8"));
		} catch(Exception e) {
			logger.warn("Could not set all parser features: " + e);
		}

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		XMLWriter x = new XMLWriter(new OutputStreamWriter(bos));
		x.setOutputProperty(XMLWriter.ENCODING, "UTF-8");
		x.setOutputProperty(XMLWriter.METHOD, "xml");
		x.setOutputProperty(XMLWriter.OMIT_XML_DECLARATION, "yes");
		x.setOutputProperty(XMLWriter.MEDIA_TYPE, "text/html");
		reader.setContentHandler(x);
		reader.parse(new InputSource(new StringReader(string)));

		String resString = bos.toString();
		if(resString == null || !resString.trim().startsWith("<"))
			throw new NullPointerException(
					"Unable to convert XML string to Element using Tag Soup.");
		Element res = toElement(resString, true);
		if(res == null)
			throw new NullPointerException(
					"Unable to convert XML string to Element using Tag Soup.");

		resString = toString(res);

		String start = resString.substring(0, resString.indexOf(">"));
		String end = resString.substring(resString.indexOf(">"));
		// System.out.println("start before: " + start);
		start = start.replaceAll("xmlns=\".+html\"", "");
		// System.out.println("start after: " + start);
		resString = start + end;
		res = toElement(resString, true);
		res = clone(res);
		return res;
	}

	public Element toElementUsingTidy(String string)
			throws Exception {
		if(string != null) {
			string = string.trim();
			if(string.indexOf("<!DOCTYPE") > 0) {
				string = string.substring(string.indexOf("<!DOCTYPE"));
			}
		}
		if(string == null || !string.startsWith("<")) {
			String msg = "Could not parse using Tidy XML. Apparently, this is not a valid XML string: '"
					+ (string.length() > 1000 ? (string.substring(0, 1000) + "... [truncated]")
							: string) + "'";
			if(string == null || !string.startsWith("for $_waql_")) {
				Thread.dumpStack();
				logger.info(msg);
			}
			throw new Exception(msg);
		}

		Tidy tidy = new Tidy();
		tidy.setQuiet(true);
		tidy.setShowWarnings(false);
		tidy.setShowErrors(0);
		tidy.setXHTML(false);
		tidy.setXmlOut(true);
		tidy.setMakeClean(true);
		tidy.setQuoteAmpersand(true);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		tidy.parseDOM(new StringReader(string), new OutputStreamWriter(bos));
		String result = bos.toString();
		if(result.startsWith("<!DOCTYPE")) {
			result = result.substring(result.indexOf(">") + 1);
		}
		// result = result.replaceAll("<\\?", "");
		Element _result = toElement(result, true);
		_result.removeAttribute("xmlns");
		return _result;
	}

	public Element clone(Element element) throws Exception {
		return toElement(toString(element));
	}
	
	public InputSource sourceToInputSource(Source source) throws Exception {
		if(source instanceof SAXSource) {
			return ((SAXSource) source).getInputSource();
		} else if(source instanceof DOMSource) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			Node node = ((DOMSource) source).getNode();
			if(node instanceof Document) {
				node = ((Document) node).getDocumentElement();
			}
			Element domElement = (Element) node;

			StreamResult result = new StreamResult(baos);
			TransformerFactory transFactory = TransformerFactory.newInstance();
			Transformer transformer = transFactory.newTransformer();
			transformer.transform(new DOMSource(domElement), result);

			InputSource isource = new InputSource(source.getSystemId());
			isource.setByteStream(new ByteArrayInputStream(baos.toByteArray()));
			return isource;
		} else if(source instanceof StreamSource) {
			StreamSource ss = (StreamSource) source;
			InputSource isource = new InputSource(ss.getSystemId());
			isource.setByteStream(ss.getInputStream());
			isource.setCharacterStream(ss.getReader());
			isource.setPublicId(ss.getPublicId());
			return isource;
		} else {
			return new InputSource(source.getSystemId());
		}
	}

	public Element getFirstChild(Element result) throws Exception {
		List<Element> list = getChildElements(result);
		if(list == null || list.isEmpty())
			throw new RuntimeException("Unable to get first child of XML element - no child elements found.");
		return list.get(0);
	}

	public List<Element> getChildElements(Element e) {
		return getChildElements(e, null);
	}

	public List<Element> getChildElements(Element e, String name) {
		if(e == null)
			return null;
		List<Element> result = new LinkedList<Element>();
		NodeList list = e.getChildNodes();
		for(int i = 0; i < list.getLength(); i++) {
			Node n = list.item(i);
			if(n instanceof Element) {
				if(name == null || name.equals(((Element) n).getLocalName())
						|| ((Element) n).getLocalName().endsWith(":" + name)) {
					result.add((Element) n);
				}
			}
		}
		return result;
	}

	public Element findElementById(Element parent, String id) {
		List<Element> els = findElementsByAttribute(parent, "id", id);
		if(els.size() > 1) {
			throw new RuntimeException(
					"Expected to find at most one element with id '" + id
							+ "', but found " + els.size()
							+ " within parent element " + parent);
		}
		if(els.isEmpty())
			return null;
		return els.get(0);
	}

	public List<Element> findElementsByAttribute(Element parent, String name,
			String value) {
		List<Element> result = new LinkedList<Element>();
		if(parent == null || name == null || value == null)
			return result;
		if(parent.getAttribute(name).equals(value)) {
			result.add(parent);
		}
		for(Element child : getChildElements(parent)) {
			List<Element> els = findElementsByAttribute(child, name, value);
			result.addAll(els);
		}
		return result;
	}

	public Document newDocument() throws Exception {
		DocumentBuilder builder = newDocumentBuilder();
		return builder.newDocument();
	}

	// TODO: replace Element with ElementWrapper to enable caching of
	// toString(..) results...
	public String toString(Element element) {
		return toString(element, false);
	}

	@SuppressWarnings("all")
	/** 
	 * This method needs to be synchronized, because of possible 
	 * bug/synchronization issue:
	 * https://issues.apache.org/jira/browse/CXF-1560
	 * */
	public synchronized String toString(Element element, boolean indent) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.METHOD, "xml");
			if(indent) {
				tr.setOutputProperty(
						"{http://xml.apache.org/xslt}indent-amount", "2");
				tr.setOutputProperty(OutputKeys.INDENT, "yes");
			} else {
				tr.setOutputProperty(OutputKeys.INDENT, "no");
			}
			tr.transform(new DOMSource(element), new StreamResult(baos));
		} catch(Exception e) {
			throw new RuntimeException(e);
		}

		String string = null;
		byte[] bytes = baos.toByteArray();
		try {
			string = new String(bytes);
		} catch(OutOfMemoryError e) {
			string = null;
			System.gc();
			String tmp = "tmp" + UUID.randomUUID().toString() + ".xml";
			new File(tmp).deleteOnExit();
			try {
				FileOutputStream fos = new FileOutputStream(tmp);
				fos.write(bytes);
				fos.close();
				bytes = null;
				System.gc();
				string = new IOUtil().readFile(tmp);
			} catch(Exception e1) {
				logger.info("Error saving temporary file.", e1);
			}
			new File(tmp).delete();
		}
		bytes = null;
		try {
			string = string
					.replaceAll(
							"<\\?xml version=\"1\\.0\" (encoding=\".*\")?( )*\\?>(\n)*",
							"");
		} catch(Throwable t) {
			logger.info("Unable to remove '<?xml ...' from string, probably too large? bytes: "
					+ string.length());
		}
		return string;
	}


	public boolean hasSimpleContent(Element e) {
		if(e.getChildNodes().getLength() == 0)
			return true;
		if(e.getChildNodes().getLength() != 1)
			return false;
		if(!(e.getChildNodes().item(0) instanceof Text))
			return false;
		return true;
	}

	public DocumentBuilderFactory getDocBuilderFactory() {
		return factory;
	}

	public DocumentBuilder newDocumentBuilder()
			throws ParserConfigurationException {
		DocumentBuilderFactory factory = getDocBuilderFactory();
		DocumentBuilder temp = factory.newDocumentBuilder();
		temp.setErrorHandler(errorHandler);
		return temp;
	}

	public Element toElement(InputStream in) throws SAXException, IOException,
			ParserConfigurationException {
		DocumentBuilder builder = newDocumentBuilder();

		Document d = builder.parse(in);
		return d.getDocumentElement();
	}

	public Element toElement(final String string)
			throws ParserConfigurationException, SAXException, IOException {
		return toElement(string, false);
	}

	public Element toElementHTML(final String string) throws Exception {
		return toElementUsingTagSoup(string);
	}

	public Element toElementSafe(final String string) {
		try {
			return toElement(string, false);
		} catch(Exception e) {
			return null;
		}
	}

	public Element toElement(String string, boolean strictXML)
			throws ParserConfigurationException, SAXException, IOException {

		if(string == null || string.trim().isEmpty())
			return null;

		String id = PerformanceInterceptor.event(EventType.START_PARSE_XML_STRICT);

		Document d = null;
		DocumentBuilder builder = newDocumentBuilder();

		if(strictXML) {
			d = builder.parse(new InputSource(new StringReader(string)));
		} else {
			try {
				d = builder.parse(new InputSource(new StringReader(string)));
			} catch(Exception e) {
				try {
					PerformanceInterceptor.event(EventType.FINISH_PARSE_XML_STRICT,id, string);
					id = PerformanceInterceptor.event(EventType.START_PARSE_XML_TIDY);
					Element result = toElementUsingTidy(string);
					PerformanceInterceptor.event(EventType.FINISH_PARSE_XML_TIDY, id, string);
					return result;
				} catch(Exception e2) {
					try {
						PerformanceInterceptor.event(EventType.FINISH_PARSE_XML_TIDY,id, string);
						id = PerformanceInterceptor.event(EventType.START_PARSE_XML_TIDY);
						Element result = toElementUsingTagSoup(string);
						PerformanceInterceptor.event(EventType.FINISH_PARSE_XML_TIDY, id, string);
						return result;
					} catch(Exception e3) {
						logger.warn("Unable to convert String to XML Element.", e3);
						PerformanceInterceptor.event(EventType.FINISH_PARSE_XML_TIDY, id, string);
						return null;
					}
				}
			}
		}
		PerformanceInterceptor.event(EventType.FINISH_PARSE_XML_STRICT, id, string);
		return d.getDocumentElement();
	}

	/**
	 * Print the canonical representation of an XML element to System.out.
	 * 
	 * @param e
	 */
	public void printCanonical(Element e) throws Exception {
		print(cloneCanonical(e), System.out);
	}


	public Element changeRootElementName1(Element el, String newName)
			throws Exception {
		if(el == null)
			return null;
		Element e = toElement("<" + newName + "/>");

		for(Element c : getChildElements(el)) {
			appendChild(e, c);
		}
		for(int i = 0; i < el.getAttributes().getLength(); i++) {
			Node n = el.getAttributes().item(i);
			Document doc = e.getOwnerDocument();
			synchronized(doc) {
				n = doc.importNode(n, true);
			}
			e.setAttributeNode((Attr) n);
		}
		return e;
	}

	public Element changeRootElementName(Element el, String newName)
			throws Exception {
		String elString = toString(el);
		elString = elString.replaceFirst("<[0-9a-zA-Z\\-\\:]+\\s*", "<"
				+ newName + " ");
		elString = elString.substring(0, elString.lastIndexOf("<"));
		StringBuilder b = new StringBuilder();
		b.append(elString);
		b.append("</");
		if(newName.contains(" "))
			newName = newName.split(" ")[0];
		b.append(newName);
		b.append(">");
		// System.out.println(b.toString());
		return toElement(b.toString());
	}

	public Element changeRootElementName(Element el, String newNamespace,
			String newName) throws Exception {
		if(el == null)
			return null;
		Element e = el.getOwnerDocument().createElement(newName);
		if(newNamespace != null) {
			e = toElement("<foo123:" + newName + " xmlns:foo123=\""
					+ newNamespace + "\"/>");
			e = toElement(toString(e));
		}

		for(Element c : getChildElements(el)) {
			appendChild(e, c);
		}
		for(int i = 0; i < el.getAttributes().getLength(); i++) {
			Node n = el.getAttributes().item(i);
			n = e.getOwnerDocument().importNode(n, true);
			e.setAttributeNode((Attr) n);
		}
		return e;
	}

	public String getJaxwsNamespace(Class<?> clazz) {
		XmlRootElement rootElAnno = clazz.getAnnotation(XmlRootElement.class);
		WebService serviceAnno = clazz.getAnnotation(WebService.class);
		if(rootElAnno != null && rootElAnno.namespace() != null
				&& !rootElAnno.namespace().equals("##default")) {
			return rootElAnno.namespace();
		}
		if(serviceAnno != null && serviceAnno.targetNamespace() != null
				&& !serviceAnno.targetNamespace().equals("##default")) {
			return serviceAnno.targetNamespace();
		}
		if(clazz.getDeclaringClass() != null) {
			String ns = getJaxwsNamespace(clazz.getDeclaringClass());
			if(ns != null && !ns.equals(""))
				return ns;
		}
		if(clazz.getEnclosingClass() != null) {
			String ns = getJaxwsNamespace(clazz.getEnclosingClass());
			if(ns != null && !ns.equals(""))
				return ns;
		}
		return "";
	}

	public Element toElement(Object jaxbObject,
			String rootElementNamespace, String rootElementName)
			throws Exception {
		JAXBContext ctx = JAXBContext.newInstance(jaxbObject.getClass());
		Element result = getDocBuilderFactory().newDocumentBuilder()
				.newDocument().createElement("result");
		ctx.createMarshaller().marshal(jaxbObject, result);
		return changeRootElementName((Element) result.getFirstChild(),
				rootElementNamespace, rootElementName);
	}

	public Element toElement(Object jaxbObject,
			String rootElementName) throws Exception {
		return toElement(jaxbObject, null, rootElementName);
	}

	public String toStringIncludingDeclarations(Element e)
			throws Exception {
		e = (Element) getDocBuilderFactory().newDocumentBuilder().newDocument()
				.importNode(e, true);
		return toString(e);
	}


	public Element toElement(List<?> list) throws Exception {
		if(list.size() == 1)
			return toElement((Element) list.get(0));
		Element result = createElement("list");
		for(Object o : list) {
			if(o instanceof Element) {
				appendChild(result, (Element) o);
			} else if(o instanceof String) {
				appendChild(result, toElement("<item>" + o + "</item>"));
			} else {
				appendChild(result, toElement((Object) o));
			}
		}
		return result;
	}

	private JAXBContext getJaxbContext(Class<?> jaxbClass,
			boolean doCacheContext) throws Exception {
		synchronized(defaultJaxbContextClasses) {
			if(!defaultJaxbContextClasses.contains(jaxbClass)) {
				defaultJaxbContextClasses.add(jaxbClass);
				defaultJaxbContext = JAXBContext
						.newInstance(defaultJaxbContextClasses
								.toArray(new Class[0]));
			}
		}
		return defaultJaxbContext;
	}

	public Element toElement(Object jaxbObject) {
		try {
			if(jaxbObject == null)
				return null;
			if(jaxbObject instanceof Element)
				return (Element) jaxbObject;
			if(jaxbObject instanceof String)
				return toElement((String) jaxbObject);
			return toElement(jaxbObject,
					getJaxbContext(jaxbObject.getClass(), true));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public Element toElementWithDynamicContext(Object jaxbObject)
			throws Exception {
		if(jaxbObject == null)
			return null;
		if(jaxbObject instanceof Element)
			return (Element) jaxbObject;
		if(jaxbObject instanceof String)
			return toElement((String) jaxbObject);
		return toElement(jaxbObject,
				JAXBContext.newInstance(jaxbObject.getClass()));
	}

	public Element toElement(Object jaxbObject, JAXBContext ctx)
			throws Exception {
		if(jaxbObject == null)
			return null;
		Element result = newDocument().createElement("result");
		try {
			ctx.createMarshaller().marshal(jaxbObject, result);
		} catch(Exception e) {
			e.printStackTrace();
			return toElementWithDynamicContext(jaxbObject);
		}
		return (Element) result.getFirstChild();
	}

	@SuppressWarnings("all")
	public <T> T toJaxbObject(Class<T> jaxbClass, Element element)
			throws Exception {
		return toJaxbObject(jaxbClass, element, true);
	}

	@SuppressWarnings("all")
	public <T> T toJaxbObjectWithDynamicContext(
			Class<T> jaxbClass, Element element) throws Exception {
		JAXBContext ctx = JAXBContext.newInstance(jaxbClass);
		return (T) ctx.createUnmarshaller().unmarshal(element);
	}

	@SuppressWarnings("all")
	public <T> T toJaxbObject(Element element,
			Class<?>... jaxbClasses) throws Exception {
		JAXBContext ctx = null;
		if(jaxbClasses.length <= 0) {
			// add default classes here:
			ctx = defaultJaxbContext;
		} else {
			ctx = JAXBContext.newInstance(jaxbClasses);
		}
		return (T) ctx.createUnmarshaller().unmarshal(element);
	}

	@SuppressWarnings("all")
	public <T> T toJaxbObject(Class<T> jaxbClass, Element element,
			boolean doCacheContext) throws Exception {
		JAXBContext ctx = getJaxbContext(jaxbClass, doCacheContext);
		return (T) ctx.createUnmarshaller().unmarshal(element);
	}

	public String toString(Object jaxbObject) {
		return toString(jaxbObject, false);
	}

	public String toString(Object jaxbObject, boolean indent) {
		return toString(toElement(jaxbObject), indent);
	}


	public Element getSOAPBodyFromEnvelope(Element env) {
		try {
			if(env instanceof SOAPEnvelope) {
				return ((SOAPEnvelope)env).getBody();
			}
			return getFirstChildElement(env, "Body");
		} catch (SOAPException e) {
			throw new RuntimeException(e);
		}
	}
	public String getSOAPBodyAsString(Element env) {
		return toString(getSOAPBodyFromEnvelope(env));
	}
	public void appendTextChild(Element parent, String textNode)
			throws Exception {
		if(parent == null || textNode == null) {
			logger.info("parent or child null in appendTextChild: p: " + parent
					+ " - t: " + textNode);
			return;
		}
//		NodeList children = parent.getChildNodes();
		Text newTextNode = parent.getOwnerDocument().createTextNode(textNode);
//		if(children.getLength() > 0) {
//			Node n = children.item(children.getLength() - 1);
//			if(n instanceof Text) {
//				newTextNode = (Text)n;
//			}
//		}
		parent.appendChild(newTextNode);
//		newTextNode.setData(newTextNode.getData() + textNode);
	}
	
	public void appendChild(Element parent, Element child)
			throws Exception {
		try {
			if(parent == null || child == null) {
				logger.info("parent or child null in appendChild: p: " + parent
						+ " - c: " + child);
				return;
			}
			if(parent.getOwnerDocument() == null) {
				logger.info("parent element getOwnerDocument() is null in Util.appendChild(..)!");
				parent.appendChild(child);
			} else {
				Document doc = parent.getOwnerDocument();
				if(doc != child.getOwnerDocument()) {
					if(doc != null) {
						synchronized(doc) {
							child = (Element) doc.importNode(child, true);
						}
					}
				}
			}
		} catch(ClassCastException e) {
			child = clone(child);
			Document doc = parent.getOwnerDocument();
			synchronized(doc) {
				child = (Element) doc.importNode(child, true);
			}
		} catch(NullPointerException e) {
			child = clone(child);
			Document doc = parent.getOwnerDocument();
			if(doc != child.getOwnerDocument()) {
				synchronized(doc) {
					child = (Element) doc.importNode(child, true);
				}
			}
		}
		try {
			parent.appendChild(child);
		} catch(DOMException e) {
			try {
				Document doc = parent.getOwnerDocument();
				synchronized(doc) {
					child = (Element) doc.importNode(child, true);
				}
				parent.appendChild(child);
			} catch(Exception e2) {
				child = toElement(toString(child));
				Document doc = parent.getOwnerDocument();
				synchronized(doc) {
					child = (Element) doc.importNode(child, true);
				}
				parent.appendChild(child);
			}
		}
	}

	public Element toElement(TinyDocumentImpl d) {
		return ((Document) ElementOverNodeInfo.wrap(d.getDocumentRoot()))
				.getDocumentElement();
	}


	public String getXPath(Node n) {
		return getXPath(n, null, true, false);
	}

	/**
	 * @author Mikkel Heisterberg, lekkim@lsdoc.org
	 * 
	 *         Adapted from http://lekkimworld.com/2007/06/19/
	 *         building_xpath_expression_from_xml_node.html
	 */
	public String getXPath(Node n, Element rootNode, boolean printArrayIndices,
			boolean printIdAttributes) {
		if(null == n)
			return null;

		Node parent = null;
		Stack<Node> hierarchy = new Stack<Node>();
		StringBuffer buffer = new StringBuffer();

		hierarchy.push(n);

		parent = n.getParentNode();
		while(null != parent && parent.getNodeType() != Node.DOCUMENT_NODE
				&& rootNode != parent) {
			hierarchy.push(parent);
			parent = parent.getParentNode();
		}

		// construct xpath
		Object obj = null;
		while(!hierarchy.isEmpty() && null != (obj = hierarchy.pop())) {
			Node node = (Node) obj;
			boolean handled = false;

			// only consider elements
			if(node.getNodeType() == Node.ELEMENT_NODE) {
				Element e = (Element) node;

				// is this the root element?
				if(buffer.length() == 0) {
					// root element - simply append element name
					buffer.append(node.getLocalName());
				} else {
					// child element - append slash and element name
					buffer.append("/");
					buffer.append(node.getLocalName());

					if(node.hasAttributes()) {
						// see if the element has a name or id attribute
						if(e.hasAttribute("id") && printIdAttributes) {
							// id attribute found - use that
							buffer.append("[@id='" + e.getAttribute("id")
									+ "']");
							handled = true;
						} else if(e.hasAttribute("name") && printIdAttributes) {
							// name attribute found - use that
							buffer.append("[@name='" + e.getAttribute("name")
									+ "']");
							handled = true;
						}
					}

					if(!handled) {
						// no known attribute we could use - get sibling index
						int prev_siblings = 1;
						Node prev_sibling = node.getPreviousSibling();
						while(null != prev_sibling) {
							if(prev_sibling.getNodeType() == node.getNodeType()) {
								if(prev_sibling.getLocalName()
										.equalsIgnoreCase(node.getLocalName())) {
									prev_siblings++;
								}
							}
							prev_sibling = prev_sibling.getPreviousSibling();
						}
						if(printArrayIndices)
							buffer.append("[" + prev_siblings + "]");
					}
				}
			}
		}

		return buffer.toString();
	}

	public Element wrapElement(Element toWrap, String nameOfWrapperElement)
			throws Exception {
		Element wrap = toElement("<" + nameOfWrapperElement + "/>");
		appendChild(wrap, toWrap);
		return wrap;
	}

	public QName getQName(Element e) {
		return e.getNamespaceURI() == null ? new QName(e.getLocalName())
				: new QName(e.getNamespaceURI(), e.getLocalName());
	}

	public Element getFirstChildElement(Element e, String name) {
		List<Element> list = getChildElements(e, name);
		if(list.isEmpty())
			return null;
		return list.get(0);
	}

	public List<Element> findDescendantsByName(Element e, String name) {
		List<Element> result = new LinkedList<Element>();
		findDescendantsByName(e, name, result);
		return result;
	}
	
	public void findDescendantsByName(Element e, String name, List<Element> list) {
		if(e.getLocalName().equals(name))
			list.add(e);
		for(Element e1 : getChildElements(e))
			findDescendantsByName(e1, name, list);
	}

	public void replaceElementWithTextNode(Element el, String text) {
		if(text == null)
			text = "";
		Text t = el.getOwnerDocument().createTextNode(text);
		el.getParentNode().replaceChild(t, el);
	}
	
	public void replaceElementWithTextNode(Element el, List<String> text) {
		StringBuilder b = new StringBuilder();
		for(String s : text) {
			b.append(s);
		}
		Text t = el.getOwnerDocument().createTextNode(b.toString());
		el.getParentNode().replaceChild(t, el);
	}
	public net.sf.json.JSON toJSON(String s) {
		return JSONSerializer.toJSON(s); 
	}
	public net.sf.json.JSON toJSON(Element e) {
		try {
			// remove unneccesary namespaces etc.
			e = cloneCanonical(e);
		} catch (Exception e1) {
			throw new RuntimeException(e1);
		}
		return new XMLSerializer().read(toString(e));
	}
	public Element toElement(JSON json) throws ParserConfigurationException, SAXException, IOException {
		XMLSerializer serializer = new XMLSerializer(); 
        String xml = serializer.write(json);
        return toElement(xml);
	}

	
}
