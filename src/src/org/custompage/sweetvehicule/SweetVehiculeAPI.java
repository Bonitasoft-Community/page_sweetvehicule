package org.custompage.sweetvehicule;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bonitasoft.engine.api.IdentityAPI;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.api.ProfileAPI;
import org.bonitasoft.engine.api.TenantAPIAccessor;
import org.bonitasoft.engine.bpm.flownode.HumanTaskInstance;
import org.bonitasoft.engine.bpm.flownode.HumanTaskInstanceSearchDescriptor;
import org.bonitasoft.engine.bpm.process.ProcessDefinition;
import org.bonitasoft.engine.exception.BonitaHomeNotSetException;
import org.bonitasoft.engine.exception.ServerAPIException;
import org.bonitasoft.engine.exception.UnknownAPITypeException;
import org.bonitasoft.engine.identity.User;
import org.bonitasoft.engine.profile.Profile;
import org.bonitasoft.engine.profile.ProfileCriterion;
import org.bonitasoft.engine.search.SearchOptionsBuilder;
import org.bonitasoft.engine.search.SearchResult;
import org.bonitasoft.engine.session.APISession;
import org.bonitasoft.ext.properties.BonitaProperties;
import org.bonitasoft.log.event.BEvent;
import org.bonitasoft.log.event.BEventFactory;
import org.bonitasoft.log.event.BEvent.Level;
import org.bonitasoft.web.extension.page.PageResourceProvider;
import org.json.simple.JSONValue;

public class SweetVehiculeAPI {

    public static Logger logger = Logger.getLogger(SweetVehiculeAPI.class.getName());

    public static String cstLogHeader = "~~~~ SweetVehiculeAPI:";
    private final static BEvent EVENT_ERROR_JSONPARSING = new BEvent(SweetVehiculeAPI.class.getName(), 1, Level.ERROR,
            "Json Parsing error", "Parameters are in error", "No result to display",
            "Check exception");
    private final static BEvent EVENT_NO_RULE_FOUND = new BEvent(SweetVehiculeAPI.class.getName(), 2,
            Level.APPLICATIONERROR,
            "No rule found", "Given rule are not found", "Operation can't be done",
            "Check the rule Id");
    private final static BEvent EVENT_API_ERROR = new BEvent(SweetVehiculeAPI.class.getName(), 3, Level.APPLICATIONERROR,
            "Access Error", "Error accessing information", "Information can't be retrieved",
            "Are you still connected? Verify your connection");
    private final static BEvent EVENT_SAVED_WITH_SUCCESS = new BEvent(SweetVehiculeAPI.class.getName(), 4, Level.SUCCESS,
            "Rules saved", "Rules saved with success");
    private final static BEvent EVENT_REMOVED_WITH_SUCCESS = new BEvent(SweetVehiculeAPI.class.getName(), 5, Level.SUCCESS,
            "Rule removed", "Rules removed with success");

    private final static BEvent EVENT_ALREADY_ASSIGNED = new BEvent(SweetVehiculeAPI.class.getName(), 6, Level.APPLICATIONERROR,
            "Task already assigned", "It's not possible to get this task, it's already assigned to a different user", "Task is not accessible", "Ask user to free the task, or choose a different one");

    public static class ParameterSource {

        public String jsonSt;
        public List<BEvent> listEvents = new ArrayList<BEvent>();
        public Long startIndex;
        public Long maxResults;
        public APISession apiSession;

        // -------------- task access
        /**
         * User wants to see its tasks
         */
        public boolean myTasks = true;

        public List<Map<String, Object>> listMonitoring;

        public Long taskId;
        // boolean 
        public boolean isadministrator = false;

        /**
         * the ShowAllRules show all rule only if you are a administrator
         */
        public boolean showAllRules = false;
        public boolean showMyDelegation = true;
        public boolean showMyAffectation = true;
        // -------------- rule management
        Long delegationRuleId;
        Map<String, Object> delegationRule;

