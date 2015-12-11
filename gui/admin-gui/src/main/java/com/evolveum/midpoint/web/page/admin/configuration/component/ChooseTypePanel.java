/*
 * Copyright (c) 2010-2013 Evolveum
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

package com.evolveum.midpoint.web.page.admin.configuration.component;

import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.util.SimplePanel;
import com.evolveum.midpoint.web.page.admin.dto.ObjectViewDto;
import com.evolveum.midpoint.web.util.WebMiscUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.StringResourceModel;

import javax.xml.namespace.QName;

/**
 *  @author shood
 *
 *  TODO use a better name (ChooseObjectPanel ? ObjectChoosePanel ?)
 *  Distinguish between chooser panels that reside on "main page" and
 *  the one that resides in the popup window (ObjectSelectionPanel).
 */
public class ChooseTypePanel<T extends ObjectType> extends SimplePanel<ObjectViewDto> {

    private static final Trace LOGGER = TraceManager.getTrace(ChooseTypePanel.class);

    private static final String ID_OBJECT_NAME = "name";
    private static final String ID_LINK_CHOOSE = "choose";
    private static final String ID_LINK_REMOVE = "remove";

    private static final String MODAL_ID_OBJECT_SELECTION_POPUP = "objectSelectionPopup";

    public ChooseTypePanel(String id, IModel<ObjectViewDto> model){
        super(id, model);
    }

    @Override
    protected void initLayout() {

        final Label name = new Label(ID_OBJECT_NAME, new AbstractReadOnlyModel<String>(){

            @Override
            public String getObject(){
                ObjectViewDto dto = getModel().getObject();

                if (dto.getName() != null)
                    return getModel().getObject().getName();
                else if (ObjectViewDto.BAD_OID.equals(dto.getOid())){
                    return createStringResource("chooseTypePanel.ObjectNameValue.badOid").getString();
                } else {
                    return createStringResource("chooseTypePanel.ObjectNameValue.null").getString();
                }
            }
        });
        name.setOutputMarkupId(true);

        AjaxLink choose = new AjaxLink(ID_LINK_CHOOSE) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                 changeOptionPerformed(target);
            }
        };

        AjaxLink remove = new AjaxLink(ID_LINK_REMOVE) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                setToDefault();
                target.add(name);
            }
        };

        add(choose);
        add(remove);
        add(name);

        initDialog();
    }

    private void initDialog() {
        final ModalWindow dialog = new ModalWindow(MODAL_ID_OBJECT_SELECTION_POPUP);

        ObjectSelectionPanel.Context context = new ObjectSelectionPanel.Context(this) {

            // It seems that when modal window is open, ChooseTypePanel.this points to
            // wrong instance of ChooseTypePanel (the one that will not be used afterwards -
            // any changes made to its models are simply lost). So we want to get the reference to the "correct" one.
            public ChooseTypePanel getRealParent() {
                return WebMiscUtil.theSameForPage(ChooseTypePanel.this, getCallingPageReference());
            }

            @Override
            public void chooseOperationPerformed(AjaxRequestTarget target, ObjectType object) {
                getRealParent().choosePerformed(target, object);
            }

            @Override
            public ObjectQuery getDataProviderQuery() {
                return getRealParent().getChooseQuery();
            }

            public boolean isSearchEnabled() {
                return getRealParent().isSearchEnabled();
            }

            @Override
            public QName getSearchProperty() {
                return getRealParent().getSearchProperty();
            }

            @Override
            public Class<? extends ObjectType> getObjectTypeClass() {
                return getRealParent().getObjectTypeClass();
            }

        };

        ObjectSelectionPage.prepareDialog(dialog, context, this, "chooseTypeDialog.title", ID_OBJECT_NAME);
        add(dialog);
    }

    protected  boolean isSearchEnabled(){
        return false;
    }

    protected QName getSearchProperty(){
        return null;
    }

    protected ObjectQuery getChooseQuery(){
        return null;
    }

    private void choosePerformed(AjaxRequestTarget target, ObjectType object){
        ModalWindow window = (ModalWindow) get(MODAL_ID_OBJECT_SELECTION_POPUP);
        window.close(target);

        ObjectViewDto o = getModel().getObject();

        o.setName(WebMiscUtil.getName(object));
        o.setOid(object.getOid());

        if(LOGGER.isTraceEnabled()){
            LOGGER.trace("Choose operation performed: {} ({})", o.getName(), o.getOid());
        }

        target.add(get(ID_OBJECT_NAME));
    }

    private void changeOptionPerformed(AjaxRequestTarget target){
        ModalWindow window = (ModalWindow)get(MODAL_ID_OBJECT_SELECTION_POPUP);
        window.show(target);
    }

    private void setToDefault(){
        ObjectViewDto dto = new ObjectViewDto();
        dto.setType(getObjectTypeClass());
        getModel().setObject(dto);
    }

    public Class<T> getObjectTypeClass(){
        return ChooseTypePanel.this.getModelObject().getType();
    }
}
