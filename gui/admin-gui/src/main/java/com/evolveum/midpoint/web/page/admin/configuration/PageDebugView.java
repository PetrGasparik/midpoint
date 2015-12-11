/*
 * Copyright (c) 2010-2015 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.web.page.admin.configuration;

import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.RetrieveOption;
import com.evolveum.midpoint.schema.SchemaConstantsGenerated;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ReportTypeUtil;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.Holder;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.AuthorizationAction;
import com.evolveum.midpoint.web.application.PageDescriptor;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.AjaxSubmitButton;
import com.evolveum.midpoint.web.component.assignment.AssignmentEditorDto;
import com.evolveum.midpoint.web.component.util.LoadableModel;
import com.evolveum.midpoint.web.component.AceEditor;
import com.evolveum.midpoint.web.page.admin.dto.ObjectViewDto;
import com.evolveum.midpoint.web.security.MidPointApplication;
import com.evolveum.midpoint.web.util.WebMiscUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LookupTableType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ReportType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

import org.apache.commons.lang.StringUtils;
import org.apache.wicket.Page;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxCheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.util.string.StringValue;

import javax.xml.namespace.QName;

import java.util.Collection;

@PageDescriptor(url = "/admin/config/debug", action = {
        @AuthorizationAction(actionUri = PageAdminConfiguration.AUTH_CONFIGURATION_ALL,
                label = PageAdminConfiguration.AUTH_CONFIGURATION_ALL_LABEL, description = PageAdminConfiguration.AUTH_CONFIGURATION_ALL_DESCRIPTION),
        @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_CONFIGURATION_DEBUG_URL,
                label = "PageDebugView.auth.debug.label", description = "PageDebugView.auth.debug.description")})
public class PageDebugView extends PageAdminConfiguration {

    private static final String DOT_CLASS = PageDebugView.class.getName() + ".";
    private static final String OPERATION_LOAD_OBJECT = DOT_CLASS + "loadObject";
    private static final String OPERATION_SAVE_OBJECT = DOT_CLASS + "saveObject";
    private static final String ID_PLAIN_TEXTAREA = "plain-textarea";

    private static final Trace LOGGER = TraceManager.getTrace(PageDebugView.class);

    public static final String PARAM_OBJECT_ID = "objectId";
    public static final String PARAM_OBJECT_TYPE = "objectType";
    private IModel<ObjectViewDto> model;
    private AceEditor editor;
    private final IModel<Boolean> encrypt = new Model<Boolean>(true);
    private final IModel<Boolean> saveAsRaw = new Model<>(true);
    private final IModel<Boolean> reevaluateSearchFilters = new Model<>(false);
    private final IModel<Boolean> validateSchema = new Model<Boolean>(false);
    private final IModel<Boolean> switchToPlainText = new Model<Boolean>(false);
    private TextArea<String> plainTextarea;
    final Form mainForm = new Form("mainForm");

    public PageDebugView() {
        model = new LoadableModel<ObjectViewDto>(false) {

            @Override
            protected ObjectViewDto load() {
                return loadObject();
            }
        };
        initLayout();
    }

    @Override
    protected IModel<String> createPageSubTitleModel() {
        return new AbstractReadOnlyModel<String>() {

            @Override
            public String getObject() {
                return createStringResource("PageDebugView.subTitle", model.getObject().getName()).getString();
            }
        };
    }

    private ObjectViewDto loadObject() {
        StringValue objectOid = getPageParameters().get(PARAM_OBJECT_ID);
        if (objectOid == null || StringUtils.isEmpty(objectOid.toString())) {
            getSession().error(getString("pageDebugView.message.oidNotDefined"));
            throw new RestartResponseException(PageDebugList.class);
        }

        Task task = createSimpleTask(OPERATION_LOAD_OBJECT);
        OperationResult result = task.getResult(); //todo is this result != null ?
        ObjectViewDto dto = null;
        try {
            MidPointApplication application = PageDebugView.this.getMidpointApplication();

            GetOperationOptions rootOptions = GetOperationOptions.createRaw();

            rootOptions.setResolveNames(true);
            Collection<SelectorOptions<GetOperationOptions>> options = SelectorOptions.createCollection(rootOptions);
            // FIXME: ObjectType.class will not work well here. We need more specific type.
            //todo on page debug list create page params, put there oid and class for object type and send that to this page....read it here
            Class type = ObjectType.class;
            StringValue objectType = getPageParameters().get(PARAM_OBJECT_TYPE);
            if (objectType != null && StringUtils.isNotBlank(objectType.toString())){
            	type = getPrismContext().getSchemaRegistry().determineCompileTimeClass(new QName(SchemaConstantsGenerated.NS_COMMON, objectType.toString()));
            }

            if (UserType.class.isAssignableFrom(type)) {
                options.add(SelectorOptions.create(UserType.F_JPEG_PHOTO,
                        GetOperationOptions.createRetrieve(RetrieveOption.INCLUDE)));
            }

            if(objectType != null && LookupTableType.COMPLEX_TYPE.getLocalPart().equals(objectType.toString())){
                options.add(SelectorOptions.create(LookupTableType.F_ROW,
                        GetOperationOptions.createRetrieve(RetrieveOption.INCLUDE)));
            }

            PrismObject<ObjectType> object = getModelService().getObject(type, objectOid.toString(), options, task, result);

            PrismContext context = application.getPrismContext();
            String xml = context.serializeObjectToString(object, PrismContext.LANG_XML);
            dto = new ObjectViewDto(object.getOid(), WebMiscUtil.getName(object), object, xml);

            result.recomputeStatus();
        } catch (Exception ex) {
            result.recordFatalError("Couldn't load object.", ex);
        }

        if (dto == null) {
            showResultInSession(result);
            throw new RestartResponseException(PageDebugList.class);
        }

        if (!result.isSuccess()) {
            showResult(result);
        }

        if (!WebMiscUtil.isSuccessOrHandledErrorOrWarning(result)) {
            showResultInSession(result);
            throw new RestartResponseException(PageDebugList.class);
        }

        return dto;
    }

    private void initLayout() {
        add(mainForm);

        final IModel<Boolean> editable = new Model<Boolean>(false);

        mainForm.add(new AjaxCheckBox("encrypt", encrypt) {

			@Override
			protected void onUpdate(AjaxRequestTarget target) {
			}
        });

        mainForm.add(new AjaxCheckBox("saveAsRaw", saveAsRaw) {

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
            }
        });

        mainForm.add(new AjaxCheckBox("reevaluateSearchFilters", reevaluateSearchFilters) {

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
            }
        });

        mainForm.add(new AjaxCheckBox("validateSchema", validateSchema) {

			@Override
			protected void onUpdate(AjaxRequestTarget target) {
			}
        });

        mainForm.add(new AjaxCheckBox("edit", editable) {

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                editPerformed(target, editable.getObject());
            }
        });

        mainForm.add(new AjaxCheckBox("switchToPlainText", switchToPlainText) {

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                if (switchToPlainText.getObject().booleanValue()){
                    editor.setVisible(false);
                    plainTextarea.setVisible(true);
                } else {
                    editor.setVisible(true);
                    plainTextarea.setVisible(false);
                }
                target.add(mainForm);
            }
        });

        plainTextarea = new TextArea<>(ID_PLAIN_TEXTAREA,
                new PropertyModel<String>(model, ObjectViewDto.F_XML));
        plainTextarea.setVisible(false);
        plainTextarea.setEnabled(editable.getObject());

        mainForm.add(plainTextarea);

        editor = new AceEditor("aceEditor", new PropertyModel<String>(model, ObjectViewDto.F_XML));
        editor.setReadonly(!editable.getObject());
        mainForm.add(editor);

        initButtons(mainForm);
    }

    private void initButtons(final Form mainForm) {
        AjaxSubmitButton saveButton = new AjaxSubmitButton("saveButton",
                createStringResource("pageDebugView.button.save")) {

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                savePerformed(target);
            }

            @Override
            protected void onError(AjaxRequestTarget target, Form<?> form) {
                target.add(getFeedbackPanel());
            }
        };
        mainForm.add(saveButton);

        AjaxButton backButton = new AjaxButton("backButton",
                createStringResource("pageDebugView.button.back")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                //target.appendJavaScript("history.go(-1)");
                //todo wtf????
                Page requestPage = (Page)getSession().getAttribute("requestPage");

                if(requestPage != null){
                	setResponsePage(requestPage);
                	getSession().setAttribute("requestPage", null);
                } else {
                	setResponsePage(new PageDebugList(false));
                }
            }
        };
        mainForm.add(backButton);
    }

    public void editPerformed(AjaxRequestTarget target, boolean editable) {
        editor.setReadonly(!editable);
        plainTextarea.setEnabled(editable);
        target.add(mainForm);
        editor.refreshReadonly(target);
    }
    
    private boolean isReport(PrismObject object){
    	if (object.getCompileTimeClass() != null && object.getCompileTimeClass() == ReportType.class){
    		return true;
    	}
    	
    	if (object.getDefinition() != null && object.getDefinition().getName().equals(ReportType.COMPLEX_TYPE)){
    		return true;
    	}
    	
    	return false;
    }

    public void savePerformed(AjaxRequestTarget target) {
        ObjectViewDto dto = model.getObject();
        if (StringUtils.isEmpty(dto.getXml())) {
            error(getString("pageDebugView.message.cantSaveEmpty"));
            target.add(getFeedbackPanel());
            return;
        }

        Task task = createSimpleTask(OPERATION_SAVE_OBJECT);
        OperationResult result = task.getResult();
        try {

            PrismObject<ObjectType> oldObject = dto.getObject();
            oldObject.revive(getPrismContext());

            Holder<PrismObject<ObjectType>> objectHolder = new Holder<PrismObject<ObjectType>>(null);
            if (editor.isVisible()) {
                validateObject(editor.getModel().getObject(), objectHolder, validateSchema.getObject(), result);
            } else {
                validateObject(plainTextarea.getModel().getObject(), objectHolder, validateSchema.getObject(), result);
            }

            if (result.isAcceptable()) {
                PrismObject<ObjectType> newObject = objectHolder.getValue();

                ObjectDelta<ObjectType> delta = oldObject.diff(newObject, true, true);

                if (delta.getPrismContext() == null) {
                	LOGGER.warn("No prism context in delta {} after diff, adding it", delta);
                	delta.revive(getPrismContext());
                }

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Delta to be applied:\n{}", delta.debugDump());
                }
                
                //quick fix for now (MID-1910), maybe it should be somewhere in model..
//                if (isReport(oldObject)){
//                	ReportTypeUtil.applyConfigurationDefinition((PrismObject)newObject, delta, getPrismContext());
//                }

                Collection<ObjectDelta<? extends ObjectType>> deltas = (Collection) MiscUtil.createCollection(delta);
                ModelExecuteOptions options = new ModelExecuteOptions();
                if (saveAsRaw.getObject()) {
                    options.setRaw(true);
                }
                if (reevaluateSearchFilters.getObject()) {
                    options.setReevaluateSearchFilters(true);
                }
                if(!encrypt.getObject()) {
                	options.setNoCrypt(true);
                }

                getModelService().executeChanges(deltas, options, task, result);

                result.computeStatus();
            }
        } catch (Exception ex) {
            result.recordFatalError("Couldn't save object.", ex);
        }

        if (result.isError()) {
            showResult(result);
            target.add(getFeedbackPanel());
        } else {
            showResultInSession(result);
            setResponsePage(new PageDebugList(false));
        }
    }
}