        public static ParameterSource getInstanceFromJson(APISession apiSession, String jsonSt) {
            ParameterSource parameterSource = new ParameterSource();
            parameterSource.apiSession = apiSession;
            parameterSource.jsonSt = jsonSt;
            if (parameterSource.jsonSt == null)
                return parameterSource;

            final HashMap<String, Object> jsonHash = (HashMap<String, Object>) JSONValue.parse(parameterSource.jsonSt);
            if (jsonHash == null) {
                // error during parsing
                parameterSource.listEvents
                        .add(new BEvent(EVENT_ERROR_JSONPARSING, "Json[" + parameterSource.jsonSt + "]"));
                return parameterSource;
            }
            // task management
            parameterSource.myTasks = getBooleanValue(jsonHash.get("mytasks"), true);
            parameterSource.listMonitoring = (List<Map<String, Object>>) jsonHash.get("monitoring");

            // Rule management
            parameterSource.delegationRuleId = jsonHash.get(DelegationRule.cstjsonRuleId) != null
                    ? Long.valueOf(jsonHash.get(DelegationRule.cstjsonRuleId).toString()) : null;
            parameterSource.delegationRule = (Map) jsonHash.get("delegationRule");

            // there is a selectedUser ? Then the user changed.
            if (parameterSource.delegationRule != null) {
                if (parameterSource.delegationRule.get("selecteduser") != null) {
                    Map<String, Object> selectedUser = (Map<String, Object>) parameterSource.delegationRule
                            .get("selecteduser");
                    if (selectedUser.get("id") != null) {
                        parameterSource.delegationRule.put(DelegationRule.cstjsonDelegatorId, selectedUser.get("id"));
                    }
                }
                if (parameterSource.delegationRule.get(DelegationRule.cstjsonDelegates) != null) {
                    // if this is a list of MAP, let's calculates that to a list of LONG
                    List<Long> listDelegatesid = new ArrayList<Long>();
                    for (Map<String, Object> oneDelegate : (List<Map<String, Object>>) parameterSource.delegationRule
                            .get(DelegationRule.cstjsonDelegates)) {
                        Long delegateId = null;
                        Map<String, Object> selectedUser = (Map<String, Object>) oneDelegate.get("selecteduser");
                        if (selectedUser != null && selectedUser.get("id") != null) {
                            delegateId = (Long) selectedUser.get("id");
                        }
                        // no change, same user
                        else if (oneDelegate.get("delegateId") != null) {
                            delegateId = (Long) oneDelegate.get("delegateId");
                        }
                        // avoid double declaration
                        if (delegateId != null && (!listDelegatesid.contains(delegateId)))
                            listDelegatesid.add(delegateId);
                    }
                    parameterSource.delegationRule.put(DelegationRule.cstjsonDelegatesLong, listDelegatesid);

                }
            }
            // isAdministrateur ?
            ProfileAPI profileAPI;
            try {
                profileAPI = TenantAPIAccessor.getProfileAPI(parameterSource.apiSession);

                parameterSource.isadministrator = false;
                for (Profile profile : profileAPI.getProfilesForUser(parameterSource.apiSession.getUserId(), 0, 1000,
                        ProfileCriterion.ID_ASC)) {
                    if (profile.getName().equalsIgnoreCase("Administrator"))
                        parameterSource.isadministrator = true;

                }
            } catch (Exception e) {
                logger.severe(cstLogHeader + " Exception " + e.getMessage());

            }
            parameterSource.taskId = getLongValue(jsonHash.get("taskId"), null);

            // show my delegation ?
            parameterSource.showMyDelegation = getBooleanValue(jsonHash.get("showMyDelegation"), true);

            parameterSource.showMyAffectation = getBooleanValue(jsonHash.get("showMyAffectation"), true);
            parameterSource.showAllRules = getBooleanValue(jsonHash.get("showAllRules"), true);

            return parameterSource;

        }
    }

    public static SweetVehiculeAPI getInstance() {
        return new SweetVehiculeAPI();
    }

