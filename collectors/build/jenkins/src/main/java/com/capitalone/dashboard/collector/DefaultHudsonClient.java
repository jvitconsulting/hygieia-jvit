package com.capitalone.dashboard.collector;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import com.capitalone.dashboard.model.Build;
import com.capitalone.dashboard.model.BuildStatus;
import com.capitalone.dashboard.model.HudsonJob;
import com.capitalone.dashboard.model.RepoBranch;
import com.capitalone.dashboard.model.SCM;
import com.capitalone.dashboard.util.Supplier;

/**
 * HudsonClient implementation that uses RestTemplate and JSONSimple to fetch
 * information from Hudson instances.
 */
@Component
public class DefaultHudsonClient implements HudsonClient {
	private static final Logger LOG = LoggerFactory.getLogger(DefaultHudsonClient.class);

	private final RestOperations rest;
	private final HudsonSettings settings;

	private static final String JOB_QUERY = "jobs[name,url,builds[number,url],lastSuccessfulBuild[timestamp,builtOn],lastBuild[timestamp,builtOn]]";
	// private static final String JOBS_URL_SUFFIX = "/api/json?tree=jobs";

	private static final String[] CHANGE_SET_ITEMS_TREE = new String[] { "user", "author[fullName]", "revision", "id",
			"msg", "timestamp", "date", "paths[file]" };

	private static final String[] BUILD_DETAILS_TREE = new String[] { "number", "url", "timestamp", "duration",
			"building", "result", "culprits[fullName]",
			"changeSets[items[" + StringUtils.join(CHANGE_SET_ITEMS_TREE, ",") + "],kind]",
			"changeSet[items[" + StringUtils.join(CHANGE_SET_ITEMS_TREE, ",") + "]", "kind",
			"revisions[module,revision]]", "actions[lastBuiltRevision[SHA1,branch[SHA1,name]],remoteUrls]" };

	private static final String BUILD_DETAILS_URL_SUFFIX = "/api/json?tree="
			+ StringUtils.join(BUILD_DETAILS_TREE, ",");

	@Autowired
	public DefaultHudsonClient(Supplier<RestOperations> restOperationsSupplier, HudsonSettings settings) {
		this.rest = restOperationsSupplier.get();
		this.settings = settings;
	}

	@Override
	public Map<HudsonJob, Set<Build>> getInstanceJobs(String instanceUrl) {

		LOG.debug("Enter getInstanceJobs");
		Map<HudsonJob, Set<Build>> result = new LinkedHashMap<>();

		int pageSize = settings.getPageSize();
		// Default pageSize to 1000 for backward compatibility of settings when
		// pageSize defaults to 0
		if (pageSize <= 0) {
			pageSize = 1000;
		}

		try {
			String projectsURL = "projects/" + "Teams_Swaggy_PlatformMicroServices";
			ResponseEntity<String> response = makeRestCall(instanceUrl, projectsURL);
			String projectsJSON = XMLToJSONConvertor.convertXmlToJSON(response.getBody());
			JSONObject parse = (JSONObject) new JSONParser().parse(projectsJSON);
			Object projectObject = parse.get("project");
			if (projectObject instanceof JSONObject) {
				JSONObject jsonObject = (JSONObject) projectObject;
				JSONObject projectsObject = (JSONObject) jsonObject.get("projects");
				JSONArray projectsArray = (JSONArray) projectsObject.get("project");
				for (Object object : projectsArray) {
					JSONObject projectArrayObject = (JSONObject) object;
					String pid = (String) projectArrayObject.get("id");

					// extract build Types ( Jobs )
					String buildTypeUrl = "projects/" + pid + "/buildTypes";
					ResponseEntity<String> buildTyperesponse = makeRestCall(instanceUrl, buildTypeUrl);
					String buildTypesJSON = XMLToJSONConvertor.convertXmlToJSON(buildTyperesponse.getBody());
					JSONObject buildTypesObject;
					buildTypesObject = (JSONObject) new JSONParser().parse(buildTypesJSON);
					Object buildTypes = buildTypesObject.get("buildTypes");
					Object buildType = ((JSONObject) buildTypes).get("buildType");
					if (buildType instanceof JSONObject) {
						JSONObject buildTypeObject = (JSONObject) buildType;
						HudsonJob hudsonJob = new HudsonJob();
						hudsonJob.setJobName(str(buildTypeObject, "name"));
						hudsonJob.setDescription(str(buildTypeObject, "id"));
						hudsonJob.setInstanceUrl(instanceUrl);
						Set<Build> builds2 = getBuilds(instanceUrl, hudsonJob);
						result.put(hudsonJob, builds2);
					} else if (buildType instanceof JSONArray) {
						JSONArray jsonArray = (JSONArray) buildType;
						for (Object item : jsonArray) {
							JSONObject buildTypeObject = (JSONObject) item;
							HudsonJob hudsonJob = new HudsonJob();
							hudsonJob.setJobName(str(buildTypeObject, "name"));
							hudsonJob.setDescription(str(buildTypeObject, "id"));
							hudsonJob.setInstanceUrl(instanceUrl);
							Set<Build> builds2 = getBuilds(instanceUrl, hudsonJob);
							result.put(hudsonJob, builds2);
						}
					}
				}
			}
		} catch (Exception e) {
			LOG.error(e.getMessage());
		}

		LOG.info("Environments = HudsonJob");

		return result;
	}

