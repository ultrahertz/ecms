/*
 * Copyright (C) 2003-2008 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.services.wcm.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeIterator;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;
import javax.portlet.PortletRequest;

import org.apache.commons.lang.ArrayUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.services.cms.templates.TemplateService;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.services.wcm.core.NodeLocation;
import org.exoplatform.services.wcm.core.WCMConfigurationService;
import org.exoplatform.services.wcm.portal.LivePortalManagerService;
import org.exoplatform.services.wcm.publication.WCMComposer;
import org.exoplatform.services.wcm.search.QueryCriteria.DATE_RANGE_SELECTED;
import org.exoplatform.services.wcm.search.QueryCriteria.DatetimeRange;
import org.exoplatform.services.wcm.search.QueryCriteria.QueryProperty;
import org.exoplatform.services.wcm.search.base.AbstractPageList;
import org.exoplatform.services.wcm.search.base.NodeSearchFilter;
import org.exoplatform.services.wcm.search.base.PageListFactory;
import org.exoplatform.services.wcm.search.base.SearchDataCreator;
import org.exoplatform.services.wcm.utils.AbstractQueryBuilder.LOGICAL;
import org.exoplatform.services.wcm.utils.AbstractQueryBuilder.ORDERBY;
import org.exoplatform.services.wcm.utils.AbstractQueryBuilder.PATH_TYPE;
import org.exoplatform.services.wcm.utils.AbstractQueryBuilder.QueryTermHelper;
import org.exoplatform.services.wcm.utils.SQLQueryBuilder;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.application.portlet.PortletRequestContext;

/**
 * Created by The eXo Platform SAS Author : Hoa Pham hoa.pham@exoplatform.com
 * Oct 8, 2008
 */
public class SiteSearchServiceImpl implements SiteSearchService {

  private static final String IS_ENABLED_FUZZY_SEARCH = "isEnabledFuzzySearch";
  
  private static final String FUZZY_SEARCH_INDEX = "fuzzySearchIndex";
  
  /** The live portal manager service. */
  private LivePortalManagerService livePortalManagerService;

  /** The ecm template service. */
  private TemplateService templateService;

  /** The wcm configuration service. */
  private WCMConfigurationService configurationService;

  /** The jcr repository service. */
  private RepositoryService repositoryService;

  /** The exclude node types. */
  private CopyOnWriteArraySet<String> excludeNodeTypes = new CopyOnWriteArraySet<String>();

  /** The include node types. */
  private CopyOnWriteArraySet<String> includeNodeTypes = new CopyOnWriteArraySet<String>();

  /** The exclude mime types. */
  private CopyOnWriteArraySet<String> excludeMimeTypes = new CopyOnWriteArraySet<String>();

  /** The include mime types. */
  private CopyOnWriteArraySet<String> includeMimeTypes = new CopyOnWriteArraySet<String>();
  
  private boolean isEnabledFuzzySearch = true;
  
  private double fuzzySearchIndex = 0.8;

  /**
   * Instantiates a new site search service impl.
   *
   * @param portalManagerService the portal manager service
   * @param templateService the template service
   * @param configurationService the configuration service
   * @param repositoryService the repository service
   * @param initParams the init params
   *
   * @throws Exception the exception
   */
  public SiteSearchServiceImpl(LivePortalManagerService portalManagerService,
                               TemplateService templateService,
                               WCMConfigurationService configurationService,
                               RepositoryService repositoryService,
                               InitParams initParams) throws Exception {
    this.livePortalManagerService = portalManagerService;
    this.templateService = templateService;
    this.repositoryService = repositoryService;
    this.configurationService = configurationService;
    if (initParams != null) {
      ValueParam isEnabledFuzzySearchValue = initParams.getValueParam(IS_ENABLED_FUZZY_SEARCH);
      if (isEnabledFuzzySearchValue != null)
        isEnabledFuzzySearch = Boolean.parseBoolean(isEnabledFuzzySearchValue.getValue());
      ValueParam enabledFuzzySearchValue = initParams.getValueParam(FUZZY_SEARCH_INDEX);
      if (enabledFuzzySearchValue != null) {
        try {
          fuzzySearchIndex = Double.parseDouble(enabledFuzzySearchValue.getValue());
        } catch (NumberFormatException e) {
//          log.warn("The current fuzzySearchIndex value is not a number, default value 0.8 will be used");
          fuzzySearchIndex = 0.8;
        }
      }
      if (fuzzySearchIndex < 0 || fuzzySearchIndex >= 1) {
//        log.warn("The current fuzzySearchIndex value is out of range from 0 to 1, default value 0.8 will be used");
        fuzzySearchIndex = 0.8;
      }
    }

  }

