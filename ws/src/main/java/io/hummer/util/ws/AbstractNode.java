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

import io.hummer.util.coll.CollectionsUtil;
import io.hummer.util.log.LogUtil;
import io.hummer.util.net.NetUtil;
import io.hummer.util.par.GlobalThreadPool;
import io.hummer.util.str.StringUtil;
import io.hummer.util.xml.XMLUtil;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.BindException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.annotation.Resource;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;
import javax.ws.rs.Path;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.RuntimeDelegate;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.ws.BindingType;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Provider;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.WebServiceProvider;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.http.HTTPBinding;
import javax.xml.ws.spi.http.HttpExchange;

import org.apache.commons.lang.NotImplementedException;
import org.apache.log4j.Logger;
import org.eclipse.jetty.jaxws2spi.JettyHttpContext;
import org.eclipse.jetty.jaxws2spi.JettyHttpServer;
import org.eclipse.jetty.jaxws2spi.JettyHttpServerProvider;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.log.Slf4jLog;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;

import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("all")
@XmlRootElement
public abstract class AbstractNode implements IAbstractNode {

	@XmlElement
	private EndpointReference epr;
	@XmlElement
	private String wadlURL;

	@SuppressWarnings("all")
	private List<Handler> activeHandlers = new LinkedList<Handler>();
	@SuppressWarnings("all")
	private List<Handler> inactiveHandlers = new LinkedList<Handler>();
	private Endpoint endpoint;

	private static final XMLUtil xmlUtil = new XMLUtil();
	private static final StringUtil strUtil = new StringUtil();
	private static final NetUtil netUtil = new NetUtil();
	private static final CollectionsUtil collUtil = new CollectionsUtil();
	
	public static final Map<String,AbstractNode> deployedNodes = new HashMap<String, AbstractNode>();
	public static final Map<Integer, Server> servers = new HashMap<Integer, Server>();
	public static final Map<Integer,JettyHttpServer> httpServers = new HashMap<Integer, JettyHttpServer>();
	private static final Logger logger = LogUtil.getLogger(AbstractNode.class);
	private static final String SYSPROP_HTTP_SERVER_PROVIDER_CLASS = "com.sun.net.httpserver.HttpServerProvider";

	@XmlTransient
	@WebMethod(exclude=true)
	public EndpointReference getEPR() {
		return getEpr();
	}
	@WebMethod(exclude=true)
	public void setEPR(EndpointReference epr) {
		this.setEpr(epr);
	}
	@XmlTransient
	@WebMethod(exclude=true)
	public String getWadlURL() {
		return wadlURL;
	}
	@WebMethod(exclude=true)
	public void setWadlURL(String wadlURL) {
		this.wadlURL = wadlURL;
	}
	
	@WebServiceProvider
	@BindingType(value=HTTPBinding.HTTP_BINDING)
	public static class CrossdomainXML implements Provider<Source> {
		@Resource
	    protected WebServiceContext wsContext;

		public Source invoke(Source request) {
			Source result = null;
			try {
				result = new DOMSource(xmlUtil.toElement("<?xml version=\"1.0\"?>" +
						"<!DOCTYPE cross-domain-policy SYSTEM \"http://www.macromedia.com/xml/dtds/cross-domain-policy.dtd\">" +
							"<cross-domain-policy>" +
							"<allow-access-from domain=\"*\"/>" +
							"</cross-domain-policy>"));
			} catch (Exception e) {
				logger.warn("Unexpected error.", e);
			}
			return result;
		}
	}

	@WebMethod
	@SOAPBinding(parameterStyle=ParameterStyle.BARE, style=Style.DOCUMENT, use=Use.LITERAL)
	public void terminate(@WebParam final TerminateRequest params) {
		new Thread() {
			public void run() {
				logger.info("Node " + this + " is terminating...");
				// actually, the Runtime Shutdown Hook should also cause the terminate task to be executed 
				// when the Java process dies, but let's do the job here too, since we assume that doing 
				// it twice is better than not doing it at all..
				try {
					Runnable r = getTerminateTask(params);
					if(r != null) 
						r.run();
				} catch (Exception e) { }
				try {
					Thread.sleep(2500);
				} catch (InterruptedException e) { }
				System.exit(0);
			}
		}.start();
		return;
	}

	protected Runnable getTerminateTask(TerminateRequest params) {
		return null;
	}

