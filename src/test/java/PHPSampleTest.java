
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.CloudFoundryClient;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.CloudEntity;
import org.cloudfoundry.client.lib.domain.CloudService;
import org.cloudfoundry.client.lib.domain.CloudServiceOffering;
import org.cloudfoundry.client.lib.domain.Staging;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Uninterruptibles;


/**
 * 
 * @author 
 *
 */
public class PHPSampleTest
{


	private static final int DEFAULT_MEMORY = 512; // MB

	private static final String ENDPOINT = System.getProperty("endpoint", "https://api.run.pivotal.io");
	private static final String USER = System.getProperty("user");
	private static final String PASSWORD = System.getProperty("password");
	private static final String ORG = System.getProperty("org", "rsucasas-org");
	private static final String SPACE = System.getProperty("space", "development");
	private static final boolean ALL_CERTS = Boolean.parseBoolean(System.getProperty("trustSelfSignedCerts", "true"));
	private static final String MYSQL_SERVICE_LABEL = System.getProperty("vcap.mysql.label", "cleardb");
	private static final String MYSQL_SERVICE_PLAN = System.getProperty("vcap.mysql.plan", "spark");
	private static final String BUILDPACK_PHP = System.getProperty("buildpack", "https://github.com/cloudfoundry/php-buildpack.git");

	private static final String TEST_APP_DIR = "src/tests/resources";
	private static final String APP_NAME = "seaclouds_test-0";
	private static final String SERVICE_NAME = "mysql-test";

	private CloudFoundryClient client;


	@BeforeClass
	public void init() throws IOException
	{
		CloudCredentials credentials = new CloudCredentials(USER, PASSWORD);
		client = new CloudFoundryClient(credentials, getTargetURL(ENDPOINT), ORG, SPACE, ALL_CERTS);
		client.login();
	}


	@AfterClass
	public void tearDown()
	{
		client.deleteAllServices();
		client.deleteAllApplications();
	}


	@Test
	public void testCreateDB()
	{
		System.out.println("testCreateDB ... ");
		
		Optional<CloudServiceOffering> optionalCloudServiceOffering = Iterables.tryFind(client.getServiceOfferings(),
			new Predicate<CloudServiceOffering>()
			{
				public boolean apply(CloudServiceOffering input)
				{
					return input.getLabel().equalsIgnoreCase(MYSQL_SERVICE_LABEL);
				}
			});
		if (!optionalCloudServiceOffering.isPresent())
		{
			Assert.fail();
		}
		CloudService serviceDB = new CloudService(CloudEntity.Meta.defaultMeta(), SERVICE_NAME);
		final CloudServiceOffering cloudServiceOffering = optionalCloudServiceOffering.get();
		serviceDB.setLabel(cloudServiceOffering.getLabel());
		// TODO order plans and get the cheapest
		serviceDB.setPlan(MYSQL_SERVICE_PLAN);
		client.createService(serviceDB);
		assertNotNull(client.getService(SERVICE_NAME));
	}
	
	
	@Test(dependsOnMethods = "testCreateDB")
	public void testCreateApplication() 
	{
		System.out.println("testCreateApplication ... ");
		
		try 
		{
			String defaultDomainName = client.getDefaultDomain().getName();
			
			// initialize parameters ...
			Staging staging = new Staging(null, BUILDPACK_PHP);
			
			// uris
			List<String> uris = new ArrayList<String>();
		    uris.add(computeAppUrl(APP_NAME, defaultDomainName));
		    
		    // serviceNames
		    List<String> serviceNames = new ArrayList<String>();

		    // create application
		    client.createApplication(APP_NAME, staging, DEFAULT_MEMORY, uris, serviceNames);
	       
			assertNotNull(client.getApplication(APP_NAME));
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}


	@Test(dependsOnMethods = "testCreateApplication")
	public void testUploadAndStartPHPApplicationWithDB() throws IOException
	{
		System.out.println("testUploadAndStartPHPApplicationWithDB ... ");
		
		try
		{
			client.bindService(APP_NAME, SERVICE_NAME);
			
			// TODO process environment values
			
			File file = new File(TEST_APP_DIR + "cf-ex-php-info.zip");
			client.uploadApplication(APP_NAME, file.getCanonicalPath());
			
			client.startApplication(APP_NAME);
			
			CloudApplication app = client.getApplication(APP_NAME);
			while (app.getState() != CloudApplication.AppState.STARTED) {
	            app = client.getApplication(APP_NAME);
	            Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);
			}
			
			assertNotNull(client.getApplication(APP_NAME));
		}
		finally
		{
			client.stopApplication(APP_NAME);
		}
	}


	private static URL getTargetURL(String target)
	{
		try
		{
			return URI.create(target).toURL();
		}
		catch (MalformedURLException e)
		{
			throw new RuntimeException("The target URL is not valid: " + e.getMessage());
		}
	}


	private static String computeAppUrl(String appName, String defaultDomainName)
	{
		return appName + "." + defaultDomainName;
	}

	
}
