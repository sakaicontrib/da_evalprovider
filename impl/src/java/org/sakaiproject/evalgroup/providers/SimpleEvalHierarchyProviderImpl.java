package org.sakaiproject.evalgroup.providers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.HibernateException;
import org.hibernate.SQLQuery;
import org.hibernate.Session;

import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;

import org.sakaiproject.evaluation.providers.EvalHierarchyProvider;
import org.sakaiproject.evaluation.constant.EvalConstants;
import org.sakaiproject.evaluation.logic.model.EvalHierarchyNode;
import org.sakaiproject.evaluation.logic.externals.EvalExternalLogic;
import org.sakaiproject.evaluation.model.EvalGroupNodes;
import org.sakaiproject.genericdao.hibernate.HibernateGeneralGenericDao;

import org.sakaiproject.hierarchy.HierarchyService;
import org.sakaiproject.hierarchy.model.HierarchyNode;
import org.sakaiproject.hierarchy.utils.HierarchyUtils;
import org.springframework.orm.hibernate3.HibernateCallback;

public class SimpleEvalHierarchyProviderImpl extends HibernateGeneralGenericDao implements EvalHierarchyProvider {

  private static final Log LOG = LogFactory.getLog(SimpleEvalHierarchyProviderImpl.class);

  protected EvalExternalLogic externalLogic;
  public void setExternalLogic(EvalExternalLogic externalLogic) {
    this.externalLogic = externalLogic;
  }

  private HierarchyService hierarchyService;
  public void setHierarchyService(HierarchyService hierarchyService) {
    this.hierarchyService = hierarchyService;
  }

  protected static String PERM_ASSIGN_EVALUATION_COPY;
  protected static String PERM_TA_ROLE_COPY;

  /**
   * Initialize this provider
   */
  public void init() {
    LOG.info("init");
  }

  /**
    * Get the hierarchy root node of the eval hierarchy
    * 
    * @return the {@link EvalHierarchyNode} representing the root of the hierarchy
    * @throws IllegalStateException if no node can be obtained
    */
  public EvalHierarchyNode getRootLevelNode() {
          EvalHierarchyNode node;
          HierarchyNode hNode = hierarchyService.getRootNode("delegatedAccessHierarchyId");
          node = makeEvalNode(hNode);
          return node;
  }

   /**
    * Get the node object for a specific node id
    * 
    * @param nodeId a unique id for a hierarchy node
    * @return a {@link EvalHierarchyNode} object or null if none found
    */
   public EvalHierarchyNode getNodeById(String nodeId) {
          LOG.debug("getNodeById("+nodeId+")");
          EvalHierarchyNode node;

          HierarchyNode hNode = hierarchyService.getNodeById(nodeId);
          node = makeEvalNode(hNode);
          return node;
   }

   /**
    * Get a set of nodes based on an array of nodeIds,
    * allows efficient lookup of nodes
    * 
    * @param nodeIds unique ids for hierarchy nodes
    * @return a set of {@link EvalHierarchyNode} objects based on the given ids
    */
   public Set<EvalHierarchyNode> getNodesByIds(String[] nodeIds) {
          if (nodeIds == null) {
            throw new IllegalArgumentException("nodeIds cannot br null");
          }
          Set<EvalHierarchyNode> s = new HashSet<>();
          Map<String, HierarchyNode> nodes = hierarchyService.getNodesByIds(nodeIds);
          for (HierarchyNode node : nodes.values()) {
              EvalHierarchyNode eNode = makeEvalNode(node);
              if (eNode != null) {
                  s.add( eNode );
              }
          }
          return s;
   }