    /* -------------------------------------------------------------------- */
    /*                                                                      */
    /* Initial */
    /*                                                                      */
    /* -------------------------------------------------------------------- */
    public Map<String, Object> getInit(ParameterSource parameterSource, PageResourceProvider pageResourceProvider) {
        Map<String, Object> result = new HashMap<String, Object>();

        try {
            result = getListDelegation(parameterSource, pageResourceProvider);
            result.put("isAdministrator", parameterSource.isadministrator);

            Map<String, Object> resultTasks = getMyTasks(parameterSource, pageResourceProvider);
            result.putAll(resultTasks);

        } catch (Exception e) {
            logger.severe("SweetVehicule: Exception " + e.getMessage());
            List<BEvent> listEvents = new ArrayList<BEvent>();
            listEvents.add(new BEvent(EVENT_API_ERROR, e, ""));
            result.put("listevents", BEventFactory.getHtml(listEvents));
        }
        return result;
    }
    /* -------------------------------------------------------------------- */
    /*                                                                      */
    /* getListDelegates */
    /*                                                                      */
    /* -------------------------------------------------------------------- */

    public Map<String, Object> getListDelegation(ParameterSource parameterSource,
            PageResourceProvider pageResourceProvider) {
        List<BEvent> listEvents = new ArrayList<BEvent>();
        Map<String, Object> result = new HashMap<String, Object>();

        try {
            IdentityAPI identityAPI = TenantAPIAccessor.getIdentityAPI(parameterSource.apiSession);
            Long userId = parameterSource.apiSession.getUserId();

            List<Map<String, Object>> listDelegation = new ArrayList<Map<String, Object>>();
            LoadResult loadResult = getListDelegationFilter(parameterSource, pageResourceProvider);

            for (DelegationRule delegationRule : loadResult.listFilterDelegation) {
                try {
                    // transform to a Map for JSON
                    Map<String, Object> oneDelegation = delegationRule.getAttributes(true);
                    if (delegationRule.getDelegatorId() != null) {
                        User user = identityAPI.getUser(delegationRule.getDelegatorId());
                        oneDelegation.put("delegatorUserName", user.getUserName());
                        oneDelegation.put("delegatorFirstName", user.getFirstName());
                        oneDelegation.put("delegatorLastName", user.getLastName());
                        oneDelegation.put("delegatorCompleteName", getUserCompleteName(user));
                    }
                    // then do the same with delegate, add the delegate list as a String
                    String listUserSt = "";
                    List<Map<String, Object>> listDelegatesMap = new ArrayList<Map<String, Object>>();
                    for (Long userDelegateid : delegationRule.getListDelegatesUserId()) {
                        if (listUserSt.length() > 0)
                            listUserSt += ", ";
                        User userDelegate = identityAPI.getUser(userDelegateid);
                        listUserSt += userDelegate.getFirstName() + " " + userDelegate.getLastName() + " ("
                                + userDelegate.getUserName() + ")";
                        Map<String, Object> recordDelegate = new HashMap<String, Object>();
                        recordDelegate.put("delegateId", userDelegate.getId());
                        recordDelegate.put("delegateUserName", userDelegate.getUserName());
                        recordDelegate.put("delegateFirstName", userDelegate.getFirstName());
                        recordDelegate.put("delegateLastName", userDelegate.getLastName());
                        recordDelegate.put("delegateCompleteName", getUserCompleteName(userDelegate));
                        listDelegatesMap.add(recordDelegate);
                    }
                    oneDelegation.put("delegateUserSt", listUserSt);
                    oneDelegation.put(DelegationRule.cstjsonDelegates, listDelegatesMap);

                    listDelegation.add(oneDelegation);
                } catch (Exception e) {
                    logger.severe("SweetVehicule: Exception " + e.getMessage());
                }
            }

            result.put("listDelegations", listDelegation);

            List<Map<String, Object>> listAffectation = new ArrayList<Map<String, Object>>();
            Set<Long> mapAffectation = new HashSet<Long>();
            for (DelegationRule delegationRule : loadResult.listFilterDelegation) {
                if (mapAffectation.contains(delegationRule.getDelegatorId()))
                    continue;
                if (delegationRule.getDelegatorId() == null)
                    continue;

                User user = identityAPI.getUser(delegationRule.getDelegatorId());
                Map<String, Object> oneAffectation = new HashMap<String, Object>();
                oneAffectation.put("username", user.getUserName());
                oneAffectation.put("userid", user.getId());
                oneAffectation.put("usercompletename", getUserCompleteName(user));
                mapAffectation.add(delegationRule.getDelegatorId());
                listAffectation.add(oneAffectation);
            }
            result.put("listMonitoring", listAffectation);

            result.put("isAdministrator", parameterSource.isadministrator);

        } catch (Exception e) {
            listEvents.add(EVENT_API_ERROR);

        }
        result.put("listevents", BEventFactory.getHtml(listEvents));

        return result;
    }

