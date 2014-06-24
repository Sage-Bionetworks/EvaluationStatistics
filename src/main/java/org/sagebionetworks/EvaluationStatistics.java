package org.sagebionetworks;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.SynapseClientImpl;
import org.sagebionetworks.client.SynapseProfileProxy;
import org.sagebionetworks.evaluation.model.Evaluation;
import org.sagebionetworks.evaluation.model.Submission;
import org.sagebionetworks.evaluation.model.SubmissionBundle;
import org.sagebionetworks.evaluation.model.SubmissionStatus;
import org.sagebionetworks.evaluation.model.SubmissionStatusEnum;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.PaginatedResults;
import org.sagebionetworks.repo.model.dao.WikiPageKey;
import org.sagebionetworks.repo.model.v2.wiki.V2WikiHeader;
import org.sagebionetworks.repo.model.v2.wiki.V2WikiPage;

/**
 * Hello world!
 *
 */
public class EvaluationStatistics {
    public static void main( String[] args ) throws Exception {
    	computeEvaluationStatistics();
    }
    
	private static final int PAGE_SIZE = 50;

	public static void computeEvaluationStatistics() throws Exception {
        	String synapseUserName = getProperty("SYNAPSE_USERNAME");
        	String synapsePassword = getProperty("SYNAPSE_PASSWORD");
            SynapseClient synapseClient = createSynapseClient();
            synapseClient.login(synapseUserName, synapsePassword);
            PaginatedResults<Evaluation> evalPGs = synapseClient.getEvaluationsPaginated(0, Integer.MAX_VALUE);
            System.out.println("There are "+evalPGs.getTotalNumberOfResults()+" evaluations in the system.");
            List<Evaluation> evals = evalPGs.getResults();
            if (evals.size()!=evalPGs.getTotalNumberOfResults()) throw new IllegalStateException();
            for (Evaluation eval : evals) {
        		if (true) break; // temporary
            	String eid = eval.getId();
            	long total = Integer.MAX_VALUE;
            	Map<SubmissionStatusEnum,Integer> statusCounts = new HashMap<SubmissionStatusEnum,Integer>();
            	for (int offset=0; offset<total; offset+=PAGE_SIZE) {
            		PaginatedResults<SubmissionBundle> submissionPGs = synapseClient.getAllSubmissionBundles(eid, offset, PAGE_SIZE);
            		total = (int)submissionPGs.getTotalNumberOfResults();
            		List<SubmissionBundle> submissionBundles = submissionPGs.getResults();
            		for (int i=0; i<submissionBundles.size(); i++) {
            			Submission sub = submissionBundles.get(i).getSubmission();
            			SubmissionStatus status = submissionBundles.get(i).getSubmissionStatus();
            			int count = 0;
            			if (statusCounts.containsKey(status.getStatus())) {
            				count = statusCounts.get(status.getStatus());
            			}
            			statusCounts.put(status.getStatus(), count+1);
            		}
            	}
        		System.out.println("For evaluation "+eid+": "+statusCounts);
        		break; // temporary
            }
    		String projectId = getProperty("PROJECT_ID");
    		V2WikiPage rootPage = synapseClient.getV2RootWikiPage(projectId, ObjectType.ENTITY);
    		List<V2WikiHeader> wikiPages = 
    			synapseClient.getV2WikiHeaderTree(projectId, ObjectType.ENTITY).getResults();
    		System.out.println("There are "+wikiPages.size()+" wiki pages");
    		for (V2WikiHeader header : wikiPages) {
    			WikiPageKey key = new WikiPageKey();
    			key.setOwnerObjectId(projectId);
    			key.setOwnerObjectType(ObjectType.ENTITY);
    			key.setWikiPageId(header.getId());
    			//V2WikiPage page = synapseClient.getV2WikiPage(key);
    			String markdown = synapseClient.downloadV2WikiMarkdown(key);
    			System.out.println("id: "+header.getId()+" parent: "+header.getParentId()+
    					" title: "+header.getTitle()+" markdown: "+markdown);
    		}
    }
    
	private static Properties properties = null;

	public static void initProperties() {
		if (properties!=null) return;
		properties = new Properties();
		InputStream is = null;
    	try {
    		is = EvaluationStatistics.class.getClassLoader().getResourceAsStream("global.properties");
    		properties.load(is);
    	} catch (IOException e) {
    		throw new RuntimeException(e);
    	} finally {
    		if (is!=null) try {
    			is.close();
    		} catch (IOException e) {
    			throw new RuntimeException(e);
    		}
    	}
   }
	
	public static String getProperty(String key) {
		initProperties();
		String commandlineOption = System.getProperty(key);
		if (commandlineOption!=null) return commandlineOption;
		String embeddedProperty = properties.getProperty(key);
		if (embeddedProperty!=null) return embeddedProperty;
		// (could also check environment variables)
		throw new RuntimeException("Cannot find value for "+key);
	}	
	  
	private static SynapseClient createSynapseClient() {
			boolean staging = false;
			SynapseClientImpl scIntern = new SynapseClientImpl();
			if (staging) {
				scIntern.setAuthEndpoint("https://repo-staging.prod.sagebase.org/auth/v1");
				scIntern.setRepositoryEndpoint("https://repo-staging.prod.sagebase.org/repo/v1");
				scIntern.setFileEndpoint("https://repo-staging.prod.sagebase.org/file/v1");
			} else { // prod
				scIntern.setAuthEndpoint("https://repo-prod.prod.sagebase.org/auth/v1");
				scIntern.setRepositoryEndpoint("https://repo-prod.prod.sagebase.org/repo/v1");
				scIntern.setFileEndpoint("https://repo-prod.prod.sagebase.org/file/v1");
			}
			return SynapseProfileProxy.createProfileProxy(scIntern);

	  }
}
