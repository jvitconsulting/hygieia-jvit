package com.capitalone.dashboard.event;

import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;

import com.capitalone.dashboard.model.Build;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.Commit;
import com.capitalone.dashboard.model.Component;
import com.capitalone.dashboard.model.Dashboard;
import com.capitalone.dashboard.model.EnvironmentComponent;
import com.capitalone.dashboard.model.Pipeline;
import com.capitalone.dashboard.model.PipelineCommit;
import com.capitalone.dashboard.model.PipelineStage;
import com.capitalone.dashboard.model.RepoBranch;
import com.capitalone.dashboard.model.SCM;
import com.capitalone.dashboard.repository.BinaryArtifactRepository;
import com.capitalone.dashboard.repository.BuildRepository;
import com.capitalone.dashboard.repository.CollectorItemRepository;
import com.capitalone.dashboard.repository.CollectorRepository;
import com.capitalone.dashboard.repository.CommitRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import com.capitalone.dashboard.repository.DashboardRepository;
import com.capitalone.dashboard.repository.JobRepository;
import com.capitalone.dashboard.repository.PipelineRepository;

@org.springframework.stereotype.Component
public class EnvironmentComponentEventListener extends HygieiaMongoEventListener<EnvironmentComponent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentComponentEventListener.class);

    private final DashboardRepository dashboardRepository;
    private final ComponentRepository componentRepository;
    public final BinaryArtifactRepository binaryArtifactRepository;
    private final BuildRepository buildRepository;
    public final JobRepository<?> jobRepository;
    private final CommitRepository commitRepository;

	private ArrayList<String> envNames;
    


    @Autowired
    public EnvironmentComponentEventListener(DashboardRepository dashboardRepository,
                              CollectorItemRepository collectorItemRepository,
                              ComponentRepository componentRepository,
                              BinaryArtifactRepository binaryArtifactRepository,
                              PipelineRepository pipelineRepository,
                              CollectorRepository collectorRepository,
                              BuildRepository buildRepository,
                              JobRepository<?> jobRepository, CommitRepository commitRepository) {
        super(collectorItemRepository, pipelineRepository, collectorRepository);
        this.dashboardRepository = dashboardRepository;
        this.componentRepository = componentRepository;
        this.binaryArtifactRepository = binaryArtifactRepository;
        this.buildRepository = buildRepository;
        this.jobRepository = jobRepository;
        this.commitRepository = commitRepository;
        
        this.envNames = new ArrayList<String>();
    		envNames.add("CTI_0 - Check Current B4 Start");
    		envNames.add("CTI_1a SBE Incremental Deploy");
    		envNames.add("CTI_1b SFE Incremental Deploy");
    		envNames.add("CTI_2 Validate Deploy and Run System Tests");
    		envNames.add("CTI_3a Success - Output Stable (specific) & Current + Update AcceptedVersion");
    		envNames.add("NTI_0 - Check Current B4 Start");
    		envNames.add("NTI_1 SBE/SFE Incremental Deploy");
    		envNames.add("NTI_2 Validate Deploy and Run System Tests");
    		envNames.add("NTI_3a Success");
    		envNames.add("QAS1_0 - Check Current Before Start");
    		envNames.add("QAS1_1a SBE/SFE Deploy");
    		envNames.add("QAS1_2 Validate Deploy and Execute System Team Tests");
    		envNames.add("QAS1_3a Success");
        envNames.add("QAS2_0 - Check Current Before Start");
        envNames.add("QAS2_1a SBE/SFE Deploy");
        envNames.add("QAS2_2 Validate Deploy and Execute System Team Tests");
        envNames.add("QAS2_3a Success");
    		envNames.add("PROD_0 - Check Current Before Start");
    		envNames.add("PROD_1a SBE/SFE Deploy");
    		envNames.add("PROD_2 Validate Deploy and Execute System Team Tests");
    		envNames.add("PROD_3a Success");
        
//        this.envNames = new ArrayList<String>();
//		try {
//			BufferedReader envreader = new BufferedReader(new InputStreamReader(getResourceFromClassLoader("environments.txt")));
//			String envname;
//			while ((envname = envreader.readLine()) != null) {
//				envNames.add(envname);
//			}
//			envreader.close();
//		} catch (Exception e) {
//			LOGGER.error(e.getLocalizedMessage());
//		}
        
    }
    
    private InputStream getResourceFromClassLoader(String name) {
		InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
			if (in == null) {
				throw new NullPointerException("No resource returned.");
		}
		return in;
	}

    @Override
    public void onAfterSave(AfterSaveEvent<EnvironmentComponent> event) {
        super.onAfterSave(event);

        EnvironmentComponent environmentComponent = event.getSource();
        if(!environmentComponent.isDeployed()){
            return;
        }

        processEnvironmentComponent(environmentComponent);
    }

    /**
     * For the environment component, find all team dashboards related to the environment component and add the
     * commits to the proper stage
     * @param environmentComponent
     */
    private void processEnvironmentComponent(EnvironmentComponent environmentComponent) {
        List<Dashboard> dashboards = findTeamDashboardsForEnvironmentComponent(environmentComponent);

        for (Dashboard dashboard : dashboards) {
            Pipeline pipeline = getOrCreatePipeline(dashboard);

        	if (LOGGER.isDebugEnabled()) {
        		LOGGER.debug("Attempting to update pipeline " + pipeline.getId());
        	}
            
            addCommitsToEnvironmentStage(environmentComponent, pipeline);
            pipelineRepository.save(pipeline);
        }

    }

    /**
     * Must first start by finding all artifacts that relate to an environment component based on the name, and potentially
     * the timestamp of the last time an artifact came through the environment.
     *
     * Multiple artifacts could have been built but never deployed.
     * @param environmentComponent
     * @param pipeline
     */
    @SuppressWarnings("PMD.NPathComplexity")
    private void addCommitsToEnvironmentStage(EnvironmentComponent environmentComponent, Pipeline pipeline){
		try{
		PrintWriter writer = new PrintWriter("/home/ubuntu/loga.txt", "UTF-8");
		writer.println("EnvironmentComponentEventListener.addCommitsToEnvironmentStage()");
    	String niceEnvironmentName = getNiceEnvironmentName(environmentComponent.getEnvironmentName());
		getOrCreateEnvironmentStage(pipeline, niceEnvironmentName);
        String pseudoEnvName = niceEnvironmentName;
        Build build = environmentComponent.getBuildInfo();
        writer.println(pseudoEnvName);
        	if (build != null) {
        		writer.println("build got");
				for (SCM scm : build.getSourceChangeSet()) {
					writer.println("change set");
					PipelineCommit commit = new PipelineCommit(scm, environmentComponent.getAsOfDate());
					pipeline.addCommit(niceEnvironmentName, commit);
				}
        	}
        	writer.println("build updated");
        	boolean hasFailedBuilds = !pipeline.getFailedBuilds().isEmpty();
            processPreviousFailedBuilds(build, pipeline);
            /**
             * If some build events are missed, here is an attempt to move commits to the build stage
             * This also takes care of the problem with Jenkins first build change set being empty.
             *
             * Logic:
             * If the build start time is after the scm commit, move the commit to build stage. Match the repo at the very least.
             */
            Map<String, PipelineCommit> commitStageCommits = pipeline.getCommitsByEnvironmentName(PipelineStage.COMMIT.getName());
            Map<String, PipelineCommit> envStageCommits = pipeline.getCommitsByEnvironmentName(pseudoEnvName);
            for (String rev : commitStageCommits.keySet()) {
                PipelineCommit commit = commitStageCommits.get(rev);
                if ((commit.getScmCommitTimestamp() < build.getStartTime()) && !envStageCommits.containsKey(rev) && isMoveCommitToBuild(build, commit)) {
                    pipeline.addCommit(pseudoEnvName, commit);
                }
            }
            pipelineRepository.save(pipeline);
            if (hasFailedBuilds) {
                buildRepository.save(build);
            }
            writer.close();
		}catch(Exception e){
			LOGGER.info(e.getMessage());
		}

    }

    private String getNiceEnvironmentName(String environmentName) {
		for (String string : envNames) {
			if(environmentName.startsWith(string)){
				return string;
			}
		}
		return "Dummy";
	}

	/**
     * Iterate over failed builds, if the failed build collector item id matches the successful builds collector item id
     * take all the commits from the changeset of the failed build and add them to the pipeline and also to the changeset
     * of the successful build.  Then remove the failed build from the collection after it has been processed.
     *
     * @param successfulBuild
     * @param pipeline
     */
    private void processPreviousFailedBuilds(Build successfulBuild, Pipeline pipeline) {

        if (!pipeline.getFailedBuilds().isEmpty()) {
            Iterator<Build> failedBuilds = pipeline.getFailedBuilds().iterator();

            while (failedBuilds.hasNext()) {
                Build b = failedBuilds.next();
                if (b.getCollectorItemId().equals(successfulBuild.getCollectorItemId())) {
                    for (SCM scm : b.getSourceChangeSet()) {
                        PipelineCommit failedBuildCommit = new PipelineCommit(scm, successfulBuild.getStartTime());
                        pipeline.addCommit(PipelineStage.BUILD.getName(), failedBuildCommit);
                        successfulBuild.getSourceChangeSet().add(scm);
                    }
                    failedBuilds.remove();

                }
            }
        }
    }


    private boolean isMoveCommitToBuild(Build build, SCM scm) {
        List<Commit> commitsFromRepo = getCommitsFromCommitRepo(scm);
        List<RepoBranch> codeReposFromBuild = build.getCodeRepos();
        Set<String> codeRepoUrlsFromCommits = new HashSet<>();
        for (Commit c : commitsFromRepo) {
            codeRepoUrlsFromCommits.add(getRepoNameOnly(c.getScmUrl()));
        }

        for (RepoBranch rb : codeReposFromBuild) {
            if (codeRepoUrlsFromCommits.contains(getRepoNameOnly(rb.getUrl()))) {
                return true;
            }
        }
        return false;
    }

    private List<Commit> getCommitsFromCommitRepo(SCM scm) {
        return commitRepository.findByScmRevisionNumber(scm.getScmRevisionNumber());
    }

    private String getRepoNameOnly(String url) {
        try {
            URL temp = new URL(url);
            return temp.getHost() + temp.getPath();
        } catch (MalformedURLException e) {
            return url;
        }
    }

    

    /**
     * Finds team dashboards for a given environment componentby way of the deploy collector item
     * @param environmentComponent
     * @return
     */
    private List<Dashboard> findTeamDashboardsForEnvironmentComponent(EnvironmentComponent environmentComponent){
        List<Dashboard> dashboards;
        CollectorItem deploymentCollectorItem = collectorItemRepository.findOne(environmentComponent.getCollectorItemId());
        List<Component> components = componentRepository.findByDeployCollectorItemId(deploymentCollectorItem.getId());
        dashboards = dashboardRepository.findByApplicationComponentsIn(components);
        return dashboards;
    }
    
    
}