    private LoadResult getListDelegationFilter(ParameterSource parameterSource,
            PageResourceProvider pageResourceProvider) {

        LoadResult loadResult = loadDelegationsRules(pageResourceProvider);
        for (DelegationRule delegationRule : loadResult.listDelegations) {
            boolean keep = false;
            if (parameterSource.isadministrator && parameterSource.showAllRules)
                keep = true;
            if (parameterSource.showMyDelegation && delegationRule.isOwnedBy(parameterSource.apiSession.getUserId()))
                keep = true;
            if (parameterSource.showMyAffectation && delegationRule.isAffected(parameterSource.apiSession.getUserId()))
                keep = true;
            if (keep)
                loadResult.listFilterDelegation.add(delegationRule);

        }
        return loadResult;
    }

    /* -------------------------------------------------------------------- */
    /*                                                                      */
    /* getListTask */
    /*                                                                      */
    /* -------------------------------------------------------------------- */
    public Map<String, Object> getMyTasks(ParameterSource parameterSource, PageResourceProvider pageResourceProvider) {

        Map<String, Object> result = new HashMap<String, Object>();
        List<Map<String, Object>> listTasks = new ArrayList<Map<String, Object>>();
        List<BEvent> listEvents = new ArrayList<BEvent>();

        List<Long> listUsers = new ArrayList<Long>();
        try {
            if (parameterSource.myTasks) {
                listUsers.add(parameterSource.apiSession.getUserId());
            }
            ProcessAPI processAPI = TenantAPIAccessor.getProcessAPI(parameterSource.apiSession);
            IdentityAPI identityAPI = TenantAPIAccessor.getIdentityAPI(parameterSource.apiSession);

            // searchOptionBuilder.filter( HumanTaskInstanceSearchDescriptor.ASSIGNEE_ID, parameterSource.apiSession.getUserId());
            if (parameterSource.listMonitoring != null)
                for (Map<String, Object> oneMonitoring : parameterSource.listMonitoring) {
                    if (getBooleanValue(oneMonitoring.get("show"), false)) {
                        Long userId = getLongValue(oneMonitoring.get("userid"), null);
                        listUsers.add(userId);
                    }
                }
            // no way at this moment to search a GROUP list of all users, so search list per list
            Map<Long, String> cacheProcessName = new HashMap<Long, String>();
        
            
            for (Long userId : listUsers) {
                SearchOptionsBuilder searchOptionBuilder = new SearchOptionsBuilder(0, 100);
                User user = identityAPI.getUser(userId);
                //SearchResult<HumanTaskInstance> searchResult = processAPI.searchHumanTaskInstances(searchOptionBuilder.done());
                SearchResult<HumanTaskInstance> searchResult = null;
                List<HumanTaskInstance> collectList= new ArrayList<HumanTaskInstance>();
                // myself ? Search my Assigned and pending task
                if (userId ==  parameterSource.apiSession.getUserId())
                {
                    searchOptionBuilder.filter(HumanTaskInstanceSearchDescriptor.ASSIGNEE_ID, parameterSource.apiSession.getUserId()); 
                    searchResult = processAPI.searchAssignedAndPendingHumanTasks(searchOptionBuilder.done());
                    collectList.addAll(searchResult.getResult());

                    searchOptionBuilder = new SearchOptionsBuilder(0, 100);
                    searchResult = processAPI.searchPendingTasksForUser(userId, searchOptionBuilder.done());
                    collectList.addAll(searchResult.getResult());
                }
                else
                {
                    searchResult = processAPI.searchPendingTasksForUser(userId, searchOptionBuilder.done());
                    collectList.addAll(searchResult.getResult());
                }
                for (HumanTaskInstance humanTaskInstance : collectList) {
                    Map<String, Object> oneTask = new HashMap<String, Object>();
                    oneTask.put("taskId", humanTaskInstance.getId());
                    oneTask.put("isassigned",
                            humanTaskInstance.getAssigneeId() == parameterSource.apiSession.getUserId());

                    oneTask.put("taskname", humanTaskInstance.getName());
                    oneTask.put("description", humanTaskInstance.getDescription());
                    oneTask.put("case", humanTaskInstance.getParentProcessInstanceId());
                    if (!cacheProcessName.containsKey(humanTaskInstance.getProcessDefinitionId())) {
                        ProcessDefinition processDefinition = processAPI
                                .getProcessDefinition(humanTaskInstance.getProcessDefinitionId());
                        cacheProcessName.put(humanTaskInstance.getProcessDefinitionId(), processDefinition.getName());
                    }
                    oneTask.put("processname", cacheProcessName.get(humanTaskInstance.getProcessDefinitionId()));

                    oneTask.put("ownerId", humanTaskInstance.getAssigneeId());
                    if (humanTaskInstance.getClaimedDate() != null)
                        oneTask.put("duedate", sdfHtml5.format(humanTaskInstance.getClaimedDate()));
                    if (userId != parameterSource.apiSession.getUserId())
                        oneTask.put("owner", getUserCompleteName(user));
                    // Access the task
                    oneTask.put("uritasklink", "/bonita/portal/form/taskInstance/"+humanTaskInstance.getId());

                    listTasks.add(oneTask);
                }
            }
            result.put("listtasks", listTasks);
        } catch (Exception e) {
            logger.severe(cstLogHeader + " Exception " + e.getMessage());
            listEvents.add(new BEvent(EVENT_API_ERROR, e, ""));
        }
        result.put("listevents", BEventFactory.getHtml( listEvents) );
        return result;
    }

