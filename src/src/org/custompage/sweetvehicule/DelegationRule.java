package org.custompage.sweetvehicule;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bonitasoft.engine.api.IdentityAPI;
import org.bonitasoft.engine.identity.User;
import org.json.simple.JSONValue;

/* -------------------------------------------------------------------- */
/*                                                                      */
/* getListDelegates                                                          */
/*                                                                      */
/* -------------------------------------------------------------------- */

public class DelegationRule {

    public static Logger logger = Logger.getLogger(SweetVehiculeAPI.class.getName());

    
    /**
     * A rule contains : 
     * a DELEGATOR this is the person who are in vacation, who DELEGATES
     * a list of DELEGATES : theses persons replace the delegator
     * @param userId
     * @param dateDelegation
     * @return
     */
    Long delegatorUserId;
    
    List<Long> listDelegatesUserId= new ArrayList<Long>();
    
    Date periodFrom;
    Date periodTo;
    
    long ruleId;
    /**
     * a name to help user to manage the list of rule
     */
    String ruleName;
    
    /* -------------------------------------------------------------------- */
    /*                                                                      */
    /* gettsetter                                                          */
    /*                                                                      */
    /* -------------------------------------------------------------------- */

    public long getRuleId()
    {
        return ruleId;
    }
    public String getRuleName()
    {
        return ruleName;
    }
    public Long getDelegatorId()
    {
        return delegatorUserId;
    }
    public void setDelegatorId(Long delegatorUserId)
    {
        this.delegatorUserId = delegatorUserId;
    }
    
    public List<Long> getListDelegatesUserId()
    {
        return listDelegatesUserId;
    }
    
    /**
     * get the delegation from a Record
     * Record may be the HTML source or the JSON source
     */
    public static DelegationRule getInstance( Map<String,Object> record )
    {
        DelegationRule delegationRule = new DelegationRule();
        if (record==null)
        {
            delegationRule.ruleId =System.currentTimeMillis() ;
        }
        else
        {
                delegationRule.ruleId = record.get("ruleId")==null ? System.currentTimeMillis() : (Long) record.get("ruleId");
                delegationRule.setAttributes( record );
        }
        return delegationRule;
        
    }

    public static String cstjsonRuleId = "ruleId";
    public static String cstjsonRuleName = "ruleName";
    public static String cstjsonDelegatorId = "delegatorId";
    public static String cstjsonPeriodFrom = "periodFrom";
    public static String cstjsonPeriodTo = "periodTo";
    public static String cstjsonDelegates = "delegates";
    public static String cstjsonDelegatesLong = "delegateslong";

  
    public void setAttributes(Map<String,Object> record )
    {
        delegatorUserId = (Long) record.get(cstjsonDelegatorId);
        ruleName = (String) record.get(cstjsonRuleName);
        periodFrom = SweetVehiculeAPI.getDateValue( record.get(cstjsonPeriodFrom),null);
        periodTo = SweetVehiculeAPI.getDateValue( record.get(cstjsonPeriodTo),null );
        List<Long> listDelegates=(List)record.get(cstjsonDelegatesLong);
        if (listDelegates!=null)
        {
            listDelegatesUserId = listDelegates;
        }
        
    }
    public Map<String,Object> getAttributes( boolean toHtml)
    {
        Map<String,Object> record = new HashMap<String,Object>();
        record.put( cstjsonRuleId, ruleId);
        record.put( cstjsonRuleName, ruleName);        
        record.put( cstjsonDelegatorId, delegatorUserId);
        record.put( cstjsonPeriodFrom, SweetVehiculeAPI.getDateFormatValue( periodFrom,toHtml) );
        record.put( cstjsonPeriodTo, SweetVehiculeAPI.getDateFormatValue( periodTo, toHtml));
        record.put( cstjsonDelegatesLong, listDelegatesUserId);
        return record;
    }

    /* -------------------------------------------------------------------- */
    /*                                                                      */
    /* JsonAccess                                                          */
    /*                                                                      */
    /* -------------------------------------------------------------------- */
    public static DelegationRule getInstanceFromJson( String jsonSt)
    {
        Map<String, Object> record = (Map<String, Object>) JSONValue.parse(jsonSt);
        return getInstance(record);
    }
    
    public String getJsonValue( boolean toHtml)
    {
        Map<String,Object> record = getAttributes( toHtml );
        String jsonSt = JSONValue.toJSONString(record);
       return jsonSt;
    }
    /* -------------------------------------------------------------------- */
    /*                                                                      */
    /* tool                                                          */
    /*                                                                      */
    /* -------------------------------------------------------------------- */
/**
 * the rule is owned by this user
 * @param userId
 * @return
 */
    public boolean isOwnedBy(long userId )
    {
        if (delegatorUserId !=null && delegatorUserId.longValue() == userId)
            return true;
        return false;
    }
    
    
    public boolean isAffected( long userId )
    {
        return isAffectedNow(userId, null);
    }
    /**
     * is this rule contains the delegate person, at the current date ? 
     * @param userId
     * @param dateDelegation
     * @return
     */
    public boolean isAffectedNow( long userId, Date dateDelegation )
    {
        if (dateDelegation!=null)
        {
            if (periodFrom !=null && dateDelegation.getTime() < periodFrom.getTime() )
                return false;
            if (periodTo !=null &&  periodTo.getTime() < dateDelegation.getTime())
                return false;
        }
       for (long delegateUserId : listDelegatesUserId)
       {
           if (delegateUserId == userId)
               return true;
       }
       return false;
    }
    
    /**
     * return true if a periodTo is given, and are in the past according the reference data
     * @param dateReference
     * @return
     */
    public boolean isExpired(Date dateReference)
    {
        if (periodTo !=null && periodTo.getTime() < dateReference.getTime())
            return true;
        return false;
    }
}
