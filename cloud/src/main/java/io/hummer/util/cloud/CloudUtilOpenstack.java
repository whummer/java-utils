package io.hummer.util.cloud;

import io.hummer.util.Configuration;
import io.hummer.util.log.LogUtil;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javassist.util.proxy.MethodFilter;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;

import org.apache.log4j.Logger;
import org.jclouds.Constants;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.openstack.nova.NovaClient;
import org.jclouds.openstack.nova.domain.Server;
import org.jclouds.openstack.nova.options.CreateServerOptions;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.google.common.collect.ImmutableMap;

/**
 * This class provides various utility methods to access 
 * Openstack Cloud installations, retrieve running instances, 
 * start new instances, get user information, etc..
 * 
 * @author Waldemar Hummer
 */
@SuppressWarnings("all")
public class CloudUtilOpenstack extends CloudUtilEuca {

	static final Logger logger = LogUtil.getLogger(CloudUtilOpenstack.class);
	private static final String STATE_RUNNING = "running";

	/** Openstack seems to require only a very small delay 
	 * between requests (in contrast to Eucalyptus), 
	 * or actually no delay at all. */
	private static final long MIN_DELAY_BETWEEN_REQUESTS_IN_MS = 100;
	
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
		
		
		/* 
		 * TODO: openstack API to launch new instances
		 *  NovaServerForCreate sfc = new NovaServerForCreate();
			sfc.setName("Dynamic CloudScale Instance");
			sfc.setFlavorRef('1');
			sfc.setImageRef('aceed938-b246-4dae-9c50-d06f01069ae6');
			sfc.setKeyName('default');
			Server newServer = openstack.getComputeClient().createServer(sfc);
		 * */

		if(publicIP) {
			
			String providerURL = Configuration.getHost(Configuration.PROP_CLOUD_CLC);
			String apiKey = "foobar"; // TODO!
			String imageId = "2";
			String flavorId = "1";
			String identity = "hummer";
			int numInstances = 1;
			
			//ProviderMetadata d = new AnonymousProviderMetadata(new NovaApiMetadata(), providerURL);
			
			Properties overrides = new Properties();
			overrides.setProperty(Constants.PROPERTY_ENDPOINT, providerURL);

			@SuppressWarnings("deprecation")
			ComputeServiceContext context = new ComputeServiceContextFactory()
					.createContext("nova", identity, apiKey, 
							//ImmutableSet.<Module>of(new JschSshClientModule()),
							overrides);

			// run some nodes
			//Set<? extends NodeMetadata> nodes = 
			//		context.getComputeService().createNodesInGroup("webserver", numInstances);
			//logger.info("Successfully started " + nodes.size() + " new instance(s)");

			// when you need access to nova-specific features, use the provider-specific context
			NovaClient novaClient = NovaClient.class.cast(
					context.getProviderSpecificContext().getApi());

			Map<String, String> metadata = ImmutableMap.of("jclouds", "nova");
			CreateServerOptions opt = new CreateServerOptions();
			opt = opt.withMetadata(metadata);
			Server server = novaClient.createServer("myservername", imageId, flavorId, opt);
			System.out.println("Started server: " + server);

			context.close();


			
//			NovaComputeServiceAdapter service = new NovaComputeServiceAdapter()
//			Context ctx = new RestContextImpl(d, identity, utils, new Closer());
//			ComputeServiceContext context = new ComputeServiceContextImpl(, backendType, computeService, utils).createContext
//			NovaComputeService s = new novacom
//			NovaClient c = NovaApiMetadata.
//			ServersResource serversResource = openstack.getComputeEndpoint().servers();
//			ServerResource serverResource = serversResource.server(newServer.getId());
//			AssociateFloatingIpAction action = new AssociateFloatingIpAction("128.131.172.110");
//			serverResource.action().post(action, String.class);
		}
		
		if(blockUntilRunning) {
			String reservationID = res.getReservation().getReservationId();
			while(!allInstancesRunning(reservationID)) {
				logger.info("Waiting for instances in reservation " + reservationID);
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

	protected long getMinDelayBetweenRequests() {
		return MIN_DELAY_BETWEEN_REQUESTS_IN_MS;
	}

}