    /**
     * assign the task, and return the URL
     * 
     * @param parameterSource
     * @param pageResourceProvider
     * @return
     */
    public Map<String, Object> accessTask(ParameterSource parameterSource, PageResourceProvider pageResourceProvider) {

        Map<String, Object> result = new HashMap<String, Object>();
        List<BEvent> listEvents = new ArrayList<BEvent>();
        try {
            ProcessAPI processAPI = TenantAPIAccessor.getProcessAPI(parameterSource.apiSession);
            HumanTaskInstance humanTaskInstance = processAPI.getHumanTaskInstance(parameterSource.taskId);
            if (humanTaskInstance.getAssigneeId()!=0 && humanTaskInstance.getAssigneeId()!= parameterSource.apiSession.getUserId())
            {
                // Oups, assigned to someone else !
                IdentityAPI identityAPI = TenantAPIAccessor.getIdentityAPI(parameterSource.apiSession);
                User user = identityAPI.getUser( humanTaskInstance.getAssigneeId()  );
                listEvents.add( new BEvent( EVENT_ALREADY_ASSIGNED, "User["+ user.getUserName()+"]"));
            }
            else
            {
                processAPI.assignUserTask(parameterSource.taskId, parameterSource.apiSession.getUserId());
                result.put("isassigned", true);
                /*
                 * HumanTaskInstance taskInstance= processAPI.getHumanTaskInstance(parameterSource.taskId);
                 * ProcessDefinition processDefinition = processAPI.getProcessDefinition( taskInstance.getProcessDefinitionId());
                 */
                // String url = "/bonita/portal/resource/taskInstance/"+processDefinition.getName()+"/"+processDefinition.getVersion()+
                // see https://documentation.bonitasoft.com/bonita/7.7/bonita-bpm-portal-urls#toc8
                String url = "/bonita/portal/form/taskInstance/" + parameterSource.taskId;
                result.put("url", url);
            }
            // return "http://localhost:64176/bonita/portal/resource/taskInstance/Populate/1.0/Walter%20Bates/content/?id=60019&displayConfirmation=false";
        } catch (Exception e) {
            logger.severe(cstLogHeader + " Exception " + e.getMessage());
            listEvents.add(new BEvent(EVENT_API_ERROR, e, ""));
        }
        result.put("listevents", BEventFactory.getHtml( listEvents) );
        return result;
    }

