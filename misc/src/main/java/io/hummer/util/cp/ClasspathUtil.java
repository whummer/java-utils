package io.hummer.util.cp;

import io.hummer.util.Configuration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.vfs.UrlTypeVFS;
import org.reflections.vfs.Vfs;

/**
 * Often used utility functions for Java classpath handling.
 * 
 * @author Waldemar Hummer
 */
public class ClasspathUtil {

	private static final ClasspathUtil instance = new ClasspathUtil();

	/**
	 * reads a system resource, i.e., a file which is available at runtime
	 * either in one of the directories/JARs in the CLASSPATH, or in the directory
	 * from which the JVM is executing.
	 */
	public String getSystemResource(String name) {
		Map<URL,String> res = getSystemResources(name, null, true);
		if(res.isEmpty())
			return null;
		return res.values().iterator().next();
	}
	public Map<URL, String> getSystemResources(String name) {
		return getSystemResources(name, null, false);
	}
	public Map<URL, String> getSystemResources(String name, String urlPattern) {
		return getSystemResources(name, urlPattern, false);
	}
	private Map<URL, String> getSystemResources(String name, String urlPattern, boolean returnFirst) {
		return getSystemResources(name, urlPattern, returnFirst, true);
	}
	public static Map<URL, String> getSystemResources(String name, String urlPattern, boolean returnFirst, boolean dontUseLogger) {
		try {
			if(urlPattern == null)
				urlPattern = ".*";
			Map<URL, String> result = new HashMap<URL, String>();
			Set<URL> urls = new HashSet<URL>();
			urls.addAll(ClasspathHelper.forPackage(""));
			Configuration.fixJavaClasspath(); /* required to avoid bug in reflections lib. */
			urls.addAll(ClasspathHelper.forJavaClassPath());
//			if(!dontUseLogger) {
//				logger.info("Reflection urls: " + urls);
//			}
			Vfs.addDefaultURLTypes(new UrlTypeVFS());
			Reflections reflections = new Reflections(
					new ConfigurationBuilder()
	                .setUrls(urls)
					.setScanners(new ResourcesScanner()));
			if(name.startsWith("/"))
				name = name.substring(1);
			Set<String> files = reflections.getResources(Pattern.compile(name));
			if(files != null && !files.isEmpty()) {
				for(String f : files) {
					for(URL u : doGetSystemResources(f)) {
						//System.out.println("--> " + u);
						if(u.toString().matches(urlPattern)) {
							String content = new String(readBytes(u.openStream()));
							result.put(u, content);
							if(returnFirst)
								return result;
						}
					}
				}
			}
			return result;
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static List<URL> doGetSystemResources(String name) throws IOException {
		List<URL> result = new LinkedList<URL>();
		Enumeration<URL> e = ClassLoader.getSystemResources(name);
		while(e.hasMoreElements()) {
			result.add(e.nextElement());
		}
		return result;
	}

	public static byte[] readBytes(InputStream in) throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		int b = 0;
		while((b = in.read()) >= 0)
			bos.write(b);
		in.close();
		bos.close();
		return bos.toByteArray();
	}

	public static synchronized ClasspathUtil getInstance() {
		return instance;
	}

}
