package io.hummer.util.net;

import io.hummer.util.Configuration;
import io.hummer.util.log.LogUtil;
import io.hummer.util.net.NetUtil;
import io.hummer.util.ws.AbstractNode;
import io.hummer.util.ws.DynamicWSClient;

import java.net.MalformedURLException;
import java.net.URL;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.log4j.Logger;

@WebService(targetNamespace=Configuration.NAMESPACE_INFOSYS_TOOLS, name="NetProxy", serviceName="NetProxy")
public interface INetProxy {

	@WebService(targetNamespace=Configuration.NAMESPACE_INFOSYS_TOOLS, name="NetProxy", serviceName="NetProxy")
	public static class NetProxy extends AbstractNode implements INetProxy {

		private static final Logger logger = LogUtil.getLogger(NetProxy.class);
		public static final int DEFAULT_PORT = 15467;
		public static final String DEFAULT_URL = 
				"http://localhost:" + DEFAULT_PORT + "/NetProxy";
		public static final String DEFAULT_WSDL_URL = DEFAULT_URL + "?wsdl";

		@SOAPBinding(parameterStyle=ParameterStyle.BARE)
		public PingResponse ping(PingRequest req) {
			PingResponse res = new PingResponse();
			res.running = new NetUtil().ping(req.getHost());
			res.host = req.getHost();
			return res;
		}

		public static INetProxy getClient() {
			return getClient("localhost");
		}
		public static INetProxy getClient(String host) {
			if(!host.contains(":"))
				host += ":" + DEFAULT_PORT;
			try {
				String url = DEFAULT_URL.replace("localhost:" + DEFAULT_PORT, host);
				return getClient(new URL(url + "?wsdl"));
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}
		public static INetProxy getClient(URL url) {
			try {
				return DynamicWSClient.createClientJaxws(
						INetProxy.class, url);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		public static void startSafe() {
			startSafe(DEFAULT_URL);
		}
		public static void startSafe(String url) {
			try {
				start(url);
			} catch (Exception e) {
				logger.info("Unable to start NetProxy (maybe there is another instance already running?): " + e);
			}
		}
		public static void start() {
			start(DEFAULT_URL);
		}
		public static void start(String url) {
			try {
				new NetProxy().deploy(url);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	@XmlRootElement(name="ping")
	public static class PingRequest {
		@XmlElement
		private String host;
		@XmlElement
		private int port;

		public PingRequest() {}
		public PingRequest(String host) {}
		public PingRequest(String host, int port) {
			this.host = host;
		}
		
		@XmlTransient
		public String getHost() {
			return host;
		}
		public void setHost(String host) {
			this.host = host;
		}
		@XmlTransient
		public int getPort() {
			return port;
		}
		public void setPort(int port) {
			this.port = port;
		}
	}
	@XmlRootElement
	public static class PingResponse {
		@XmlElement
		private String host;
		@XmlElement
		private boolean running;
		
		@XmlTransient
		public boolean isRunning() {
			return running;
		}
		@XmlTransient
		public String getHost() {
			return host;
		}
		@Override
		public String toString() {
			return "[host " + host + " " + (running ? "up" : "down") + "]";
		}
	}

	@WebMethod
	@SOAPBinding(parameterStyle=ParameterStyle.BARE)
	PingResponse ping(PingRequest req);

}