    /* -------------------------------------------------------------------- */
    /*                                                                      */
    /* Rule management */
    /*                                                                      */
    /* -------------------------------------------------------------------- */
    public static class LoadResult {

        List<BEvent> listEvents = new ArrayList<BEvent>();
        /**
         * Alll delegations
         */
        List<DelegationRule> listDelegations = new ArrayList<DelegationRule>();

        /**
         * delegation that the user can access Filtered by the showAffection / showDelegation filter
         * Administrator : all
         * User : all where he is involved
         */
        List<DelegationRule> listFilterDelegation = new ArrayList<DelegationRule>();
    }

    public Map<String, Object> saveRule(ParameterSource parameterSource, PageResourceProvider pageResourceProvider) {
        List<BEvent> listEvents = new ArrayList<BEvent>();
        String status = cstLogHeader + "saveRule Id=[" + parameterSource.delegationRuleId + "]";

        DelegationRule delegation = null;
        try {

            Long ruleId = getLongValue(parameterSource.delegationRule.get(DelegationRule.cstjsonRuleId), null);
            LoadResult loadResult = loadDelegationsRules(pageResourceProvider);
            listEvents.addAll(loadResult.listEvents);
            for (int i = 0; i < loadResult.listDelegations.size(); i++) {
                DelegationRule delegationRule = loadResult.listDelegations.get(i);

                if (ruleId != null
                        && delegationRule.getRuleId() == ruleId) {
                    // save this rule
                    status += "Found rule;";
                    delegationRule.setAttributes(parameterSource.delegationRule);
                    delegation = delegationRule;
                }
            }
            if (delegation == null) {
                // New rule
                status += "New rule;";
                delegation = DelegationRule.getInstance(parameterSource.delegationRule);
                loadResult.listDelegations.add(delegation);
            }
            // then now we have to complete the delegation 
            if (delegation.getDelegatorId() == null) {
                // complete by the user itself
                delegation.setDelegatorId(parameterSource.apiSession.getUserId());

            }

            loadResult.listDelegations = purgeDelegationsRules(loadResult.listDelegations);
            listEvents.addAll(saveDelegationRule(loadResult.listDelegations, pageResourceProvider));
            if (!BEventFactory.isError(listEvents)) {
                listEvents.add(EVENT_SAVED_WITH_SUCCESS);
            }
            status += "Done with success;";

        } catch (Exception e) {
            listEvents.add(EVENT_API_ERROR);
            status += "Exception " + e.getMessage();
        }
        Map<String, Object> result = new HashMap<String, Object>();
        logger.info(cstLogHeader + " : saveRule " + status);

        Map<String, Object> delegationMap = delegation.getAttributes(true);
        if (delegation.getDelegatorId() != null) {
            IdentityAPI identityAPI;
            try {
                identityAPI = TenantAPIAccessor.getIdentityAPI(parameterSource.apiSession);
                User user = identityAPI.getUser(delegation.getDelegatorId());
                delegationMap.put("delegatorCompleteName", getUserCompleteName(user));
            } catch (Exception e) {
            }
        }

        result.put("delegation", delegationMap);
        result.put("listevents", BEventFactory.getHtml(listEvents));
        return result;
    }

