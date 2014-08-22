package io.hummer.util.cloud;

import io.hummer.util.Configuration;
import io.hummer.util.log.LogUtil;
import io.hummer.util.misc.MiscUtil;
import io.hummer.util.str.StringUtil;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javassist.util.proxy.MethodFilter;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;

import org.apache.log4j.Logger;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;

/**
 * This class provides various utility methods to access 
 * Eucalyptus Cloud installations, retrieve running instances, 
 * start new instances, get user information, etc..
 * 
 * @author Waldemar Hummer
 */
public class CloudUtilEuca extends CloudUtil {
	
	static final Logger logger = LogUtil.getLogger(CloudUtilEuca.class);
	private static final String STATE_RUNNING = "running";
	
	private static final long MIN_DELAY_BETWEEN_REQUESTS_IN_MS = 1100;
	protected static long lastRequestTime;

	/**
	 * Returns an EC2/Euca client wrapper by a Java proxy. The wrapper
	 * takes care of keeping track of the request timestamps and inserts
	 * sleep times to avoid denial of service by the Euca server.
	 * @return
	 * @throws Exception
	 */
	private AmazonEC2Client getClient() throws Exception {

		AWSCredentials cred = new PropertiesCredentials(new File(Configuration.CLOUD_CREDENTIALS_FILE));
		ClientConfiguration config = new ClientConfiguration();

		final AmazonEC2Client actualClient = new AmazonEC2Client(cred, config);
		actualClient.setEndpoint(Configuration.getValue(Configuration.PROP_CLOUD_CLC));

	    ProxyFactory factory = new ProxyFactory();
	    factory.setSuperclass(AmazonEC2Client.class);
	    factory.setFilter(
	            new MethodFilter() {
					public boolean isHandled(Method method) {
	                    return Modifier.isPublic(method.getModifiers());
	                }
	            }
	        );
	    MethodHandler handler = new MethodHandler() {
	        public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
	        	/* sleep between invocations to avoid Eucalyptus DoS */
	            sleepToAvoidDoS();
	            Object result = null;
	            try {
	            	/* perform actual invocation */
	            	result = thisMethod.invoke(actualClient, args);
				} catch (Exception e) {
					/* this may be an indicator that we have not waited long enough 
					 * between invocations. --> Sleep a while and give it one more try. */
					Thread.sleep(1500);
	            	result = thisMethod.invoke(actualClient, args);
				}
	        	/* update last request timestamp */
	            lastRequestTime = System.currentTimeMillis();
	            return result;
	        }
	    };

	    AmazonEC2Client clientWrapper = (AmazonEC2Client) factory.create(
	    		new Class<?>[]{AWSCredentials.class, ClientConfiguration.class}, 
	    		new Object[]{cred, config}, handler);

		return clientWrapper;
	}

	public void startNewInstance(String imageID, boolean publicIP, boolean blockUntilRunning) throws Exception {
		RunInstancesRequest req = new RunInstancesRequest();
		//boolean priv = Configuration.getBoolean(Configuration.PROP_CLOUD_PRIV_ADDR, true);
		boolean priv = !publicIP;
		String instanceType = Configuration.getValue(Configuration.PROP_CLOUD_INSTANCE_TYPE);
		String keyName = Configuration.getValue(Configuration.PROP_CLOUD_KEY_NAME);
		if(Configuration.containsKey(Configuration.PROP_CLOUD_GROUPS)) {
			Set<String> groups = new HashSet<String>(Arrays.asList(
					Configuration.getValue(Configuration.PROP_CLOUD_GROUPS).split("( |,)")));
			groups.remove("");
			req.setSecurityGroups(groups);
		}
		req.setImageId(imageID);
		req.setKeyName(keyName);
		req.setMinCount(1);
		req.setInstanceType(instanceType);
		req.setPrivateAddressing(priv);

		RunInstancesResult res = getClient().runInstances(req);

		if(blockUntilRunning) {
			String reservationID = res.getReservation().getReservationId();
			while(!allInstancesRunning(reservationID)) {
				System.out.println("Waiting for instances in reservation " + reservationID);
				Thread.sleep(5000);
			}
		}
	}

	private boolean allInstancesRunning(String reservationID) throws Exception {
		DescribeInstancesRequest r = new DescribeInstancesRequest();
		DescribeInstancesResult res1 = getClient().describeInstances(r);
		for(Reservation rsrv : res1.getReservations()) {
			if(rsrv.getReservationId().equals(reservationID)) {
				for(Instance i : rsrv.getInstances()) {
					String state = i.getState().getName();
					if(!state.equalsIgnoreCase(STATE_RUNNING)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	public Set<String> getImageIDsForManifestName(String manifestPattern) throws Exception {
		Set<String> result = new HashSet<String>();
		for(Image i : getClient().describeImages().getImages()) {
			String manifest = i.getImageLocation();
			if(manifest != null && manifest.matches(manifestPattern)) {
				result.add(i.getImageId());
			}
		}
		return result;
	}

	public Set<String> getImageIDsForImageName(String namePattern) throws Exception {
		Set<String> result = new HashSet<String>();
		for(Image i : getClient().describeImages().getImages()) {
			logger.info("Image name: " + i.getName());
			String name = i.getName();
			if(name != null && name.matches(namePattern)) {
				result.add(i.getImageId());
			}
		}
		return result;
	}

	public List<CloudInstance> getInstances(String imageID, String userID) throws Exception {
		StringUtil strUtil = new StringUtil();
		DescribeInstancesRequest req = new DescribeInstancesRequest();
		DescribeInstancesResult res = getClient().describeInstances(req);
		List<CloudInstance> result = new LinkedList<CloudInstance>();
		for(Reservation reserv : res.getReservations()) {
			if(userID == null || reserv.getOwnerId().equals(userID)) {
				for(Instance i : reserv.getInstances()) {
					if(isRunningState(i.getState())) {
						if(imageID == null || i.getImageId().equals(imageID)) {
							String pub = !strUtil.isEmpty(i.getPublicIpAddress()) 
									? i.getPublicIpAddress() : i.getPublicDnsName();
							String priv = !strUtil.isEmpty(i.getPrivateIpAddress()) 
									? i.getPrivateIpAddress() : i.getPrivateDnsName();
							result.add(new CloudInstance(pub, priv));
						}
					}
				}
			}
		}
		return result;
	}

	public boolean isRunningState(InstanceState s) {
		return s != null && s.getName().equalsIgnoreCase(STATE_RUNNING);
	}

	/**
	 * Eucalyptus only allows a certain number of user requests per time unit,
	 * and blocks with a Denial of Service (DoS) if too many requests are sent.
	 * Hence, we keep track if the last request time and use this method to 
	 * sleep between requests.
	 */
	protected void sleepToAvoidDoS() {
		long sleep = (lastRequestTime + getMinDelayBetweenRequests()) - System.currentTimeMillis();
		if(sleep > 0) {
			new MiscUtil().sleep(sleep);
		}
	}

	protected long getMinDelayBetweenRequests() {
		return MIN_DELAY_BETWEEN_REQUESTS_IN_MS;
	}

}
