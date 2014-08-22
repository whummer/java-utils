package io.hummer.util.net;

import io.hummer.util.log.LogUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * This class provides utility methods related to networking,
 * sockets, IP communication, etc.
 * 
 * @author Waldemar Hummer
 */
public class NetUtil {

	private static final NetUtil instance = new NetUtil();

	private static Logger logger = LogUtil.getLogger();

	static final int DEFAULT_PORT = 80;
	static final int DEFAULT_TIMEOUT = 3*1000;

	public boolean isPortOpen(String url) {
		try {
			URL _url = new URL(url);
			return isPortOpen(_url);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	public boolean isPortOpen(URL url) {
		return isPortOpen(url.getHost(), url.getPort());
	}
	public boolean isPortOpen(int port) {
		return isPortOpen("localhost", port);
	}
	/**
	 * attempts to establish a connection to the given host and port,
	 * with default timeout of 3000 milliseconds.
	 * @param host
	 * @param port
	 * @return
	 */
	public boolean isPortOpen(String host, int port) {
		return isPortOpen(host, port, DEFAULT_TIMEOUT, false);
	}
	public boolean isPortOpen(String host, int port, long timeoutMS, boolean requirePingResponse) {
		boolean conn = false;
		try {
			host = host.trim();
			if(isLocalhost(host)) {
				/* try to open a server socket on the given port. */
				try {
					ServerSocket s = new ServerSocket(port);
					conn = false;
					s.close();
				} catch (Exception e) {
					conn = true;
				}
			} else {
				/* try to connect to the given remote host and port. */
				SocketAddress sockaddr = new InetSocketAddress(host, port);
				if(requirePingResponse) {
					if(!ping(host, (int)timeoutMS)) {
						return false;
					}
				}
				Socket s = new Socket();
				s.connect(sockaddr, (int)timeoutMS);
				conn = s.isConnected();
				s.close();
			}
		} catch (Exception e) { /* swallow */ }
		return conn;
	}

	public URL replaceHost(URL url, String newHost) {
		return replaceHost(url.toString(), newHost);
	}
	public URL replaceHost(String url, String newHost) {
		try {
			if(newHost.contains(":")) {
				/* host with port */
				return new URL(url.replaceAll("([^/]*://)[^/]*(/.*)", "$1" + newHost + "$2"));
			} else {
				/* host without port */
				return new URL(url.replaceAll("([^/]*://)[^:]*(:.*)", "$1" + newHost + "$2"));
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public boolean ping(String host) {
		return ping(host, DEFAULT_TIMEOUT);
	}
	public boolean ping(String host, int timeout) {
		try {
			InetAddress a = InetAddress.getByName(host);
			return a.isReachable(timeout);
		} catch (Exception e) {
			logger.info("Unable to perform ping.", e);
			return false;
		}
	}

	public long getDownloadSize(String url) throws MalformedURLException {
		return getDownloadSize(new URL(url));
	}
	public long getDownloadSize(URL url) {
		try {
			List<?> values = url.openConnection().getHeaderFields().get("Content-Length");
			if (values != null && !values.isEmpty()) {
				String sLength = (String) values.get(0);
				System.out.println("Content-Length: " + sLength);
				if (sLength != null) {
					return Long.parseLong(sLength);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			/* swallow */
		}
		return -1;
	}

	public boolean isLocalhost(String host) {
		// TODO improve/extend this method..
		host = host.trim();
		if(host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("0.0.0.0")) {
			return true;
		}
		return false;
	}
	
	public static NetUtil getInstance() {
		return instance;
	}

	public String getUrlBeforePath(URL u) {
		if(u == null)
			return null;
		String url = u.toExternalForm();
		String out = "";
		out = u.getProtocol() + "://";
		url = url.substring(url.indexOf("://") + "://".length());
		if(!url.contains("/")) {
			out += url;
		} else {
			out += url.substring(0, url.indexOf("/"));
		}
		return out;
	}

}
