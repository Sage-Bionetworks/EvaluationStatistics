package org.sagebionetworks;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.ContentType;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.SynapseClientImpl;
import org.sagebionetworks.client.SynapseProfileProxy;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.downloadtools.FileUtils;
import org.sagebionetworks.evaluation.model.Evaluation;
import org.sagebionetworks.evaluation.model.Submission;
import org.sagebionetworks.evaluation.model.SubmissionBundle;
import org.sagebionetworks.evaluation.model.SubmissionStatus;
import org.sagebionetworks.evaluation.model.SubmissionStatusEnum;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.PaginatedResults;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.UserProfile;
import org.sagebionetworks.repo.model.dao.WikiPageKey;
import org.sagebionetworks.repo.model.v2.wiki.V2WikiHeader;
import org.sagebionetworks.repo.model.v2.wiki.V2WikiPage;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;

/**
 *
 */
public class EvaluationStatistics {
    public static void main( String[] args ) throws Exception {
    	EvaluationStatistics evaluatonStatistics = new EvaluationStatistics();
    	evaluatonStatistics.computeAndPublishEvaluationStats();
    }
    
	private static final int PAGE_SIZE = 50;
	
	private SynapseClient synapseClient;
	private Map<String,UserProfile> userProfileCache;
	
	public EvaluationStatistics() throws SynapseException {
    	String synapseUserName = getProperty("SYNAPSE_USERNAME");
    	String synapsePassword = getProperty("SYNAPSE_PASSWORD");
        synapseClient = createSynapseClient();
        synapseClient.login(synapseUserName, synapsePassword);
        
        userProfileCache = new HashMap<String,UserProfile>();
	}
	
	public UserProfile getUserProfile(String userId) throws SynapseException {
		UserProfile result = userProfileCache.get(userId);
		if (result==null) {
			result = synapseClient.getUserProfile(userId);
			userProfileCache.put(userId, result);
		}
		return result;
	}

