package io.hummer.util.ws;

import io.hummer.util.xml.XMLUtil;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import javax.wsdl.Definition;
import javax.wsdl.WSDLException;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceContext;

import com.ibm.wsdl.Constants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

public class WebServiceUtil {

    private static final Logger logger = LoggerFactory.getLogger(WebServiceUtil.class);
	private static WebServiceUtil instance;
	public static final String INBOUND_HEADER_LIST_PROPERTY1 = "com.sun.xml.internal.ws.api.message.HeaderList";
	public static final String INBOUND_HEADER_LIST_PROPERTY2 = "com.sun.xml.ws.api.message.HeaderList";

	private XMLUtil xmlUtil = new XMLUtil();

    public Definition readWsdl(String wsdlUrl, boolean resolveImports) throws WSDLException {
        return readWsdl(wsdlUrl, false, resolveImports, 1, 1000);
    }

    private Definition readWsdl(String wsdlUrl, boolean verbose, boolean resolveImports, int numRetries,
                               long retryIntervalMilliseconds) throws WSDLException {
        WSDLFactory factory = WSDLFactory.newInstance();
        WSDLReader reader = factory.newWSDLReader();
        int attempts = 0;
        int maxAttempts = numRetries + 1;
        while (true) {
            attempts++;
            try {
                reader.setFeature(Constants.FEATURE_VERBOSE, verbose);
                reader.setFeature(Constants.FEATURE_IMPORT_DOCUMENTS, resolveImports);

                return reader.readWSDL(wsdlUrl);
            } catch (WSDLException ex) {
                if (throwException(attempts, maxAttempts, retryIntervalMilliseconds)) {
                    throw ex;
                }
            } catch (RuntimeException ex) {
                if (throwException(attempts, maxAttempts, retryIntervalMilliseconds)) {
                    throw ex;
                }
            }
        }
    }
	
    public QName getSingleServiceName(String serviceWSDL) throws Exception {
    	return getSingleServiceName(readWsdl(serviceWSDL, true));
	}
    public QName getSingleServiceName(Definition serviceWSDL) throws Exception {
    	Iterator<?> iter = serviceWSDL.getServices().keySet().iterator();
    	QName result = null;
    	while(iter.hasNext()) {
    		if(result != null)
    			throw new RuntimeException("Ambiguity: WSDL contains more than one service elements: " + serviceWSDL);
    		result = (QName)iter.next();
    	}
		return result;
	}

    private boolean throwException(int attempts, int maxAttempts, long retryIntervalMilliseconds) {
        logger.trace("retry fetching wsdl");
        if (attempts >= maxAttempts) {
            return true;
        }
        try {
            Thread.sleep(retryIntervalMilliseconds);
        } catch (InterruptedException e) {
            // no problem
        }
        return false;
    }

	public static synchronized WebServiceUtil getInstance() {
		if(instance == null) {
			instance = new WebServiceUtil();
		}
		return instance;
	}

	public List<Element> getSoapHeaders(WebServiceContext wsContext)
			throws Exception {
		List<?> temp = (List<?>) wsContext.getMessageContext().get(
				INBOUND_HEADER_LIST_PROPERTY1);
		if(temp == null) {
			temp = (List<?>) wsContext.getMessageContext().get(
					INBOUND_HEADER_LIST_PROPERTY2);
		}
		List<Element> result = new LinkedList<Element>();
		Random r = new Random();
		if(temp != null) {
			for(Object o : temp) {
				String prefix = "ns" + r.nextInt(100);
				// use reflection here because of potential classpath problems
				// with com.sun.xml.internal.ws.api.message.Header
				String localPart = (String) o.getClass()
						.getMethod("getLocalPart").invoke(o);
				String namespaceURI = (String) o.getClass()
						.getMethod("getNamespaceURI").invoke(o);
				String stringContent = (String) o.getClass()
						.getMethod("getStringContent").invoke(o);
				String name = prefix + ":" + localPart;
				if(namespaceURI == null) {
					name = localPart;
				}
				Element e = xmlUtil.toElement("<" + name + " xmlns:" + prefix
						+ "=\"" + namespaceURI + "\">" + stringContent + "</"
						+ name + ">");
				result.add(e);
			}
		} else {
			logger.info("No SOAP headers found in WS Context " + wsContext
					+ ". Message context properties are: "
					+ wsContext.getMessageContext());
		}
		return result;
	}

	public Element getFirstChildIfSOAPBody(Element result)
			throws Exception {
		if(result != null && result.getTagName() != null
				&& result.getTagName().contains("Body")) {
			result = xmlUtil.getChildElements(result).get(0);
			result = xmlUtil.toElement(xmlUtil.toString(result));
		}
		return result;
	}

}
