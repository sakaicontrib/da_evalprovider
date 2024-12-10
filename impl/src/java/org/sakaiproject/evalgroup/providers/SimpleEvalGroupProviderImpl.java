/**
 * $URL:  $
 * $Id:  $
 ***********************************************************************************
 *
 * Copyright (c) 2009 The Sakai Foundation.
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.evalgroup.providers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.sakaiproject.evaluation.constant.EvalConstants;
import org.sakaiproject.evaluation.logic.externals.EvalExternalLogic;
import org.sakaiproject.evaluation.logic.model.EvalGroup;
import org.sakaiproject.evaluation.providers.EvalGroupsProvider;

@Setter
@Slf4j
public class SimpleEvalGroupProviderImpl implements EvalGroupsProvider 
{

	protected EvalExternalLogic externalLogic;

    private DataSource dataSource;

    protected static String PERM_ASSIGN_EVALUATION_COPY;
	protected static String PERM_TA_ROLE_COPY;

	/**
	 * Initialize this provider
	 */
	public void init() 
	{
		log.info("init");
		try 
		{
			Field field = SimpleEvalGroupProviderImpl.class.getField("PERM_ASSIGN_EVALUATION");
			PERM_ASSIGN_EVALUATION_COPY = (String) field.get(this);
		} 
		catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e)
		{
			PERM_ASSIGN_EVALUATION_COPY = "provider.assign.eval";
		} 
		try 
		{
			Field field = SimpleEvalGroupProviderImpl.class.getField("PERM_TA_ROLE");
			PERM_TA_ROLE_COPY = (String) field.get(this);
		} 
		catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e)
		{
			PERM_TA_ROLE_COPY = "provider.role.ta";
		} 
	}

	/* (non-Javadoc)
	 * @see org.sakaiproject.evaluation.providers.EvalGroupsProvider#countEvalGroupsForUser(java.lang.String, java.lang.String)
	 */