   /**
    * Get all children nodes for this node in the hierarchy, 
    * will return no nodes if this is not a parent node
    * 
    * @param nodeId a unique id for a hierarchy node
    * @param directOnly if true then only include the nodes 
    * which are directly connected to this node, 
    * else return every node that is a child of this node
    * @return a Set of {@link EvalHierarchyNode} objects representing 
    * all children nodes for the specified parent,
    * empty set if no children found
    */
   public Set<EvalHierarchyNode> getChildNodes(String nodeId, boolean directOnly) {
     Set<EvalHierarchyNode> eNodes = new HashSet<>();
     Set<HierarchyNode> nodes = hierarchyService.getChildNodes(nodeId, directOnly);
     for (HierarchyNode node : nodes) {
         EvalHierarchyNode eNode = makeEvalNode(node);
         if (eNode != null && (node.title == null || !node.title.startsWith("/site/"))) {
             eNodes.add( eNode );
         }
     }
     return eNodes;
   }


   /**
    * Get all the userIds for users which have a specific permission in a set of
    * hierarchy nodes, this can be used to check one node or many nodes as needed,
    * <br/>The actual permissions this should handle are shown at the top of this class
    * 
    * @param nodeIds an array of unique ids for hierarchy nodes
    * @param hierarchyPermConstant a HIERARCHY_PERM constant from {@link EvalConstants}
    * @return a set of userIds (not username/eid)
    */
   public Set<String> getUserIdsForNodesPerm(String[] nodeIds, String hierarchyPermConstant) {
     Set<String> s;
     s = hierarchyService.getUserIdsForNodesPerm(nodeIds, hierarchyPermConstant);
     return s;
   }

   /**
    * Get the hierarchy nodes which a user has a specific permission in,
    * this is used to find a set of nodes which a user should be able to see and to build
    * the list of hierarchy nodes for selecting eval groups to assign evaluations to,
    * <br/>The actual permissions this should handle are shown at the top of this class
    * 
    * @param userId the internal user id (not username)
    * @param hierarchyPermConstant a HIERARCHY_PERM constant from {@link EvalConstants}
    * @return a Set of {@link EvalHierarchyNode} objects
    */
   public Set<EvalHierarchyNode> getNodesForUserPerm(String userId, String hierarchyPermConstant) {
       Set<EvalHierarchyNode> evalNodes = new HashSet<>();
       Set<HierarchyNode> nodes = hierarchyService.getNodesForUserPerm(userId, hierarchyPermConstant);
       if (nodes != null && nodes.size() > 0) {
           for (HierarchyNode hierarchyNode : nodes) {
                evalNodes.add( makeEvalNode(hierarchyNode) );
           }
       }
       return evalNodes;
   }

   /**
    * Determine if a user has a specific hierarchy permission at a specific hierarchy node,
    * <br/>The actual permissions this should handle are shown at the top of this class
    * 
    * @param userId the internal user id (not username)
    * @param nodeId a unique id for a hierarchy node
    * @param hierarchyPermConstant a HIERARCHY_PERM constant from {@link EvalConstants}
    * @return true if the user has this permission, false otherwise
    */
   public boolean checkUserNodePerm(String userId, String nodeId, String hierarchyPermConstant) {
        return hierarchyService.checkUserNodePerm(userId, nodeId, hierarchyPermConstant);
   }


   /**
    * Gets the list of nodes in the path from an eval group to the root node,
    * should be in order with the first node being the root node and the last node being
    * the parent node for the given eval group
    *  
    * @param evalGroupId the unique ID of an eval group
    * @return a List of {@link EvalHierarchyNode} objects (ordered from root to evalgroup)
    */
   public List<EvalHierarchyNode> getNodesAboveEvalGroup(String evalGroupId) {
        List<EvalHierarchyNode> l = new ArrayList<>();
        String nodeId = getNodeIdForEvalGroup(evalGroupId);
        if (nodeId != null) {
             HierarchyNode currentNode = hierarchyService.getNodeById(nodeId);
             Set<HierarchyNode> parents = hierarchyService.getParentNodes(nodeId, false);
             parents.add(currentNode);
             List<HierarchyNode> sorted = HierarchyUtils.getSortedNodes(parents);
             // now convert the nodes to eval nodes
             for (HierarchyNode node : sorted) {
                  l.add( makeEvalNode(node) );
             }
         }
         return l;
   }
   
