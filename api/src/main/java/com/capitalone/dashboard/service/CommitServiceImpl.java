package com.capitalone.dashboard.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Commit;
import com.capitalone.dashboard.model.CommitComponentResponse;
import com.capitalone.dashboard.model.CommitResponse;
import com.capitalone.dashboard.model.Component;
import com.capitalone.dashboard.model.DataResponse;
import com.capitalone.dashboard.model.QCommit;
import com.capitalone.dashboard.repository.CollectorRepository;
import com.capitalone.dashboard.repository.CommitRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import com.capitalone.dashboard.request.CommitRequest;
import com.google.common.collect.Iterables;
import com.mysema.query.BooleanBuilder;

@Service
public class CommitServiceImpl implements CommitService {

    private final CommitRepository commitRepository;
    private final ComponentRepository componentRepository;
    private final CollectorRepository collectorRepository;
    private final CollectorService collectorService;

    @Autowired
    public CommitServiceImpl(CommitRepository commitRepository,
                             ComponentRepository componentRepository,
                             CollectorRepository collectorRepository,
                             CollectorService colllectorService) {
        this.commitRepository = commitRepository;
        this.componentRepository = componentRepository;
        this.collectorRepository = collectorRepository;
        this.collectorService = colllectorService;
    }

    @Override
    public DataResponse<Iterable<Commit>> search(CommitRequest request) {
    	return searchSingleComponent(request);
    }

	private DataResponse<Iterable<Commit>> searchSingleComponent(CommitRequest request) {
		QCommit commit = new QCommit("search");
        BooleanBuilder builder = new BooleanBuilder();

        Component component = componentRepository.findOne(request.getComponentId());
        CollectorItem item = component.getFirstCollectorItemForType(CollectorType.SCM);
        if (item == null) {
        	Iterable<Commit> results = new ArrayList<>();
            return new DataResponse<>(results, new Date().getTime());
        }
        builder.and(commit.collectorItemId.eq(item.getId()));

        if (request.getNumberOfDays() != null) {
            long endTimeTarget = new LocalDate().minusDays(request.getNumberOfDays()).toDate().getTime();
            builder.and(commit.scmCommitTimestamp.goe(endTimeTarget));
        } else if (request.validCommitDateRange()) {
            builder.and(commit.scmCommitTimestamp.between(request.getCommitDateBegins(), request.getCommitDateEnds()));
        }

        if (request.validChangesRange()) {
            builder.and(commit.numberOfChanges.between(request.getChangesGreaterThan(), request.getChangesLessThan()));
        }

        if (!request.getRevisionNumbers().isEmpty()) {
            builder.and(commit.scmRevisionNumber.in(request.getRevisionNumbers()));
        }

        if (!request.getAuthors().isEmpty()) {
            builder.and(commit.scmAuthor.in(request.getAuthors()));
        }

        if (StringUtils.isNotBlank(request.getMessageContains())) {
            builder.and(commit.scmCommitLog.contains(request.getMessageContains()));
        }

        Collector collector = collectorRepository.findOne(item.getCollectorId());
        return new DataResponse<>(commitRepository.findAll(builder.getValue()), collector.getLastExecuted());
	}
    