	public void computeAndPublishEvaluationStats() throws Exception {
    		String wikiProjectId = getProperty("PROJECT_ID");
    		Map<String, V2WikiHeader> pageMap = getWikiPages(wikiProjectId);
    		V2WikiPage rootPage = synapseClient.getV2RootWikiPage(wikiProjectId, ObjectType.ENTITY);
            
            
            PaginatedResults<Evaluation> evalPGs = synapseClient.getEvaluationsPaginated(0, Integer.MAX_VALUE);
            System.out.println("There are "+evalPGs.getTotalNumberOfResults()+" evaluations in the system.");
            List<Evaluation> evals = evalPGs.getResults();
            if (evals.size()!=evalPGs.getTotalNumberOfResults()) throw new IllegalStateException();
            Map<String, List<String>> parentProjectNameToEvalIdMap = new TreeMap<String, List<String>>();
            Map<String, String> evalIdToMarkdownMap = new HashMap<String,String>();
            int evalCount = 0;
            for (Evaluation eval : evals) {
               	String eid = eval.getId();
               	System.out.println(eid+" "+eval.getName());
            	Project parentProject = synapseClient.getEntity(eval.getContentSource(), Project.class);
            	String parentProjectName = parentProject.getName();
            	if (parentProjectName==null) parentProjectName = eval.getContentSource();
            	List<String> evalIds;
            	if (parentProjectNameToEvalIdMap.containsKey(parentProjectName)) {
            		evalIds = parentProjectNameToEvalIdMap.get(parentProjectName);
            	} else {
            		evalIds = new ArrayList<String>();
            		parentProjectNameToEvalIdMap.put(parentProjectName, evalIds);
            	}
            	evalIds.add(eid);
             	long total = Integer.MAX_VALUE;
            	Map<SubmissionStatusEnum,Integer> statusToCountMap = new HashMap<SubmissionStatusEnum,Integer>();
            	Map<String, TeamSubmissionStats> teams = new TreeMap<String, TeamSubmissionStats>();
            	Map<String, UserSubmissionStats> users = new TreeMap<String, UserSubmissionStats>();
            	Map<Date, Integer> submissionsPerWeek = new TreeMap<Date, Integer>();
            	for (int offset=0; offset<total; offset+=PAGE_SIZE) {
            		PaginatedResults<SubmissionBundle> submissionPGs = synapseClient.getAllSubmissionBundles(eid, offset, PAGE_SIZE);
            		total = (int)submissionPGs.getTotalNumberOfResults();
            		List<SubmissionBundle> submissionBundles = submissionPGs.getResults();
            		for (int i=0; i<submissionBundles.size(); i++) {
            			Submission sub = submissionBundles.get(i).getSubmission();
            			SubmissionStatus status = submissionBundles.get(i).getSubmissionStatus();
            			String userName = getUserProfile(sub.getUserId()).getUserName();
            			if (sub.getSubmitterAlias()!=null) {
	            			TeamSubmissionStats tss = teams.get(sub.getSubmitterAlias());
	            			if (tss==null) {
	            				tss = new TeamSubmissionStats(sub.getSubmitterAlias());
	            				teams.put(sub.getSubmitterAlias(), tss);
	            			}
	            			updateTSS(tss, sub, status, userName);
            			}
            			if (userName!=null) {
            				UserSubmissionStats uss = users.get(userName);
            				if (uss==null) {
            					uss = new UserSubmissionStats(userName);
            					users.put(userName, uss);
            				}
            				updateUSS(uss, sub, status);
            			}
            			Date week = getWeekForDate(sub.getCreatedOn());
            			int statusCount = 0;
            			if (statusToCountMap.containsKey(status.getStatus())) {
            				statusCount = statusToCountMap.get(status.getStatus());
            			}
            			statusToCountMap.put(status.getStatus(), statusCount+1);
            			int submissionsPerWeekCount = 0;
            			if (submissionsPerWeek.containsKey(week)) {
            				submissionsPerWeekCount = submissionsPerWeek.get(week);
            			}
            			submissionsPerWeek.put(week, submissionsPerWeekCount+1);
            		}
            	}
        		int nSubmissions = total==Integer.MAX_VALUE ? 0 : (int)total;
        		Integer nScored = statusToCountMap.get(SubmissionStatusEnum.SCORED); if (nScored==null) nScored=0;
        		Integer nInvalid = statusToCountMap.get(SubmissionStatusEnum.INVALID); if (nInvalid==null) nInvalid=0;
        		int nNotScored = nSubmissions - nScored - nInvalid;
        		if (nNotScored<0) throw new IllegalStateException();
        		StringBuilder sb = new StringBuilder();
        		// evaluation title
        		sb.append("\n##"+eval.getName()+"\n");
        		// overall statistics
           		sb.append("\n###Overall statistics\n");
           		sb.append(statsMarkdownTable(
        				nSubmissions,
        				nScored,
        				nInvalid,
        				nNotScored,
        				teams.size(),
        				users.size())+"\n");
        		// submissions per week
        		if (!submissionsPerWeek.isEmpty()) {
               		sb.append("\n###Submissions per week\n");
       				sb.append(submissionsPerWeekMarkdownTable(submissionsPerWeek)+"\n");
        		}
        		// team statistics
        		if (!teams.isEmpty()) {
              		sb.append("\n###Team statistics\n");
              		sb.append(teamStatsTable(teams)+"\n");
        		}
        		// user statistics
        		if (!users.isEmpty()) {
              		sb.append("\n###Participant statistics\n");
       			sb.append(userStatsTable(users)+"\n");
        		}
         		String markdown = sb.toString();
        		evalIdToMarkdownMap.put(eid, markdown);
        		if (evalCount++>=10) break; // TEMPORARY
            }
            
            for (String parentProjectName : parentProjectNameToEvalIdMap.keySet()) {
            	StringBuilder markdown = new StringBuilder();
            	for (String evalId : parentProjectNameToEvalIdMap.get(parentProjectName)) {
            		markdown.append(evalIdToMarkdownMap.get(evalId));
            	}
	    		V2WikiHeader header = pageMap.get(parentProjectName);
	    		try {
		    		if (header==null) {
		    			// need to create page
		    			createWikiPage(wikiProjectId, rootPage.getId(), parentProjectName, markdown.toString());
		    		} else {
		    			// need to update page
		    			updateWikiPage(wikiProjectId, header, markdown.toString());
		            }
	    		} catch (Exception e) {
	    			// log the exception and go on to the next one
	    			e.printStackTrace();
	    		}
            }
    }
	
