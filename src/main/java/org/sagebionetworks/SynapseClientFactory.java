package org.sagebionetworks;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.http.HttpStatus;


public class SynapseClientFactory {

	private static final List<Integer> NO_RETRY_STATUSES = Arrays.asList(
			HttpStatus.SC_ACCEPTED, // SynapseResultNotReadyException
			HttpStatus.SC_NOT_FOUND, // SynapseNotFoundException
			HttpStatus.SC_BAD_REQUEST, // SynapseBadRequestException
			HttpStatus.SC_PRECONDITION_FAILED, // SynapseConflictingUpdateException
			HttpStatus.SC_GONE, // SynapseDeprecatedServiceException
			HttpStatus.SC_FORBIDDEN, // SynapseForbiddenException, SynapseTermsOfUseException
			HttpStatus.SC_UNAUTHORIZED, // SynapseUnauthorizedException
			HttpStatus.SC_CONFLICT // 409
		);
		
	private static ExtendedSynapseClient createSynapseClientIntern() {
		ExtendedSynapseClientImpl scIntern = new ExtendedSynapseClientImpl();
		scIntern.setAuthEndpoint("https://repo-prod.prod.sagebase.org/auth/v1");
		scIntern.setRepositoryEndpoint("https://repo-prod.prod.sagebase.org/repo/v1");
		scIntern.setFileEndpoint("https://repo-prod.prod.sagebase.org/file/v1");
		
		return  scIntern;
	}

	public static ExtendedSynapseClient createSynapseClient() {
		final ExtendedSynapseClient synapseClientIntern = createSynapseClientIntern();
		
		List<Class<? extends Exception>> noRetryExceptions = new ArrayList<Class<? extends Exception>>();
		noRetryExceptions.add(java.util.zip.ZipException.class);
		final ExponentialBackoffRunner exponentialBackoffRunner = new ExponentialBackoffRunner(
				NO_RETRY_STATUSES, 
				noRetryExceptions,
				ExponentialBackoffRunner.DEFAULT_NUM_RETRY_ATTEMPTS);

		InvocationHandler handler = new InvocationHandler() {
			public Object invoke(final Object proxy, final Method method, final Object[] args)
					throws Throwable {
				return exponentialBackoffRunner.execute(new Executable<Object>() {
					public Object execute() throws Throwable {
						try {
							Object result = method.invoke(synapseClientIntern, args);
							return result;
						} catch (IllegalAccessException  e) {
							throw new RuntimeException(e);
						} catch (InvocationTargetException e) {
							if (e.getCause()==null) throw e; else throw e.getCause();
						}
					}
				});
			}
		};
			
		return (ExtendedSynapseClient) Proxy.newProxyInstance(ExtendedSynapseClient.class.getClassLoader(),
				new Class[] { ExtendedSynapseClient.class },
				handler);
	}

}