   public EvalHierarchyNode getNodeForEvalGroup(String evalGroupId){
       String nodeId = getNodeIdForEvalGroup(evalGroupId);
       if (nodeId != null) {
            HierarchyNode n =  hierarchyService.getNodeById(nodeId);
            if(n != null){
                return makeEvalNode(n);
            }
       }
        return null;
   }

   /**
    * Get the set of eval group ids beneath a specific hierarchy node, note that this should only
    * include the eval groups directly beneath this node and not any groups that are under
    * child nodes of this node<br/>
    * Note: this will not fail if the nodeId is invalid, it will just return no results<br/>
    * Convenience method for {@link #getEvalGroupsForNodes(String[])}
    * 
    * @param nodeId a unique id for a hierarchy node
    * @return a Set of eval group ids representing the eval groups beneath this hierarchy node
    */
   public Set<String> getEvalGroupsForNode(String nodeId) {
        if (nodeId == null || nodeId.equals("")) {
            throw new IllegalArgumentException("nodeId cannot be null or blank");
        }
        Set<String> s = new HashSet<>();
        EvalGroupNodes egn = getEvalGroupNodeByNodeId(nodeId);
        if (egn != null) {
             String[] evalGroups = egn.getEvalGroups();
             s.addAll( Arrays.asList( evalGroups ) );
        }
        return s;
   }

   /**
    * Get the set of eval group ids beneath a set of hierarchy nodes, note that this should only
    * include the eval groups directly beneath these nodes and not any groups that are under
    * child nodes of this node<br/>
    * Note: this will not fail if the nodeId is invalid, it will just return no results,
    * an empty array of nodeids will return an empty map
    * 
    * @param nodeIds a set of unique ids for hierarchy nodes
    * @return a Map of nodeId -> a set of eval group ids representing the eval groups beneath that node
    */
   public Map<String, Set<String>> getEvalGroupsForNodes(String[] nodeIds) {
       return getEvalGroupsForNodes(nodeIds, -1, null);
   }
   
   public Map<String, Set<String>> getEvalGroupsForNodes(String[] nodeIds, int filterLevel, String filter) {
        if (nodeIds == null) {
            throw new IllegalArgumentException("nodeIds cannot be null");
        }
        Map<String, Set<String>> m = new HashMap<>();
        if (nodeIds.length > 0) {
             List<EvalGroupNodes> l = getEvalGroupNodesByNodeId(nodeIds, filterLevel, filter);
             for (EvalGroupNodes egn : l) {
                 Set<String> s = new HashSet<>();
                 String[] evalGroups = egn.getEvalGroups();
                 s.addAll( Arrays.asList( evalGroups ) );
                 m.put(egn.getNodeId(), s);
              }
        }
        return m;
   }

   /**
    * Get the count of the number of eval groups assigned to each node in a group of nodes
    * @param nodeIds an array of unique ids for hierarchy nodes
    * @return a map of nodeId -> number of eval groups
    */
   public Map<String, Integer> countEvalGroupsForNodes(String[] nodeIds) {
        Map<String, Integer> m = new HashMap<>();
        for( String nodeId : nodeIds ) {
             m.put( nodeId, 0 );
        }

        List<EvalGroupNodes> l = getEvalGroupNodesByNodeId(nodeIds, -1, null);
        for( EvalGroupNodes egn : l ) {
             m.put(egn.getNodeId(), egn.getEvalGroups().length);
        }
        return m;
   }

   private EvalHierarchyNode makeEvalNode(HierarchyNode node) {
        EvalHierarchyNode eNode = new EvalHierarchyNode();
        eNode.id = node.id;
        eNode.title = node.title;
        eNode.description = node.description;
        eNode.directChildNodeIds = node.directChildNodeIds;
        eNode.childNodeIds = node.childNodeIds;
        eNode.directParentNodeIds = node.directParentNodeIds;
        eNode.parentNodeIds = node.parentNodeIds;
        return eNode;
    }

    private EvalGroupNodes getEvalGroupNodeByNodeId(String nodeId) {
        List<EvalGroupNodes> l = getEvalGroupNodesByNodeId(new String[] {nodeId}, -1, null);
        EvalGroupNodes egn = null;
        if (!l.isEmpty()) {
            egn = (EvalGroupNodes) l.get(0);
        }
        return egn;
    }

