package com.capitalone.dashboard.collector;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64;
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
import com.capitalone.dashboard.model.Environment;
import com.capitalone.dashboard.model.EnvironmentComponent;
import com.capitalone.dashboard.model.SCM;
import com.capitalone.dashboard.model.UDeployApplication;
import com.capitalone.dashboard.model.UDeployEnvResCompData;
import com.capitalone.dashboard.util.Supplier;

@Component
public class DefaultUDeployClient implements UDeployClient {
	
	private static final int MAX_BUILD_FETCH_NUMBER = 100;

	private static final Logger LOGGER = LoggerFactory.getLogger(DefaultUDeployClient.class);

	private final UDeploySettings uDeploySettings;
	private final RestOperations restOperations;

	private List<String> deployEnvironments = null ;
	private Map<String, String> systemtests;
	Map<String,Map<String,ArrayList<Long>>> timingMap =  new HashMap<String, Map<String,ArrayList<Long>>>();

	@Autowired
	public DefaultUDeployClient(UDeploySettings uDeploySettings, Supplier<RestOperations> restOperationsSupplier) {
		this.uDeploySettings = uDeploySettings;
		this.restOperations = restOperationsSupplier.get();
		Authenticator.setDefault(new Authenticator(){
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication ("","".toCharArray());
			}
		});
		