  /*
   * (non-Javadoc)
   * @seeorg.exoplatform.services.wcm.search.SiteSearchService#
   * addExcludeIncludeDataTypePlugin
   * (org.exoplatform.services.wcm.search.ExcludeIncludeDataTypePlugin)
   */
  public void addExcludeIncludeDataTypePlugin(ExcludeIncludeDataTypePlugin plugin) {
    excludeNodeTypes.addAll(plugin.getExcludeNodeTypes());
    excludeMimeTypes.addAll(plugin.getExcludeMimeTypes());
    includeMimeTypes.addAll(plugin.getIncludeMimeTypes());
    includeNodeTypes.addAll(plugin.getIncludeNodeTypes());
  }

  /*
   * (non-Javadoc)
   * @see
   * org.exoplatform.services.wcm.search.SiteSearchService#searchSiteContents
   * (org.exoplatform.services.wcm.search.QueryCriteria,
   * org.exoplatform.services.jcr.ext.common.SessionProvider, int)
   */
  public AbstractPageList<ResultNode> searchSiteContents(SessionProvider sessionProvider,
                                                    QueryCriteria queryCriteria,
                                                    int pageSize,
                                                    boolean isSearchContent) throws Exception {
    ManageableRepository currentRepository = repositoryService.getCurrentRepository();
    NodeLocation location = configurationService.getLivePortalsLocation(currentRepository.getConfiguration().getName());
    Session session = sessionProvider.getSession(location.getWorkspace(),currentRepository);
    if (queryCriteria.isSearchWebpage()) {
      session = sessionProvider.getSession("portal-system", WCMCoreUtils.getRepository());
    }
    QueryManager queryManager = session.getWorkspace().getQueryManager();
    long startTime = System.currentTimeMillis();
    Query query = createQuery(queryCriteria, queryManager);
    String suggestion = getSpellSuggestion(queryCriteria.getKeyword(),currentRepository);
    AbstractPageList<ResultNode> pageList = null;
    pageList = PageListFactory.createPageList(query.getStatement(),
                                              session.getWorkspace().getName(),
                                              query.getLanguage(),
                                              IdentityConstants.SYSTEM.equals(session.getUserID()),
                                              new NodeFilter(isSearchContent, queryCriteria),
                                              new DataCreator(),
                                              pageSize,
                                              0);
    
    long queryTime = System.currentTimeMillis() - startTime;
    pageList.setQueryTime(queryTime);
    pageList.setSpellSuggestion(suggestion);
    return pageList;
  }
  
  /**
   * 
   */
  public AbstractPageList<ResultNode> searchSEOContents(SessionProvider sessionProvider,
                                                      QueryCriteria queryCriteria,
                                                      int pageSize,
                                                      boolean isSearchContent) throws Exception {
    ManageableRepository currentRepository = repositoryService.getCurrentRepository();
    Session session = sessionProvider.getSession("portal-system", currentRepository);
    QueryManager queryManager = session.getWorkspace().getQueryManager();
    long startTime = System.currentTimeMillis();
    Query query = createQuery(queryCriteria, queryManager);
    String suggestion = getSpellSuggestion(queryCriteria.getKeyword(), currentRepository);
    AbstractPageList<ResultNode> pageList = PageListFactory.createPageList(query.getStatement(),
                                                                           session.getWorkspace()
                                                                                  .getName(),
                                                                           query.getLanguage(),
                                                                           true,
                                                                           null,
                                                                           new SeoDataCreator(),
                                                                           pageSize,
                                                                           0);

    long queryTime = System.currentTimeMillis() - startTime;
    pageList.setQueryTime(queryTime);
    pageList.setSpellSuggestion(suggestion);
    return pageList;
  }

