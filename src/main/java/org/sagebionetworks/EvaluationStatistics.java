package org.sagebionetworks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.ContentType;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.evaluation.model.Evaluation;
import org.sagebionetworks.evaluation.model.Submission;
import org.sagebionetworks.evaluation.model.SubmissionBundle;
import org.sagebionetworks.evaluation.model.SubmissionContributor;
import org.sagebionetworks.evaluation.model.SubmissionStatus;
import org.sagebionetworks.evaluation.model.SubmissionStatusEnum;
import org.sagebionetworks.reflection.model.PaginatedResults;
import org.sagebionetworks.repo.model.ACCESS_TYPE;
import org.sagebionetworks.repo.model.AccessControlList;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.ResourceAccess;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.UserProfile;
import org.sagebionetworks.repo.model.auth.LoginRequest;
import org.sagebionetworks.repo.model.dao.WikiPageKey;
import org.sagebionetworks.repo.model.file.FileHandle;
import org.sagebionetworks.repo.model.v2.wiki.V2WikiHeader;
import org.sagebionetworks.repo.model.v2.wiki.V2WikiPage;
import org.sagebionetworks.repo.model.wiki.WikiPage;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;

import com.xeiam.xchart.BitmapEncoder;
import com.xeiam.xchart.Chart;
import com.xeiam.xchart.ChartBuilder;
import com.xeiam.xchart.StyleManager.ChartTheme;
import com.xeiam.xchart.StyleManager.ChartType;

/**
 *
 */
public class EvaluationStatistics {
	public static void main( String[] args ) throws Exception {
		EvaluationStatistics evaluatonStatistics = new EvaluationStatistics();
		evaluatonStatistics.computeAndPublishEvaluationStats();
		System.exit(0);
	}

	private static final int PAGE_SIZE = 10;

	private static final boolean CREATE_WEEKLY_SUBMISSION_PLOT = true;

	public static ContentType MARKDOWN_FILE_CONTENT_TYPE = ContentType.APPLICATION_OCTET_STREAM;

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

	private static final int LONG_TABLE_THRESHOLD = 20;
	
	private static final String ROOT_WIKI_TEMPLATE = "The pages below show the statistics of all Evaluation Queues in Synapse, organized by their parent Project.\n\n" + 
			"To add a challenge to the list, see the instructions under \"Enable Challenge Statistics\" "+
			"in the [Challenge Administration Instructions](http://docs.synapse.org/articles/challenge_administration.html).\n\n"+
			"####Summary statistics:";

	private ExtendedSynapseClient synapseClient;
	private Map<String,UserProfile> userProfileCache;

	public EvaluationStatistics() throws SynapseException {
		String synapseUserName = getProperty("SYNAPSE_USERNAME");
		String synapsePassword = getProperty("SYNAPSE_PASSWORD");
		synapseClient = SynapseClientFactory.createSynapseClient();
		LoginRequest loginRequest = new LoginRequest();
		loginRequest.setUsername(synapseUserName);
		loginRequest.setPassword(synapsePassword);
		synapseClient.login(loginRequest);

		userProfileCache = new HashMap<String,UserProfile>();
	}
	
	public SynapseClient getSynapseClient() {return synapseClient;} // for testing

	public UserProfile getUserProfile(String userId) throws SynapseException {
		UserProfile result = userProfileCache.get(userId);
		if (result==null) {
			result = synapseClient.getUserProfile(userId);
			userProfileCache.put(userId, result);
		}
		return result;
	}
	