	public Set<Build> getBuilds(String instanceURl, HudsonJob job) throws java.text.ParseException {

		Set<Build> builds = new HashSet<>();
		String endPoint = "buildTypes/" + job.getDescription() + "/builds";
		ResponseEntity<String> response = makeRestCall(instanceURl, endPoint);
		String convertXmlToJSON = XMLToJSONConvertor.convertXmlToJSON(response.getBody());
		try {
			JSONObject parse = (JSONObject) new JSONParser().parse(convertXmlToJSON);
			Object object = ((JSONObject) parse.get("builds")).get("build");
			if (object instanceof JSONArray) {
				JSONArray buildJSONArray = (JSONArray) object;
				for (Object buildObject : buildJSONArray) {
					String buildId = str((JSONObject) buildObject, "id");
					String build = "builds/" + buildId;
					ResponseEntity<String> buildObjectResponse = makeRestCall(instanceURl, build);
					String buildXmlToJSON = XMLToJSONConvertor.convertXmlToJSON(buildObjectResponse.getBody());
					JSONObject parseBuild = (JSONObject) ((JSONObject) new JSONParser().parse(buildXmlToJSON))
							.get("build");
					Build buildData = getBuildInfo(job, parseBuild);
					String buildUrl = "https://teamcity.sapphirepri.com/viewLog.html?buildId="+buildId+"&tab=buildResultsDiv&buildTypeId="+job.getDescription();
					buildData.setBuildUrl(buildUrl);
					builds.add(buildData);
				}
			} else if(object instanceof JSONObject){
				String build = "buildTypes/" + job.getDescription() + "/builds/" + str((JSONObject) object, "number");
				ResponseEntity<String> buildObjectResponse = makeRestCall(instanceURl, build);
				String buildXmlToJSON = XMLToJSONConvertor.convertXmlToJSON(buildObjectResponse.getBody());
				JSONObject parseBuild = (JSONObject) ((JSONObject) new JSONParser().parse(buildXmlToJSON)).get("build");
				Build buildData = getBuildInfo(job, parseBuild);
				builds.add(buildData);
			}
		} catch (ParseException e) {
			LOG.info(e.getMessage());
		}
		return builds;
	}