    /**
     * @param parameterSource
     * @param tenantAPI
     * @return
     */
    public Map<String, Object> removeRule(ParameterSource parameterSource, PageResourceProvider pageResourceProvider) {
        List<BEvent> listEvents = new ArrayList<BEvent>();
        LoadResult loadResult = loadDelegationsRules(pageResourceProvider);
        listEvents.addAll(loadResult.listEvents);
        // search the rule to delete
        int foundIndex = -1;
        String ruleName = "";
        for (int i = 0; i < loadResult.listDelegations.size(); i++) {
            DelegationRule delegationRule = loadResult.listDelegations.get(i);

            if (parameterSource.delegationRuleId != null
                    && delegationRule.getRuleId() == parameterSource.delegationRuleId.longValue()) {
                foundIndex = i;
                ruleName = delegationRule.getRuleName();
                break;
            }
        }
        if (foundIndex >= 0) {
            loadResult.listDelegations = purgeDelegationsRules(loadResult.listDelegations);
            loadResult.listDelegations.remove(foundIndex);
            listEvents.addAll(saveDelegationRule(loadResult.listDelegations, pageResourceProvider));
            listEvents.add(new BEvent(EVENT_REMOVED_WITH_SUCCESS, "Rule [" + ruleName + "]"));
        } else
            listEvents.add(new BEvent(EVENT_NO_RULE_FOUND, "RuleId[" + parameterSource.delegationRuleId + "]"));

        // get back the current list
        Map<String, Object> result = getListDelegation(parameterSource, pageResourceProvider);
        // override the list events
        result.put("listevents", BEventFactory.getHtml(listEvents));
        return result;
    }

    /* -------------------------------------------------------------------- */
    /*                                                                      */
    /* Load, Save */
    /*                                                                      */
    /* -------------------------------------------------------------------- */

    private final static String cstDomainRules = "rules";

    /**
     * Load all delegations in listDelegations member
     * 
     * @param pageResourceProvider
     * @return
     */
    private LoadResult loadDelegationsRules(PageResourceProvider pageResourceProvider) {
        LoadResult loadResult = new LoadResult();
        BonitaProperties bonitaProperties = new BonitaProperties(pageResourceProvider);
        loadResult.listEvents.addAll(bonitaProperties.loaddomainName(cstDomainRules));

        // then get all rules
        for (Object key : bonitaProperties.keySet()) {
            String value = (String) bonitaProperties.get(key);
            DelegationRule delegationRule = DelegationRule.getInstanceFromJson(value);
            loadResult.listDelegations.add(delegationRule);
        }
        return loadResult;
    }

    private List<BEvent> saveDelegationRule(List<DelegationRule> listDelegationRule,
            PageResourceProvider pageResourceProvider) {
        List<BEvent> listEvents = new ArrayList<BEvent>();
        LoadResult loadResult = new LoadResult();
        BonitaProperties bonitaProperties = new BonitaProperties(pageResourceProvider);
        listEvents.addAll(bonitaProperties.loaddomainName(cstDomainRules));

        // then save all rules
        bonitaProperties.clear();
        for (DelegationRule delegationRule : listDelegationRule) {
            bonitaProperties.put(delegationRule.getRuleId(), delegationRule.getJsonValue(false));
        }
        listEvents.addAll(bonitaProperties.store());
        return listEvents;
    }

    private List<DelegationRule> purgeDelegationsRules(List<DelegationRule> listDelegation) {
        // rule expired : does not keep it
        Calendar c = Calendar.getInstance();
        c.add(Calendar.WEEK_OF_YEAR, -1);

        List<DelegationRule> listNewRules = new ArrayList<DelegationRule>();
        for (DelegationRule rule : listDelegation) {
            if (!rule.isExpired(c.getTime()))
                listNewRules.add(rule);
        }
        return listNewRules;
    }

    private static Long getLongValue(Object value, Long defaultValue) {
        if (value == null)
            return defaultValue;
        try {
            return Long.valueOf(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    protected static Boolean getBooleanValue(Object value, Boolean defaultValue) {
        if (value == null)
            return defaultValue;
        try {
            return Boolean.valueOf(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // "2018-07-12T07:00:00.000Z"
    // 2018-07-11T07:00:00.000Z
    private static SimpleDateFormat sdfHtml5 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000'Z'");
    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");

    protected static Date getDateValue(Object value, Date defaultValue) {
        if (value == null)
            return defaultValue;
        try {
            return sdf.parse(value.toString());
        } catch (Exception e) {
        }
        try {
            return sdfHtml5.parse(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    protected static String getDateFormatValue(Date value, boolean toHtml) {
        if (value == null)
            return null;
        if (toHtml)
            return sdfHtml5.format(value);
        return sdf.format(value);
    }

    public String getUserCompleteName(User user) {
        return user.getFirstName() + " " + user.getLastName() + " (" + user.getUserName() + ")";
    }
    // 
}
