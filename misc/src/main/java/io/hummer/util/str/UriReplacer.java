/**
 * 
 */
package io.hummer.util.str;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Daniel Domberger
 *
 * Class to handle urls from the Testbeds config.xml. 
 */
public class UriReplacer {
	String uri;
	String hostId;
	
	public UriReplacer(String uri) {
		this.uri = uri;
		
		Pattern p = Pattern.compile("!\\(hostname#?(\\w*)\\).*");
		Matcher m = p.matcher(uri);
		if(m.find())
			hostId = m.group(1);
		else
			hostId = null;
	}
	
	/**
	 * Returns the HostId from the uri as String. An empty String if there is
	 * no HostId.
	 * @return	If present, the hostId of the uri. An empty String otherwise.
	 */
	public String getHostId() {
		return hostId;
	}
	
	public boolean hasFixedHostname() {
		return !uri.matches(".*!\\(hostname(#\\w*)?\\).*");
	}
	
	public String replaceHostname(String hostname) {
		uri = uri.replaceAll("!\\(hostname(#\\w*)?\\)", hostname);
		return uri;
	}

	/**
	 * Replaces the InstanceNo place holder with the given number
	 * @param number	Number to replace the InstanceNo placeholder with
	 * @return	The URL with the replaced InstanceNo
	 */
	public String replaceInstanceNo(int number) {
		uri = uri.replaceAll("!\\(instanceNo\\)", Integer.toString(number));
		return uri;
	}
	
	public static String replaceInstanceNo(String uri, int number) {
		return uri.replaceAll("!\\(instanceNo\\)", Integer.toString(number));
	}
	
	public static String replaceHostname(String uri, String hostname) {
		return uri.replaceAll("!\\(hostname(#\\w*)?\\)", hostname);
	}
	
	public String getUri() {
		return uri;
	}
}