	public void computeAndPublishEvaluationStats() throws Exception {
		UserProfile myUserProfile = synapseClient.getMyProfile();
		Long myPrincipalId = Long.parseLong(myUserProfile.getOwnerId());
		String wikiProjectId = getProperty("PROJECT_ID");
		Map<String, V2WikiHeader> pageMap = getWikiPages(wikiProjectId);
		V2WikiPage rootPage = synapseClient.getV2RootWikiPage(wikiProjectId, ObjectType.ENTITY);


		Map<String, List<Evaluation>> parentProjectNameToEvalMap = new TreeMap<String, List<Evaluation>>();
		Map<Evaluation, WikiContent> evalToWikiContentMap = new HashMap<Evaluation,WikiContent>();
		long evaluationTotal = 1;
		int evaluationPageSize = PAGE_SIZE;
		long numberOfSubmissionsAcrossAllChallenges = 0L;
		Set<String> usersAcrossChallenges = new HashSet<String>();
		for (int evaluationOffset=0; evaluationOffset<evaluationTotal; evaluationOffset+=evaluationPageSize) {
			PaginatedResults<Evaluation> evalPGs = synapseClient
					.getReadableEvaluationsPaginated(evaluationOffset, evaluationPageSize);
			evaluationTotal = evalPGs.getTotalNumberOfResults();
			for (Evaluation eval : evalPGs.getResults()) {
				String eid = eval.getId();
				System.out.println(eid+" "+eval.getName());
				AccessControlList acl = synapseClient.getEvaluationAcl(eid);
				boolean foundIt = false;
				// note: this isn't perfect:  If access is granted to a group I'm in then this check won't pass
				for (ResourceAccess ra : acl.getResourceAccess()) {
					// System.out.println("\t"+ra);
					if (ra.getAccessType().contains(ACCESS_TYPE.READ_PRIVATE_SUBMISSION) && ((long)ra.getPrincipalId())==myPrincipalId) {
						foundIt = true;
						break;
					}
				}
				if (!foundIt) {
					// System.out.println("\t"+myPrincipalId+" lacks READ_PRIVATE_SUBMISSION to evaluation "+eid);
					continue;
				}
				long totalNumberOfSubmissions = 1;
				Map<SubmissionStatusEnum,Integer> statusToCountMap = new HashMap<SubmissionStatusEnum,Integer>();
				Map<String, TeamSubmissionStats> teams = new TreeMap<String, TeamSubmissionStats>();
				Map<String, UserSubmissionStats> users = new TreeMap<String, UserSubmissionStats>();
				SortedMap<Date, Integer> submissionsPerWeek = new TreeMap<Date, Integer>();
				Map<String,Team> teamMap = new HashMap<String,Team>();
				for (long offset=0; offset<totalNumberOfSubmissions; offset+=PAGE_SIZE) {
					PaginatedResults<SubmissionBundle> submissionPGs = synapseClient.getAllSubmissionBundles(eid, offset, PAGE_SIZE);
					totalNumberOfSubmissions = submissionPGs.getTotalNumberOfResults();
					List<SubmissionBundle> submissionBundles = submissionPGs.getResults();
					for (int i=0; i<submissionBundles.size(); i++) {
						Submission sub = submissionBundles.get(i).getSubmission();
						SubmissionStatus status = submissionBundles.get(i).getSubmissionStatus();
						Set<String> contributorIds = new HashSet<String>();
						contributorIds.add(sub.getUserId());
						Set<SubmissionContributor> contributors = sub.getContributors();
						if (contributors!=null) {
							for (SubmissionContributor sc : contributors) {
								contributorIds.add(sc.getPrincipalId());
							}
						}
						Set<String> contributorNames = new HashSet<String>();
						for (String contributorId : contributorIds) {
							contributorNames.add(getUserProfile(contributorId).getUserName());
						}
						String teamName=null;
						String teamId = sub.getTeamId();
						if (teamId!=null) {
							Team team = teamMap.get(teamId);
							if (team==null) {
								team = synapseClient.getTeam(teamId);
								teamMap.put(teamId, team);
							}
							teamName=team.getName();
						}
						if (teamName==null) teamName=sub.getSubmitterAlias();
						if (teamName!=null) {
							TeamSubmissionStats tss = teams.get(teamName);
							if (tss==null) {
								tss = new TeamSubmissionStats(teamName);
								teams.put(teamName, tss);
							}
							updateTSS(tss, sub, status, contributorNames);
						}
						for (String userName : contributorNames) {
							UserSubmissionStats uss = users.get(userName);
							if (uss==null) {
								uss = new UserSubmissionStats(userName);
								users.put(userName, uss);
							}
							updateUSS(uss, sub, status, teamName);
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
				numberOfSubmissionsAcrossAllChallenges += totalNumberOfSubmissions;
				usersAcrossChallenges.addAll(users.keySet());

				if (totalNumberOfSubmissions==0) continue; // if there are no entries, don't build a wiki
				WikiContent wikiContent = new WikiContent();
				int nSubmissions = (int)totalNumberOfSubmissions;
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
					if (CREATE_WEEKLY_SUBMISSION_PLOT) { 
						File file = createWeeklySubmissionPlot(eid, submissionsPerWeek, true);
						wikiContent.addFile(file);
						sb.append(imageMarkdownForFile(file)+"\n");
					} else {
						sb.append(submissionsPerWeekMarkdownTable(submissionsPerWeek)+"\n");
					}
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
				wikiContent.setMarkdown(markdown);
				evalToWikiContentMap.put(eval, wikiContent);
				// record the evaluation as a child of its parent project
				String parentProjectName = eval.getContentSource();
				try {
					Project parentProject = synapseClient.getEntity(eval.getContentSource(), Project.class);
					if (parentProject.getName()!=null) {
						parentProjectName = parentProject.getName();
					}
				} catch (SynapseException e) {
					// if we can't retrieve the parent project, 
					// just continue, using the syn ID as the project name
				}

				List<Evaluation> evals;
				if (parentProjectNameToEvalMap.containsKey(parentProjectName)) {
					evals = parentProjectNameToEvalMap.get(parentProjectName);
				} else {
					evals = new ArrayList<Evaluation>();
					parentProjectNameToEvalMap.put(parentProjectName, evals);
				}
				evals.add(eval);
			}
		}
		
		//
		long numberOfchallenges = parentProjectNameToEvalMap.size();
		long numberOfDistinctChallengeParticipantsAcrossAllChallenges=usersAcrossChallenges.size();
		WikiContent rootWikiContent = new WikiContent();
		StringBuilder rootMarkdown = new StringBuilder();
		rootMarkdown.append(ROOT_WIKI_TEMPLATE);
		rootMarkdown.append("\nNumber of challenges: "+numberOfchallenges);
		rootMarkdown.append("\nNumber of distinct challenge participants across all challenges: "+numberOfDistinctChallengeParticipantsAcrossAllChallenges);
		rootMarkdown.append("\nNumber of submissions across all challenges: "+numberOfSubmissionsAcrossAllChallenges);
		rootWikiContent.setMarkdown(rootMarkdown.toString());
		updateWikiPage(wikiProjectId, rootPage.getId(), rootWikiContent);

		// now we combine all the Evaluations under a project into one
		System.out.println(parentProjectNameToEvalMap.size()+" subpages to create/update...");
		for (String parentProjectName : parentProjectNameToEvalMap.keySet()) {
			WikiContent projectWikiContent = new WikiContent();
			StringBuilder markdown = new StringBuilder();
			boolean firstTime=true;
			for (Evaluation eval : parentProjectNameToEvalMap.get(parentProjectName)) {
				if (firstTime) {
					firstTime=false;
					markdown.append(
							"#["+parentProjectName+"](https://www.synapse.org/#!Synapse:"+
							eval.getContentSource()+")\n\n");
				}
				WikiContent evaluationWikiContent = evalToWikiContentMap.get(eval);
				markdown.append(evaluationWikiContent.getMarkdown());
				projectWikiContent.getFiles().addAll(evaluationWikiContent.getFiles());
			}
			projectWikiContent.setMarkdown(markdown.toString());
			V2WikiHeader header = pageMap.get(parentProjectName);
			try {
				if (header==null) {
					// need to create page
					createWikiPage(wikiProjectId, rootPage.getId(), parentProjectName, projectWikiContent);
				} else {
					// need to update page
					updateWikiPage(wikiProjectId, header.getId(), projectWikiContent);
				}
			} catch (Exception e) {
				// log the exception and go on to the next one
				e.printStackTrace();
			}
		}
		System.out.println("...wiki-page update complete.");

		// now clean up any unneeded wiki pages:
		for (String pageTitle : pageMap.keySet()) {
			V2WikiHeader header = pageMap.get(pageTitle);
			if (!parentProjectNameToEvalMap.containsKey(pageTitle) && 
					!header.getId().equals(rootPage.getId())) {
				// then delete this wiki page
				System.out.println("Deleting unneeded wiki page: "+pageTitle+" ("+header.getId()+")");
				WikiPageKey pageKey = new WikiPageKey();
				pageKey.setOwnerObjectId(wikiProjectId);
				pageKey.setOwnerObjectType(ObjectType.ENTITY);
				pageKey.setWikiPageId(header.getId());
				synapseClient.deleteV2WikiPage(pageKey);
			}
		}
	}

	private static String imageMarkdownForFile(File file) throws UnsupportedEncodingException {
		String name = file.getName();
		String urlEncodedName = URLEncoder.encode(name, "utf-8");
		return "${image?fileName="+urlEncodedName+"&align=None&scale=100}";
	}

	private static void updateTSS(TeamSubmissionStats tss, Submission sub, SubmissionStatus status, Set<String> userNames) {
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
		tss.getUsers().addAll(userNames);
	}

	private static void updateUSS(UserSubmissionStats uss, Submission sub, SubmissionStatus status, String teamName) {
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
		if (teamName!=null) uss.getTeams().add(teamName);
	}

	public WikiPage createWikiPage(String projectId, String rootWikiId, String title, WikiContent wikiContent) throws Exception {
		System.out.println("Creating wiki sub-page for "+title);
		WikiPage page = new WikiPage();
		page.setParentWikiId(rootWikiId);
		page.setTitle(title);
		page.setMarkdown(wikiContent.getMarkdown());
		List<FileHandle> fileHandles = createFileHandlesFromFiles(wikiContent.getFiles());
		List<String> fileHandleIds = new ArrayList<String>();
		for (FileHandle fileHandle : fileHandles) fileHandleIds.add(fileHandle.getId());
		page.setAttachmentFileHandleIds(fileHandleIds);
		return synapseClient.createWikiPage(projectId, ObjectType.ENTITY, page);
	}

	public void updateWikiPage(String projectId, String wikiPageId, WikiContent wikiContent) throws ClientProtocolException, FileNotFoundException, IOException, SynapseException, JSONObjectAdapterException {
		WikiPageKey key = new WikiPageKey();
		key.setOwnerObjectId(projectId);
		key.setOwnerObjectType(ObjectType.ENTITY);
		key.setWikiPageId(wikiPageId);
		String markdown = null;
		try {
			markdown = synapseClient.downloadV2WikiMarkdown(key);
		} catch (java.util.zip.ZipException e) {
			markdown = null;
		}
		String newMarkdown = wikiContent.getMarkdown();
		if (markdown==null || !markdown.equals(newMarkdown)) {
			System.out.println("Updating wiki sub-page "+wikiPageId);
			// then publish the new markdown
			V2WikiPage v2page = synapseClient.getV2WikiPage(key);
			WikiPage page = new WikiPage();
			page.setId(v2page.getId());
			page.setEtag(v2page.getEtag());
			page.setParentWikiId(v2page.getParentWikiId());
			page.setTitle(v2page.getTitle());
			page.setMarkdown(newMarkdown);
			page.setCreatedBy(v2page.getCreatedBy());
			page.setCreatedOn(v2page.getCreatedOn());
			List<FileHandle> fileHandles = createFileHandlesFromFiles(wikiContent.getFiles());
			List<String> fileHandleIds = new ArrayList<String>();
			for (FileHandle fileHandle : fileHandles) fileHandleIds.add(fileHandle.getId());
			page.setAttachmentFileHandleIds(fileHandleIds);
			synapseClient.updateWikiPage(projectId, ObjectType.ENTITY, page);
		} else {
			System.out.println("No update needed for wiki sub-page "+wikiPageId);
		}

	}

	private List<FileHandle> createFileHandlesFromFiles(List<File> files) throws IOException, SynapseException {
		List<FileHandle> result = new ArrayList<FileHandle>();
		for (File file : files) {
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(file);
				result.add(synapseClient.multipartUpload(fis, file.length(), file.getName(), 
						MARKDOWN_FILE_CONTENT_TYPE.toString(), null, true, true));
			} finally {
				fis.close();
			}
		}
		return result;	
	}

	public Map<String, V2WikiHeader> getWikiPages(String projectId) throws SynapseException, JSONObjectAdapterException {
		Map<String, V2WikiHeader> result = new HashMap<String, V2WikiHeader>();
		long totalNumberOfWikiPages = 1;
		for (long offset = 0; offset<totalNumberOfWikiPages; offset+=PAGE_SIZE) {
			PaginatedResults<V2WikiHeader> pages = 
					synapseClient.getV2WikiHeaderTree(projectId, ObjectType.ENTITY, (long)PAGE_SIZE, offset);
			totalNumberOfWikiPages = pages.getTotalNumberOfResults();
			for (V2WikiHeader header : pages.getResults()) {
				// titles are Evaluation names, which should be unique
				if (result.containsKey(header.getTitle())) throw new RuntimeException("Repeated title: "+header.getTitle());
				result.put(header.getTitle(), header);
			}
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

	public static String submissionsPerWeekMarkdownTable(Map<Date,Integer> subs) {
		boolean isLong = subs.size()>LONG_TABLE_THRESHOLD;
		StringBuilder sb = new StringBuilder();
		if (isLong) sb.append("{| class=\"short\"\n");
		sb.append(markdownRow(new String[]{"week starting", "#submissions"}));
		sb.append(markdownTableDivider(2));
		for (Date date : subs.keySet()) {
			sb.append(markdownRow(new String[]{DATE_FORMAT.format(date), ""+subs.get(date)}));
		}
		if (isLong) sb.append("|}\n");
		return sb.toString();
	}

	public static String teamStatsTable(Map<String,TeamSubmissionStats> teams) {
		if (teams==null || teams.isEmpty()) throw new IllegalArgumentException("no data");
		boolean isLong = teams.size()>LONG_TABLE_THRESHOLD;
		StringBuilder sb = new StringBuilder();
		if (isLong) sb.append("{| class=\"short\"\n");
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
					setToString(tss.getUsers())
			}));
		}
		if (isLong) sb.append("|}\n");
		return sb.toString();
	}

