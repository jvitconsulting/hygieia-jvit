package com.capitalone.dashboard.util;

import com.capitalone.dashboard.model.Dashboard;
import com.capitalone.dashboard.model.PipelineCommit;
import com.capitalone.dashboard.model.PipelineStage;
import com.capitalone.dashboard.model.Widget;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class PipelineUtils {


	private PipelineUtils() {

	}

	public static ArrayList<String> populateEnvironments() {

		ArrayList<String> includeEnvironments = new ArrayList<String>();
		try {
			BufferedReader envreader = new BufferedReader(new InputStreamReader(getResourceFromClassLoader("environments.txt")));
			String envname;
			while ((envname = envreader.readLine()) != null) {
				includeEnvironments.add(envname);
			}
			envreader.close();
		} catch (Exception e) {
			
		}
		return includeEnvironments;
	}

	public static InputStream getResourceFromClassLoader(String name) {
		InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
		if (in == null) {
			throw new NullPointerException("No resource returned.");
		}
		return in;
	}

	public static Map<String, PipelineCommit> commitSetToMap(Set<PipelineCommit> set) {
		Map<String, PipelineCommit> returnMap = new HashMap<>();
		for (PipelineCommit commit : set) {
			returnMap.put(commit.getScmRevisionNumber(), commit);
		}
		return returnMap;
	}

	public static Map<PipelineStage, String> getStageToEnvironmentNameMap(Dashboard dashboard) {
		Map<PipelineStage, String> rt = new LinkedHashMap<>();

		for (Widget widget : dashboard.getWidgets()) {
			if (widget.getName().equalsIgnoreCase("build")) {
				rt.put(PipelineStage.valueOf("Build"), "Build");
			}
			if (widget.getName().equalsIgnoreCase("repo")) {
				rt.put(PipelineStage.valueOf("Commit"), "Commit");
			}
			
			ArrayList<String> populateEnvironments = populateEnvironments();

			for (String env : populateEnvironments) {
				rt.put(PipelineStage.valueOf(env), env);
			}

			if (widget.getName().equalsIgnoreCase("pipeline")) {
				Map<?, ?> gh = (Map<?, ?>) widget.getOptions().get("mappings");
				for (Map.Entry<?, ?> entry : gh.entrySet()) {
					rt.put(PipelineStage.valueOf((String) entry.getKey()), (String) entry.getValue());

				}

			}
		}

		return rt;
	}

	public static Map<String, String> getOrderForStages(Dashboard dashboard) {
		Map<String, String> rt = new LinkedHashMap<>();
		rt.put("0", "Commit");
		rt.put("1", "Build");

		ArrayList<String> populateEnvironments = populateEnvironments();
		int i =2; 
		for (String string : populateEnvironments) {
			rt.put(String.valueOf(i), string);
			i++;
		}

		for (Widget widget : dashboard.getWidgets()) {
			if (widget.getName().equalsIgnoreCase("pipeline")) {
				Map<?, ?> gh = (Map<?, ?>) widget.getOptions().get("order");
				int count = 2;
				if (gh != null) {
					for (Map.Entry<?, ?> entry : gh.entrySet()) {
						rt.put(Integer.parseInt((String) entry.getKey()) + count + "", (String) entry.getValue());
					}
				}

			}
		}

		return rt;
	}

	public static String getProdStage(Dashboard dashboard) {
		String prodStage = "";
		for (Widget widget : dashboard.getWidgets()) {
			if (widget.getName().equalsIgnoreCase("pipeline")) {
				prodStage = (String) widget.getOptions().get("prod");
			}
		}
		return prodStage;
	}

	public static void setStageToEnvironmentNameMap(Dashboard dashboard, Map<PipelineStage, String> map) {
		Map<String, String> mappingsMap = new HashMap<>();

		for (Map.Entry<PipelineStage, String> e : map.entrySet()) {
			if (PipelineStage.BUILD.equals(e.getKey()) || PipelineStage.COMMIT.equals(e.getKey())) {
				continue;
			}

			mappingsMap.put(e.getKey().getName().toLowerCase(), e.getValue());
		}

		for (Widget widget : dashboard.getWidgets()) {
			if (widget.getName().equalsIgnoreCase("pipeline")) {

				widget.getOptions().put("mappings", mappingsMap);
			}
		}
	}
}