	private static void updateTSS(TeamSubmissionStats tss, Submission sub, SubmissionStatus status, String userName) {
		tss.setSubmissionCount(tss.getSubmissionCount()+1);
		if (status.getStatus()==SubmissionStatusEnum.SCORED) {
			tss.setScoredCount(tss.getScoredCount()+1);
		}
		if (status.getStatus()==SubmissionStatusEnum.INVALID) {
			tss.setInvalidCount(tss.getInvalidCount()+1);
		}
		if (tss.getLastSubmission()==null || tss.getLastSubmission().compareTo(sub.getCreatedOn())<0) {
			tss.setLastSubmission(sub.getCreatedOn());
		}
		if (userName!=null) tss.getUsers().add(userName);
	}
	
	private static void updateUSS(UserSubmissionStats uss, Submission sub, SubmissionStatus status) {
		uss.setSubmissionCount(uss.getSubmissionCount()+1);
		if (status.getStatus()==SubmissionStatusEnum.SCORED) {
			uss.setScoredCount(uss.getScoredCount()+1);
		}
		if (status.getStatus()==SubmissionStatusEnum.INVALID) {
			uss.setInvalidCount(uss.getInvalidCount()+1);
		}
		if (uss.getLastSubmission()==null || uss.getLastSubmission().compareTo(sub.getCreatedOn())<0) {
			uss.setLastSubmission(sub.getCreatedOn());
		}
		if (sub.getSubmitterAlias()!=null) uss.getTeams().add(sub.getSubmitterAlias());
	}
	
	public static ContentType MARKDOWN_FILE_CONTENT_TYPE = ContentType.APPLICATION_OCTET_STREAM;
	
	public void createWikiPage(String projectId, String rootWikiId, String title, String markdown) throws Exception {
		System.out.println("Creating wiki sub-page for "+title);
		V2WikiPage page = new V2WikiPage();
		page.setParentWikiId(rootWikiId);
		page.setTitle(title);
		String markdownFileHandleId = uploadMarkdown(markdown);
		page.setMarkdownFileHandleId(markdownFileHandleId);
		synapseClient.createV2WikiPage(projectId, ObjectType.ENTITY, page);
	}
	
	public void updateWikiPage(String projectId, V2WikiHeader header, String newMarkdown) throws ClientProtocolException, FileNotFoundException, IOException, SynapseException, JSONObjectAdapterException {
		WikiPageKey key = new WikiPageKey();
		key.setOwnerObjectId(projectId);
		key.setOwnerObjectType(ObjectType.ENTITY);
		key.setWikiPageId(header.getId());
		String markdown = synapseClient.downloadV2WikiMarkdown(key);
		if (markdown==null || !markdown.equals(newMarkdown)) {
			System.out.println("Updating wiki sub-page "+header.getId());
			// then publish the new markdown
			V2WikiPage page = synapseClient.getV2WikiPage(key);
			String markdownFileHandleId = uploadMarkdown(newMarkdown);
			page.setMarkdownFileHandleId(markdownFileHandleId);
			synapseClient.updateV2WikiPage(projectId, ObjectType.ENTITY, page);
		} else {
			System.out.println("No update needed for wiki sub-page "+header.getId());
			
		}
		
	}
	