	@SuppressWarnings("PMD")
	private Build getBuildInfo(HudsonJob job, JSONObject buildObject) throws java.text.ParseException {

		// JSONObject object = (JSONObject) buildObject.get("agent");
		Build build = new Build();
		BuildStatus s = str(buildObject, "status").equals("FAILURE") ? BuildStatus.Failure : BuildStatus.Success;
		build.setBuildStatus(s);
		// String[] split = str(buildObject, "number").split("\\.");
		// if(split.length>0){			
		// 	build.setNumber(split[split.length-1]);
		// }else{
		// 	build.setNumber(str(buildObject, "number"));
		// }
		build.setNumber(str(buildObject, "number"));
		DateFormat df = new SimpleDateFormat("yyyyMMdd'T'hhmmssZ");
		String startdate = str(buildObject, "startDate");
		Date startD = df.parse(startdate);
		build.setStartTime(startD.getTime());

		String finishDate = str(buildObject, "finishDate");
		Date finishD = df.parse(finishDate);
		build.setEndTime(finishD.getTime());

		build.setDuration(finishD.getTime() - startD.getTime());
		build.setBuildUrl(job.getInstanceUrl());
		build.setSourceChangeSet(getScmChangeList(job.getInstanceUrl(), str(buildObject, "id")));
		build.setTimestamp(System.currentTimeMillis());

		return build;
	}

		private List<SCM> getScmChangeList(String instanceUrl, String buildid) throws java.text.ParseException {

		List<SCM> sourceChangeSet = new ArrayList<SCM>();
		String endpoint = "changes?build=" + buildid;
		ResponseEntity<String> changesResponse = makeRestCall(instanceUrl, endpoint);
		String changesJSON = XMLToJSONConvertor.convertXmlToJSON(changesResponse.getBody());
		try {
			JSONObject rootObject = (JSONObject) new JSONParser().parse(changesJSON);
			Object chnageArray = ((JSONObject) rootObject.get("changes")).get("change");
			if (chnageArray instanceof JSONArray) {
				JSONArray changeJSONArray = (JSONArray) chnageArray;
				for (Object changeObject : changeJSONArray) {

					String changeEndpoint = "changes/" + str((JSONObject) changeObject, "id");
					ResponseEntity<String> changeObjectResponse = makeRestCall(instanceUrl, changeEndpoint);
					String changeObjectJsonRoot = XMLToJSONConvertor.convertXmlToJSON(changeObjectResponse.getBody());
					JSONObject changeObjectJson = (JSONObject) ((JSONObject) new JSONParser().parse(changeObjectJsonRoot)).get("change");
					sourceChangeSet.add(getSCM(changeObjectJson));
				}
			} else if (chnageArray instanceof JSONObject) {
				String changeEndpoint = "changes/" + str((JSONObject) chnageArray, "id");
				ResponseEntity<String> changeObjectResponse = makeRestCall(instanceUrl, changeEndpoint);
				String changeObjectJsonRoot = XMLToJSONConvertor.convertXmlToJSON(changeObjectResponse.getBody());
				JSONObject changeObjectJson = (JSONObject) ((JSONObject) new JSONParser().parse(changeObjectJsonRoot)).get("change");
				sourceChangeSet.add(getSCM(changeObjectJson));
			}
		} catch (ParseException e) {
			LOG.info(e.getLocalizedMessage());
		}
		return sourceChangeSet;

	}

	private SCM getSCM(JSONObject changeObjectJson) throws java.text.ParseException {

		long count = 0;
		Object object = changeObjectJson.get("files");
		if (object instanceof JSONArray) {
			JSONArray jsonArray = (JSONArray) object;
			count = jsonArray.size();
		} else {
			count = 1;
		}
		SCM scm = new SCM();
		scm.setNumberOfChanges(count);
		scm.setScmAuthor(str(changeObjectJson, "username"));
		scm.setScmBranch(str(changeObjectJson, "id"));
		scm.setScmCommitLog(str(changeObjectJson, "comment"));
		String str2 = str(changeObjectJson, "date");
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd'T'HHmmss+0000");
		scm.setScmCommitTimestamp(df.parse(str2).getTime());
		scm.setScmRevisionNumber(str(changeObjectJson, "version"));
		scm.setScmUrl(str(changeObjectJson, "webUrl"));
		return scm;

	}

	private String str(JSONObject json, String key) {
		Object value = json.get(key);
		return value == null ? null : value.toString();
	}

	public String buildJobQueryString() {
		StringBuilder query = new StringBuilder(JOB_QUERY);
		int depth = settings.getFolderDepth();
		for (int i = 1; i < depth; i++) {
			query.insert((query.length() - i), ",");
			query.insert((query.length() - i), JOB_QUERY.substring(0, JOB_QUERY.length() - 1));
			query.insert((query.length() - i), "]");
		}
		return query.toString();
	}

