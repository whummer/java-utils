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
package io.hummer.util;

import io.hummer.util.log.LogUtil;
import io.hummer.util.str.UriReplacer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import com.google.common.io.Resources;

/**
 * This file provides easy access to configuration properties (key/value pairs) 
 * which are configured in a configuration file. The lookup of configuration files is 
 * as follows:
 * 1) resource /infosys.properties which comes bundled with the InfosysTools.jar file
 * 2) resource config.properties in any of the folders/packages on the classpath
 * 3) file config.properties, seen from the execution directory of the JVM
 * 4) file ../config.properties, seen from the execution directory of the JVM
 * 5) file ../../config.properties, seen from the execution directory of the JVM
 *
 * Properties in later found files overwrite previously found property values.
 *
 * @author Waldemar Hummer
 */
public class Configuration {

	private static Properties props;
	private static final Properties manuallyOverwrittenProps = new Properties();
	private static Logger logger = LogUtil.getLogger(Configuration.class);
	public static final String NAMESPACE = "http://infosys.tuwien.ac.at/WS-Aggregation";
	public static final String NAMESPACE_INFOSYS_TOOLS = "http://dsg.tuwien.ac.at/Tools";

	public static final String PROP_REGISTRY_URL = "deploy.registry.url";
	public static final String PROP_GATEWAY_URL = "deploy.gateway.url";
	public static final String PROP_WEBAPP_URL = "deploy.webapp.url";
	public static final String PROP_HOST = "deploy.host";
	public static final String PROP_BINDHOST = "deploy.bindhost";
	public static final String PROP_IPLOOKUP_URL = "deploy.iplookup.url";
	public static final String PROP_IPLOOKUP_IGNORE = "deploy.iplookup.ignoreIPs";

	public static final String PROP_CACHE_IN_RAM = "cache.ram";
	public static final String PROP_CACHE_IN_DB = "cache.db";
	public static final String PROP_CACHE_OVERWRITE = "cache.overwrite";
	
	public static final String PROP_CAPTCHA_VERIFY_URL = "captcha.verification.url";
	public static final String PROP_CAPTCHA_PRIVATE_KEY = "captcha.privateKey";
	public static final String PROP_CAPTCHA_PUBLIC_KEY = "captcha.publicKey";

	public static final String PROP_CLOUD_CLC = "deploy.cloud.clc";
	public static final String PROP_CLOUD_PKEY = "deploy.cloud.privateKey";
	public static final String PROP_CLOUD_PRIV_ADDR = "deploy.cloud.privateAddressing";
	public static final String PROP_CLOUD_IMAGE_ID = "deploy.cloud.imageID";
	public static final String PROP_CLOUD_INSTANCE_TYPE = "deploy.cloud.instanceType";
	public static final String PROP_CLOUD_KEY_NAME = "deploy.cloud.keyName";
	public static final String PROP_CLOUD_GROUPS = "deploy.cloud.groups";
	public static final String PROP_CLOUD_UTIL_CLASS = "deploy.cloud.util.class";
	public static final String CLOUD_CREDENTIALS_FILE = 
			System.getProperty("user.home") + "/.euca/credentials.properties";

	public static final String SYSPROP_ADDITIONAL_CONFIGS = "infosys.configfiles";
	public static final String SYSPROP_ADDITIONAL_CONFIGS1 = "wscov.configfiles";
	
	private static List<URL> additionalConfigFiles = new LinkedList<URL>();


	public static synchronized String getValue(String key) {
		try {
			if(key == null)
				return null;
			return getProps().getProperty(key);
		} catch (Exception e) {
			logger.warn("Unable to get configuration value '" + key + "'.", e);
			return null;
		}
	}
	
	public static String getString(String key) {
		return getValue(key);
	}
	
	public static Boolean getBoolean(String key) {
		return getBoolean(key, null);
	}
	public static Boolean getBoolean(String key, Boolean dflt) {
		try {
			if(key == null)
				return dflt;
			String s = getProps().getProperty(key);
			if(s == null)
				return dflt;
			s = s.trim();
			if(s.equalsIgnoreCase("true") || s.equals("1")) 
				return true;
			if(s.equalsIgnoreCase("false") || s.equals("0")) 
				return false;
			throw new IllegalArgumentException("Unable to interpret boolean value of property named '" + key + "': " + s);
		} catch (Exception e) {
			logger.info("Unable to get Boolean property", e);
			return dflt;
		}
	}

	public static Integer getInteger(String key) {
		return getInteger(key, null);
	}
	public static Integer getInteger(String key, Integer dflt) {
		try {
			if(key == null)
				return dflt;
			String s = getProps().getProperty(key);
			if(s == null)
				return dflt;
			s = s.trim();
			try {
				return Integer.parseInt(s);
			} catch (Exception e) {
				throw new IllegalArgumentException("Unable to parse Integer value of property named '" + key + "': " + s);
			}
		} catch (Exception e) {
			logger.info("Unable to get Integer property", e);
			return dflt;
		}
	}

