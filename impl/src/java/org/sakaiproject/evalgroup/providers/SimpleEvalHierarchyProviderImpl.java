package org.sakaiproject.evalgroup.providers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;

import org.sakaiproject.evaluation.providers.EvalHierarchyProvider;
import org.sakaiproject.evaluation.constant.EvalConstants;
import org.sakaiproject.evaluation.logic.model.EvalHierarchyNode;
import org.sakaiproject.evaluation.logic.externals.EvalExternalLogic;
import org.sakaiproject.evaluation.model.EvalGroupNodes;
import org.sakaiproject.evaluation.dao.EvaluationDao;
import org.sakaiproject.genericdao.hibernate.HibernateGeneralGenericDao;

import org.sakaiproject.evaluation.logic.externals.ExternalHierarchyLogic;
import org.sakaiproject.hierarchy.HierarchyService;
import org.sakaiproject.hierarchy.model.HierarchyNode;
import org.sakaiproject.hierarchy.utils.HierarchyUtils;
import org.sakaiproject.genericdao.api.search.Search;
import org.sakaiproject.genericdao.api.search.Restriction;
import org.sakaiproject.genericdao.api.search.Order;

import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService.SelectionType;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.exception.IdUnusedException;



public class SimpleEvalHierarchyProviderImpl extends HibernateGeneralGenericDao implements EvalHierarchyProvider {

  private static final Log log = LogFactory.getLog(SimpleEvalHierarchyProviderImpl.class);

  protected EvalExternalLogic externalLogic;
  public void setExternalLogic(EvalExternalLogic externalLogic) {
    this.externalLogic = externalLogic;
  }

  private HierarchyService hierarchyService;
  public void setHierarchyService(HierarchyService hierarchyService) {
    this.hierarchyService = hierarchyService;
  }

  

  protected static  String PERM_ASSIGN_EVALUATION_COPY;

  protected static  String PERM_TA_ROLE_COPY;	
	

	
	/**
   * Initialize this provider
   */
  public void init() {
    log.info("init");
    
  }
  
  
	/**
    * Get the hierarchy root node of the eval hierarchy
    * 
    * @return the {@link EvalHierarchyNode} representing the root of the hierarchy
    * @throws IllegalStateException if no node can be obtained
    */
  public EvalHierarchyNode getRootLevelNode() {
	  /* EvalHierarchyNode rootNode = new EvalHierarchyNode();
	  rootNode.title = "Root";
	  rootNode.id = "1";
	  Set<String> children = new TreeSet<String>();
	  for (EvalHierarchyNode node1 : getChildNodes(rootNode.id, false)) {
	  	children.add(node1.id);
	  }
	  
	  rootNode.childNodeIds = children;
	  
	  Set<String> directChildren = new TreeSet<String>();
	  for (EvalHierarchyNode node2 : getChildNodes(rootNode.id, true)) {
	  	directChildren.add(node2.id);
	  }
	  
	  rootNode.directChildNodeIds = directChildren;
	  
	  return rootNode; */
          EvalHierarchyNode node = null;
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
          log.debug("getNodeById("+nodeId+")");
          EvalHierarchyNode node = null;
 
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
          Set<EvalHierarchyNode> s = new HashSet<EvalHierarchyNode>();
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
     Set<EvalHierarchyNode> eNodes = new HashSet<EvalHierarchyNode>();
     Set<HierarchyNode> nodes = hierarchyService.getChildNodes(nodeId, directOnly);
     for (HierarchyNode node : nodes) {
         EvalHierarchyNode eNode = makeEvalNode(node);
         if (eNode != null) {
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
     Set<String> s = null;
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
       Set<EvalHierarchyNode> evalNodes = new HashSet<EvalHierarchyNode>();
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
        boolean allowed = false;
        allowed = hierarchyService.checkUserNodePerm(userId, nodeId, hierarchyPermConstant);
        return allowed;
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
        List<EvalHierarchyNode> l = new ArrayList<EvalHierarchyNode>();
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
        Set<String> s = new HashSet<String>();
        EvalGroupNodes egn = getEvalGroupNodeByNodeId(nodeId);
        if (egn != null) {
             String[] evalGroups = egn.getEvalGroups();
             for (int i = 0; i < evalGroups.length; i++) {
                    s.add(evalGroups[i]);
             }
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
        if (nodeIds == null) {
            throw new IllegalArgumentException("nodeIds cannot be null");
        }
        Map<String, Set<String>> m = new HashMap<String, Set<String>>();
        if (nodeIds.length > 0) {
             List<EvalGroupNodes> l = getEvalGroupNodesByNodeId(nodeIds);
             for (EvalGroupNodes egn : l) {
                 Set<String> s = new HashSet<String>();
                 String[] evalGroups = egn.getEvalGroups();
                 for (int i = 0; i < evalGroups.length; i++) {
                        s.add(evalGroups[i]);
                 }
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
        Map<String, Integer> m = new HashMap<String, Integer>();
        for (int i = 0; i < nodeIds.length; i++) {
             m.put(nodeIds[i], 0);
        }
    
        List<EvalGroupNodes> l = getEvalGroupNodesByNodeId(nodeIds);
        for (Iterator<EvalGroupNodes> iter = l.iterator(); iter.hasNext();) {
            EvalGroupNodes egn = (EvalGroupNodes) iter.next();
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
        List<EvalGroupNodes> l = getEvalGroupNodesByNodeId(new String[] {nodeId});
        EvalGroupNodes egn = null;
        if (!l.isEmpty()) {
            egn = (EvalGroupNodes) l.get(0);
        }
        return egn;
    }

    private List<EvalGroupNodes> getEvalGroupNodesByNodeId(String[] nodeIds) {
        /* List<EvalGroupNodes> l = findBySearch(EvalGroupNodes.class, new Search(
                new Restriction("nodeId", nodeIds),
                new Order("id")
        ) ); */
       List<EvalGroupNodes> l = new ArrayList<EvalGroupNodes>();
       for (String nodeId : nodeIds) {
         List<String> childIds = new ArrayList<String>();
         for (HierarchyNode child : hierarchyService.getChildNodes(nodeId, true)) {
           if(child != null && child.title != null && child.title.startsWith("/site/")) {
            childIds.add(child.id);
           }
         }
         l.add(new EvalGroupNodes(null, nodeId, childIds.toArray(new String[childIds.size()])));
       }
       return l;
    }

    @SuppressWarnings("unchecked")
    public String getNodeIdForEvalGroup(String evalGroupId) {
        String hql = "select egn.nodeId from EvalGroupNodes egn join egn.evalGroups egrps where egrps.id = ? order by egn.nodeId";
        String[] params = new String[] {evalGroupId};
        List<String> l = getHibernateTemplate().find(hql, params);
        if (l.isEmpty()) {
             return null;
        }
        return (String) l.get(0);
    }


}
