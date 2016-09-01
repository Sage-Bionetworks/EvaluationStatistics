package org.sagebionetworks;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.SynapseClientImpl;
import org.sagebionetworks.client.SynapseProfileProxy;
import org.sagebionetworks.utils.DefaultHttpClientSingleton;
import org.sagebionetworks.utils.HttpClientHelper;


public class SynapseClientFactory {

	private static final int TIMEOUT_MILLIS = 5 * 60 * 1000; // 5 minutes, as milliseconds   

	private static SynapseClient createSynapseClientIntern() {
		HttpClientHelper.setGlobalConnectionTimeout(DefaultHttpClientSingleton.getInstance(), TIMEOUT_MILLIS);	
		HttpClientHelper.setGlobalSocketTimeout(DefaultHttpClientSingleton.getInstance(), TIMEOUT_MILLIS);	
		SynapseClientImpl scIntern = new SynapseClientImpl();
		scIntern.setAuthEndpoint("https://repo-prod.prod.sagebase.org/auth/v1");
		scIntern.setRepositoryEndpoint("https://repo-prod.prod.sagebase.org/repo/v1");
		scIntern.setFileEndpoint("https://repo-prod.prod.sagebase.org/file/v1");
		return SynapseProfileProxy.createProfileProxy(scIntern);
	}

	public static SynapseClient createSynapseClient() {
		final SynapseClient synapseClientIntern = createSynapseClientIntern();

		InvocationHandler handler = new InvocationHandler() {
			public Object invoke(final Object proxy, final Method method, final Object[] args)
					throws Throwable {
				return ExponentialBackoffUtil.executeWithExponentialBackoff(new Executable<Object>() {
					public Object execute() throws Throwable {
						try {
							return method.invoke(synapseClientIntern, args);
						} catch (IllegalAccessException  e) {
							throw new RuntimeException(e);
						} catch (InvocationTargetException e) {
							if (e.getCause()==null) throw e; else throw e.getCause();
						}
					}
				});
			}
		};
			
		return (SynapseClient) Proxy.newProxyInstance(SynapseClient.class.getClassLoader(),
				new Class[] { SynapseClient.class },
				handler);
	}

}