	public static String userStatsTable(Map<String,UserSubmissionStats> users) {
		if (users==null || users.isEmpty()) throw new IllegalArgumentException("no data");
		boolean isLong = users.size()>LONG_TABLE_THRESHOLD;
		StringBuilder sb = new StringBuilder();
		if (isLong) sb.append("{| class=\"short\"\n");
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
					setToString(tss.getTeams())
			}));
		}
		if (isLong) sb.append("|}\n");
		return sb.toString();
	}

	public static String setToString(Set<String> set) {
		StringBuilder sb = new StringBuilder();
		if (!set.isEmpty()) {
			boolean firstTime = true;
			for (String s : set) {
				if (firstTime) {
					firstTime=false;
				} else {
					sb.append(",");
				}
				sb.append(s);
			}
		}
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

	// if 'bar' is true then make a bar plot, else make a line plot
	public static Chart createWeeklySubmissionChart( SortedMap<Date,Integer> input, boolean bar) {
		SortedMap<Date,Integer> filledIn = Util.fillInMissing(input, new WeekIncrementer(), 0);
		List<Date> weeks = new ArrayList<Date>();
		List<Number> submissionCounts = new ArrayList<Number>();
		for (Date date : filledIn.keySet()) {
			weeks.add(date);
			submissionCounts.add(filledIn.get(date));
		}

		// Create Chart
		ChartBuilder chartBuilder = new ChartBuilder().
				width(1000).height(300).
				xAxisTitle("Week").yAxisTitle("Submission Count").
				theme(ChartTheme.GGPlot2);
		if (bar) chartBuilder = chartBuilder.chartType(ChartType.Bar);
		Chart chart = chartBuilder.build();

		chart.addSeries("Submission Count", weeks, submissionCounts);

		return chart;
	}

	public static File createWeeklySubmissionPlot(String evalId, SortedMap<Date,Integer> input, boolean bar) throws IOException {
		Chart chart = createWeeklySubmissionChart(input, bar);
		String tempDir = System.getProperty("java.io.tmpdir");
		File file = new File(tempDir, "weeklySubmissions_"+evalId+(bar?"_bar":"")+".jpg");
		BitmapEncoder.saveJPG(chart, file.getAbsolutePath(), 0.95f);
		return file;
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
}