	public static Long getLong(String key) {
		return getLong(key, null);
	}
	public static Long getLong(String key, Long dflt) {
		try {
			if(key == null)
				return dflt;
			String s = getProps().getProperty(key);
			if(s == null)
				return dflt;
			s = s.trim();
			try {
				return Long.parseLong(s);
			} catch (Exception e) {
				throw new IllegalArgumentException("Unable to parse Long value of property named '" + key + "': " + s);
			}
		} catch (Exception e) {
			logger.info("Unable to get Long property", e);
			return dflt;
		}
	}
	
	public static synchronized void unsetValue(String key) throws Exception {
		setValue(key, null);
	}
	public static synchronized void setValue(String key, String value) throws Exception {
		if(value == null) {
			manuallyOverwrittenProps.remove(key);
		} else {
			manuallyOverwrittenProps.setProperty(key, value);
		}
	}
	
	public static boolean containsKey(String key) {
		String v = getValue(key);
		return (v != null) && !v.trim().isEmpty();
	}

	public static void findProperties(ClassLoader cl) {
		try {
			getProps().load(cl.getResourceAsStream("/infosys.properties"));
		} catch (Exception e) { }
		try {
			getProps().load(cl.getResourceAsStream("/config.properties"));
		} catch (Exception e) { }
	}
	public static void findProperties(Class<?> c) {
		try {
			getProps().load(c.getResourceAsStream("/infosys.properties"));
		} catch (Exception e) { }
		try {
			getProps().load(c.getResourceAsStream("/config.properties"));
		} catch (Exception e) { }
	}
	
	private static Properties getProps() throws Exception {
		if(props == null) {
			props = new Properties();
			try {
				Set<URL> seenURLs = new HashSet<URL>();
				@SuppressWarnings("all")
				List<Enumeration<URL>> resources = Arrays.asList(
						ClassLoader.getSystemResources("infosys.properties"),
						Configuration.class.getClassLoader().
						getResources("infosys.properties"));
				for(Enumeration<URL> e : resources) {
					while(e.hasMoreElements()) {
						try {
							URL url = e.nextElement();
							if(!seenURLs.contains(url)) {
								seenURLs.add(url);
								logger.info("Properties file: " + url);
								props.load(url.openStream());
							}
						} catch (Exception e2) {
							logger.debug("Error loading properties. ", e2);
						}
					}
				}
			} catch (Exception e) { }

			try {
				Map<URL,String> res = getSystemResources("infosys.properties");
				for(URL u : res.keySet()) {
					logger.info("Properties file: " + u);
					props.load(new ByteArrayInputStream(res.get(u).getBytes()));					
				}
			} catch (Exception e1) {
				try {
					props.load(Configuration.class.getResourceAsStream("/infosys.properties"));
				} catch (Exception e) { }
			}
			try {
				props.load(Configuration.class.getResourceAsStream("/config.properties"));
			} catch (Exception e) { }
			try {
				props.load(new FileInputStream("config.properties"));
				logger.info("Properties file: config.properties");
			} catch (Exception e) { }
			try {
				props.load(new FileInputStream("../config.properties"));
				logger.info("Properties file: ../config.properties");
			} catch (Exception e) { }
			try {
				props.load(new FileInputStream("../../config.properties"));
				logger.info("Properties file: ../../config.properties");
			} catch (Exception e) { }
			try {
				for(String sysProp : Arrays.asList(
						SYSPROP_ADDITIONAL_CONFIGS, 
						SYSPROP_ADDITIONAL_CONFIGS1)) {
					if(System.getProperty(sysProp) != null) {
						for(String s : System.getProperty(sysProp).split("[,;: ]+")) {
							if(s != null && !s.trim().isEmpty()) {
								File f = new File(s);
								boolean loaded = false;
								if(f.exists()) {
									props.load(new FileInputStream(f));
									logger.info("Properties file: " + f.toURI().toURL());
									additionalConfigFiles.add(f.toURI().toURL());
									loaded = true;
								} else {
									try {
										URL u = new URL(s);
										props.load(u.openStream());
										logger.info("Properties file: " + u);
										additionalConfigFiles.add(u);
										loaded = true;
									} catch (Exception e) {
										/* swallow */
									}
								}
								if(!loaded) {
									logger.info("Could not load configuration file requested via System property: " + s);
								}
							}
						}
					}
				}
			} catch (Exception e) { }
		}
		
		/* 
		 * combine properties from variable 'props' and overwrite 
		 * values with properties from 'manuallyOverwrittenProps'. 
		 */
		
		Properties p = new Properties(props);
		p.putAll(manuallyOverwrittenProps);
		
		return p;
	}

	public static boolean isLikelyBehindFirewall() {
		String dyn = doDynamicIpLookup(PROP_IPLOOKUP_URL);
		if(dyn != null)
			return !dyn.equals(getHost());
		return false;
	}

