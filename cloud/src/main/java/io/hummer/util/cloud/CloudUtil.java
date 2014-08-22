package io.hummer.util.cloud;

import io.hummer.util.Configuration;
import io.hummer.util.io.IOUtil;
import io.hummer.util.log.LogUtil;

import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

/**
 * This abstract class provides various utility methods to access 
 * Cloud installations, retrieve running instances, 
 * start new instances, get user information, etc..
 * 
 * @author Waldemar Hummer
 */
public abstract class CloudUtil {

	private static CloudUtil instance;
	private static final Logger logger = LogUtil.getLogger(CloudUtil.class);

	public abstract void startNewInstance(String imageID, boolean publicIP, boolean blockUntilRunning) throws Exception;
	
	public abstract List<CloudInstance> getInstances(String imageID, String userID) throws Exception;

	/**
	 * This method is Eucalyptus-specific. It takes as argument a string pattern
	 * for an image manifest file name, and returns a set of image IDs which match 
	 * the pattern. The manifest file name in Eucalyptus contains the name of 
	 * the S3/Walrus bucket in which the image parts (and the manifest) are 
	 * stored, plus the actual file name of the manifest; e.g., myBucket/myImage.manifest.xml
	 * 
	 * @param manifestPattern
	 * @return
	 * @throws Exception
	 */
	public abstract Set<String> getImageIDsForManifestName(String manifestPattern) throws Exception;

	public abstract Set<String> getImageIDsForImageName(String namePattern) throws Exception;

	public void startNewInstance(String imageID) throws Exception {
		startNewInstance(imageID, false);
	}
	public void startNewInstance(String imageID, boolean publicIP) throws Exception {
		startNewInstance(imageID, publicIP, true);
	}

	/**
	 * This method is Eucalyptus-specific. It takes as argument a string pattern
	 * for an image manifest file name. The manifest file name in Eucalyptus contains
	 * the name of the S3/Walrus bucket in which the image parts (and the manifest) are 
	 * stored, plus the actual file name of the manifest; e.g., myBucket/myImage.manifest.xml
	 * 
	 * @param manifestPattern
	 * @return
	 * @throws Exception
	 */
	public String getImageIDForManifestName(String manifestPattern) throws Exception {
		Set<String> ids = getImageIDsForManifestName(manifestPattern);
		if(ids.isEmpty())
			return null;
		if(ids.size() > 1)
			logger.warn("multiple Cloud VM image IDs found for name pattern '" + manifestPattern + "': " + ids);
		return ids.iterator().next();
	}

	/**
	 * This method is Openstack-specific. It takes as argument a string pattern
	 * for an image name.
	 * 
	 * @param namePattern
	 * @return
	 * @throws Exception
	 */
	public String getImageIDForImageName(String namePattern) throws Exception {
		Set<String> ids = getImageIDsForManifestName(namePattern);
		if(ids.isEmpty())
			return null;
		if(ids.size() > 1)
			logger.warn("multiple Cloud VM image IDs found for name pattern '" + namePattern + "': " + ids);
		return ids.iterator().next();
	}

	public int getNumInstances(String imageID, String userID) throws Exception {
		return getInstances(imageID, userID).size();
	}
	
	public List<CloudInstance> getInstancesOfImage(String imageID) throws Exception {
		return getInstances(imageID, null);
	}
	public List<CloudInstance> getInstancesOfUser(String userID) throws Exception {
		return getInstances(null, userID);
	}
	public List<CloudInstance> getInstances() throws Exception {
		return getInstances(null, null);
	}
	
	public CloudInstance getInstanceByIP(String ip) throws Exception {
		for(CloudInstance i : getInstances()) {
			if(i.getPrivateIP().equals(ip) || i.getPublicIP().equals(ip)) {
				return i;
			}
		}
		return null;
	}
	
	public String getMyIP() {
		String ip = new IOUtil().exec("/sbin/ifconfig eth0 | grep 'inet addr:' | cut -d: -f2 | awk '{ print $1}'");
		if(ip != null) ip = ip.trim();
		return ip;
	}
	
	public String getIPPrivate(String publicOrPrivateIP) {
		try {
			CloudInstance i = getInstanceByIP(publicOrPrivateIP);
			if(i == null) {
				logger.warn("Instance not found for IP address: " + publicOrPrivateIP);
				return null;
			}
			return i.getPrivateIP();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	public String getIPPublic(String publicOrPrivateIP) {
		try {
			CloudInstance i = getInstanceByIP(publicOrPrivateIP);
			if(i == null) {
				logger.info("Found no Cloud VM Instance with IP: " + publicOrPrivateIP);
				return publicOrPrivateIP;
			}
			return i.getPublicIP();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String getMyIPPrivate() {
		return getIPPrivate(getMyIP());
	}
	public String getMyIPPublic() {
		return getIPPublic(getMyIP());
	}

	public static synchronized CloudUtil getInstance() {
		if(instance == null) {
			String className = Configuration.getValue(Configuration.PROP_CLOUD_UTIL_CLASS);
			Class<?> clazz;
			try {
				clazz = Class.forName(className);
				instance = (CloudUtil) clazz.getConstructor().newInstance();
			} catch(NoClassDefFoundError e) {
				logger.warn("Unable to instantiate CloudUtil instance; class: " + className + "; error: " + e);
			} catch (Exception e) {
				throw new RuntimeException("Unable to instantiate CloudUtil instance; class: " + className + "; error: " + e);
			} 
		}
		return instance;
	}

}