  /**
   * Gets the spell suggestion.
   *
   * @param checkingWord the checking word
   * @param manageableRepository the manageable repository
   *
   * @return the spell suggestion
   *
   * @throws Exception the exception
   */
  private String getSpellSuggestion(String checkingWord, ManageableRepository manageableRepository) throws Exception {
    //Retrieve spell suggestion in special way to avoid access denied exception
    String suggestion = null;
    Session session = null;
    try{
      session = manageableRepository.getSystemSession(manageableRepository.getConfiguration().getDefaultWorkspaceName());
      QueryManager queryManager = session.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery("SELECT rep:spellcheck() FROM nt:base WHERE jcr:path like '/' AND SPELLCHECK('"
                                                 + checkingWord + "')",
                                             Query.SQL);
      RowIterator rows = query.execute().getRows();
      Value value = rows.nextRow().getValue("rep:spellcheck()");
      if (value != null) {
        suggestion = value.getString();
      }
    } catch (Exception e) {
    } finally {
      if (session != null)
        session.logout();
    }
    return suggestion;
  }

  /**
   * Search site content.
   *
   * @param queryCriteria the query criteria
   * @param queryManager the query manager
   *
   * @return the query result
   *
   * @throws Exception the exception
   */
  private Query createQuery(QueryCriteria queryCriteria, QueryManager queryManager) throws Exception {
    SQLQueryBuilder queryBuilder = new SQLQueryBuilder();
    mapQueryTypes(queryCriteria, queryBuilder);
    if (queryCriteria.isFulltextSearch()) {
      mapQueryPath(queryCriteria, queryBuilder);
      mapFulltextQueryTearm(queryCriteria, queryBuilder);
    } else {
      searchByNodeName(queryCriteria, queryBuilder);
    }
    mapCategoriesCondition(queryCriteria,queryBuilder);
    mapDatetimeRangeSelected(queryCriteria,queryBuilder);
    mapMetadataProperties(queryCriteria,queryBuilder);
    orderBy(queryCriteria, queryBuilder);
    String queryStatement = queryBuilder.createQueryStatement();
    Query query = queryManager.createQuery(queryStatement, Query.SQL);
    return query;
  }

  /**
   * Map query path.
   *
   * @param queryCriteria the query criteria
   * @param queryBuilder the query builder
   *
   * @throws Exception the exception
   */
  private void mapQueryPath(final QueryCriteria queryCriteria, final SQLQueryBuilder queryBuilder) throws Exception {
    queryBuilder.setQueryPath(getSitePath(queryCriteria), PATH_TYPE.DECENDANTS);
  }

  /**
   * Gets the site path.
   *
   * @param queryCriteria the query criteria
   *
   * @return the site path
   *
   * @throws Exception the exception
   */
  private String getSitePath(final QueryCriteria queryCriteria) throws Exception {
    String siteName = queryCriteria.getSiteName();
    if (queryCriteria.isSearchWebpage()) {
      if ("all".equals(siteName) || siteName == null || siteName.trim().length() == 0) {
        return "/production/mop:workspace/mop:portalsites";
      } else {
        return "/production/mop:workspace/mop:portalsites/mop:" + siteName;
      }
    }
    String sitePath = null;
    if (siteName != null) {
      sitePath = livePortalManagerService.getPortalPathByName(siteName);
    } else {
      String repository = repositoryService.getCurrentRepository().getConfiguration().getName();
      sitePath = configurationService.getLivePortalsLocation(repository).getPath();
    }
    return sitePath;
  }

  /**
   * Map query term.
   *
   * @param queryCriteria the query criteria
   * @param queryBuilder the query builder
   */
  private void mapFulltextQueryTearm(final QueryCriteria queryCriteria,
                                     final SQLQueryBuilder queryBuilder) {
    String keyword = queryCriteria.getKeyword();
    if (keyword == null || keyword.length() == 0)
      return;
    QueryTermHelper queryTermHelper = new QueryTermHelper();
    String queryTerm = null;
    keyword = keyword.replaceAll("'", "''");
    if (isEnabledFuzzySearch) {
      if (keyword.contains("*") || keyword.contains("?") || keyword.contains("~")) {
        queryTerm = queryTermHelper.contains(keyword).buildTerm();
      } else {
        queryTerm = queryTermHelper.contains(keyword).allowFuzzySearch(fuzzySearchIndex).buildTerm();
      }      
    } else {
      keyword = keyword.replace("~", "\\~");
      keyword = keyword.replace("*", "\\*");
      keyword = keyword.replace("?", "\\?");
      queryTerm = queryTermHelper.contains(keyword).buildTerm();
    }
    String scope = queryCriteria.getFulltextSearchProperty();
    if (QueryCriteria.ALL_PROPERTY_SCOPE.equals(scope) || scope == null) {
      queryBuilder.contains(null, queryTerm, LOGICAL.NULL);
    } else {
      queryBuilder.contains(scope, queryTerm, LOGICAL.NULL);
    }
  }

