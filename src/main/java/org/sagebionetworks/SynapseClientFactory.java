package org.sagebionetworks;

import static org.sagebionetworks.Constants.DEFAULT_NUM_RETRY_ATTEMPTS;
import static org.sagebionetworks.Constants.NO_RETRY_EXCEPTIONS;
import static org.sagebionetworks.Constants.NO_RETRY_STATUSES;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.sagebionetworks.client.SynapseClient;


public class SynapseClientFactory {

	private static ExtendedSynapseClient createSynapseClientIntern() {
		ExtendedSynapseClientImpl scIntern = new ExtendedSynapseClientImpl();
		scIntern.setAuthEndpoint("https://repo-prod.prod.sagebase.org/auth/v1");
		scIntern.setRepositoryEndpoint("https://repo-prod.prod.sagebase.org/repo/v1");
		scIntern.setFileEndpoint("https://repo-prod.prod.sagebase.org/file/v1");
		
	     InvocationHandler handler = new SynapseInvocationHandler(scIntern);
	     return (ExtendedSynapseClient) Proxy.newProxyInstance(ExtendedSynapseClient.class.getClassLoader(),
					new Class[] { ExtendedSynapseClient.class }, handler);

	}
	
	/**
	 * This handler just times and logs each call.
	 *
	 */
	private static class SynapseInvocationHandler implements InvocationHandler {

		public SynapseInvocationHandler(SynapseClient wrapped) {
			super();
			this.wrapped = wrapped;
		}

		private SynapseClient wrapped;
		
		@Override
		public Object invoke(Object proxy, Method method, Object[] args)
				throws Throwable {
			// Start the timer
			long start = System.currentTimeMillis();
			try{
				return method.invoke(wrapped, args);
			} catch (InvocationTargetException e) {
				// We must catch InvocationTargetException to avoid UndeclaredThrowableExceptions
				// see: http://amitstechblog.wordpress.com/2011/07/24/java-proxies-and-undeclaredthrowableexception/
				throw e.getCause();
			} finally{
				long elapse = System.currentTimeMillis()-start;
			}
		}
		
	}
	
	public static <T extends V,V> V createRetryingProxy(final T underlying, final Class<V> implementedInterface) {
		final ExponentialBackoffRunner exponentialBackoffRunner = new ExponentialBackoffRunner(
				NO_RETRY_EXCEPTIONS, NO_RETRY_STATUSES, DEFAULT_NUM_RETRY_ATTEMPTS);

		InvocationHandler handler = new InvocationHandler() {
			public Object invoke(final Object proxy, final Method method, final Object[] outerArgs)
					throws Throwable {
				return exponentialBackoffRunner.execute(new Executable<Object,Object[]>() {
					public Object execute(Object[] args) throws Throwable {
						try {
							Object result = method.invoke(underlying, args);
							return result;
						} catch (IllegalAccessException  e) {
							throw new RuntimeException(e);
						} catch (InvocationTargetException e) {
							if (e.getCause()==null) throw e; else throw e.getCause();
						}
					}
					public Object[] refreshArgs(Object[] args) {
						return args; // NO-OP
					}
				}, outerArgs);
			}
		};

		return (V) Proxy.newProxyInstance(SynapseClientFactory.class.getClassLoader(),
				new Class[] {implementedInterface },
				handler);
	}

	public static ExtendedSynapseClient createSynapseClient() {
		final ExtendedSynapseClient synapseClientIntern = createSynapseClientIntern();
		return createRetryingProxy(synapseClientIntern, ExtendedSynapseClient.class);
	}

}