	@Override
	public Build getBuildDetails(String buildUrl, String instanceUrl) {
		try {
			String newUrl = rebuildJobUrl(buildUrl, instanceUrl);
			String url = joinURL(newUrl, BUILD_DETAILS_URL_SUFFIX);
			ResponseEntity<String> result = makeRestCall(url);
			String resultJSON = result.getBody();
			if (StringUtils.isEmpty(resultJSON)) {
				LOG.error("Error getting build details for. URL=" + url);
				return null;
			}
			JSONParser parser = new JSONParser();
			try {
				JSONObject buildJson = (JSONObject) parser.parse(resultJSON);
				Boolean building = (Boolean) buildJson.get("building");
				// Ignore jobs that are building
				if (!building) {
					Build build = new Build();
					build.setNumber(buildJson.get("number").toString());
					build.setBuildUrl(buildUrl);
					build.setTimestamp(System.currentTimeMillis());
					build.setStartTime((Long) buildJson.get("timestamp"));
					build.setDuration((Long) buildJson.get("duration"));
					build.setEndTime(build.getStartTime() + build.getDuration());
					build.setBuildStatus(getBuildStatus(buildJson));
					build.setStartedBy(firstCulprit(buildJson));
					if (settings.isSaveLog()) {
						build.setLog(getLog(buildUrl));
					}

					// For git SCM, add the repoBranches. For other SCM types,
					// it's handled while adding changesets
					build.getCodeRepos().addAll(getGitRepoBranch(buildJson));

					boolean isPipelineJob = "org.jenkinsci.plugins.workflow.job.WorkflowRun"
							.equals(getString(buildJson, "_class"));

					// Need to handle duplicate changesets bug in Pipeline jobs
					// (https://issues.jenkins-ci.org/browse/JENKINS-40352)
					Set<String> commitIds = new HashSet<>();
					// This is empty for git
					Set<String> revisions = new HashSet<>();

					if (isPipelineJob) {
						for (Object changeSetObj : getJsonArray(buildJson, "changeSets")) {
							JSONObject changeSet = (JSONObject) changeSetObj;
							addChangeSet(build, changeSet, commitIds, revisions);
						}
					} else {
						JSONObject changeSet = (JSONObject) buildJson.get("changeSet");
						if (changeSet != null) {
							addChangeSet(build, changeSet, commitIds, revisions);
						}
					}
					return build;
				}

			} catch (ParseException e) {
				LOG.error("Parsing build: " + buildUrl, e);
			}
		} catch (RestClientException rce) {
			LOG.error("Client exception loading build details: " + rce.getMessage() + ". URL =" + buildUrl);
		} catch (MalformedURLException mfe) {
			LOG.error("Malformed url for loading build details" + mfe.getMessage() + ". URL =" + buildUrl);
		} catch (URISyntaxException use) {
			LOG.error("Uri syntax exception for loading build details" + use.getMessage() + ". URL =" + buildUrl);
		} catch (RuntimeException re) {
			LOG.error("Unknown error in getting build details. URL=" + buildUrl, re);
		} catch (UnsupportedEncodingException unse) {
			LOG.error("Unsupported Encoding Exception in getting build details. URL=" + buildUrl, unse);
		}
		return null;
	}

	// This method will rebuild the API endpoint because the buildUrl obtained
	// via Jenkins API
	// does not save the auth user info and we need to add it back.
	public static String rebuildJobUrl(String build, String server)
			throws URISyntaxException, MalformedURLException, UnsupportedEncodingException {
		URL instanceUrl = new URL(server);
		String userInfo = instanceUrl.getUserInfo();
		String instanceProtocol = instanceUrl.getProtocol();

		// decode to handle spaces in the job name.
		URL buildUrl = new URL(URLDecoder.decode(build, "UTF-8"));
		String buildPath = buildUrl.getPath();

		String host = buildUrl.getHost();
		int port = buildUrl.getPort();
		URI newUri = new URI(instanceProtocol, userInfo, host, port, buildPath, null, null);
		return newUri.toString();
	}