  /**
   * Search by node name.
   *
   * @param queryCriteria the query criteria
   * @param queryBuilder the query builder
   *
   * @throws Exception the exception
   */
  private void searchByNodeName(final QueryCriteria queryCriteria,
                                final SQLQueryBuilder queryBuilder) throws Exception {
    queryBuilder.queryByNodeName(getSitePath(queryCriteria), queryCriteria.getKeyword());
  }

  /**
   * Map datetime range selected.
   *
   * @param queryCriteria the query criteria
   * @param queryBuilder the query builder
   */
  private void mapDatetimeRangeSelected(final QueryCriteria queryCriteria,
                                        final SQLQueryBuilder queryBuilder) {
    DATE_RANGE_SELECTED selectedDateRange = queryCriteria.getDateRangeSelected();
    if (selectedDateRange == null)
      return;
    if (DATE_RANGE_SELECTED.CREATED == selectedDateRange) {
      DatetimeRange createdDateRange = queryCriteria.getCreatedDateRange();
      queryBuilder.betweenDates("exo:dateCreated",
                                createdDateRange.getFromDate(),
                                createdDateRange.getToDate(),
                                LOGICAL.AND);
    } else if (DATE_RANGE_SELECTED.MODIFIDED == selectedDateRange) {
      DatetimeRange modifiedDateRange = queryCriteria.getLastModifiedDateRange();
      queryBuilder.betweenDates("exo:dateModified",
                                modifiedDateRange.getFromDate(),
                                modifiedDateRange.getToDate(),
                                LOGICAL.AND);
    } else if (DATE_RANGE_SELECTED.START_PUBLICATION == selectedDateRange) {
      throw new UnsupportedOperationException();
    } else if (DATE_RANGE_SELECTED.END_PUBLICATION == selectedDateRange) {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Map categories condition.
   *
   * @param queryCriteria the query criteria
   * @param queryBuilder the query builder
   */
  private void mapCategoriesCondition(QueryCriteria queryCriteria, SQLQueryBuilder queryBuilder) {
    String[] categoryUUIDs = queryCriteria.getCategoryUUIDs();
    if (categoryUUIDs == null)
      return;
    queryBuilder.openGroup(LOGICAL.AND);
    queryBuilder.like("exo:category", categoryUUIDs[0], LOGICAL.NULL);
    if (categoryUUIDs.length > 1) {
      for (int i = 1; i < categoryUUIDs.length; i++) {
        queryBuilder.like("exo:category", categoryUUIDs[i], LOGICAL.OR);
      }
    }
    queryBuilder.closeGroup();
  }

  /**
   * Map metadata properties.
   *
   * @param queryCriteria the query criteria
   * @param queryBuilder the query builder
   */
  private void mapMetadataProperties(final QueryCriteria queryCriteria, SQLQueryBuilder queryBuilder) {
    QueryProperty[] queryProperty = queryCriteria.getQueryMetadatas();
    if (queryProperty == null)
      return;
    queryBuilder.openGroup(LOGICAL.AND);
    queryBuilder.like(queryProperty[0].getName(), queryProperty[0].getName(), LOGICAL.NULL);
    if (queryProperty.length > 1) {
      for (int i = 1; i < queryProperty.length; i++) {
        queryBuilder.like(queryProperty[i].getName(), queryProperty[i].getName(), LOGICAL.OR);
      }
    }
    queryBuilder.closeGroup();
  }

  /**
   * Map query specific node types.
   *
   * @param queryCriteria the query criteria
   * @param queryBuilder the query builder
   * @param nodeTypeManager the node type manager
   *
   * @throws Exception the exception
   */
  private void mapQuerySpecificNodeTypes(final QueryCriteria queryCriteria,
                                         final SQLQueryBuilder queryBuilder,
                                         final NodeTypeManager nodeTypeManager) throws Exception {
    String[] contentTypes = queryCriteria.getContentTypes();
    NodeType fistType = nodeTypeManager.getNodeType(contentTypes[0]);
    queryBuilder.openGroup(LOGICAL.AND);
    if (fistType.isMixin()) {
      queryBuilder.like("jcr:mixinTypes", contentTypes[0], LOGICAL.NULL);
    } else {
      queryBuilder.equal("jcr:primaryType", contentTypes[0], LOGICAL.NULL);
    }
    if(contentTypes.length>1) {
      for (int i=1; i<contentTypes.length; i++) {
        String type = contentTypes[i];
        NodeType nodetype = nodeTypeManager.getNodeType(type);
        if (nodetype.isMixin()) {
          queryBuilder.like("jcr:mixinTypes", type, LOGICAL.OR);
        } else {
          queryBuilder.equal("jcr:primaryType", type, LOGICAL.OR);
        }
      }
    }
    queryBuilder.closeGroup();
    //Remove some specific mimtype
    queryBuilder.openGroup(LOGICAL.AND_NOT);
    queryBuilder.like("jcr:mixinTypes", "exo:cssFile", LOGICAL.NULL);
    queryBuilder.like("jcr:mixinTypes","exo:jsFile",LOGICAL.OR);
    queryBuilder.closeGroup();
  }

  /**
   * Map query types.
   *
   * @param queryCriteria the query criteria
   * @param queryBuilder the query builder
   *
   * @throws Exception the exception
   */
  private void mapQueryTypes(final QueryCriteria queryCriteria, final SQLQueryBuilder queryBuilder) throws Exception {
    queryBuilder.selectTypes(null);
    // Searh on nt:base
    queryBuilder.fromNodeTypes(null);
    ManageableRepository currentRepository = repositoryService.getCurrentRepository();
    NodeTypeManager manager = currentRepository.getNodeTypeManager();
    // Query all documents for specific content types
    String[] contentTypes = queryCriteria.getContentTypes();
    if ((contentTypes != null && contentTypes.length > 0 && queryCriteria.getKeyword() == null)
        || queryCriteria.isSearchWebpage()) {
      mapQuerySpecificNodeTypes(queryCriteria, queryBuilder, manager);
      return;
    }
    List<String> selectedNodeTypes = templateService.getDocumentTemplates();
    queryBuilder.openGroup(LOGICAL.AND);
    queryBuilder.equal("jcr:primaryType", "nt:resource", LOGICAL.NULL);
    // query on exo:rss-enable nodetypes for title, summary field
    queryBuilder.equal("jcr:mixinTypes", "exo:rss-enable", LOGICAL.OR);
    // query on metadata nodetype
    List<String> publicatioTypes = new ArrayList<String>(4);
    for (NodeTypeIterator iterator = manager.getAllNodeTypes(); iterator
    .hasNext();) {
      NodeType nodeType = iterator.nextNodeType();
      if (nodeType.isNodeType("publication:webpagesPublication")) {
        publicatioTypes.add(nodeType.getName());
        continue;
      }
      if (!nodeType.isNodeType("exo:metadata"))
        continue;
      if (nodeType.isMixin()) {
        queryBuilder.equal("jcr:mixinTypes", nodeType.getName(), LOGICAL.OR);
      } else {
        queryBuilder.equal("jcr:primaryType", nodeType.getName(), LOGICAL.OR);
      }
    }
    for (String type : selectedNodeTypes) {
      NodeType nodetype = manager.getNodeType(type);
      if (nodetype.isMixin()) {
        queryBuilder.like("jcr:mixinTypes", type, LOGICAL.OR);
      } else {
        queryBuilder.equal("jcr:primaryType", type, LOGICAL.OR);
      }
    }
    queryBuilder.closeGroup();
    //unwanted document types: exo:cssFile, exo:jsFile
    if(excludeMimeTypes.size()<1) return;
    queryBuilder.openGroup(LOGICAL.AND_NOT);
    String[] mimetypes = excludeMimeTypes.toArray(new String[]{});
    queryBuilder.equal("jcr:mimeType",mimetypes[0],LOGICAL.NULL);
    for(int i=1; i<mimetypes.length; i++) {
      queryBuilder.equal("jcr:mimeType",mimetypes[i],LOGICAL.OR);
    }
    queryBuilder.closeGroup();
    //Unwanted document by mixin nodetypes
    queryBuilder.openGroup(LOGICAL.AND_NOT);
    queryBuilder.like("jcr:mixinTypes", "exo:cssFile", LOGICAL.NULL);
    queryBuilder.like("jcr:mixinTypes","exo:jsFile",LOGICAL.OR);
    queryBuilder.closeGroup();
  }

  /**
   * Order by.
   *
   * @param queryCriteria the query criteria
   * @param queryBuilder the query builder
   */
  private void orderBy(final QueryCriteria queryCriteria, final SQLQueryBuilder queryBuilder) {
    queryBuilder.orderBy("jcr:score", ORDERBY.DESC);
  }
  
  public static class NodeFilter implements NodeSearchFilter {

    private boolean isSearchContent;
    private QueryCriteria queryCriteria;
    
    public NodeFilter(boolean isSearchContent, QueryCriteria queryCriteria) {
      this.isSearchContent = isSearchContent;
      this.queryCriteria = queryCriteria;
    }
    
    @Override
    public Node filterNodeToDisplay(Node node) {
      try {
        Node displayNode = getNodeToCheckState(node);
        if(displayNode == null) return null;
        if (isSearchContent) return displayNode;
        NodeLocation nodeLocation = NodeLocation.getNodeLocationByNode(displayNode);
        WCMComposer wcmComposer = WCMCoreUtils.getService(WCMComposer.class);
        HashMap<String, String> filters = new HashMap<String, String>();
        filters.put(WCMComposer.FILTER_MODE, WCMComposer.MODE_LIVE);
        return wcmComposer.getContent(nodeLocation.getRepository(),
                                      nodeLocation.getWorkspace(),
                                      nodeLocation.getPath(),
                                      filters,
                                      WCMCoreUtils.getSystemSessionProvider());
      } catch (Exception e) {
        return null;
      }
    }
    
    protected Node getNodeToCheckState(Node node)throws Exception{
      Node displayNode = node;
      if (node.getPath().contains("web contents/site artifacts")) {
        return null;
      }
      if (displayNode.isNodeType("nt:resource")) {
        displayNode = node.getParent();
      }
      if (displayNode.isNodeType("exo:htmlFile")) {
        Node parent = displayNode.getParent();
        if (queryCriteria.isSearchWebContent()) {
          if (parent.isNodeType("exo:webContent")) return parent;
          return null;
        }
        if (parent.isNodeType("exo:webContent")) return null;
        return displayNode;
      }
      /*
      if(queryCriteria.isSearchWebContent()) {
        if(!queryCriteria.isSearchDocument()) {
          if(!displayNode.isNodeType("exo:webContent"))
            return null;
        }
        if(queryCriteria.isSearchWebpage()) {
          if (!displayNode.isNodeType("publication:webpagesPublication"))
            return null;
        }
      } else if(queryCriteria.isSearchWebpage()) {
          if (queryCriteria.isSearchDocument()) {
            return displayNode;
          } else if (!displayNode.isNodeType("publication:webpagesPublication"))
            return null;
      }
      */
      String[] contentTypes = queryCriteria.getContentTypes();
      if(contentTypes != null && contentTypes.length>0) {
        String primaryNodeType = displayNode.getPrimaryNodeType().getName();
        if(!ArrayUtils.contains(contentTypes,primaryNodeType))
          return null;
      }
      return displayNode;
    }
    
  }
  
  public static class DataCreator implements SearchDataCreator<ResultNode> {

    @Override
    public ResultNode createData(Node node, Row row) {
      try {
        return new ResultNode(node, row);
      } catch (Exception e) {
        return null;
      }
    }
    
  }
  
  public static class SeoDataCreator implements SearchDataCreator<ResultNode> {

    @Override
    public ResultNode createData(Node node, Row row) {
      try {
        PortalRequestContext portalRequestContext = Util.getPortalRequestContext();        
        PortletRequestContext portletRequestContext = WebuiRequestContext.getCurrentInstance();
        PortletRequest portletRequest = portletRequestContext.getRequest();

        String baseURI = portletRequest.getScheme() + "://" + portletRequest.getServerName() + ":"
            + String.format("%s", portletRequest.getServerPort()) + portalRequestContext.getPortalContextPath();
        return new ResultNode(node, row, baseURI + "/" + SeoDataCreator.getUserNavigationURI(node).toString());
      } catch (Exception e) {
        return null;
      }
    }
    
    /**
     * Get user navigation URI from equivalent mop node in portal-system workspace
     * @param node the mop node in portal-system workspace
     * @return 
     * @throws RepositoryException
     */
    public static StringBuilder getUserNavigationURI(Node node) throws RepositoryException {
      if (!node.isNodeType("mop:portalsites")) {
        StringBuilder builder = getUserNavigationURI(node.getParent());
        String name = node.getName();
        if ("mop:children".equals(name) || "mop:default".equals(name)
            || "mop:rootnavigation".equals(name)) {
          return builder.append("");
        } else {
          if (builder.length() > 0) {
            builder.append("/");
          }
          return builder.append(node.getName().replaceFirst("mop:", ""));
        }
      } else {
        return new StringBuilder();
      }

    } 
    
  }  
}