	@WebMethod(exclude=true)
	public void deploy(String url) throws Exception {
		deploy(this, url);
	}
	@SuppressWarnings("all")
	public static void deploy(final Object service, String url, Handler<?> ... handler) throws AbstractNodeException {

		long t1 = System.currentTimeMillis();

		try {

		URL u = new URL(url);

		if(strUtil.isEmpty(System.getProperty(SYSPROP_HTTP_SERVER_PROVIDER_CLASS))) {
			System.setProperty(SYSPROP_HTTP_SERVER_PROVIDER_CLASS,
				JettyHttpServerProvider.class.getName());
		}

		ContextHandlerCollection chc = new ContextHandlerCollection();

		// disable log output from Metro and Jetty
		java.util.logging.Logger.getAnonymousLogger().getParent().setLevel(Level.WARNING);
		Class<?> cls3 = org.eclipse.jetty.server.Server.class;
		Class<?> cls4 = org.eclipse.jetty.server.AbstractConnector.class;
		org.apache.log4j.Level lev3 = Logger.getLogger(cls3).getLevel();
		org.apache.log4j.Level lev4 = Logger.getLogger(cls4).getLevel();
		Logger.getLogger(cls3).setLevel(org.apache.log4j.Level.WARN);
		Logger.getLogger(cls4).setLevel(org.apache.log4j.Level.WARN);

		JettyHttpServer httpServer = httpServers.get(u.getPort());
		Server server = servers.get(u.getPort());
		if(httpServer == null) {
			org.eclipse.jetty.util.log.Log.setLog(new Slf4jLog());
			server = new Server(u.getPort());

			SelectChannelConnector connector = new SelectChannelConnector();
	        connector.setPort(u.getPort());
	        connector.setAcceptQueueSize(1000);
	        connector.setThreadPool(new ExecutorThreadPool(GlobalThreadPool.getExecutorService()));
	        server.setConnectors(new Connector[]{connector});
	        
	        server.setHandler(chc);
	        
	        httpServer = new JettyHttpServer(server, true);

	        httpServers.put(u.getPort(), httpServer);
	        servers.put(u.getPort(), server);

			if(!server.isStarted())
				server.start();
		}
		
		JettyHttpContext wsContext1 = (JettyHttpContext)httpServer.createContext(u.getPath());
		//com.sun.net.httpserver.HttpHandler h;
		wsContext1.setHandler(new HttpHandler() {
			public void handle(com.sun.net.httpserver.HttpExchange ex) throws IOException {
				System.out.println("handle: " + ex);
			}
		});
		System.out.println("--> " + wsContext1.getHandler());
		Endpoint endpoint = Endpoint.create(service);
		if(service instanceof AbstractNode) {
			if(((AbstractNode)service).endpoint != null)
				logger.warn("AbstractNode " + service + " has apparently been double-deployed, " +
						"because there already exists an endpoint for this instance.");
			((AbstractNode)service).endpoint = endpoint;
		}
		
		// add JAX-WS handlers (e.g., needed for TeCoS invocation intercepting...)
		List<Handler> handlers = endpoint.getBinding().getHandlerChain();
		handlers.addAll(Arrays.asList(handler));
		endpoint.getBinding().setHandlerChain(handlers);
		if(service instanceof AbstractNode) {
			AbstractNode a = (AbstractNode)service;
			for(Handler h : handlers)
				if(!a.activeHandlers.contains(h))
					a.activeHandlers.add(h);
		}

		Class<?> cls1 = org.eclipse.jetty.util.component.AbstractLifeCycle.class;
		Class<?> cls2 = org.eclipse.jetty.server.handler.ContextHandler.class;
		org.apache.log4j.Level lev1 = Logger.getLogger(cls1).getLevel();
		org.apache.log4j.Level lev2 = Logger.getLogger(cls2).getLevel();
		try {
			String bindUrl = u.getProtocol() + "://0.0.0.0:" + u.getPort() + u.getPath();
			if(u.getQuery() != null) {
				bindUrl += "?" + u.getQuery();
			}
			Logger.getLogger(cls1).setLevel(org.apache.log4j.Level.OFF);
			Logger.getLogger(cls2).setLevel(org.apache.log4j.Level.WARN);
			logger.info("Binding service to " + bindUrl);
			endpoint.publish(bindUrl);
		} catch (Exception e) {
			if(e instanceof BindException || (e.getCause() != null && e.getCause() instanceof BindException)
					|| (e.getCause() != null && e.getCause().getCause() != null && e.getCause().getCause() instanceof BindException)) {
				/** we expect a BindException here, just swallow */ 
			} else {
				logger.warn("Unexpected error.", e);
			}
		} finally {
			Logger.getLogger(cls1).setLevel(lev1);
			Logger.getLogger(cls2).setLevel(lev2);
			Logger.getLogger(cls3).setLevel(lev3);
			Logger.getLogger(cls4).setLevel(lev4);
		}

		Field f = endpoint.getClass().getDeclaredField("actualEndpoint");
		f.setAccessible(true);
		
		// DO NOT do this (the two lines below), because HttpEndpoint creates some nasty 
		// compile-time dependencies with respect to JAXWS-RT. At runtime, we can (hopefully) 
		// assume that this class is present, in all newer Sun JVM implementations..
		//HttpEndpoint httpEndpoint = (HttpEndpoint)f.get(e1);
		//httpEndpoint.publish(wsContext1);
		Object httpEndpoint = f.get(endpoint);
		httpEndpoint.getClass().getMethod("publish", Object.class).invoke(httpEndpoint, wsContext1);

		Endpoint e2 = Endpoint.create(new CrossdomainXML());
		JettyHttpContext wsContext2 = (JettyHttpContext)httpServer.createContext("/crossdomain.xml");
		e2.publish(wsContext2);
		

		// Also deploy as RESTful service..
		if(service instanceof AbstractNode) {
			AbstractNode node = (AbstractNode)service;
			if(node.isRESTfulService()) {
				String path = u.getPath();
				if(!path.contains("/"))
					path = "/";
				path = "/rest";
				String wadlURL = netUtil.getUrlBeforePath(u) + path + "/application.wadl";
				if(logger.isDebugEnabled()) logger.debug("Deploying node as RESTful service: " + wadlURL);
				JettyHttpContext wsContext3 = (JettyHttpContext)httpServer.createContext(path);
	            
	            ResourceConfig rc = new PackagesResourceConfig(
	            		service.getClass().getPackage().getName(), AbstractNode.class.getPackage().getName());
	            HttpHandler h = RuntimeDelegate.getInstance().createEndpoint(rc, HttpHandler.class);
	            wsContext3.setHandler(h);
	            node.setWadlURL(wadlURL);
			}
			deployedNodes.put(url, node);
		}

		final HttpHandler h = wsContext1.getHandler();
		wsContext1.setHandler(new HttpHandler() {
			public void handle(com.sun.net.httpserver.HttpExchange ex)
					throws IOException {

				if(!ex.getRequestMethod().equals("OPTIONS")) {
					//System.out.println("handle");
					h.handle(ex);
				}
				//System.out.println(new HashMap<>(ex.getResponseHeaders()));

				// Allow CORS policy (cross-domain requests, from Web browsers)
				ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
				ex.getResponseHeaders().add("Access-Control-Allow-Headers", 
						"Origin, Content-Type, Accept, Transfer-Encoding, "
						+ "Accept-Encoding, Accept-Language, Connection, Cookie, Host, "
						+ "Referer, User-Agent, Server, Authorization, x-requested-with");
				ex.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
				ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
				ex.getResponseHeaders().add("Access-Control-Max-Age", "1209600");
				ex.getResponseHeaders().add("Access-Control-Expose-Headers", "Location");

				if(ex.getRequestMethod().equals("OPTIONS")) {
					ex.sendResponseHeaders(200, -1);
					ex.getResponseBody().close();
					return;
				}
				//System.out.println(new HashMap<>(ex.getResponseHeaders()));
			}
		});

		// add shutdown task for this node
		if(service instanceof AbstractNode) {
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					try {
						Runnable r = ((AbstractNode)service).getTerminateTask(null);
						if(r != null) 
							r.run();
					} catch (Exception e) { }
				}
			});
		}
		
		
		} catch (Exception e) {
			throw new AbstractNodeException(e);
		}

		long diff = System.currentTimeMillis() - t1;
		logger.info("Deployment took " + diff + "ms");

	}

	public static AbstractNode getDeployedNodeForResourceUri(UriInfo info) throws Exception {
		String absolute = info.getAbsolutePath().toString();
		URL u = new URL(absolute);
		absolute = absolute.replace("/rest/", "/");
		String path = u.getProtocol() + "://";
		absolute = absolute.substring(absolute.indexOf("://") + "://".length());
		path += absolute.substring(0, absolute.indexOf("/") + 1);
		absolute = absolute.substring(absolute.indexOf("/") + 1);
		path += absolute.substring(0, absolute.indexOf("/"));
		if(path.endsWith("REST")) {
			path = path.substring(0, path.length() - "REST".length());
		}
		AbstractNode n = deployedNodes.get(path);
		if(n == null) {
			n = deployedNodes.get(path + "?wsdl");
		}
		if(n == null) {
			if(logger.isDebugEnabled()) logger.debug(path + " not contained in node list " + deployedNodes);
			if(deployedNodes.size() == 1) {
				n = deployedNodes.values().iterator().next();
			}
		}
		return n;
	}
	
	public static AbstractNode getDeployedNodeForEndpoint(Endpoint endpoint) {
		// TODO
		throw new NotImplementedException();
	}

	protected boolean isRESTfulService() {
		Class<?> c = getClass();
		do {
			if(c.isAnnotationPresent(Path.class))
				return true;
			for(Method m : c.getDeclaredMethods()){
				if(m.isAnnotationPresent(Path.class))
					return true;
			}
			c = c.getSuperclass();
		} while(c != null && c != Object.class);
		return false;
	}

	@WebMethod
	@SOAPBinding(parameterStyle=ParameterStyle.BARE, style=Style.DOCUMENT, use=Use.LITERAL)
	@SuppressWarnings("all")
	public void setHandlerActive(@WebParam final SetHandlerActiveRequest params) throws AbstractNodeException {
		try {
		
		Class<?> clazz = Class.forName(params.handlerSuperClass);
		List<Handler> fromList = params.active ? inactiveHandlers : activeHandlers;
		List<Handler> toList = params.active ? activeHandlers : inactiveHandlers;
		boolean done = false;
		for(int i = 0; i < fromList.size(); i ++) {
			Handler<?> h = fromList.get(i);
			if(clazz.isAssignableFrom(h.getClass())) {
				fromList.remove(i--);
				if(!toList.contains(h)) {
					toList.add(h);
					done = true;
				} else {
					done = true;
				}
			}
		}
		try {
			if(!done && params.active) {
				Handler<?> h = (Handler<?>)clazz.newInstance();
				boolean alreadyIncluded = false;
				for(Handler h1 : activeHandlers) {
					if(clazz.isAssignableFrom(h1.getClass()))
						alreadyIncluded = true;
				}
				if(!alreadyIncluded)
					activeHandlers.add(h);
			}
		} catch (Exception e) {
			endpoint.getBinding().setHandlerChain(activeHandlers);
			throw e;
		}
		endpoint.getBinding().setHandlerChain(activeHandlers);
		
		} catch (Exception e) {
			throw new AbstractNodeException(e);
		}
	}

	public static <T extends AbstractNode> T selectRandomAvailableNode(List<T> candidates) {
		if(candidates == null || candidates.isEmpty())
			return null;
		int numAttempts = 100;
		for(int i = 0; i < numAttempts; i ++) {
			T node = collUtil.getRandom(candidates);
			if(node == null)
				return null;
			if(netUtil.isPortOpen(node.getEPR().getAddress())) {
				return node;
			}
		}
		throw new IllegalArgumentException(
				"Unable to determine available node in candidate list after " + numAttempts + " attempts.");
	}

	@XmlTransient
	protected EndpointReference getEpr() {
		return epr;
	}
	protected void setEpr(EndpointReference epr) {
		this.epr = epr;
	}
	
	@Override
	@WebMethod(exclude=true)
	public String toString() {
		if(getEPR() == null || getEPR().getAddress() == null)
			return super.toString();
		return "[" + getEPR().getAddress() + "]";
	}
	
	@Override
	@WebMethod(exclude=true)
	public boolean equals(Object o) {
		if(!(o instanceof AbstractNode))
			return false;
		AbstractNode n = (AbstractNode)o;
		if(n.getEPR() == null) return false;
		return n.getEPR().equals(this.getEPR());
	}
	
	@Override
	@WebMethod(exclude=true)
	public int hashCode() {
		if(getEpr() == null)
			return super.hashCode();
		return getEpr().hashCode();
	}
	
}