	/**
	 * Grabs changeset information for the given build.
	 *
	 * @param build
	 *            a Build
	 * @param changeSet
	 *            the build JSON object
	 * @param commitIds
	 *            the commitIds
	 * @param revisions
	 *            the revisions
	 */
	private void addChangeSet(Build build, JSONObject changeSet, Set<String> commitIds, Set<String> revisions) {
		String scmType = getString(changeSet, "kind");
		Map<String, RepoBranch> revisionToUrl = new HashMap<>();

		// Build a map of revision to module (scm url). This is not always
		// provided by the Hudson API, but we can use it if available.
		// For git, this map is empty.
		for (Object revision : getJsonArray(changeSet, "revisions")) {
			JSONObject json = (JSONObject) revision;
			String revisionId = json.get("revision").toString();
			if (StringUtils.isNotEmpty(revisionId) && !revisions.contains(revisionId)) {
				RepoBranch rb = new RepoBranch();
				rb.setUrl(getString(json, "module"));
				rb.setType(RepoBranch.RepoType.fromString(scmType));
				revisionToUrl.put(revisionId, rb);
				build.getCodeRepos().add(rb);
			}
		}

		for (Object item : getJsonArray(changeSet, "items")) {
			JSONObject jsonItem = (JSONObject) item;
			String commitId = getRevision(jsonItem);
			if (StringUtils.isNotEmpty(commitId) && !commitIds.contains(commitId)) {
				SCM scm = new SCM();
				scm.setScmAuthor(getCommitAuthor(jsonItem));
				scm.setScmCommitLog(getString(jsonItem, "msg"));
				scm.setScmCommitTimestamp(getCommitTimestamp(jsonItem));
				scm.setScmRevisionNumber(commitId);
				RepoBranch repoBranch = revisionToUrl.get(scm.getScmRevisionNumber());
				if (repoBranch != null) {
					scm.setScmUrl(repoBranch.getUrl());
					scm.setScmBranch(repoBranch.getBranch());
				}

				scm.setNumberOfChanges(getJsonArray(jsonItem, "paths").size());
				build.getSourceChangeSet().add(scm);
				commitIds.add(commitId);
			}
		}
	}

	/**
	 * Gathers repo urls, and the branch name from the last built revision.
	 * Filters out the qualifiers from the branch name and sets the unqualified
	 * branch name. We assume that all branches are in remotes/origin.
	 */

	@SuppressWarnings("PMD")
	private List<RepoBranch> getGitRepoBranch(JSONObject buildJson) {
		List<RepoBranch> list = new ArrayList<>();

		JSONArray actions = getJsonArray(buildJson, "actions");
		for (Object action : actions) {
			JSONObject jsonAction = (JSONObject) action;
			if (jsonAction.size() > 0) {
				JSONObject lastBuiltRevision = null;
				JSONArray branches = null;
				JSONArray remoteUrls = getJsonArray((JSONObject) action, "remoteUrls");
				if (!remoteUrls.isEmpty()) {
					lastBuiltRevision = (JSONObject) jsonAction.get("lastBuiltRevision");
				}
				if (lastBuiltRevision != null) {
					branches = getJsonArray((JSONObject) lastBuiltRevision, "branch");
				}
				// As of git plugin 3.0.0, when multiple repos are configured in
				// the git plugin itself instead of MultiSCM plugin,
				// they are stored unordered in a HashSet. So it's buggy and we
				// cannot associate the correct branch information.
				// So for now, we loop through all the remoteUrls and associate
				// the built branch(es) with all of them.
				if (branches != null && !branches.isEmpty()) {
					for (Object url : remoteUrls) {
						String sUrl = (String) url;
						if (sUrl != null && !sUrl.isEmpty()) {
							sUrl = removeGitExtensionFromUrl(sUrl);
							for (Object branchObj : branches) {
								String branchName = getString((JSONObject) branchObj, "name");
								if (branchName != null) {
									String unqualifiedBranchName = getUnqualifiedBranch(branchName);
									RepoBranch grb = new RepoBranch(sUrl, unqualifiedBranchName,
											RepoBranch.RepoType.GIT);
									list.add(grb);
								}
							}
						}
					}
				}
			}
		}
		return list;
	}