    public DataResponse<Iterable<CommitComponentResponse>> searchAllComponents(CommitRequest request) {
        if (request == null) {
            return emptyResponse();
        }
        QCommit commit = new QCommit("search");
        BooleanBuilder builder = new BooleanBuilder();
        
        Iterable<Component> findAll = componentRepository.findAll();
        for (Component comp : findAll) {
        	List<CollectorItem> items = comp.getCollectorItems().get(CollectorType.SCM);
            if (items != null && !items.isEmpty()) {
            	CollectorItem item = Iterables.getFirst(items, null);
            	builder.or(commit.collectorItemId.eq(item.getId()));
            }
		}
		long endTimeTarget = new LocalDate().minusDays(14).toDate().getTime();
		builder.and(commit.scmCommitTimestamp.goe(endTimeTarget));
        Iterable<Commit> result;
        result = commitRepository.findAll(builder.getValue());
        
        Map<String,ArrayList<CommitResponse>> compCommitmap = new HashMap<String,ArrayList<CommitResponse>>();
        Map<String,ArrayList<String>> compNameAuthorsMap = new HashMap<String,ArrayList<String>>();
        
        
        for (Commit comt : result) {
			
        	String compName = getCompName(comt.getCollectorItemId());
        	
        	ArrayList<CommitResponse> commitResArray = compCommitmap.get(compName);
        	if(commitResArray==null){
				commitResArray = new ArrayList<CommitResponse>();
        		compCommitmap.put(compName,commitResArray);
        	}
        	
          	ArrayList<String> authorsList = compNameAuthorsMap.get(compName);
        	if(authorsList==null){
        		authorsList = new ArrayList<String>();
        		compNameAuthorsMap.put(compName,authorsList);
        	}
        	authorsList.add(comt.getScmAuthor());
        	
			CommitResponse commitRes = new CommitResponse();
			commitRes.setNumberOfChanges(comt.getNumberOfChanges());
			commitRes.setScmAuthor(comt.getScmAuthor());
			commitRes.setScmCommitLog(comt.getScmCommitLog());
			commitRes.setScmCommitTimestamp(comt.getScmCommitTimestamp());
			commitRes.setScmUrl(comt.getScmUrl());
			commitRes.setComponentName(compName);
			commitResArray.add(commitRes);
			
		}
        
        ArrayList<CommitComponentResponse> commitCompResArray = new ArrayList<CommitComponentResponse>();
        Set<String> keySet = compCommitmap.keySet();
        
        for (String compName : keySet) {
			CommitComponentResponse resComp = new CommitComponentResponse();
			resComp.setCompName(compName);
			ArrayList<CommitResponse> resCommits = compCommitmap.get(compName);
			resComp.setResCommit(resCommits);
			
			Map<String,Integer> mapp = new HashMap<String,Integer>();
			for (CommitResponse comm : resCommits) {
				String scmAuthor = comm.getScmAuthor();
				Integer integer = mapp.get(scmAuthor);
				if (integer == null) {
					mapp.put(scmAuthor, 1);
				} else {
					mapp.put(scmAuthor, integer + 1);
				}
			}
			
			int max = 0;
			String author = null; 
			
			Collection<String> values = mapp.keySet();
			for (String authorName : values) {
				Integer integer = mapp.get(authorName);
				if(integer>max){
					max = integer;
					author=authorName;
				}
			}
			resComp.setTopContributor(author);
			resComp.setTopContribution(max);
			
			commitCompResArray.add(resComp);
			
		}
        
		return new DataResponse<>(commitCompResArray, 10);
    }

    private String getCompName(ObjectId collectorItemId) {
        Iterable<Component> findAll = componentRepository.findAll();
        for (Component comp : findAll) {
        	List<CollectorItem> items = comp.getCollectorItems().get(CollectorType.SCM);
            if (items != null && !items.isEmpty()) {
            	CollectorItem item = Iterables.getFirst(items, null);
            	if(item.getId().equals(collectorItemId)){
            		return comp.getName();
            	}
            }
		}
		return "";
	}

	private DataResponse<Iterable<CommitComponentResponse>> emptyResponse() {
    	Iterable<CommitComponentResponse> results = new ArrayList<>();
        return new DataResponse<>(results, new Date().getTime());
	}

	@Override
    public String createFromGitHubv3(JSONObject request) throws ParseException, HygieiaException {
        GitHubv3 gitHubv3 = new GitHubv3(request.toJSONString());

        if ((gitHubv3.getCollector() == null) || (gitHubv3.getCollectorItem() == null) || (CollectionUtils.isEmpty(gitHubv3.getCommits())))
            throw new HygieiaException("Nothing to update.", HygieiaException.NOTHING_TO_UPDATE);

        Collector col = collectorService.createCollector(gitHubv3.getCollector());
        if (col == null) throw new HygieiaException("Failed creating collector.", HygieiaException.COLLECTOR_CREATE_ERROR);

        CollectorItem item = gitHubv3.getCollectorItem();
        item.setCollectorId(col.getId());
        CollectorItem colItem = collectorService.createCollectorItem(item);
        if (colItem == null) throw new HygieiaException("Failed creating collector item.", HygieiaException.COLLECTOR_ITEM_CREATE_ERROR);

        int count = 0;
        for (Commit c : gitHubv3.getCommits()) {
            if (isNewCommit(colItem, c)) {
                c.setCollectorItemId(colItem.getId());
                commitRepository.save(c);
                count = count + 1;
            }
        }
        return col.getId() + ":" + colItem.getId() + ":" + count + " new commit(s) inserted.";

    }


