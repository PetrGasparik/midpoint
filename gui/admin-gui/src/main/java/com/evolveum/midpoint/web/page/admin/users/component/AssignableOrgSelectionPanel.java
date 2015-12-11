package com.evolveum.midpoint.web.page.admin.users.component;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectQueryUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.AjaxTabbedPanel;
import com.evolveum.midpoint.web.component.org.OrgTreeTablePanel;
import com.evolveum.midpoint.web.component.util.LoadableModel;
import com.evolveum.midpoint.web.page.PageBase;
import com.evolveum.midpoint.web.page.admin.users.PageUsers;
import com.evolveum.midpoint.web.page.admin.users.dto.OrgTableDto;
import com.evolveum.midpoint.web.page.admin.users.dto.OrgTreeDto;
import com.evolveum.midpoint.web.util.WebMiscUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OrgType;
import org.apache.commons.lang.Validate;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.tabs.AbstractTab;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import java.util.ArrayList;
import java.util.List;

public class AssignableOrgSelectionPanel <T extends ObjectType> extends AbstractAssignableSelectionPanel<T> {

    private static final Trace LOGGER = TraceManager.getTrace(AssignableOrgSelectionPanel.class);

    private static final String DOT_CLASS = AssignableOrgSelectionPanel.class.getName() + ".";
    private static final String OPERATION_LOAD_ORG_UNITS = DOT_CLASS + "loadOrgUnits";

	private final static String ID_TABS = "tabs";
	private final static String ID_ASSIGN_ROOT = "assignRoot";
	
	public AssignableOrgSelectionPanel(String id, Context context) {
		super(id, context);
	}

	protected Panel createPopupContent(){
        final IModel<List<ITab>> tabModel = new LoadableModel<List<ITab>>(false) {

            @Override
            protected List<ITab> load() {
                List<PrismObject<OrgType>> roots = loadOrgRoots();

                List<ITab> tabs = new ArrayList<>();
                for (PrismObject<OrgType> root : roots) {
                    final String oid = root.getOid();
                    tabs.add(new AbstractTab(createTabTitle(root)) {

                        @Override
                        public WebMarkupContainer getPanel(String panelId) {
                            return new OrgTreeTablePanel(panelId, new Model(oid)){

                                @Override
                                protected CharSequence computeTreeHeight() {
                                    return "";
                                }
                            };
                        }
                    });
                }

                if (tabs.isEmpty()) {
                    getSession().warn(getString("assignablePopupContent.message.noOrgStructureDefined"));
                    throw new RestartResponseException(PageUsers.class);
                }

                return tabs;
            }
        };

	    AjaxTabbedPanel tabbedPanel = new AjaxTabbedPanel(ID_TABS, tabModel.getObject(), new Model<>(0));
	    tabbedPanel.setOutputMarkupId(true);
	     addOrReplace(tabbedPanel);
	     
	     AjaxButton assignRootButton = new AjaxButton(ID_ASSIGN_ROOT, createStringResource("AssignableOrgSelectionPanel.button.assignRoot")) {

	            @Override
	            public void onClick(AjaxRequestTarget target) {
	                addPerformed(target, getSelectedRoot());
	            }
	        };
	        add(assignRootButton);
	     return tabbedPanel;
	}
	
	private List<PrismObject<OrgType>> loadOrgRoots() {
        OperationResult result = new OperationResult(OPERATION_LOAD_ORG_UNITS);

        PageBase pageBase = WebMiscUtil.getPageBase(this);
        Task task = pageBase.createSimpleTask(OPERATION_LOAD_ORG_UNITS);
        List<PrismObject<OrgType>> list = new ArrayList<>();
        try {
            ObjectQuery query = ObjectQueryUtil.createRootOrgQuery(pageBase.getPrismContext());
            list = pageBase.getModelService().searchObjects(OrgType.class, query, null, task, result);

            if (list.isEmpty()) {
                warn(getString("assignablePopupContent.message.noOrgStructureDefined"));
            }
        } catch (Exception ex) {
            LoggingUtils.logException(LOGGER, "Unable to load org. unit", ex);
            result.recordFatalError("Unable to load org unit", ex);
        } finally {
            result.computeStatus();
        }

        return list;
    }
	
	private IModel<String> createTabTitle(final PrismObject<OrgType> org) {
	    return new AbstractReadOnlyModel<String>() {

	        @Override
	        public String getObject() {
	            PolyString displayName = org.getPropertyRealValue(OrgType.F_DISPLAY_NAME, PolyString.class);
	            if (displayName != null) {
	                return displayName.getOrig();
	            }

                return WebMiscUtil.getName(org);
	        }
	    };
	}
	 
	@Override
	protected Panel getTablePanel() {
		return (AjaxTabbedPanel) get(ID_TABS);
	}
	 
	public List<ObjectType> getSelectedObjects(){
	    List<ObjectType> selected = new ArrayList<>();
	    AjaxTabbedPanel orgPanel = (AjaxTabbedPanel) getTablePanel();
	    OrgTreeTablePanel orgPanels = (OrgTreeTablePanel) orgPanel.get("panel");
    	List<OrgTableDto> orgs = orgPanels.getSelectedOrgs();
     	for (OrgTableDto org : orgs){
     		selected.add(org.getObject());
     	}
        return selected;
	}
	
	public List<ObjectType> getSelectedRoot(){
	    List<ObjectType> selected = new ArrayList<>();
	    AjaxTabbedPanel orgPanel = (AjaxTabbedPanel) getTablePanel();
//	    int selectedTab = orgPanel.getSelectedTab();
//	    OrgTreeTablePanel orgPanels = (OrgTreeTablePanel) orgPanel.get(selectedTab);
	    OrgTreeTablePanel orgPanels = (OrgTreeTablePanel) orgPanel.get("panel");
    	OrgTreeDto org = orgPanels.getRootFromProvider();
     	selected.add(org.getObject());
        return selected;
	}
	
	
	 
	public void setType(Class<T> type){
		Validate.notNull(type, "Class must not be null.");
		this.type = type;

        AjaxTabbedPanel table = (AjaxTabbedPanel) getTablePanel();
        if (table != null) {
        	createPopupContent();
//            replace(createPopupContent());
        }
	}
}