	public String uploadMarkdown(String markdown) throws UnsupportedEncodingException, IOException, SynapseException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		FileUtils.writeCompressedString(markdown, baos);
		String markdownFileHandleId = synapseClient.uploadToFileHandle(baos.toByteArray(), MARKDOWN_FILE_CONTENT_TYPE);
		return markdownFileHandleId;
	}
	
	public Map<String, V2WikiHeader> getWikiPages(String projectId) throws SynapseException, JSONObjectAdapterException {
		Map<String, V2WikiHeader> result = new HashMap<String, V2WikiHeader>();
		List<V2WikiHeader> wikiPageHeaders = 
    			synapseClient.getV2WikiHeaderTree(projectId, ObjectType.ENTITY).getResults();
		for (V2WikiHeader header : wikiPageHeaders) {
			// titles are Evaluation names, which should be unique
			if (result.containsKey(header.getTitle())) throw new RuntimeException("Repeated title: "+header.getTitle());
			result.put(header.getTitle(), header);
		}
		return result;
	}
	
	public static String statsMarkdownTable(
			int nSubmissions,
			int nScored,
			int nInvalid,
			int nNotScored,
			int nUniqueTeams,
			int nUniqueUsers) {
		StringBuilder sb = new StringBuilder();
		sb.append(markdownRow(new String[]{"statistic", "#"}));
		sb.append(markdownTableDivider(2));
		sb.append(markdownRow(new String[]{"#submissions", ""+nSubmissions}));
		sb.append(markdownRow(new String[]{"#scored", ""+nScored}));
		sb.append(markdownRow(new String[]{"#invalid", ""+nInvalid}));
		sb.append(markdownRow(new String[]{"#notScored", ""+nNotScored}));
		sb.append(markdownRow(new String[]{"#uniqueTeams", ""+nUniqueTeams}));
		sb.append(markdownRow(new String[]{"#uniqueUsers", ""+nUniqueUsers}));
		return sb.toString();
	}
	
	public static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
	
	public static String submissionsPerWeekMarkdownTable(Map<Date,Integer> subs) {
		StringBuilder sb = new StringBuilder();
		sb.append("{| class=\"short\"");
		sb.append(markdownRow(new String[]{"week starting", "#submissions"}));
		sb.append(markdownTableDivider(2));
		for (Date date : subs.keySet()) {
			sb.append(markdownRow(new String[]{DATE_FORMAT.format(date), ""+subs.get(date)}));
		}
		sb.append("|}");
		return sb.toString();
	}
	
	public static String teamStatsTable(Map<String,TeamSubmissionStats> teams) {
		if (teams==null || teams.isEmpty()) throw new IllegalArgumentException("no data");
		StringBuilder sb = new StringBuilder();
		sb.append("{| class=\"short\"");
		sb.append(markdownRow(new String[]{
				"team", 
				"#submissions", 
				"#scored", 
				"#invalid",
				"last submission",
				"participants"}));
		sb.append(markdownTableDivider(6));
		for (String name : teams.keySet()) {
			TeamSubmissionStats tss = teams.get(name);
			sb.append(markdownRow(new String[]{
					name, 
					""+tss.getSubmissionCount(),
					""+tss.getScoredCount(),
					""+tss.getInvalidCount(),
					DATE_FORMAT.format(tss.getLastSubmission()),
					tss.getUsers().toString()
			}));
		}
		sb.append("|}");
		return sb.toString();
	}
	
	public static String userStatsTable(Map<String,UserSubmissionStats> users) {
		if (users==null || users.isEmpty()) throw new IllegalArgumentException("no data");
		StringBuilder sb = new StringBuilder();
		sb.append("{| class=\"short\"");
		sb.append(markdownRow(new String[]{
				"participant", 
				"#submissions", 
				"#scored", 
				"#invalid",
				"last submission",
				"teams"}));
		sb.append(markdownTableDivider(6));
		for (String name : users.keySet()) {
			UserSubmissionStats tss = users.get(name);
			sb.append(markdownRow(new String[]{
					name, 
					""+tss.getSubmissionCount(),
					""+tss.getScoredCount(),
					""+tss.getInvalidCount(),
					DATE_FORMAT.format(tss.getLastSubmission()),
					tss.getTeams().toString()
			}));
		}
		sb.append("|}");
		return sb.toString();
	}
	

	public static String markdownRow(String[] row) {
		if (row.length<1) throw new IllegalArgumentException();
		StringBuilder sb = new StringBuilder();
		sb.append(row[0]);
		for (int i=1; i<row.length; i++) sb.append("|"+row[i]);
		sb.append("\n");
		return sb.toString();
		
	}
	public static String markdownTableDivider(int n) {
		if (n<1) throw new IllegalArgumentException();
		StringBuilder sb = new StringBuilder();
		sb.append("---");
		for (int i=0; i<n-1; i++) {
			sb.append("|---");
		}
		sb.append("\n");
		return sb.toString();
	}
	
	public static Date getWeekForDate(Date date) {
		// TODO:  This seems to be in local time, not UTC
		DateTime dateTime = new DateTime(date, DateTimeZone.UTC);
		DateTime beginningOfWeek = dateTime.weekOfWeekyear().roundFloorCopy();
		return beginningOfWeek.toDate();
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