    private boolean isNewCommit(CollectorItem repo, Commit commit) {
        return commitRepository.findByCollectorItemIdAndScmRevisionNumber(
                repo.getId(), commit.getScmRevisionNumber()) == null;
    }

    private class GitHubv3 {
        private static final String REPO_URL = "url";
        private static final String BRANCH = "branch";
        private static final String SCM_TAG = "scm";
        private CollectorItem collectorItem;
        private Collector collector;
        private List<Commit> commits = new ArrayList<>();
        private String branch;
        private String url;

        private JSONObject jsonObject;
        private JSONParser parser = new JSONParser();


        public GitHubv3(String json) throws ParseException, HygieiaException {

            this.jsonObject = (JSONObject) parser.parse(json);
            buildCommits();
            if (!CollectionUtils.isEmpty(commits)) {
                buildCollectorItem();
                buildCollector();
            }
        }

        private void buildCollector() {
            collector = new Collector();
            collector.setCollectorType(CollectorType.SCM);
            collector.setLastExecuted(System.currentTimeMillis());
            collector.setOnline(true);
            collector.setEnabled(true);
            collector.setName("GitHub");
        }

        private void buildCollectorItem() {
            if (!StringUtils.isEmpty(branch)) {
                collectorItem = new CollectorItem();
                collectorItem.setEnabled(false);
                collectorItem.setPushed(true);
                collectorItem.setLastUpdated(System.currentTimeMillis());
                collectorItem.getOptions().put(REPO_URL, url);
                collectorItem.getOptions().put(BRANCH, branch);
                collectorItem.getOptions().put(SCM_TAG, "Github");
            }
        }


        public CollectorItem getCollectorItem() {
            return collectorItem;
        }

        public Collector getCollector() {
            return collector;
        }

        public List<Commit> getCommits() {
            return commits;
        }

        private void buildCommits() throws HygieiaException {

            JSONArray commitArray = (JSONArray) jsonObject.get("commits");
            JSONObject repoObject = (JSONObject) jsonObject.get("repository");
            url = str(repoObject, "url"); // Repo can be null, but ok to throw NPE.
            branch = str(jsonObject, "ref").replace("refs/heads/", ""); //wow!
            if (CollectionUtils.isEmpty(commitArray)) return;
            for (Object c : commitArray) {
                JSONObject cObj = (JSONObject) c;
                JSONObject authorObject = (JSONObject) cObj.get("author");
                String message = str(cObj, "message");
                String author = str(authorObject, "name");
                long timestamp = new DateTime(str(cObj, "timestamp"))
                        .getMillis();
                int numberChanges = ((JSONArray) cObj.get("added")).size() +
                        ((JSONArray) cObj.get("removed")).size() +
                        ((JSONArray) cObj.get("modified")).size();
                
                JSONArray parents = (JSONArray) jsonObject.get("parents");
				List<String> parentShas = new ArrayList<>();
				if (parents != null) {
					for (Object parentObj : parents) {
						parentShas.add(str((JSONObject)parentObj, "sha"));
					}
				}
                
                Commit commit = new Commit();
                commit.setScmUrl(url);
                commit.setTimestamp(System.currentTimeMillis()); // this is hygieia timestamp.
                commit.setScmRevisionNumber(str(cObj, "id"));
                commit.setScmParentRevisionNumbers(parentShas);
                commit.setScmAuthor(author);
                commit.setScmCommitLog(message);
                commit.setScmCommitTimestamp(timestamp); // actual search timestamp
                commit.setNumberOfChanges(numberChanges);
                commit.setScmBranch(branch);
                commits.add(commit);
            }
        }

        private String str(JSONObject json, String key) throws HygieiaException {
            if (json == null) {
                throw new HygieiaException("Field '" + key + "' cannot be missing or null or empty", HygieiaException.JSON_FORMAT_ERROR);
            }
            Object value = json.get(key);
            return (value == null) ? null : value.toString();
        }

    }


}