public int countEvalGroupsForUser(String userId, String permission)
{
	int count = 0;
	String userEid = this.getUserEid(userId);
	String query = "SELECT count(*) as total from GRP_PROVIDER_group_membership where user_eid = '" + userEid + "';";

	try (Connection conn = dataSource.getConnection();
	     Statement statement = conn.createStatement();
	     ResultSet result = statement.executeQuery(query)) {

		if (result.next()) {
			count = result.getInt("total");
		}
	} catch (SQLException ex) {
        log.warn("SQLException: {}", ex.getMessage());
	}
	return count;
}

	/* (non-Javadoc)
	 * @see org.sakaiproject.evaluation.providers.EvalGroupsProvider#countUserIdsForEvalGroups(java.lang.String[], java.lang.String)
	 */
	public int countUserIdsForEvalGroups(String[] groupIds, String permission) {
		
		return this.getUserIdsForEvalGroups(groupIds, permission).size();
	}

	public List<EvalGroup> getEvalGroupsForUser(String userId, String permission) {
		List<EvalGroup> evalGroups = new ArrayList<>();

		Connection conn;
		try {
				conn = dataSource.getConnection();
				Statement statement = conn.createStatement();
				if(isUserAdmin(userId)) {
					ResultSet result = statement.executeQuery("SELECT * from GRP_PROVIDER_groups;");
					while (result.next()) {
						evalGroups.add(this.makeEvalGroupObject(result.getString("id")));
					}
				} else { 
					String userEid = this.getUserEid(userId);
					ResultSet result = statement.executeQuery("SELECT GRP_PROVIDER_groups.id from GRP_PROVIDER_groups, GRP_PROVIDER_group_membership where user_eid = '"+userEid+"' and GRP_PROVIDER_groups.id = GRP_PROVIDER_group_membership.group_id; ");
					while (result.next()) {
						evalGroups.add(this.makeEvalGroupObject(result.getString("id")));
					}
				}
				conn.close();
		} catch (SQLException ex) {
			System.err.println("SQLException: " + ex.getMessage());
		}

		return evalGroups;
	}

	/* (non-Javadoc)
	 * @see org.sakaiproject.evaluation.providers.EvalGroupsProvider#getEvalGroupsForUser(java.lang.String)
	 */
	public Map<String, List<EvalGroup>> getEvalGroupsForUser(String userId) {
		Map<String, List<EvalGroup>> groups = new HashMap<>();
		for(String permission : new String[]{PERM_TAKE_EVALUATION, PERM_BE_EVALUATED, PERM_ASSIGN_EVALUATION_COPY, PERM_TA_ROLE_COPY}) {
			List<EvalGroup> list = this.getEvalGroupsForUser(userId, permission);
			if(list != null && ! list.isEmpty()) {
				groups.put(permission, list);
			}
		}
		return groups;
	}

	/* (non-Javadoc)
	 * @see org.sakaiproject.evaluation.providers.EvalGroupsProvider#getGroupByGroupId(java.lang.String)
	 */
	public EvalGroup getGroupByGroupId(String groupId) {
		EvalGroup group = null;
		try (Connection conn = dataSource.getConnection();
			 Statement statement = conn.createStatement();
			 ResultSet result = statement.executeQuery("Select id from GRP_PROVIDER_groups where id = " + groupId + ";")) {
			if (result != null) {
				group = this.makeEvalGroupObject(groupId);
			}
		} catch (SQLException ex) {
			log.warn("SQLException getGroupByGroupId: " + ex.getMessage());
		}
		return group;
	}

	/* (non-Javadoc)
	 * @see org.sakaiproject.evaluation.providers.EvalGroupsProvider#getUserIdsForEvalGroups(java.lang.String[], java.lang.String)
	 */
	public Set<String> getUserIdsForEvalGroups(String[] groupIds, String permission) {
		Set<String> userIds = new TreeSet<>();
		try (Connection conn = dataSource.getConnection();
			 Statement statement = conn.createStatement()) {
			if (groupIds != null) {
				for (String groupId : groupIds) {
					try (ResultSet result = statement.executeQuery("Select user_eid from GRP_PROVIDER_group_membership where group_id = " + groupId + ";")) {
						while (result.next()) {
							userIds.add(this.getUserId(result.getString("user_eid")));
						}
					}
				}
			}
		} catch (SQLException ex) {
			log.warn("SQLException getUserIdsForEvalGroups: {}", ex.getMessage());
		}
		return userIds;
	}

	/* (non-Javadoc)
	 * @see org.sakaiproject.evaluation.providers.EvalGroupsProvider#isUserAllowedInGroup(java.lang.String, java.lang.String, java.lang.String)
	 */
	public boolean isUserAllowedInGroup(String userId, String permission, String groupId) {
		boolean isAllowed = false;
		Connection conn;
		try {
			conn = dataSource.getConnection();
			Statement statement = conn.createStatement();
			ResultSet result = statement.executeQuery("Select * from GRP_PROVIDER_groups, GRP_PROVIDER_group_membership where GRP_PROVIDER_groups.id = "+groupId+" and GRP_PROVIDER_groups.id = GRP_PROVIDER_group_membership.group_id and GRP_PROVIDER_group_membership.user_eid = '"+userId+"';");
			if (result != null) {
				isAllowed = true;
			}
			conn.close();
		} catch (SQLException ex) {
			System.err.println("SQLException: " + ex.getMessage());
		}

		return isAllowed;
	}

	/**
	 * @param permission
	 * @param role
	 * @param userId
	 * @return
	 */
	protected boolean mapsTo(String permission, String role, String userId) {
		boolean result = false;
		if (PERM_TAKE_EVALUATION.equals(permission)) {
			result = "student".equals(role) || "S".equalsIgnoreCase(role);
		} else if (PERM_BE_EVALUATED.equals(permission)) {
			result = "instructor".equals(role) || "I".equalsIgnoreCase(role);
		} else if (PERM_ASSIGN_EVALUATION_COPY.equals(permission)) {
			result = this.isUserAdmin(userId);
		} else if (PERM_TA_ROLE_COPY.equals(permission)) {
			// unknown how to decide this
		}
		return result;
	}

	protected boolean isUserAdmin(String userId)
	{
		//return this.externalLogic.isUserAdmin(userId);
		return false;
	}

	protected EvalGroup makeEvalGroupObject(String groupId) 
	{
		String title = "";
		try (Connection conn = dataSource.getConnection();
			 Statement statement = conn.createStatement();
			 ResultSet result = statement.executeQuery("Select * from GRP_PROVIDER_groups where id = " + groupId + ";")) {
			if (result.first()) {
				title = result.getString("title");
			}
		} catch (SQLException ex) {
			log.warn("SQLException makeEvalGroupObject: {}", ex.getMessage());
		}
		return new EvalGroup(groupId, title, EvalConstants.GROUP_TYPE_PROVIDED);
	}

	/**
	 * @param userId
	 * @return
	 */
	protected String getUserEid(String userId)
	{
		return this.externalLogic.getUserUsername(userId);
	}
	
	protected String getUserId(String userEid)
	{
		return this.externalLogic.getUserId(userEid);
	}
}