	private String removeGitExtensionFromUrl(String url) {
		String sUrl = url;
		// remove .git from the urls
		if (sUrl.endsWith(".git")) {
			sUrl = sUrl.substring(0, sUrl.lastIndexOf(".git"));
		}
		return sUrl;
	}

	/**
	 * Gets the unqualified branch name given the qualified one of the following
	 * forms: 1. refs/remotes/<remote name>/<branch name> 2. remotes/<remote
	 * name>/<branch name> 3. origin/<branch name> 4. <branch name>
	 * 
	 * @param qualifiedBranch
	 * @return the unqualified branch name
	 */

	private String getUnqualifiedBranch(String qualifiedBranch) {
		String branchName = qualifiedBranch;
		Pattern pattern = Pattern.compile("(refs/)?remotes/[^/]+/(.*)|(origin[0-9]*/)?(.*)");
		Matcher matcher = pattern.matcher(branchName);
		if (matcher.matches()) {
			if (matcher.group(2) != null) {
				branchName = matcher.group(2);
			} else if (matcher.group(4) != null) {
				branchName = matcher.group(4);
			}
		}
		return branchName;
	}

	private long getCommitTimestamp(JSONObject jsonItem) {
		if (jsonItem.get("timestamp") != null) {
			return (Long) jsonItem.get("timestamp");
		} else if (jsonItem.get("date") != null) {
			String dateString = (String) jsonItem.get("date");
			try {
				return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(dateString).getTime();
			} catch (java.text.ParseException e) {
				// Try an alternate date format...looks like this one is used by
				// Git
				try {
					return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").parse(dateString).getTime();
				} catch (java.text.ParseException e1) {
					LOG.error("Invalid date string: " + dateString, e);
				}
			}
		}
		return 0;
	}

	private String getString(JSONObject json, String key) {
		return (String) json.get(key);
	}

	private String getRevision(JSONObject jsonItem) {
		// Use revision if provided, otherwise use id
		Long revision = (Long) jsonItem.get("revision");
		return revision == null ? getString(jsonItem, "id") : revision.toString();
	}

	private JSONArray getJsonArray(JSONObject json, String key) {
		Object array = json.get(key);
		return array == null ? new JSONArray() : (JSONArray) array;
	}

	private String firstCulprit(JSONObject buildJson) {
		JSONArray culprits = getJsonArray(buildJson, "culprits");
		if (CollectionUtils.isEmpty(culprits)) {
			return null;
		}
		JSONObject culprit = (JSONObject) culprits.get(0);
		return getFullName(culprit);
	}

	private String getFullName(JSONObject author) {
		return getString(author, "fullName");
	}

	private String getCommitAuthor(JSONObject jsonItem) {
		// Use user if provided, otherwise use author.fullName
		JSONObject author = (JSONObject) jsonItem.get("author");
		return author == null ? getString(jsonItem, "user") : getFullName(author);
	}

	private BuildStatus getBuildStatus(JSONObject buildJson) {
		String status = buildJson.get("result").toString();
		switch (status) {
		case "SUCCESS":
			return BuildStatus.Success;
		case "UNSTABLE":
			return BuildStatus.Unstable;
		case "FAILURE":
			return BuildStatus.Failure;
		case "ABORTED":
			return BuildStatus.Aborted;
		default:
			return BuildStatus.Unknown;
		}
	}

