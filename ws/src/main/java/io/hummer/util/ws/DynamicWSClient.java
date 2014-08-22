package io.hummer.util.ws;

import io.hummer.util.ws.EndpointReference;
import io.hummer.util.ws.WebServiceClient;
import io.hummer.util.ws.request.InvocationRequest;
import io.hummer.util.ws.request.RequestType;
import io.hummer.util.xml.XMLUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import org.w3c.dom.Element;

public class DynamicWSClient<T> implements InvocationHandler {

	private static final Map<Class<?>,Object> clientCache = new HashMap<Class<?>,Object>();
	private static final XMLUtil xmlUtil = new XMLUtil();
	
	private EndpointReference epr;
	private URL wsdlLocation; 
	private QName serviceName;
	private Class<T> serviceInterface;
	private List<Element> headers = new LinkedList<Element>();

	public DynamicWSClient(EndpointReference ref) {
		epr = ref;
	}
	private DynamicWSClient(URL wsdlLocation, QName serviceName) {
		this.wsdlLocation = wsdlLocation;
		this.serviceName = serviceName;
	}
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if(args == null && method.getName().equals("toString")) 
			return this.toString();
		if(args == null)
			throw new RuntimeException("Arguments must not be null.");
		if(args.length != 1) 
			throw new RuntimeException("Illegal number of arguments (" + args.length + "), expected 1.");
		
		WebServiceClient client = null;
		if(epr != null)
			client = WebServiceClient.getClient(epr);
		else if(serviceName != null)
			client = WebServiceClient.getClient(wsdlLocation, serviceName);
		else
			client = WebServiceClient.getClient(new EndpointReference(wsdlLocation));
		
		Element request = xmlUtil.toElement(args[0]);
		String ns1 = request.getNamespaceURI();
		WebService anno = serviceInterface.getAnnotation(WebService.class);
		String ns2 = anno == null ? null : anno.targetNamespace();
		if(!ns1.equals(ns2) && ns2 != null) {
			request = xmlUtil.toElement(args[0], ns2, request.getLocalName());
		}
		
		InvocationRequest invRequest = new InvocationRequest(RequestType.SOAP, request, headers);
		
		Element result = client.invoke(invRequest).getResultAsElement();
		if(method.getReturnType().getName().equals("void")) {
			return null;
		} else if(method.getReturnType() == String.class) {
			return result.getTextContent();
		} else if(Long.class.isAssignableFrom(method.getReturnType()) ||
				long.class.isAssignableFrom(method.getReturnType())) {
			return Long.parseLong(result.getTextContent());
		} else if(Integer.class.isAssignableFrom(method.getReturnType()) ||
				int.class.isAssignableFrom(method.getReturnType())) {
			return Integer.parseInt(result.getTextContent());
		} else if(Short.class.isAssignableFrom(method.getReturnType()) ||
				short.class.isAssignableFrom(method.getReturnType())) {
			return Short.parseShort(result.getTextContent());
		} else if(Double.class.isAssignableFrom(method.getReturnType()) ||
				double.class.isAssignableFrom(method.getReturnType())) {
			return Double.parseDouble(result.getTextContent());
		}
		if(result.getTagName().contains("Body"))
			result = xmlUtil.getChildElements(result).get(0);

		ns1 = xmlUtil.getJaxwsNamespace(method.getReturnType());
		if(ns1 != null && !ns1.equals("") && !ns1.equals(ns2)) {
			result = xmlUtil.changeRootElementName(result, ns1, result.getLocalName());
		}

		Object jaxb = xmlUtil.toJaxbObject(method.getReturnType(), result);
		return jaxb;
	}

	@SuppressWarnings("all")
	public static <T> T createClient(Class<T> serviceToWrap, URL wsdlLocation, QName serviceName) {
		DynamicWSClient<T> client = new DynamicWSClient<T>(wsdlLocation, serviceName);
		client.serviceInterface = serviceToWrap;
        return (T)(Proxy.newProxyInstance(serviceToWrap.getClassLoader(),
            new Class[] {serviceToWrap}, client));
    }
	@SuppressWarnings("all")
	public static <T> T createClient(Class<T> serviceToWrap, URL wsdlLocation) {
		DynamicWSClient<T> client = new DynamicWSClient<T>(wsdlLocation, null);
		client.serviceInterface = serviceToWrap;
        return (T)(Proxy.newProxyInstance(serviceToWrap.getClassLoader(),
            new Class[] {serviceToWrap}, client));
    }

	public static <T> T createClientJaxws(Class<T> serviceToWrap, URL wsdlLocation) {
		return createClientJaxws(serviceToWrap, wsdlLocation, false);
	}
	@SuppressWarnings("all")
	public static <T> T createClientJaxws(Class<T> serviceToWrap, URL wsdlLocation, boolean doCache) {
		try {
			if(doCache && clientCache.containsKey(serviceToWrap)) {
				return (T)clientCache.get(serviceToWrap);
			}
			QName serviceName = new WebServiceUtil().getSingleServiceName(wsdlLocation.toString());
			Service s = Service.create(wsdlLocation, serviceName);
			T serv = s.getPort(serviceToWrap);
			if(doCache) {
				clientCache.put(serviceToWrap, serv);
			}
			return serv;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
    }
	
	public static void addHeader(Object proxyInstance, Element header) {
		DynamicWSClient<?> client = (DynamicWSClient<?>)Proxy.getInvocationHandler(proxyInstance);
		client.headers.add(header);
		System.out.println(client.headers);
	}
	public static void clearHeaders(Object proxyInstance) {
		DynamicWSClient<?> client = (DynamicWSClient<?>)Proxy.getInvocationHandler(proxyInstance);
		client.headers.clear();
	}
	
	public Class<T> getServiceInterface() {
		return serviceInterface;
	}
}