		populateEnvironments();
		
	}

	private void populateEnvironments() {

		deployEnvironments = new ArrayList<String>();
		try {
			BufferedReader envreader = new BufferedReader(new InputStreamReader(getResourceFromClassLoader("environments.txt")));
			String envname;
			while ((envname = envreader.readLine()) != null) {
				deployEnvironments.add(envname);
			}
			envreader.close();
		} catch (Exception e) {
			LOGGER.error(e.getLocalizedMessage());
		}
		
		systemtests = new HashMap<String,String>();
		try {
			BufferedReader envreader = new BufferedReader(new InputStreamReader(getResourceFromClassLoader("systemtests.txt")));
			String envname;
			while ((envname = envreader.readLine()) != null) {
				String[] split = envname.split("=");
				systemtests.put(split[0],split[1]);
			}
			envreader.close();
		} catch (Exception e) {
			LOGGER.error(e.getLocalizedMessage());
		}
		
		
		
	}

	private InputStream getResourceFromClassLoader(String name) {
		InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
			if (in == null) {
				throw new NullPointerException("No resource returned.");
		}
		return in;
	}

	@Override
	public List<UDeployApplication> getApplications(String instanceUrl) {
		List<UDeployApplication> applications = new ArrayList<>();
		
		try {
			BufferedReader apssReader = new BufferedReader(new InputStreamReader(getResourceFromClassLoader("apps.txt")));
			String appName;
			while ((appName = apssReader.readLine()) != null) {
				UDeployApplication application = new UDeployApplication();
				application.setInstanceUrl(instanceUrl);
				application.setApplicationName(appName);
				application.setApplicationId(appName);
				applications.add(application);
			}
			apssReader.close();
		} catch (Exception e) {
			LOGGER.error(e.getLocalizedMessage());
		}

		return applications;

	}

	@Override
	public List<Environment> getEnvironments(UDeployApplication application) {

		
		// clear old entries
		for (Entry<String, Map<String, ArrayList<Long>>> entry : timingMap.entrySet()) {
			Map<String, ArrayList<Long>> buildTimings = entry.getValue();
			ArrayList<Long> arrayList = buildTimings.get(application.getApplicationId());
			if(arrayList!=null){
				arrayList.clear();
			}
		}
		
		List<Environment> environments = new ArrayList<>();
		
		try {
			Properties prop = new Properties();
			InputStream resourceFromClassLoader = getResourceFromClassLoader("rootprojects.txt");
			prop.load(resourceFromClassLoader);
			for (Entry<Object, Object> rootproject : prop.entrySet()) {
				String url = "projects/" + rootproject.getValue() + "/buildTypes";// + application.getApplicationId() + "/buildTypes";
				ResponseEntity<String> response = makeRestCall(application.getInstanceUrl(), url);
				String buildTypesXML = XMLToJSONConvertor.convertXmlToJSON(response.getBody());
				addEnvironments(environments, buildTypesXML, (String) rootproject.getKey());
			}
		} catch (IOException e) {
			LOGGER.error(e.getMessage());
		}
		
		LOGGER.info("Environments = buildTypes");
		Collections.sort(environments, new Comparator<Environment>() {
			@Override
			public int compare(final Environment lhs, Environment rhs) {
				return lhs.getName().compareTo(rhs.getName());
			}
		});
		return environments;
	}

	private void addEnvironments(List<Environment> environments, String buildTypesObject,String prefix) {
		
		JSONObject parse;
		try {
			parse = (JSONObject) new JSONParser().parse(buildTypesObject);
			Object object = parse.get("buildTypes");
			Object buildTypeObject = ((JSONObject) object).get("buildType");
			if (buildTypeObject instanceof JSONObject) {
				JSONObject jsonObject = (JSONObject) buildTypeObject;
				environments.add(new Environment(str(jsonObject, "id"), str(jsonObject, "name")));
			} else if (buildTypeObject instanceof JSONArray) {
				JSONArray jsonArray = (JSONArray) buildTypeObject;
				for (Object item : jsonArray) {
					JSONObject jsonObject = (JSONObject) item;
					String environId = str(jsonObject, "id");
					if (deployEnvironments.contains(environId)) {
						Environment environ = new Environment(environId, prefix + "_" + str(jsonObject, "name"));
						environments.add(environ);
					}
				}
			}
		} catch (ParseException e) {
			LOGGER.error(e.getLocalizedMessage());
		}
	}

	@SuppressWarnings("PMD.AvoidCatchingNPE")
	@Override
	public List<EnvironmentComponent> getEnvironmentComponents(UDeployApplication application,
			Environment environment) {
		LOGGER.info("DefaultUDeployClient.getEnvironmentComponents()");
		List<EnvironmentComponent> components = new ArrayList<>();
		String url = "deploy/environment/" + environment.getId() + "/latestDesiredInventory";
		try {
			for (Object item : paresAsArray(makeRestCall(application.getInstanceUrl(), url))) {
				JSONObject jsonObject = (JSONObject) item;

				JSONObject versionObject = (JSONObject) jsonObject.get("version");
				JSONObject componentObject = (JSONObject) jsonObject.get("component");
				JSONObject complianceObject = (JSONObject) jsonObject.get("compliancy");

				EnvironmentComponent component = new EnvironmentComponent();
				component.setEnvironmentID(environment.getId());
				component.setEnvironmentName(environment.getName());
				component.setEnvironmentUrl(
						normalizeUrl(application.getInstanceUrl(), "/#environment/" + environment.getId()));
				component.setComponentID(str(componentObject, "id"));
				component.setComponentName(str(componentObject, "name"));
				component.setComponentVersion(str(versionObject, "name"));
				component
						.setDeployed(complianceObject.get("correctCount").equals(complianceObject.get("desiredCount")));
				component.setAsOfDate(date(jsonObject, "date"));
				components.add(component);
			}
		} catch (NullPointerException npe) {
			LOGGER.info("No Environment data found, No components deployed");
		}

		return components;
	}

	// Called by DefaultEnvironmentStatusUpdater
	// @SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts") // agreed, this method
	// needs refactoring.
	@Override
	public List<UDeployEnvResCompData> getEnvironmentResourceStatusData(UDeployApplication application,
			Environment environment) {
		

		List<UDeployEnvResCompData> buildDataList = new ArrayList<>();
		String buildsEndpoint = "buildTypes/" + environment.getId() + "/builds";
		
		ResponseEntity<String> buildsXML = makeRestCall(application.getInstanceUrl(),
				buildsEndpoint);
		String buildsJSON = XMLToJSONConvertor.convertXmlToJSON(buildsXML.getBody());
		try {
			JSONObject parse = (JSONObject) new JSONParser().parse(buildsJSON);
			Object object = ((JSONObject) parse.get("builds")).get("build");
			if (object instanceof JSONArray) {
				JSONArray buildJSONArray = (JSONArray) object;
				// fetch last 100 builds. 
				int buildCount = 0 ;
				for (Object buildObject : buildJSONArray) {
					
					buildCount++;
					// Parse Log file and Check if this build is relevant for the APP. 
					String buildLog = application.getInstanceUrl()+"/httpAuth/downloadBuildLog.html?buildId="+str((JSONObject) buildObject, "id");
					URL url = new URL(buildLog);
					
					// Download Log File
					org.apache.commons.io.FileUtils.copyURLToFile(url, new File("log.txt"));
					String appversion = parseBuildFile("log.txt", application.getApplicationName());
					
					boolean isSystemTest = isSystemTestEnvironment(environment.getId());
					if(appversion!=null || isSystemTest){
					
						String buildEndpoint = "builds/"+str((JSONObject) buildObject, "id");
						ResponseEntity<String> buildObjectResponse = makeRestCall(application.getInstanceUrl(), buildEndpoint);
						String buildXmlToJSON = XMLToJSONConvertor.convertXmlToJSON(buildObjectResponse.getBody());
						JSONObject parseBuild = (JSONObject) ((JSONObject) new JSONParser().parse(buildXmlToJSON))
								.get("build");
										
						UDeployEnvResCompData buildData = buildUdeployEnvResCompData(environment,application, parseBuild);
						if(buildData!=null) {
							buildData.setComponentVersion(buildData.getComponentVersion() + "==" + appversion);
							buildDataList.add(buildData);
						}
						if(buildCount==MAX_BUILD_FETCH_NUMBER){
							break;
						}
					}
				}
			} else {
				String build = "buildTypes/" + environment.getId() + "/builds/" + str((JSONObject) object, "number");
				ResponseEntity<String> buildObjectResponse = makeRestCall(application.getInstanceUrl(), build);
				String buildXmlToJSON = XMLToJSONConvertor.convertXmlToJSON(buildObjectResponse.getBody());
				JSONObject parseBuild = (JSONObject) ((JSONObject) new JSONParser().parse(buildXmlToJSON)).get("build");

				UDeployEnvResCompData buildData = buildUdeployEnvResCompData(environment, application,
						parseBuild);
				buildDataList.add(buildData);
			}
		} catch (Exception e) {
			LOGGER.info(e.getLocalizedMessage());
		}
		return buildDataList;
	}

	

	private boolean isSystemTestEnvironment(String string) {
		return systemtests.keySet().contains(string);
	}


	private UDeployEnvResCompData buildUdeployEnvResCompData(Environment environment, UDeployApplication application,
			JSONObject buildObject) throws java.text.ParseException {

		
		JSONObject object = (JSONObject) buildObject.get("agent");
		UDeployEnvResCompData data = new UDeployEnvResCompData();
		data.setEnvironmentName(environment.getName());
		data.setCollectorItemId(application.getId());
		
		DateFormat df = new SimpleDateFormat("yyyyMMdd'T'hhmmssZ");
		String startdate = str(buildObject, "startDate");
		Date parse = df.parse(startdate);
		
		if(systemtests.values().contains(environment.getId())){
			
			Map<String, ArrayList<Long>> buildTimings = timingMap.get(environment.getId());
			if(buildTimings == null){
				buildTimings = new HashMap<String, ArrayList<Long>>();
				timingMap.put(environment.getId(), buildTimings);
			}
			ArrayList<Long> arrayList = buildTimings.get(application.getApplicationId());
			if(arrayList == null){
				arrayList = new ArrayList<>();
				buildTimings.put(application.getApplicationId(), arrayList);
			}
			arrayList.add(parse.getTime());
			
		}
		else if(systemtests.keySet().contains(environment.getId())){

			boolean approveBuild =false;
			Map<String, ArrayList<Long>> buildTimings = timingMap.get(environment.getId());
			for (Long time : buildTimings.get(application.getApplicationId())) {
				if (Math.abs(time.longValue()-parse.getTime()) < 120000) {
					approveBuild =true;
				}
			}
			if (!approveBuild) {
				return null;
			}
		}
		
		
		String finishdate = str(buildObject, "finishDate");
		Date parse1 = df.parse(finishdate);
		long millis = (parse1.getTime() - parse.getTime());
		long minutes = (millis / 1000)  / 60;
		long seconds = (millis / 1000) % 60;
		data.setComponentVersion(minutes+"m:"+seconds+"s");
				
		//LOGGER.info("Name and Date "+application.getApplicationName()+" "+date);
		data.setAsOfDate(parse.getTime());
		data.setDeployed(!str(buildObject, "status").equals("FAILURE"));
		// data.setComponentName(str(versionObject, "buildTypeId") + "_" +
		// str(versionObject, "number"));
		data.setComponentName(str(buildObject, "id"));
		data.setOnline("FINISHED".equalsIgnoreCase(str(buildObject, "state")));
		data.setComponentID(str(buildObject, "buildTypeId"));
		data.setResourceName(str(object, "name"));
		data.setBuildInfo(getBuildInfo(application.getInstanceUrl(),buildObject));
		return data;
	}
	
	
	@SuppressWarnings("PMD")
	private Build getBuildInfo(String instanceUrl, JSONObject buildObject) throws java.text.ParseException {

		// JSONObject object = (JSONObject) buildObject.get("agent");
		Build build = new Build();
		BuildStatus s = str(buildObject, "status").equals("FAILURE") ? BuildStatus.Failure : BuildStatus.Success;
		build.setBuildStatus(s);
		String[] split = str(buildObject, "number").split("\\.");
		if(split.length>0){			
			build.setNumber(split[split.length-1]);
		}else{
			build.setNumber(str(buildObject, "number"));
		}
		DateFormat df = new SimpleDateFormat("yyyyMMdd'T'hhmmssZ");
		String startdate = str(buildObject, "startDate");
		Date startD = df.parse(startdate);
		build.setStartTime(startD.getTime());

		String finishDate = str(buildObject, "finishDate");
		Date finishD = df.parse(finishDate);
		build.setEndTime(finishD.getTime());

		build.setDuration(finishD.getTime() - startD.getTime());
		build.setBuildUrl(instanceUrl);
		build.setSourceChangeSet(getScmChangeList(instanceUrl, str(buildObject, "id")));
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
			LOGGER.info(e.getLocalizedMessage());
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
		return scm;

	}

	// ////// Helpers
	@SuppressWarnings("PMD")
	private String parseBuildFile(String buildLogFilePath, String appName) {
	
		String pattern = "Found difference for appname";
		Path path = Paths.get(buildLogFilePath);
		try (BufferedReader reader = Files.newBufferedReader(path); LineNumberReader lineReader = new LineNumberReader(reader);) {
			String line = null;
			while ((line = lineReader.readLine()) != null) {
				if (line.contains(pattern) && line.contains(appName)) {
					line = line.substring(line.indexOf("version"));
					Matcher m = Pattern.compile("\\((.*?)\\)").matcher(line);
					while (m.find()) {
						String group = m.group(1);
						return group.substring(group.indexOf("-->")+3);
					}
				}
			}
		} catch (IOException ex) {
			LOGGER.info(ex.getLocalizedMessage());
		}
		return null;
	}

	private ResponseEntity<String> makeRestCall(String instanceUrl, String endpoint) {

		String url = normalizeUrl(instanceUrl, "/httpAuth/app/rest/" + endpoint);
		//LOGGER.info("DefaultUDeployClient.makeRestCall() " + url);
		ResponseEntity<String> response = null;
		try {
			response = restOperations.exchange(url, HttpMethod.GET, new HttpEntity<>(createHeaders()), String.class);

		} catch (RestClientException re) {
			LOGGER.error("Error with REST url: " + url);
			LOGGER.error(re.getMessage());
		}
		return response;
	}

	private String normalizeUrl(String instanceUrl, String remainder) {
		return StringUtils.removeEnd(instanceUrl, "/") + remainder;
	}

	protected HttpHeaders createHeaders() {
		String authHeader = null;
		String token = uDeploySettings.getToken();
		if (StringUtils.isEmpty(token)) {
			String auth = uDeploySettings.getUsername() + ":" + uDeploySettings.getPassword();
			byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.US_ASCII));
			authHeader = "Basic " + new String(encodedAuth);
		} else {
			String passwordIsAuthToken = "PasswordIsAuthToken:{\"token\":\"" + token + "\"}";
			byte[] encodedAuth = Base64.encodeBase64(passwordIsAuthToken.getBytes(StandardCharsets.US_ASCII));
			authHeader = "Basic " + new String(encodedAuth);
		}

		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", authHeader);
		return headers;
	}

	private JSONArray paresAsArray(ResponseEntity<String> response) {
		if (response == null)
			return new JSONArray();
		try {
			return (JSONArray) new JSONParser().parse(response.getBody());
		} catch (ParseException pe) {
			LOGGER.debug(response.getBody());
			LOGGER.error(pe.getMessage());
		}
		return new JSONArray();
	}

	private String str(JSONObject json, String key) {
		Object value = json.get(key);
		return value == null ? null : value.toString();
	}

	private long date(JSONObject jsonObject, String key) {
		Object value = jsonObject.get(key);
		return value == null ? 0 : (long) value;
	}
}
