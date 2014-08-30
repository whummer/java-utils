package io.hummer.util.log;

import io.hummer.util.cp.ClasspathUtil;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

/**
 * Logging utils.
 * @author Waldemar Hummer
 */
public class LogUtil {

	private static final AtomicBoolean loggerInitialized = new AtomicBoolean(false); /* this variable needs to stay BEFORE the logger initialization call getLogger(...)! */

	/**
	 * Determines the calling class from the Thread's 
	 * callstack and returns a logger for this class.
	 * 
	 * The entry we want is at position 2, example:
	 * 
	 * 0: java.lang.Thread.getStackTrace()
	 * 1: at.ac.tuwien.infosys.util.Util.getLogger()
	 * 2: my.package.MyClass.myInitMethod()
	 * 3: ...
	 * @return logger for the calling class
	 */
	public static Logger getLogger() {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		try {
			return getLogger(Class.forName(stack[2].getClassName()));
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
 
	public static java.util.logging.Logger getLogger(String name) {
		java.util.logging.Logger logger = java.util.logging.Logger.getLogger(name);
		logger.setParent(java.util.logging.Logger.getLogger(java.util.logging.Logger.GLOBAL_LOGGER_NAME));
		return logger;
	}

	public static Logger getLogger(Class<?> c) {
		Object lock = loggerInitialized != null ? loggerInitialized : new Object();
		synchronized(lock) {
			//System.out.println(Thread.currentThread() + " in locked zone");
			final String logPropFile = "log4j.properties";
			if(loggerInitialized == null || !loggerInitialized.get()) {
				if(loggerInitialized != null) loggerInitialized.set(true);

				File conf = new File(logPropFile);
				//System.out.println("sysconfig: ---> " + System.getProperty("log4j.configuration") + " - " + conf.exists());
				if(conf.exists()
						&& System.getProperty("log4j.configuration") == null) {
					System.out.println("Logger configuration (file): " + new File(logPropFile).toURI());
					System.setProperty("log4j.configuration", "file:" + conf);
					if(loggerInitialized != null) loggerInitialized.set(true);
				} else if(!conf.exists()) {
					final Properties props = new Properties();
					try {
						Map<URL,String> res = ClasspathUtil.getSystemResources(
								logPropFile, "^((?!((org/apache/xml/security)|(foobar))).)*$", false, true);
						if(!res.isEmpty()) {
							//System.out.println("Found " + logPropFile + " system resources: " + res);
							for(URL u : res.keySet()) {
								InputStream configStream = u.openStream();
								props.load(configStream);
								System.out.println("Logger config (classpath): " + u);
								configStream.close();
							}
							//System.out.println("Done loading system resources.");
						} else if(ClassLoader.getSystemResource("/" + logPropFile) != null) {
							System.out.println("Loading " + logPropFile + " via ClassLoader system stream.");
							props.load(ClassLoader.getSystemResourceAsStream("/" + logPropFile));
						} else if(c.getClass().getResource(logPropFile) != null) {
							System.out.println("Loading " + logPropFile + " via resource stream.");
							props.load(c.getClass().getResourceAsStream(logPropFile));
						} else if(c.getClass().getClassLoader() != null && 
								c.getClass().getClassLoader().getResource(logPropFile) != null) {
							System.out.println("Loading " + logPropFile + " via classloader resource stream of class " + c.getClass());
							props.load(c.getClass().getClassLoader().getResourceAsStream(logPropFile));
						} else if(ClassLoader.getSystemClassLoader().getResource(logPropFile) != null) {
							System.out.println("Loading " + logPropFile + " via ClassLoader resource stream.");
							props.load(ClassLoader.getSystemClassLoader().getResourceAsStream(logPropFile));
						} else {
							System.out.println(
									"Error: Cannot find log4j configuration file.");
						}
					} catch(Exception e) {
						System.out.println(
								"Error: Cannot load log4j configuration file: " + e);
						e.printStackTrace();
					}
					/* determine whether we are running within Storm (creates threading problems) */
					boolean isStorm = false;
					try {
						Class.forName("backtype.storm.utils.Utils");
						System.out.println("Found Storm classes (http://nathanmarz.github.io) on the classpath, " +
								"which introduce threading/synchronization issues. Leaving Logger unconfigured!");
						isStorm = true;
					} catch (Exception e) { /*swallow*/ }

					if(!isStorm) {
						//System.out.println("Resetting logger config.");
						LogManager.resetConfiguration();
						//System.out.println("Configuring logger using properties: " + props);
						PropertyConfigurator.configure(props);
					}
				}
				
			}
			//System.out.println(Thread.currentThread() + " getting logger to return");
			Logger l = Logger.getLogger(c);
			//System.out.println(Thread.currentThread() + " returning logger " + l);
			return l;
		}
	}


}