	private ResponseEntity<String> makeRestCall(String instanceUrl, String endpoint) {

		String url = normalizeUrl(instanceUrl, "/httpAuth/app/rest/" + endpoint);
		//LOG.info("DefaultHudsonClient.makeRestCall() " + url);
		ResponseEntity<String> response = null;
		try {
			response = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(createHeaders()), String.class);

		} catch (RestClientException re) {
			LOG.error("Error with REST url: " + url);
			LOG.error(re.getMessage());
		}
		return response;
	}

	private String normalizeUrl(String instanceUrl, String remainder) {
		return StringUtils.removeEnd(instanceUrl, "/") + remainder;
	}

	@SuppressWarnings("PMD")
	protected ResponseEntity<String> makeRestCall(String sUrl) throws MalformedURLException, URISyntaxException {
		LOG.debug("Enter makeRestCall " + sUrl);
		URI thisuri = URI.create(sUrl);
		String userInfo = thisuri.getUserInfo();

		// get userinfo from URI or settings (in spring properties)
		if (StringUtils.isEmpty(userInfo)) {
			List<String> servers = this.settings.getServers();
			List<String> usernames = this.settings.getUsernames();
			List<String> apiKeys = this.settings.getApiKeys();
			if (CollectionUtils.isNotEmpty(servers) && CollectionUtils.isNotEmpty(usernames)
					&& CollectionUtils.isNotEmpty(apiKeys)) {
				boolean exactMatchFound = false;
				for (int i = 0; i < servers.size(); i++) {
					if ((servers.get(i) != null)) {
						String domain1 = getDomain(sUrl);
						String domain2 = getDomain(servers.get(i));
						if (StringUtils.isNotEmpty(domain1) && StringUtils.isNotEmpty(domain2)
								&& domain1.equals(domain2) && getPort(sUrl) == getPort(servers.get(i))) {
							exactMatchFound = true;
						}
						if (exactMatchFound && (i < usernames.size()) && (i < apiKeys.size())
								&& (StringUtils.isNotEmpty(usernames.get(i)))
								&& (StringUtils.isNotEmpty(apiKeys.get(i)))) {
							userInfo = usernames.get(i) + ":" + apiKeys.get(i);
						}
						if (exactMatchFound) {
							break;
						}
					}
				}
				if (!exactMatchFound) {
					LOG.warn(
							"Credentials for the following url was not found. This could happen if the domain/subdomain/IP address "
									+ "in the build url returned by Jenkins and the Jenkins instance url in your Hygieia configuration do not match: "
									+ "\"" + sUrl + "\"");
				}
			}
		}
		// Basic Auth only.
		if (StringUtils.isNotEmpty(userInfo)) {
			return rest.exchange(thisuri, HttpMethod.GET, new HttpEntity<>(createHeaders(userInfo)), String.class);
		} else {
			return rest.exchange(thisuri, HttpMethod.GET, null, String.class);
		}

	}

	protected HttpHeaders createHeaders() {
		String authHeader = null;
		String auth = settings.getUsernames().get(0) + ":" + settings.getApiKeys().get(0);
		byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.US_ASCII));
		authHeader = "Basic " + new String(encodedAuth);
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", authHeader);
		return headers;
	}

	private String getDomain(String url) throws URISyntaxException {
		URI uri = new URI(url);
		String domain = uri.getHost();
		return domain;
	}

	private int getPort(String url) throws URISyntaxException {
		URI uri = new URI(url);
		return uri.getPort();
	}

	protected HttpHeaders createHeaders(final String userInfo) {
		byte[] encodedAuth = Base64.encodeBase64(userInfo.getBytes(StandardCharsets.US_ASCII));
		String authHeader = "Basic " + new String(encodedAuth);

		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.AUTHORIZATION, authHeader);
		return headers;
	}

	protected String getLog(String buildUrl) {
		try {
			return makeRestCall(joinURL(buildUrl, "consoleText")).getBody();
		} catch (MalformedURLException mfe) {
			LOG.error("malformed url for build log", mfe);
		} catch (URISyntaxException e) {
			LOG.error("wrong syntax url for build log", e);
		}

		return "";
	}

	// join a base url to another path or paths - this will handle trailing or
	// non-trailing /'s
	public static String joinURL(String base, String... paths) throws MalformedURLException {
		StringBuilder result = new StringBuilder(base);
		for (String path : paths) {
			String p = path.replaceFirst("^(\\/)+", "");
			if (result.lastIndexOf("/") != result.length() - 1) {
				result.append('/');
			}
			result.append(p);
		}
		return result.toString();
	}
}
