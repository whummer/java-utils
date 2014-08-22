package io.hummer.util.cloud;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CloudInstance {
	public final String publicHost;
	public final String privateHost;
	public CloudInstance(String publicHost, String privateHost) {
		this.publicHost = publicHost;
		this.privateHost = privateHost;
	}
	private static String extractIP(String eucaDnsName) {
		String regex = ".*[^\\d](\\d+-\\d+-\\d+-\\d+).*";
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(eucaDnsName);
		if(!m.matches())
			return eucaDnsName;
		return m.group(1).replace("-", ".");
	}
	public String getPrivateIP() {
		return extractIP(privateHost);
	}
	public String getPublicIP() {
		return extractIP(publicHost);
	}
}