    private List<EvalGroupNodes> getEvalGroupNodesByNodeId(String[] nodeIds, int filterLevel, String filter) {
       List<EvalGroupNodes> l = new ArrayList<>();
       for (String nodeId : nodeIds) {
         List<String> childIds = new ArrayList<>();
         Map<String, HierarchyNode> nodeCache = new HashMap<>();
         for (HierarchyNode child : hierarchyService.getChildNodes(nodeId, true)) {
           if(child != null && child.title != null && child.title.startsWith("/site/")) {
                //filter nodes based on filter
                if(filterLevel > -1 && filter != null && !"".equals(filter)){
                    int nodeLevel = child.parentNodeIds.size();
                    String title = null;
                    if(nodeLevel == filterLevel){
                        //this node is on the same level as the filter, check it's title against the filter
                        title = child.description;
                    }else if(nodeLevel > filterLevel){
                        //this node is below the filter level, we need to find it's parent at the filter 
                        //level and check it's title against the filter
                        Set<String> parentNodeIds = new HashSet<>();
                        //first try to get the parents from the cache
                        for(String parent : child.parentNodeIds){
                            if(nodeCache.containsKey(parent)){
                                nodeLevel = nodeCache.get(parent).parentNodeIds.size();
                                if(nodeLevel == filterLevel){
                                    //we found the one we wanted in the cache, break out and
                                    title = nodeCache.get(parent).title;
                                    break;
                                }
                                //else ignore, b/c it's not the right level anyways
                            }else{
                                //we'll look this up later in bulk
                                parentNodeIds.add(parent);
                            }
                        }
                        if(title == null){
                            //title is still null, whihc means we didn't find it in the cache
                            //lookup parents:
                            Map<String, HierarchyNode> parentNodes = hierarchyService.getNodesByIds(parentNodeIds.toArray(new String[]{}));
                            for(HierarchyNode nParent : parentNodes.values()){
                                nodeLevel = nParent.parentNodeIds.size();
                                if(nodeLevel == filterLevel){
                                    //we found the one we wanted, save the title
                                    title = nParent.title;
                                }
                                //save this in the cache so we don't have to look it up again
                                nodeCache.put(nParent.id, nParent);
                            }
                        }
                    }
                    //ok, we should have the correct title for the correct node level for this node (or parent)
                    //unless the node was above the filter... then we'll deal with that when we get the node's
                    //children
                    if(title != null && title.toLowerCase().equals(filter.toLowerCase())){
                        //this node didn't match the filter, remove it
                        childIds.add(child.title);
                    }

                }else{
                    //no filter, just add the child
                    childIds.add(child.title);
               }
           }
         }
         l.add(new EvalGroupNodes(null, nodeId, childIds.toArray(new String[childIds.size()])));
       }
       return l;
    }

    @SuppressWarnings("unchecked")
    public String getNodeIdForEvalGroup(final String evalGroupId) {
        if(evalGroupId.startsWith("/site/")){
            final String sql = "Select ID From HIERARCHY_NODE_META where hierarchyId = ? and title = ? and isDisabled = 0";
            List<Object> l = (List) getHibernateTemplate().execute(new HibernateCallback() {

                public Object doInHibernate(Session session) throws HibernateException,
                        SQLException {
                    SQLQuery sq =session.createSQLQuery(sql);
                    sq.setParameter(0, "delegatedAccessHierarchyId");
                    sq.setParameter(1, evalGroupId);
                    return sq.list();
                }
            });
            if (l.isEmpty()) {
                return null;
            }
            return l.get(0).toString();
        }else{
            String hql = "select egn.nodeId from EvalGroupNodes egn join egn.evalGroups egrps where egrps.id = ? order by egn.nodeId";
            String[] params = new String[] {evalGroupId};
            List<String> l = (List<String>) getHibernateTemplate().find(hql, (Object[]) params);
            if (l.isEmpty()) {
                return null;
            }
            return (String) l.get(0);
        }
    }
}