	public static String getPublicIP() {
		return doDynamicIpLookup(PROP_IPLOOKUP_URL);
	}
	public static String doDynamicIpLookup(String propNameIplookupURL) {
		boolean doAgain;
		int counter = 0;
		do {
			doAgain = false;
			try {
				if(!containsKey(propNameIplookupURL))
					return null;
				String url = getValue(propNameIplookupURL).trim();
				String myIP = readFile(new URL(url).openStream());
				myIP = myIP.trim();
				return myIP;
			} catch (UnknownHostException e) {
				if(counter++ < 3) {
					try { Thread.sleep(5000); } catch (InterruptedException e1) {}
					doAgain = true;
				}
			} catch (Exception e) {
				logger.warn("Unexpected error.", e);
			}
		} while(doAgain);
		return null;
	}
	
	public static String getHost(String ... configKey) {
		String ipLookedUp = doDynamicIpLookup(PROP_IPLOOKUP_URL);
		if(ipLookedUp != null && !ipLookedUp.trim().isEmpty()) {
			String ignore = getValue(PROP_IPLOOKUP_IGNORE);
			if(ignore != null) {
				List<String> ignoreIPs = Arrays.asList(ignore.trim().split(" "));
				if(!ignoreIPs.contains(ipLookedUp)) {
					return ipLookedUp;
				}
			} else {
				return ipLookedUp;
			}
		}
		for(String key : configKey) {
			if(containsKey(key)) {
				return getValue(key);
			}
		}
		try {
			InetAddress addrs[] = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
			for (InetAddress addr: addrs) {
				String address = addr.getHostAddress();
				if(!address.startsWith("127.0.") && /* loopback address */
						!address.contains(":") /* IPv6 addresses */
						) {
					return address;
				}
			}
		} catch (Exception e) {
			logger.info("Unable to determine address of localhost: " + e);
		}

		try {
			Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", 
				"/sbin/ifconfig eth0 | grep 'inet ' | cut -d: -f2 | awk '{ print $1}'"});
			String ip = readFile(p.getInputStream());
			if(ip != null) {
				return ip.trim();
			}
		    InetAddress addr = InetAddress.getLocalHost();
		    return addr.getHostName();
		} catch (Exception e) {
			logger.warn("Unexpected error.", e);
		}
		try {
			return InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			logger.warn("Unexpected error.", e);
			throw new RuntimeException(e);
		}
	}
	public static String getUrlWithVariableHost(String urlPropKey, String ... hostPropKey) throws Exception {
		if(hostPropKey == null || hostPropKey.length <= 0) {
			hostPropKey = new String[]{PROP_HOST};
		}
		String url = getValue(urlPropKey);
		if(url == null)
			return null;
		UriReplacer r = new UriReplacer(url);
		for(String k : hostPropKey) {
			if(containsKey(k)) {
				String host = getHost(k);
				return r.replaceHostname(host);
			}
		}
		return r.replaceHostname(getHost());
	}
	public static String getRegistryAddress() throws Exception {
		return getUrlWithVariableHost(PROP_REGISTRY_URL, PROP_HOST);
	}
	
	/* some helper function. We do NOT use these helpers from Util, 
	 * because of initialization dependencies between Util <--> Configuration. */
	
	static String getSystemResource(String name) {
		Map<URL,String> res = getSystemResources(name, true);
		if(res.isEmpty())
			return null;
		return res.values().iterator().next();
	}
	static Map<URL, String> getSystemResources(String name) {
		return getSystemResources(name, false);
	}
	private static Map<URL, String> getSystemResources(String name, boolean returnFirst) {
		try {
			Map<URL, String> result = new HashMap<URL, String>();
			Set<URL> urls = new HashSet<URL>();
			urls.addAll(ClasspathHelper.forPackage(""));
			fixJavaClasspath(); /* required to avoid bug in reflections lib. */
			urls.addAll(ClasspathHelper.forJavaClassPath());
			Reflections reflections = new Reflections(
					new ConfigurationBuilder()
	                .setUrls(urls)
					.setScanners(new ResourcesScanner()));
			Set<String> files = reflections.getResources(Pattern.compile(".*" + name + ".*"));
			if(files != null && !files.isEmpty()) {
				for(String f : files) {
					URL u = Resources.getResource(f);
					result.put(u, readFile(u.openStream()));
					if(returnFirst)
						return result;
				}
			}
			return result;
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
	public static void fixJavaClasspath() {
		String classpathProp = "java.class.path";
		String javaClassPath = System.getProperty(classpathProp);
		String newClasspath = "";
        if (javaClassPath != null) {
            for (String path : javaClassPath.split(File.pathSeparator)) {
                if(new File(path).exists()) {
                	newClasspath += path + File.pathSeparator;
                } else {
                	logger.info("Removing inexistent path from CLASSPATH " +
                			"(to avoid bug in reflections lib): " + path);
                }
            }
            System.setProperty(classpathProp, newClasspath);
        }
	}
	private static String readFile(InputStream in) throws Exception {
		return new String(readBytes(in));
	}
	private static byte[] readBytes(InputStream in) throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		int b = 0;
		while((b = in.read()) >= 0)
			bos.write(b);
		in.close();
		bos.close();
		return bos.toByteArray();
	}

}
