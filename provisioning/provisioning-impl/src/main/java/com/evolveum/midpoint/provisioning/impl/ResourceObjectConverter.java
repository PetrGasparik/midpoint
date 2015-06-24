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

package com.evolveum.midpoint.provisioning.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.common.refinery.RefinedAssociationDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.repo.cache.RepositoryCache;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AttributeFetchStrategyType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.AddRemoveAttributeValuesCapabilityType;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.common.ResourceObjectPattern;
import com.evolveum.midpoint.common.refinery.RefinedAttributeDefinition;
import com.evolveum.midpoint.common.refinery.RefinedObjectClassDefinition;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.common.refinery.ResourceShadowDiscriminator;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.delta.ContainerDelta;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.match.MatchingRule;
import com.evolveum.midpoint.prism.match.MatchingRuleRegistry;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.EqualFilter;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.provisioning.api.GenericConnectorException;
import com.evolveum.midpoint.provisioning.ucf.api.AttributesToReturn;
import com.evolveum.midpoint.provisioning.ucf.api.Change;
import com.evolveum.midpoint.provisioning.ucf.api.ConnectorInstance;
import com.evolveum.midpoint.provisioning.ucf.api.ExecuteProvisioningScriptOperation;
import com.evolveum.midpoint.provisioning.ucf.api.GenericFrameworkException;
import com.evolveum.midpoint.provisioning.ucf.api.Operation;
import com.evolveum.midpoint.provisioning.ucf.api.PropertyModificationOperation;
import com.evolveum.midpoint.provisioning.ucf.api.ResultHandler;
import com.evolveum.midpoint.provisioning.ucf.impl.ConnectorFactoryIcfImpl;
import com.evolveum.midpoint.provisioning.util.ProvisioningUtil;
import com.evolveum.midpoint.schema.SearchResultMetadata;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.processor.ObjectClassComplexTypeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceAttribute;
import com.evolveum.midpoint.schema.processor.ResourceAttributeContainer;
import com.evolveum.midpoint.schema.processor.ResourceAttributeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceObjectIdentification;
import com.evolveum.midpoint.schema.processor.ResourceSchema;
import com.evolveum.midpoint.schema.processor.SearchHierarchyConstraints;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.schema.util.SchemaDebugUtil;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.util.Holder;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.exception.TunnelException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LockoutStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationProvisioningScriptType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationProvisioningScriptsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ProvisioningOperationTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowAssociationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.ActivationCapabilityType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.ActivationLockoutStatusCapabilityType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.ActivationStatusCapabilityType;

/**
 * 
 * Responsibilities:
 *     protected objects
 *     simulated activation
 *     script execution
 *     avoid duplicate values
 *     attributes returned by default/not returned by default
 *   
 * Limitations:
 *     must NOT access repository
 *     does not know about OIDs
 * 
 * @author Katarina Valalikova
 * @author Radovan Semancik
 *
 */
@Component
public class ResourceObjectConverter {
	
	@Autowired(required=true)
	private EntitlementConverter entitlementConverter;

	@Autowired(required=true)
	private MatchingRuleRegistry matchingRuleRegistry;
	
	@Autowired(required=true)
	private ResourceObjectReferenceResolver resourceObjectReferenceResolver;

	@Autowired(required=true)
	private PrismContext prismContext;

//	private PrismObjectDefinition<ShadowType> shadowTypeDefinition;

	private static final Trace LOGGER = TraceManager.getTrace(ResourceObjectConverter.class);

	public static final String FULL_SHADOW_KEY = ResourceObjectConverter.class.getName()+".fullShadow";

	
	public PrismObject<ShadowType> getResourceObject(ProvisioningContext ctx, 
			Collection<? extends ResourceAttribute<?>> identifiers, OperationResult parentResult)
					throws ObjectNotFoundException, CommunicationException, SchemaException, ConfigurationException,
					SecurityViolationException, GenericConnectorException {
		
		AttributesToReturn attributesToReturn = ProvisioningUtil.createAttributesToReturn(ctx);
		
		PrismObject<ShadowType> resourceShadow = fetchResourceObject(ctx, identifiers, 
				attributesToReturn, true, parentResult);			// todo consider whether it is always necessary to fetch the entitlements
		
		return resourceShadow;

	}
	
	/**
	 * Tries to get the object directly if primary identifiers are present. Tries to search for the object if they are not. 
	 */
	public PrismObject<ShadowType> locateResourceObject(ProvisioningContext ctx,
			Collection<? extends ResourceAttribute<?>> identifiers, OperationResult parentResult) throws ObjectNotFoundException,
			CommunicationException, SchemaException, ConfigurationException, SecurityViolationException, GenericConnectorException {
		ResourceType resource = ctx.getResource();
		ConnectorInstance connector = ctx.getConnector(parentResult);
		
		AttributesToReturn attributesToReturn = ProvisioningUtil.createAttributesToReturn(ctx);
		
		if (hasAllIdentifiers(identifiers, ctx.getObjectClassDefinition())) {
			return fetchResourceObject(ctx, identifiers, 
					attributesToReturn, true, parentResult);	// todo consider whether it is always necessary to fetch the entitlements
		} else {
			// Search
			Collection<? extends RefinedAttributeDefinition> secondaryIdentifierDefs = ctx.getObjectClassDefinition().getSecondaryIdentifiers();
			// Assume single secondary identifier for simplicity
			if (secondaryIdentifierDefs.size() > 1) {
				throw new UnsupportedOperationException("Composite secondary identifier is not supported yet");
			} else if (secondaryIdentifierDefs.isEmpty()) {
				throw new SchemaException("No secondary identifier defined, cannot search");
			}
			RefinedAttributeDefinition secondaryIdentifierDef = secondaryIdentifierDefs.iterator().next();
			ResourceAttribute<?> secondaryIdentifier = null;
			for (ResourceAttribute<?> identifier: identifiers) {
				if (identifier.getElementName().equals(secondaryIdentifierDef.getName())) {
					secondaryIdentifier = identifier;
				}
			}
			if (secondaryIdentifier == null) {
				throw new SchemaException("No secondary identifier present, cannot search. Identifiers: "+identifiers);
			}
			
			final ResourceAttribute<?> finalSecondaryIdentifier = secondaryIdentifier;

            List<PrismPropertyValue> secondaryIdentifierValues = (List) secondaryIdentifier.getValues();
            PrismPropertyValue secondaryIdentifierValue;
            if (secondaryIdentifierValues.size() > 1) {
                throw new IllegalStateException("Secondary identifier has more than one value: " + secondaryIdentifier.getValues());
            } else if (secondaryIdentifierValues.size() == 1) {
                secondaryIdentifierValue = secondaryIdentifierValues.get(0);
            } else {
                secondaryIdentifierValue = null;
            }
			ObjectFilter filter = EqualFilter.createEqual(new ItemPath(ShadowType.F_ATTRIBUTES, secondaryIdentifierDef.getName()), secondaryIdentifierDef, secondaryIdentifierValue);
			ObjectQuery query = ObjectQuery.createObjectQuery(filter);
//			query.setFilter(filter);
			final Holder<PrismObject<ShadowType>> shadowHolder = new Holder<PrismObject<ShadowType>>();
			ResultHandler<ShadowType> handler = new ResultHandler<ShadowType>() {
				@Override
				public boolean handle(PrismObject<ShadowType> shadow) {
					if (!shadowHolder.isEmpty()) {
						throw new IllegalStateException("More than one value found for secondary identifier "+finalSecondaryIdentifier);
					}
					shadowHolder.setValue(shadow);
					return true;
				}
			};
			try {
				connector.search(ctx.getObjectClassDefinition(), query, handler, attributesToReturn, null, null, parentResult);
				if (shadowHolder.isEmpty()) {
					throw new ObjectNotFoundException("No object found for secondary identifier "+secondaryIdentifier);
				}
				PrismObject<ShadowType> shadow = shadowHolder.getValue();
				return postProcessResourceObjectRead(ctx, shadow, true, parentResult);
			} catch (GenericFrameworkException e) {
				throw new GenericConnectorException(e.getMessage(), e);
			}
		}

	}

	private boolean hasAllIdentifiers(Collection<? extends ResourceAttribute<?>> attributes,
			RefinedObjectClassDefinition objectClassDefinition) {
		Collection<? extends RefinedAttributeDefinition> identifierDefs = objectClassDefinition.getIdentifiers();
		for (RefinedAttributeDefinition identifierDef: identifierDefs) {
			boolean found = false;
			for(ResourceAttribute<?> attribute: attributes) {
				if (attribute.getElementName().equals(identifierDef.getName()) && !attribute.isEmpty()) {
					found = true;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	

	public PrismObject<ShadowType> addResourceObject(ProvisioningContext ctx, 
			PrismObject<ShadowType> shadow, OperationProvisioningScriptsType scripts, OperationResult parentResult)
			throws ObjectNotFoundException, SchemaException, CommunicationException,
			ObjectAlreadyExistsException, ConfigurationException, SecurityViolationException {
		ResourceType resource = ctx.getResource();
		
		// We might be modifying the shadow (e.g. for simulated capabilities). But we do not want the changes
		// to propagate back to the calling code. Hence the clone.
		PrismObject<ShadowType> shadowClone = shadow.clone();
		ShadowType shadowType = shadowClone.asObjectable();

		Collection<ResourceAttribute<?>> resourceAttributesAfterAdd = null;

		if (isProtectedShadow(ctx.getObjectClassDefinition(), shadowClone)) {
			LOGGER.error("Attempt to add protected shadow " + shadowType + "; ignoring the request");
			throw new SecurityViolationException("Cannot get protected shadow " + shadowType);
		}

		Collection<Operation> additionalOperations = new ArrayList<Operation>();
		addExecuteScriptOperation(additionalOperations, ProvisioningOperationTypeType.ADD, scripts, resource,
				parentResult);
		entitlementConverter.processEntitlementsAdd(ctx, shadowClone);
		
		ConnectorInstance connector = ctx.getConnector(parentResult);
		try {

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("PROVISIONING ADD operation on resource {}\n ADD object:\n{}\n additional operations:\n{}",
						new Object[] { resource.asPrismObject(), shadowType.asPrismObject().debugDump(),
								SchemaDebugUtil.debugDump(additionalOperations,2) });
			}
			transformActivationAttributes(ctx, shadowType, parentResult);
			
			if (!ResourceTypeUtil.hasCreateCapability(resource)){
				throw new UnsupportedOperationException("Resource does not support 'create' operation");
			}
			
			resourceAttributesAfterAdd = connector.addObject(shadowClone, additionalOperations, parentResult);

			if (LOGGER.isDebugEnabled()) {
				// TODO: reduce only to new/different attributes. Dump all
				// attributes on trace level only
				LOGGER.debug("PROVISIONING ADD successful, returned attributes:\n{}",
						SchemaDebugUtil.prettyPrint(resourceAttributesAfterAdd));
			}

			// Be careful not to apply this to the cloned shadow. This needs to be propagated
			// outside this method.
			applyAfterOperationAttributes(shadow, resourceAttributesAfterAdd);
		} catch (CommunicationException ex) {
			parentResult.recordFatalError(
					"Could not create object on the resource. Error communicating with the connector " + connector + ": " + ex.getMessage(), ex);
			throw new CommunicationException("Error communicating with the connector " + connector + ": "
					+ ex.getMessage(), ex);
		} catch (GenericFrameworkException ex) {
			parentResult.recordFatalError("Could not create object on the resource. Generic error in connector: " + ex.getMessage(), ex);
//			LOGGER.info("Schema for add:\n{}",
//					DOMUtil.serializeDOMToString(ResourceTypeUtil.getResourceXsdSchema(resource)));
//			
			throw new GenericConnectorException("Generic error in connector: " + ex.getMessage(), ex);
		} catch (ObjectAlreadyExistsException ex){
			parentResult.recordFatalError("Could not create object on the resource. Object already exists on the resource: " + ex.getMessage(), ex);
			throw new ObjectAlreadyExistsException("Object already exists on the resource: " + ex.getMessage(), ex);
		} catch (ConfigurationException ex){
			parentResult.recordFatalError(ex);
			throw ex;
		} catch (RuntimeException ex) {
			parentResult.recordFatalError(ex);
			throw ex;
		}
		
		// Execute entitlement modification on other objects (if needed)
		executeEntitlementChangesAdd(ctx, shadowClone, scripts, parentResult);

		parentResult.recordSuccess();
		return shadow;
	}

	public void deleteResourceObject(ProvisioningContext ctx, PrismObject<ShadowType> shadow, 
			OperationProvisioningScriptsType scripts, OperationResult parentResult)
			throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException,
			SecurityViolationException {

		LOGGER.trace("Getting object identifiers");
		Collection<? extends ResourceAttribute<?>> identifiers = ShadowUtil
				.getIdentifiers(shadow);

		if (isProtectedShadow(ctx.getObjectClassDefinition(), shadow)) {
			LOGGER.error("Attempt to delete protected resource object " + ctx.getObjectClassDefinition() + ": "
					+ identifiers + "; ignoring the request");
			throw new SecurityViolationException("Cannot delete protected resource object "
					+ ctx.getObjectClassDefinition() + ": " + identifiers);
		}
		
		//check idetifier if it is not null
		if (identifiers.isEmpty() && shadow.asObjectable().getFailedOperationType()!= null){
			throw new GenericConnectorException(
					"Unable to delete object from the resource. Probably it has not been created yet because of previous unavailability of the resource.");
		}
		
		// Execute entitlement modification on other objects (if needed)
		executeEntitlementChangesDelete(ctx, shadow, scripts, parentResult);

		Collection<Operation> additionalOperations = new ArrayList<Operation>();
		addExecuteScriptOperation(additionalOperations, ProvisioningOperationTypeType.DELETE, scripts, ctx.getResource(),
				parentResult);

		ConnectorInstance connector = ctx.getConnector(parentResult);
		try {

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(
						"PROVISIONING DELETE operation on {}\n DELETE object, object class {}, identified by:\n{}\n additional operations:\n{}",
						new Object[] { ctx.getResource(), shadow.asObjectable().getObjectClass(),
								SchemaDebugUtil.debugDump(identifiers),
								SchemaDebugUtil.debugDump(additionalOperations) });
			}
			
			if (!ResourceTypeUtil.hasDeleteCapability(ctx.getResource())){
				throw new UnsupportedOperationException("Resource does not support 'delete' operation");
			}

			connector.deleteObject(ctx.getObjectClassDefinition(), additionalOperations, identifiers, parentResult);

			LOGGER.debug("PROVISIONING DELETE successful");
			parentResult.recordSuccess();

		} catch (ObjectNotFoundException ex) {
			parentResult.recordFatalError("Can't delete object " + shadow
					+ ". Reason: " + ex.getMessage(), ex);
			throw new ObjectNotFoundException("An error occured while deleting resource object " + shadow
					+ "whith identifiers " + identifiers + ": " + ex.getMessage(), ex);
		} catch (CommunicationException ex) {
			parentResult.recordFatalError(
					"Error communicating with the connector " + connector + ": " + ex.getMessage(), ex);
			throw new CommunicationException("Error communicating with the connector " + connector + ": "
					+ ex.getMessage(), ex);
		} catch (GenericFrameworkException ex) {
			parentResult.recordFatalError("Generic error in connector: " + ex.getMessage(), ex);
			throw new GenericConnectorException("Generic error in connector: " + ex.getMessage(), ex);
		}
	}
	
	public Collection<PropertyModificationOperation> modifyResourceObject(
			ProvisioningContext ctx, PrismObject<ShadowType> shadow, OperationProvisioningScriptsType scripts,
			Collection<? extends ItemDelta> itemDeltas, OperationResult parentResult)
			throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException,
			SecurityViolationException, ObjectAlreadyExistsException {
		RefinedObjectClassDefinition objectClassDefinition = ctx.getObjectClassDefinition();
		Collection<Operation> operations = new ArrayList<Operation>();
		
		Collection<? extends ResourceAttribute<?>> identifiers = ShadowUtil.getIdentifiers(shadow);

		if (isProtectedShadow(ctx.getObjectClassDefinition(), shadow)) {
			if (hasChangesOnResource(itemDeltas)) {
				LOGGER.error("Attempt to modify protected resource object " + objectClassDefinition + ": "
						+ identifiers);
				throw new SecurityViolationException("Cannot modify protected resource object "
						+ objectClassDefinition + ": " + identifiers);
			} else {
				// Return immediately. This structure of the code makes sure that we do not execute any
				// resource operation for protected account even if there is a bug in the code below.
				LOGGER.trace("No resource modifications for protected resource object {}: {}; skipping",
						objectClassDefinition, identifiers);
				return null;
			}
		}

        /*
         *  State of the shadow before execution of the deltas - e.g. with original attributes, as it may be recorded in such a way in
         *  groups of which this account is a member of. (In case of object->subject associations.)
         *
         *  This is used when the resource does NOT provide referential integrity by itself. This is e.g. the case of OpenDJ with default
         *  settings.
         *
         *  On the contrary, AD and OpenDJ with referential integrity plugin do provide automatic referential integrity, so this feature is
         *  not needed.
         *
         *  We decide based on setting of explicitReferentialIntegrity in association definition.
         */
       

		collectAttributeAndEntitlementChanges(ctx, itemDeltas, operations, shadow, parentResult);
		
		Collection<PropertyModificationOperation> sideEffectChanges = null;
		PrismObject<ShadowType> shadowBefore = shadow.clone();
		if (operations.isEmpty()){
			// We have to check BEFORE we add script operations, otherwise the check would be pointless
			LOGGER.trace("No modifications for connector object specified. Skipping processing of modifyShadow.");
		} else {
		
			// This must go after the skip check above. Otherwise the scripts would be executed even if there is no need to.
			addExecuteScriptOperation(operations, ProvisioningOperationTypeType.MODIFY, scripts, ctx.getResource(), parentResult);
			
			//check identifier if it is not null
			if (identifiers.isEmpty() && shadow.asObjectable().getFailedOperationType()!= null){
				throw new GenericConnectorException(
						"Unable to modify object in the resource. Probably it has not been created yet because of previous unavailability of the resource.");
			}
	
//			PrismObject<ShadowType> currentShadow = null;
			if (ResourceTypeUtil.isAvoidDuplicateValues(ctx.getResource()) || isRename(operations)) {
				// We need to filter out the deltas that add duplicate values or remove values that are not there
				LOGGER.trace("Fetching shadow for duplicate filtering and/or rename processing");
				shadow = getShadowToFilterDuplicates(ctx, identifiers, operations, true, parentResult);      // yes, we need associations here
				shadowBefore = shadow.clone();
			}
			
			// Execute primary ICF operation on this shadow
			sideEffectChanges = executeModify(ctx, shadow, identifiers, operations, parentResult);
		}

        /*
         *  State of the shadow after execution of the deltas - e.g. with new DN (if it was part of the delta), because this one should be recorded
         *  in groups of which this account is a member of. (In case of object->subject associations.)
         */
        PrismObject<ShadowType> shadowAfter = shadow.clone();
        for (ItemDelta itemDelta : itemDeltas) {
            itemDelta.applyTo(shadowAfter);
        }
        
        if (isRename(operations)){
			Collection<PropertyModificationOperation> renameOperations = distillRenameDeltas(itemDeltas, shadowAfter, objectClassDefinition);
			LOGGER.trace("Determining rename operation {}", renameOperations);
			sideEffectChanges.addAll(renameOperations);
		}

        // Execute entitlement modification on other objects (if needed)
		executeEntitlementChangesModify(ctx, shadowBefore, shadowAfter, scripts, itemDeltas, parentResult);
		
		parentResult.recordSuccess();
		return sideEffectChanges;
	}

	private Collection<PropertyModificationOperation> executeModify(ProvisioningContext ctx, 
			PrismObject<ShadowType> currentShadow, Collection<? extends ResourceAttribute<?>> identifiers, 
					Collection<Operation> operations, OperationResult parentResult) throws ObjectNotFoundException, CommunicationException, SchemaException, SecurityViolationException, ConfigurationException, ObjectAlreadyExistsException {
		Collection<PropertyModificationOperation> sideEffectChanges = new HashSet<>();

		RefinedObjectClassDefinition objectClassDefinition = ctx.getObjectClassDefinition();
		if (operations.isEmpty()){
			LOGGER.trace("No modifications for connector object. Skipping modification.");
			// TODO [mederly] shouldn't "return new HashSet<>()" be here?
		}
		
		// Invoke ICF
		ConnectorInstance connector = ctx.getConnector(parentResult);
		try {
			
			if (ResourceTypeUtil.isAvoidDuplicateValues(ctx.getResource())){

				if (currentShadow == null) {
					LOGGER.trace("Fetching shadow for duplicate filtering");
					currentShadow = getShadowToFilterDuplicates(ctx, identifiers, operations, false, parentResult);
				}
				
				Collection<Operation> filteredOperations = new ArrayList(operations.size());
				for (Operation origOperation: operations) {
					if (origOperation instanceof PropertyModificationOperation) {
						PropertyModificationOperation modificationOperation = (PropertyModificationOperation)origOperation;
						PropertyDelta<?> propertyDelta = modificationOperation.getPropertyDelta();
						PropertyDelta<?> filteredDelta = ProvisioningUtil.narrowPropertyDelta(propertyDelta, currentShadow,
								modificationOperation.getMatchingRuleQName(), matchingRuleRegistry);
						if (filteredDelta != null && !filteredDelta.isEmpty()) {
							if (propertyDelta == filteredDelta) {
								filteredOperations.add(origOperation);
							} else if (filteredDelta == null || filteredDelta.isEmpty()) {
									// nothing to do
							} else {
								PropertyModificationOperation newOp = new PropertyModificationOperation(filteredDelta);
								newOp.setMatchingRuleQName(modificationOperation.getMatchingRuleQName());
								filteredOperations.add(newOp);
							}
						}
					} else if (origOperation instanceof ExecuteProvisioningScriptOperation){
						filteredOperations.add(origOperation);					
					}
				}
				if (filteredOperations.isEmpty()){
					LOGGER.debug("No modifications for connector object specified (after filtering). Skipping processing.");
					parentResult.recordSuccess();
					return new HashSet<PropertyModificationOperation>();
				}
				operations = filteredOperations;
			}
			
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(
						"PROVISIONING MODIFY operation on {}\n MODIFY object, object class {}, identified by:\n{}\n changes:\n{}",
						new Object[] { ctx.getResource(), PrettyPrinter.prettyPrint(objectClassDefinition.getTypeName()),
								SchemaDebugUtil.debugDump(identifiers,1), SchemaDebugUtil.debugDump(operations,1) });
			}
			
			if (!ResourceTypeUtil.hasUpdateCapability(ctx.getResource())){
				if (operations == null || operations.isEmpty()){
					LOGGER.debug("No modifications for connector object specified (after filtering). Skipping processing.");
					parentResult.recordSuccess();
					return new HashSet<PropertyModificationOperation>();
				}
				throw new UnsupportedOperationException("Resource does not support 'update' operation");
			}
			
			Collection<ResourceAttribute<?>> identifiersWorkingCopy = cloneIdentifiers(identifiers);			// because identifiers can be modified e.g. on rename operation
			List<Collection<Operation>> operationsWaves = sortOperationsIntoWaves(operations, objectClassDefinition);
			LOGGER.trace("Operation waves: {}", operationsWaves.size());
			for (Collection<Operation> operationsWave : operationsWaves) {
				Collection<RefinedAttributeDefinition> readReplaceAttributes = determineReadReplace(operationsWave, objectClassDefinition);
				LOGGER.trace("Read+Replace attributes: {}", readReplaceAttributes);
				if (!readReplaceAttributes.isEmpty()) {
					AttributesToReturn attributesToReturn = new AttributesToReturn();
					attributesToReturn.setReturnDefaultAttributes(false);
					attributesToReturn.setAttributesToReturn(readReplaceAttributes);
					// TODO eliminate this fetch if this is first wave and there are no explicitly requested attributes
					// but make sure currentShadow contains all required attributes
					LOGGER.trace("Fetching object because of READ+REPLACE mode");
					currentShadow = fetchResourceObject(ctx, 
							identifiersWorkingCopy, attributesToReturn, false, parentResult);
					operationsWave = convertToReplace(ctx, operationsWave, currentShadow);
				}
				if (!operationsWave.isEmpty()) {
					Collection<PropertyModificationOperation> sideEffects =
							connector.modifyObject(objectClassDefinition, identifiersWorkingCopy, operationsWave, parentResult);
					sideEffectChanges.addAll(sideEffects);
					// we accept that one attribute can be changed multiple times in sideEffectChanges; TODO: normalize
				}
			}

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("PROVISIONING MODIFY successful, side-effect changes {}",
						SchemaDebugUtil.debugDump(sideEffectChanges));
			}

		} catch (ObjectNotFoundException ex) {
			parentResult.recordFatalError("Object to modify not found: " + ex.getMessage(), ex);
			throw new ObjectNotFoundException("Object to modify not found: " + ex.getMessage(), ex);
		} catch (CommunicationException ex) {
			parentResult.recordFatalError(
					"Error communicating with the connector " + connector + ": " + ex.getMessage(), ex);
			throw new CommunicationException("Error communicating with connector " + connector + ": "
					+ ex.getMessage(), ex);
		} catch (SchemaException ex) {
			parentResult.recordFatalError("Schema violation: " + ex.getMessage(), ex);
			throw new SchemaException("Schema violation: " + ex.getMessage(), ex);
		} catch (SecurityViolationException ex) {
			parentResult.recordFatalError("Security violation: " + ex.getMessage(), ex);
			throw new SecurityViolationException("Security violation: " + ex.getMessage(), ex);
		} catch (GenericFrameworkException ex) {
			parentResult.recordFatalError(
					"Generic error in the connector " + connector + ": " + ex.getMessage(), ex);
			throw new GenericConnectorException("Generic error in connector connector " + connector + ": "
					+ ex.getMessage(), ex);
		} catch (ConfigurationException ex) {
			parentResult.recordFatalError("Configuration error: " + ex.getMessage(), ex);
			throw new ConfigurationException("Configuration error: " + ex.getMessage(), ex);
		} catch (ObjectAlreadyExistsException ex) {
			parentResult.recordFatalError("Conflict during modify: " + ex.getMessage(), ex);
			throw new ObjectAlreadyExistsException("Conflict during modify: " + ex.getMessage(), ex);
		}
		
		return sideEffectChanges;
	}

	private PrismObject<ShadowType> getShadowToFilterDuplicates(ProvisioningContext ctx, 
			Collection<? extends ResourceAttribute<?>> identifiers, 
			Collection<Operation> operations, boolean fetchEntitlements, OperationResult parentResult) 
					throws ObjectNotFoundException, CommunicationException, SchemaException, SecurityViolationException, ConfigurationException {
		PrismObject<ShadowType> currentShadow;
		List<RefinedAttributeDefinition> neededExtraAttributes = new ArrayList<>();
		for (Operation operation : operations) {
            RefinedAttributeDefinition rad = getRefinedAttributeDefinitionIfApplicable(operation, ctx.getObjectClassDefinition());
            if (rad != null && (!rad.isReturnedByDefault() || rad.getFetchStrategy() == AttributeFetchStrategyType.EXPLICIT)) {
                neededExtraAttributes.add(rad);
            }
        }

		AttributesToReturn attributesToReturn = new AttributesToReturn();
		attributesToReturn.setAttributesToReturn(neededExtraAttributes);
		currentShadow = fetchResourceObject(ctx, identifiers, 
				attributesToReturn, fetchEntitlements, parentResult);
		return currentShadow;
	}

	private Collection<RefinedAttributeDefinition> determineReadReplace(Collection<Operation> operations, RefinedObjectClassDefinition objectClassDefinition) {
		Collection<RefinedAttributeDefinition> retval = new ArrayList<>();
		for (Operation operation : operations) {
			RefinedAttributeDefinition rad = getRefinedAttributeDefinitionIfApplicable(operation, objectClassDefinition);
			if (rad != null && isReadReplaceMode(rad, objectClassDefinition) && operation instanceof PropertyModificationOperation) {		// third condition is just to be sure
				PropertyDelta propertyDelta = ((PropertyModificationOperation) operation).getPropertyDelta();
				if (propertyDelta.isAdd() || propertyDelta.isDelete()) {
					retval.add(rad);		// REPLACE operations are not needed to be converted to READ+REPLACE
				}
			}
		}
		return retval;
	}

	private boolean isReadReplaceMode(RefinedAttributeDefinition rad, RefinedObjectClassDefinition objectClassDefinition) {
		if (rad.getReadReplaceMode() != null) {
			return rad.getReadReplaceMode();
		}
		// READ+REPLACE mode is if addRemoveAttributeCapability is NOT present
		return objectClassDefinition.getEffectiveCapability(AddRemoveAttributeValuesCapabilityType.class) == null;
	}

	private RefinedAttributeDefinition getRefinedAttributeDefinitionIfApplicable(Operation operation, RefinedObjectClassDefinition objectClassDefinition) {
		if (operation instanceof PropertyModificationOperation) {
			PropertyDelta propertyDelta = ((PropertyModificationOperation) operation).getPropertyDelta();
			if (isAttributeDelta(propertyDelta)) {
				QName attributeName = propertyDelta.getElementName();
				return objectClassDefinition.findAttributeDefinition(attributeName);
			}
		}
		return null;
	}

	/**
	 *  Converts ADD/DELETE VALUE operations into REPLACE VALUE, if needed
	 */
	private Collection<Operation> convertToReplace(ProvisioningContext ctx, Collection<Operation> operations, PrismObject<ShadowType> currentShadow) throws SchemaException, ConfigurationException, ObjectNotFoundException, CommunicationException {
		List<Operation> retval = new ArrayList<>(operations.size());
		for (Operation operation : operations) {
			if (operation instanceof PropertyModificationOperation) {
				PropertyDelta propertyDelta = ((PropertyModificationOperation) operation).getPropertyDelta();
				if (isAttributeDelta(propertyDelta)) {
					QName attributeName = propertyDelta.getElementName();
					RefinedAttributeDefinition rad = ctx.getObjectClassDefinition().findAttributeDefinition(attributeName);
					if (isReadReplaceMode(rad, ctx.getObjectClassDefinition()) && (propertyDelta.isAdd() || propertyDelta.isDelete())) {
						PropertyModificationOperation newOp = convertToReplace(propertyDelta, currentShadow, rad.getMatchingRuleQName());
						newOp.setMatchingRuleQName(((PropertyModificationOperation) operation).getMatchingRuleQName());
						retval.add(newOp);
						continue;
					}

				}
			}
			retval.add(operation);		// for yet-unprocessed operations
		}
		return retval;
	}

	private PropertyModificationOperation convertToReplace(PropertyDelta<?> propertyDelta, PrismObject<ShadowType> currentShadow, QName matchingRuleQName) throws SchemaException {
		if (propertyDelta.isReplace()) {
			// this was probably checked before
			throw new IllegalStateException("PropertyDelta is both ADD/DELETE and REPLACE");
		}
		// let's extract (parent-less) current values
		PrismProperty<?> currentProperty = currentShadow.findProperty(propertyDelta.getPath());
		Collection<PrismPropertyValue> currentValues = new ArrayList<>();
		if (currentProperty != null) {
			for (PrismPropertyValue currentValue : currentProperty.getValues()) {
				currentValues.add(currentValue.clone());
			}
		}
		final MatchingRule matchingRule;
		if (matchingRuleQName != null) {
			ItemDefinition def = propertyDelta.getDefinition();
			QName typeName;
			if (def != null) {
				typeName = def.getTypeName();
			} else {
				typeName = null;		// we'll skip testing rule fitness w.r.t type
			}
			matchingRule = matchingRuleRegistry.getMatchingRule(matchingRuleQName, typeName);
		} else {
			matchingRule = null;
		}
		Comparator comparator = new Comparator<PrismPropertyValue<?>>() {
			@Override
			public int compare(PrismPropertyValue<?> o1, PrismPropertyValue<?> o2) {
				if (o1.equalsComplex(o2, true, false, matchingRule)) {
					return 0;
				} else {
					return 1;
				}
			}
		};
		// add values that have to be added
		if (propertyDelta.isAdd()) {
			for (PrismPropertyValue valueToAdd : propertyDelta.getValuesToAdd()) {
				if (!PrismPropertyValue.containsValue(currentValues, valueToAdd, comparator)) {
					currentValues.add(valueToAdd.clone());
				} else {
					LOGGER.warn("Attempting to add a value of {} that is already present in {}: {}",
							new Object[]{valueToAdd, propertyDelta.getElementName(), currentValues});
				}
			}
		}
		// remove values that should not be there
		if (propertyDelta.isDelete()) {
			for (PrismPropertyValue valueToDelete : propertyDelta.getValuesToDelete()) {
				Iterator<PrismPropertyValue> iterator = currentValues.iterator();
				boolean found = false;
				while (iterator.hasNext()) {
					PrismPropertyValue pValue = iterator.next();
					LOGGER.trace("Comparing existing {} to about-to-be-deleted {}, matching rule: {}", new Object[]{pValue, valueToDelete, matchingRule});
					if (comparator.compare(pValue, valueToDelete) == 0) {
						LOGGER.trace("MATCH! compared existing {} to about-to-be-deleted {}", pValue, valueToDelete);
						iterator.remove();
						found = true;
					}
				}
				if (!found) {
					LOGGER.warn("Attempting to remove a value of {} that is not in {}: {}",
							new Object[]{valueToDelete, propertyDelta.getElementName(), currentValues});
				}
			}
		}
		PropertyDelta resultingDelta = new PropertyDelta(propertyDelta.getPath(), propertyDelta.getPropertyDefinition(), propertyDelta.getPrismContext());
		resultingDelta.setValuesToReplace(currentValues);
		return new PropertyModificationOperation(resultingDelta);
	}

	private List<Collection<Operation>> sortOperationsIntoWaves(Collection<Operation> operations, RefinedObjectClassDefinition objectClassDefinition) {
		TreeMap<Integer,Collection<Operation>> waves = new TreeMap<>();	// operations indexed by priority
		List<Operation> others = new ArrayList<>();					// operations executed at the end (either non-priority ones or non-attribute modifications)
		for (Operation operation : operations) {
			RefinedAttributeDefinition rad = getRefinedAttributeDefinitionIfApplicable(operation, objectClassDefinition);
			if (rad != null && rad.getModificationPriority() != null) {
				putIntoWaves(waves, rad.getModificationPriority(), operation);
				continue;
			}
			others.add(operation);
		}
		// computing the return value
		List<Collection<Operation>> retval = new ArrayList<>(waves.size()+1);
		Map.Entry<Integer,Collection<Operation>> entry = waves.firstEntry();
		while (entry != null) {
			retval.add(entry.getValue());
			entry = waves.higherEntry(entry.getKey());
		}
		retval.add(others);
		return retval;
	}

	private void putIntoWaves(Map<Integer, Collection<Operation>> waves, Integer key, Operation operation) {
		Collection<Operation> wave = waves.get(key);
		if (wave == null) {
			wave = new ArrayList<>();
			waves.put(key, wave);
		}
		wave.add(operation);
	}

	private Collection<ResourceAttribute<?>> cloneIdentifiers(Collection<? extends ResourceAttribute<?>> identifiers) {
		Collection<ResourceAttribute<?>> retval = new HashSet<>(identifiers.size());
		for (ResourceAttribute<?> identifier : identifiers) {
			retval.add(identifier.clone());
		}
		return retval;
	}

	private boolean isRename(Collection<Operation> modifications){
		for (Operation op : modifications){
			if (!(op instanceof PropertyModificationOperation)){
				continue;
			}
			
			if (((PropertyModificationOperation)op).getPropertyDelta().getPath().equivalent(new ItemPath(ShadowType.F_ATTRIBUTES, ConnectorFactoryIcfImpl.ICFS_NAME))){
				return true;
			}
		}
		return false;
	}
	
	private boolean isRename(ItemDelta itemDelta){
		
		if (!(itemDelta instanceof PropertyDelta)){
			return false;
		}
		
		if (itemDelta.getPath().equivalent(new ItemPath(ShadowType.F_ATTRIBUTES, ConnectorFactoryIcfImpl.ICFS_NAME))){
			return true;
		}
		return false;
	}
	

	private Collection<PropertyModificationOperation> distillRenameDeltas(Collection<? extends ItemDelta> modifications, 
			PrismObject<ShadowType> shadow, RefinedObjectClassDefinition objectClassDefinition) throws SchemaException {
				
		PropertyDelta<String> nameDelta = (PropertyDelta<String>) ItemDelta.findItemDelta(modifications, new ItemPath(ShadowType.F_ATTRIBUTES, ConnectorFactoryIcfImpl.ICFS_NAME), ItemDelta.class); 
		if (nameDelta == null){
			return null;
		}
				
//				PrismProperty<String> name = nameDelta.getPropertyNewMatchingPath();
//				String newName = name.getRealValue();
				
				Collection<PropertyModificationOperation> deltas = new ArrayList<PropertyModificationOperation>();
				
				// $shadow/attributes/icfs:name
//				String normalizedNewName = shadowManager.getNormalizedAttributeValue(name.getValue(), objectClassDefinition.findAttributeDefinition(name.getElementName()));
//				PropertyDelta<String> cloneNameDelta = nameDelta.clone();
//				cloneNameDelta.clearValuesToReplace();
//				cloneNameDelta.setValueToReplace(new PrismPropertyValue<String>(newName));
				PropertyModificationOperation operation = new PropertyModificationOperation(nameDelta.clone());
				// TODO matchingRuleQName handling - but it should not be necessary here
				deltas.add(operation);
				
				// $shadow/name
//				if (!newName.equals(shadow.asObjectable().getName().getOrig())){
					
					PropertyDelta<?> shadowNameDelta = PropertyDelta.createModificationReplaceProperty(ShadowType.F_NAME, shadow.getDefinition(), 
							ProvisioningUtil.determineShadowName(shadow));
					operation = new PropertyModificationOperation(shadowNameDelta);
		  			// TODO matchingRuleQName handling - but it should not be necessary here
					deltas.add(operation);
//				}
			
				return deltas;
		}
	
	private void executeEntitlementChangesAdd(ProvisioningContext ctx, PrismObject<ShadowType> shadow, OperationProvisioningScriptsType scripts,
			OperationResult parentResult) throws SchemaException, ObjectNotFoundException, CommunicationException, SecurityViolationException, ConfigurationException, ObjectAlreadyExistsException {
		
		Map<ResourceObjectDiscriminator, ResourceObjectOperations> roMap = new HashMap<>();
		
		entitlementConverter.collectEntitlementsAsObjectOperationInShadowAdd(ctx, roMap, shadow, parentResult);
		
		executeEntitlements(ctx, roMap, parentResult);
		
	}
	
	private void executeEntitlementChangesModify(ProvisioningContext ctx, PrismObject<ShadowType> subjectShadowBefore,
			PrismObject<ShadowType> subjectShadowAfter,
            OperationProvisioningScriptsType scripts, Collection<? extends ItemDelta> objectDeltas, OperationResult parentResult) throws SchemaException, ObjectNotFoundException, CommunicationException, SecurityViolationException, ConfigurationException, ObjectAlreadyExistsException {
		
		Map<ResourceObjectDiscriminator, ResourceObjectOperations> roMap = new HashMap<>();
		
		for (ItemDelta itemDelta : objectDeltas) {
			if (new ItemPath(ShadowType.F_ASSOCIATION).equivalent(itemDelta.getPath())) {
				ContainerDelta<ShadowAssociationType> containerDelta = (ContainerDelta<ShadowAssociationType>)itemDelta;				
				entitlementConverter.collectEntitlementsAsObjectOperation(ctx, roMap, containerDelta,
                        subjectShadowBefore, subjectShadowAfter, parentResult);
				
			} else if (isRename(itemDelta)) {
			
				ContainerDelta<ShadowAssociationType> associationDelta = ContainerDelta.createDelta(ShadowType.F_ASSOCIATION, subjectShadowBefore.getDefinition());
				PrismContainer<ShadowAssociationType> association = subjectShadowBefore.findContainer(ShadowType.F_ASSOCIATION);
				if (association == null || association.isEmpty()){
					LOGGER.trace("No shadow association container in old shadow. Skipping processing entitlements change.");
					continue;
				}

				// Delete + re-add association values that should ensure correct functioning in case of rename
				// This has to be done only for associations that require explicit referential integrity.
				// For these that do not, it is harmful (), so it must be skipped.
				for (PrismContainerValue<ShadowAssociationType> associationValue : association.getValues()) {
					QName associationName = associationValue.asContainerable().getName();
					if (associationName == null) {
						throw new IllegalStateException("No association name in " + associationValue);
					}
					LOGGER.trace("Processing association {} on rename", associationName);
					RefinedAssociationDefinition associationDefinition = ctx.getObjectClassDefinition().findAssociation(associationName);
					if (associationDefinition == null) {
						throw new IllegalStateException("No association definition for " + associationValue);
					}
					if (associationDefinition.requiresExplicitReferentialIntegrity()) {
						associationDelta.addValuesToDelete(associationValue.clone());
						associationDelta.addValuesToAdd(associationValue.clone());
					}
				}
				if (!associationDelta.isEmpty()) {
					entitlementConverter.collectEntitlementsAsObjectOperation(ctx, roMap, associationDelta, subjectShadowBefore, subjectShadowAfter, parentResult);
				}
				
//				shadowAfter.findOrCreateContainer(ShadowType.F_ASSOCIATION).addAll((Collection) association.getClonedValues());
//				entitlementConverter.processEntitlementsAdd(resource, shadowAfter, objectClassDefinition);
			}
		}
		
		
		
		executeEntitlements(ctx, roMap, parentResult);
		
	}
	
	private void executeEntitlementChangesDelete(ProvisioningContext ctx, PrismObject<ShadowType> shadow, 
			OperationProvisioningScriptsType scripts,
			OperationResult parentResult) throws SchemaException  {
		
		try {
			
			Map<ResourceObjectDiscriminator, ResourceObjectOperations> roMap = new HashMap<>();
				
			entitlementConverter.collectEntitlementsAsObjectOperationDelete(ctx, roMap,
					shadow, parentResult);
		
			executeEntitlements(ctx, roMap, parentResult);
			
		// TODO: now just log the errors, but not NOT re-throw the exception (except for some exceptions)
		// we want the original delete to take place, throwing an exception would spoil that
		} catch (SchemaException e) {
			throw e;
		} catch (CommunicationException e) {
			LOGGER.error(e.getMessage(), e);
		} catch (ObjectNotFoundException e) {
			LOGGER.error(e.getMessage(), e);
		} catch (SecurityViolationException e) {
			LOGGER.error(e.getMessage(), e);
		} catch (ConfigurationException e) {
			LOGGER.error(e.getMessage(), e);
		} catch (ObjectAlreadyExistsException e) {
			LOGGER.error(e.getMessage(), e);
		}
		
	}
	
	private void executeEntitlements(ProvisioningContext subjectCtx,
			Map<ResourceObjectDiscriminator, ResourceObjectOperations> roMap, OperationResult parentResult) throws ObjectNotFoundException, CommunicationException, SchemaException, SecurityViolationException, ConfigurationException, ObjectAlreadyExistsException {
		for (Entry<ResourceObjectDiscriminator,ResourceObjectOperations> entry: roMap.entrySet()) {
			ResourceObjectDiscriminator disc = entry.getKey();
			ProvisioningContext entitlementCtx = entry.getValue().getResourceObjectContext();
			Collection<? extends ResourceAttribute<?>> identifiers = disc.getIdentifiers();
			Collection<Operation> operations = entry.getValue().getOperations();
			
			// TODO: better handling of result, partial failures, etc.
			executeModify(entitlementCtx, null, identifiers, operations, parentResult);
			
		}
	}

	public SearchResultMetadata searchResourceObjects(final ProvisioningContext ctx,
			final ResultHandler<ShadowType> resultHandler, ObjectQuery query, final boolean fetchAssociations,
            final OperationResult parentResult) throws SchemaException,
			CommunicationException, ObjectNotFoundException, ConfigurationException, SecurityViolationException {
		RefinedObjectClassDefinition objectClassDef = ctx.getObjectClassDefinition();
		AttributesToReturn attributesToReturn = ProvisioningUtil.createAttributesToReturn(ctx);
		SearchHierarchyConstraints searchHierarchyConstraints = null;
		ResourceObjectReferenceType baseContextRef = objectClassDef.getBaseContext();
		if (baseContextRef != null) {
			PrismObject<ShadowType> baseContextShadow = resourceObjectReferenceResolver.resolve(baseContextRef, "base context specification in "+objectClassDef, parentResult);
			RefinedObjectClassDefinition baseContextObjectClassDefinition = ctx.getRefinedSchema().determineCompositeObjectClassDefinition(baseContextShadow);
			ResourceObjectIdentification baseContextIdentification = new ResourceObjectIdentification(baseContextObjectClassDefinition, ShadowUtil.getIdentifiers(baseContextShadow));
			searchHierarchyConstraints = new SearchHierarchyConstraints(baseContextIdentification, null);
		}

		ResultHandler<ShadowType> innerResultHandler = new ResultHandler<ShadowType>() {
			@Override
			public boolean handle(PrismObject<ShadowType> shadow) {
				// in order to utilize the cache right from the beginning...
				RepositoryCache.enter();
				try {
					try {
						shadow = postProcessResourceObjectRead(ctx, shadow, fetchAssociations, parentResult);
					} catch (SchemaException e) {
						throw new TunnelException(e);
					} catch (CommunicationException e) {
						throw new TunnelException(e);
					} catch (ObjectNotFoundException e) {
						throw new TunnelException(e);
					} catch (ConfigurationException e) {
						throw new TunnelException(e);
					} catch (SecurityViolationException e) {
						throw new TunnelException(e);
					}
					return resultHandler.handle(shadow);
				} finally {
					RepositoryCache.exit();
				}
			}
		};
		
		ConnectorInstance connector = ctx.getConnector(parentResult);
		SearchResultMetadata metadata = null;
		try {
			metadata = connector.search(objectClassDef, query, innerResultHandler, attributesToReturn, 
					objectClassDef.getPagedSearches(), searchHierarchyConstraints, parentResult);
		} catch (GenericFrameworkException e) {
			parentResult.recordFatalError("Generic error in the connector: " + e.getMessage(), e);
			throw new SystemException("Generic error in the connector: " + e.getMessage(), e);

		} catch (CommunicationException ex) {
			parentResult.recordFatalError(
					"Error communicating with the connector " + connector + ": " + ex.getMessage(), ex);
			throw new CommunicationException("Error communicating with the connector " + connector + ": "
					+ ex.getMessage(), ex);
		} catch (SecurityViolationException ex) {
			parentResult.recordFatalError(
					"Security violation communicating with the connector " + connector + ": " + ex.getMessage(), ex);
			throw new SecurityViolationException("Security violation communicating with the connector " + connector + ": "
					+ ex.getMessage(), ex);
		} catch (TunnelException e) {
			Throwable cause = e.getCause();
			if (cause instanceof SchemaException) {
				throw (SchemaException)cause;
			} else if (cause instanceof CommunicationException) {
				throw (CommunicationException)cause;
			} else if (cause instanceof ObjectNotFoundException) {
				throw (ObjectNotFoundException)cause;
			} else if (cause instanceof ConfigurationException) {
				throw (ConfigurationException)cause;
			} else if (cause instanceof SecurityViolationException) {
				throw (SecurityViolationException)cause;
			} if (cause instanceof GenericFrameworkException) {
				new GenericConnectorException(cause.getMessage(), cause);
			} else {
				new SystemException(cause.getMessage(), cause);
			}
		}

		parentResult.recordSuccess();
		return metadata;
	}

	@SuppressWarnings("rawtypes")
	public PrismProperty fetchCurrentToken(ProvisioningContext ctx, OperationResult parentResult)
			throws ObjectNotFoundException, CommunicationException, SchemaException, ConfigurationException {
		Validate.notNull(parentResult, "Operation result must not be null.");

		PrismProperty lastToken = null;
		ConnectorInstance connector = ctx.getConnector(parentResult);
		try {
			lastToken = connector.fetchCurrentToken(ctx.getObjectClassDefinition(), parentResult);
		} catch (GenericFrameworkException e) {
			parentResult.recordFatalError("Generic error in the connector: " + e.getMessage(), e);
			throw new CommunicationException("Generic error in the connector: " + e.getMessage(), e);

		} catch (CommunicationException ex) {
			parentResult.recordFatalError(
					"Error communicating with the connector " + connector + ": " + ex.getMessage(), ex);
			throw new CommunicationException("Error communicating with the connector " + connector + ": "
					+ ex.getMessage(), ex);
		}

		LOGGER.trace("Got last token: {}", SchemaDebugUtil.prettyPrint(lastToken));
		parentResult.recordSuccess();
		return lastToken;
	}


	private PrismObject<ShadowType> fetchResourceObject(ProvisioningContext ctx,
			Collection<? extends ResourceAttribute<?>> identifiers, 
			AttributesToReturn attributesToReturn,
			boolean fetchAssociations,
			OperationResult parentResult) throws ObjectNotFoundException,
			CommunicationException, SchemaException, SecurityViolationException, ConfigurationException {

		PrismObject<ShadowType> resourceObject = resourceObjectReferenceResolver.fetchResourceObject(ctx, identifiers, attributesToReturn, parentResult);
		return postProcessResourceObjectRead(ctx, resourceObject, fetchAssociations, parentResult);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void applyAfterOperationAttributes(PrismObject<ShadowType> shadow,
			Collection<ResourceAttribute<?>> resourceAttributesAfterAdd) throws SchemaException {
		ResourceAttributeContainer attributesContainer = ShadowUtil
				.getAttributesContainer(shadow);
		for (ResourceAttribute attributeAfter : resourceAttributesAfterAdd) {
			ResourceAttribute attributeBefore = attributesContainer.findAttribute(attributeAfter.getElementName());
			if (attributeBefore != null) {
				attributesContainer.remove(attributeBefore);
			}
			if (!attributesContainer.contains(attributeAfter)) {
				attributesContainer.add(attributeAfter.clone());
			}
		}
	}

	private Collection<Operation> determineActivationChange(ProvisioningContext ctx, ShadowType shadow, Collection<? extends ItemDelta> objectChange,
			OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException {
		ResourceType resource = ctx.getResource();
		Collection<Operation> operations = new ArrayList<Operation>();
		
		ActivationCapabilityType activationCapabilityType = ResourceTypeUtil.getEffectiveCapability(resource, ActivationCapabilityType.class);
		
		// administrativeStatus
		PropertyDelta<ActivationStatusType> enabledPropertyDelta = PropertyDelta.findPropertyDelta(objectChange,
				SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS);
		if (enabledPropertyDelta != null) {
			if (activationCapabilityType == null) {
				throw new SchemaException("Attempt to change activation administrativeStatus on "+resource+" which does not have the capability");
			}
			ActivationStatusType status = enabledPropertyDelta.getPropertyNewMatchingPath().getRealValue();
			LOGGER.trace("Found activation administrativeStatus change to: {}", status);
	
//			if (status != null) {
	
				if (ResourceTypeUtil.hasResourceNativeActivationCapability(resource)) {
					// Native activation, need to check if there is not also change to simulated activation which may be in conflict
					checkSimulatedActivationAdministrativeStatus(ctx, objectChange, status, shadow, result);
					operations.add(new PropertyModificationOperation(enabledPropertyDelta));
				} else {
					// Try to simulate activation capability
					PropertyModificationOperation activationAttribute = convertToSimulatedActivationAdministrativeStatusAttribute(ctx, enabledPropertyDelta, shadow,
							status, result);
					if (activationAttribute != null) {
						operations.add(activationAttribute);
					}
				}	
//			}
		}
		
		// validFrom
		PropertyDelta<XMLGregorianCalendar> validFromPropertyDelta = PropertyDelta.findPropertyDelta(objectChange,
				SchemaConstants.PATH_ACTIVATION_VALID_FROM);
		if (validFromPropertyDelta != null) {
			if (activationCapabilityType == null || activationCapabilityType.getValidFrom() == null) {
				throw new SchemaException("Attempt to change activation validFrom on "+resource+" which does not have the capability");
			}
			XMLGregorianCalendar xmlCal = validFromPropertyDelta.getPropertyNewMatchingPath().getRealValue();
			LOGGER.trace("Found activation validFrom change to: {}", xmlCal);
			operations.add(new PropertyModificationOperation(validFromPropertyDelta));
		}

		// validTo
		PropertyDelta<XMLGregorianCalendar> validToPropertyDelta = PropertyDelta.findPropertyDelta(objectChange,
				SchemaConstants.PATH_ACTIVATION_VALID_TO);
		if (validToPropertyDelta != null) {
			if (activationCapabilityType == null || activationCapabilityType.getValidTo() == null) {
				throw new SchemaException("Attempt to change activation validTo on "+resource+" which does not have the capability");
			}
			XMLGregorianCalendar xmlCal = validToPropertyDelta.getPropertyNewMatchingPath().getRealValue();
			LOGGER.trace("Found activation validTo change to: {}", xmlCal);
				operations.add(new PropertyModificationOperation(validToPropertyDelta));
		}
		
		PropertyDelta<LockoutStatusType> lockoutPropertyDelta = PropertyDelta.findPropertyDelta(objectChange,
				SchemaConstants.PATH_ACTIVATION_LOCKOUT_STATUS);
		if (lockoutPropertyDelta != null) {
			if (activationCapabilityType == null) {
				throw new SchemaException("Attempt to change activation lockoutStatus on "+resource+" which does not have the capability");
			}
			LockoutStatusType status = lockoutPropertyDelta.getPropertyNewMatchingPath().getRealValue();
			LOGGER.trace("Found activation lockoutStatus change to: {}", status);

			if (ResourceTypeUtil.hasResourceNativeActivationLockoutCapability(resource)) {
				// Native lockout, need to check if there is not also change to simulated activation which may be in conflict
				checkSimulatedActivationLockoutStatus(ctx, objectChange, status, shadow, result);
				operations.add(new PropertyModificationOperation(lockoutPropertyDelta));
			} else {
				// Try to simulate lockout capability
				PropertyModificationOperation activationAttribute = convertToSimulatedActivationLockoutStatusAttribute(
						ctx, lockoutPropertyDelta, shadow, status, result);
				operations.add(activationAttribute);
			}	
		}
		
		return operations;
	}
	
	private void checkSimulatedActivationAdministrativeStatus(ProvisioningContext ctx, 
			Collection<? extends ItemDelta> objectChange, ActivationStatusType status, 
			ShadowType shadow, OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException{
		if (!ResourceTypeUtil.hasResourceConfiguredActivationCapability(ctx.getResource())) {
			//nothing to do, resource does not have simulated activation, so there can be no conflict, continue in processing
			return;
		}
		
		ActivationStatusCapabilityType capActStatus = getActivationAdministrativeStatusFromSimulatedActivation(ctx, shadow, result);
		ResourceAttribute<?> activationAttribute = getSimulatedActivationAdministrativeStatusAttribute(ctx, shadow, 
				capActStatus, result);
		if (activationAttribute == null){
			return;
		}
		
		PropertyDelta simulatedActivationDelta = PropertyDelta.findPropertyDelta(objectChange, activationAttribute.getPath());
		PrismProperty simulatedAcviationProperty = simulatedActivationDelta.getPropertyNewMatchingPath();
		Collection realValues = simulatedAcviationProperty.getRealValues();
		if (realValues.isEmpty()){
			//nothing to do, no value for simulatedActivation
			return;
		}
		
		if (realValues.size() > 1){
			throw new SchemaException("Found more than one value for simulated activation.");
		}
		
		Object simluatedActivationValue = realValues.iterator().next();
		boolean transformedValue = getTransformedValue(ctx, shadow, simluatedActivationValue, result);
		
		if (transformedValue && status == ActivationStatusType.ENABLED){
			//this is ok, simulated value and also value for native capability resulted to the same vale
		} else{
			throw new SchemaException("Found conflicting change for activation. Simulated activation resulted to " + transformedValue +", but native activation resulted to " + status);
		}
		
	}
	
	private void checkSimulatedActivationLockoutStatus(ProvisioningContext ctx,
			Collection<? extends ItemDelta> objectChange, LockoutStatusType status, ShadowType shadow, OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException{
		if (!ResourceTypeUtil.hasResourceConfiguredActivationCapability(ctx.getResource())) {
			//nothing to do, resource does not have simulated activation, so there can be no conflict, continue in processing
			return;
		}
		
		ActivationLockoutStatusCapabilityType capActStatus = getActivationLockoutStatusFromSimulatedActivation(ctx, shadow, result);
		ResourceAttribute<?> activationAttribute = getSimulatedActivationLockoutStatusAttribute(ctx, shadow, capActStatus, result);
		if (activationAttribute == null){
			return;
		}
		
		PropertyDelta simulatedActivationDelta = PropertyDelta.findPropertyDelta(objectChange, activationAttribute.getPath());
		PrismProperty simulatedAcviationProperty = simulatedActivationDelta.getPropertyNewMatchingPath();
		Collection realValues = simulatedAcviationProperty.getRealValues();
		if (realValues.isEmpty()){
			//nothing to do, no value for simulatedActivation
			return;
		}
		
		if (realValues.size() > 1){
			throw new SchemaException("Found more than one value for simulated lockout.");
		}
		
		Object simluatedActivationValue = realValues.iterator().next();
		boolean transformedValue = getTransformedValue(ctx, shadow, simluatedActivationValue, result);
		
		if (transformedValue && status == LockoutStatusType.NORMAL){
			//this is ok, simulated value and also value for native capability resulted to the same vale
		} else{
			throw new SchemaException("Found conflicting change for activation lockout. Simulated lockout resulted to " + transformedValue +", but native activation resulted to " + status);
		}
		
	}
	
	private boolean getTransformedValue(ProvisioningContext ctx, ShadowType shadow, Object simulatedValue, OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException{
		ActivationStatusCapabilityType capActStatus = getActivationAdministrativeStatusFromSimulatedActivation(ctx, shadow, result);
		List<String> disableValues = capActStatus.getDisableValue();
		for (String disable : disableValues){
			if (disable.equals(simulatedValue)){
				return false; 
			}
		}
		
		List<String> enableValues = capActStatus.getEnableValue();
		for (String enable : enableValues){
			if (enable.equals(simulatedValue)){
				return true;
			}
		}
		
		throw new SchemaException("Could not map value for simulated activation: " + simulatedValue + " neither to enable nor disable values.");		
	}
	
	private void transformActivationAttributes(ProvisioningContext ctx, ShadowType shadow,
			OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException {
		if (shadow.getActivation() != null && shadow.getActivation().getAdministrativeStatus() != null) {
			if (!ResourceTypeUtil.hasResourceNativeActivationCapability(ctx.getResource())) {
				ActivationStatusCapabilityType capActStatus = getActivationAdministrativeStatusFromSimulatedActivation(
						ctx, shadow, result);
				if (capActStatus == null) {
					throw new SchemaException("Attempt to change activation/administrativeStatus on "+ctx.getResource()+" that has neither native" +
							" nor simulated activation capability");
				}
				ResourceAttribute<?> activationSimulateAttribute = getSimulatedActivationAdministrativeStatusAttribute(ctx, shadow,
						capActStatus, result);
				if (activationSimulateAttribute != null) {
					ActivationStatusType status = shadow.getActivation().getAdministrativeStatus();
					String activationRealValue = null;
					if (status == ActivationStatusType.ENABLED) {
						activationRealValue = getEnableValue(capActStatus);
					} else {
						activationRealValue = getDisableValue(capActStatus);
					}
					PrismContainer attributesContainer = shadow.asPrismObject().findContainer(ShadowType.F_ATTRIBUTES);
					Item existingAttribute = attributesContainer.findItem(activationSimulateAttribute.getElementName());
					if (!StringUtils.isBlank(activationRealValue)) {
						activationSimulateAttribute.add(new PrismPropertyValue(activationRealValue));
						if (attributesContainer.findItem(activationSimulateAttribute.getElementName()) == null){
							attributesContainer.add(activationSimulateAttribute);
						} else{
							attributesContainer.findItem(activationSimulateAttribute.getElementName()).replace(activationSimulateAttribute.getValue());
						}
					} else if (existingAttribute != null) {
						attributesContainer.remove(existingAttribute);
					}
					shadow.getActivation().setAdministrativeStatus(null);
				}
			}
		}
		
		if (shadow.getActivation() != null && shadow.getActivation().getLockoutStatus() != null) {
			if (!ResourceTypeUtil.hasResourceNativeActivationCapability(ctx.getResource())) {
				ActivationLockoutStatusCapabilityType capActStatus = getActivationLockoutStatusFromSimulatedActivation(
						ctx, shadow, result);
				if (capActStatus == null) {
					throw new SchemaException("Attempt to change activation/lockout on "+ctx.getResource()+" that has neither native" +
							" nor simulated activation capability");
				}
				ResourceAttribute<?> activationSimulateAttribute = getSimulatedActivationLockoutStatusAttribute(ctx, shadow,
						capActStatus, result);
				
				if (activationSimulateAttribute != null) {
					LockoutStatusType status = shadow.getActivation().getLockoutStatus();
					String activationRealValue = null;
					if (status == LockoutStatusType.NORMAL) {
						activationRealValue = getLockoutNormalValue(capActStatus);
					} else {
						activationRealValue = getLockoutLockedValue(capActStatus);
					}
					PrismContainer attributesContainer = shadow.asPrismObject().findContainer(ShadowType.F_ATTRIBUTES);
					Item existingAttribute = attributesContainer.findItem(activationSimulateAttribute.getElementName());
					if (!StringUtils.isBlank(activationRealValue)) {
						activationSimulateAttribute.add(new PrismPropertyValue(activationRealValue));
						if (attributesContainer.findItem(activationSimulateAttribute.getElementName()) == null){
							attributesContainer.add(activationSimulateAttribute);
						} else{
							attributesContainer.findItem(activationSimulateAttribute.getElementName()).replace(activationSimulateAttribute.getValue());
						}
					} else if (existingAttribute != null) {
						attributesContainer.remove(existingAttribute);
					}
					shadow.getActivation().setLockoutStatus(null);
				}
			}
		}
	}
	
	private boolean hasChangesOnResource(
			Collection<? extends ItemDelta> itemDeltas) {
		for (ItemDelta itemDelta : itemDeltas) {
			if (isAttributeDelta(itemDelta) || SchemaConstants.PATH_PASSWORD.equals(itemDelta.getParentPath())) {
				return true;
			} else if (SchemaConstants.PATH_ACTIVATION.equivalent(itemDelta.getParentPath())){
				return true;
			} else if (new ItemPath(ShadowType.F_ASSOCIATION).equivalent(itemDelta.getPath())) {
				return true;				
			}
		}
		return false;
	}


	private void collectAttributeAndEntitlementChanges(ProvisioningContext ctx, 
			Collection<? extends ItemDelta> objectChange, Collection<Operation> operations, 
			PrismObject<ShadowType> shadow, OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException {
		if (operations == null) {
			operations = new ArrayList<Operation>();
		}
		for (ItemDelta itemDelta : objectChange) {
			if (isAttributeDelta(itemDelta) || SchemaConstants.PATH_PASSWORD.equivalent(itemDelta.getParentPath())) {
				if (itemDelta instanceof PropertyDelta) {
					PropertyModificationOperation attributeModification = new PropertyModificationOperation(
							(PropertyDelta) itemDelta);
					operations.add(attributeModification);
				} else if (itemDelta instanceof ContainerDelta) {
					// skip the container delta - most probably password change
					// - it is processed earlier
					continue;
				} else {
					throw new UnsupportedOperationException("Not supported delta: " + itemDelta);
				}
			} else if (SchemaConstants.PATH_ACTIVATION.equivalent(itemDelta.getParentPath())){
				Collection<Operation> activationOperations = determineActivationChange(ctx, shadow.asObjectable(), objectChange, result);
				if (activationOperations != null){
					operations.addAll(activationOperations);
				}
			} else if (new ItemPath(ShadowType.F_ASSOCIATION).equivalent(itemDelta.getPath())) {
				if (itemDelta instanceof ContainerDelta) {
					entitlementConverter.collectEntitlementChange(ctx, (ContainerDelta<ShadowAssociationType>)itemDelta, operations);
				} else {
					throw new UnsupportedOperationException("Not supported delta: " + itemDelta);
				}				
			} else {
				LOGGER.trace("Skip converting item delta: {}. It's not resource object change, but it is shadow change.", itemDelta);	
			}
			
		}
	}

	private boolean isAttributeDelta(ItemDelta itemDelta) {
		return new ItemPath(ShadowType.F_ATTRIBUTES).equivalent(itemDelta.getParentPath());
	}

	public List<Change<ShadowType>> fetchChanges(ProvisioningContext ctx, PrismProperty<?> lastToken,
			OperationResult parentResult) throws SchemaException,
			CommunicationException, ConfigurationException, SecurityViolationException, GenericFrameworkException, ObjectNotFoundException {
		Validate.notNull(parentResult, "Operation result must not be null.");

		LOGGER.trace("START fetch changes, objectClass: {}", ctx.getObjectClassDefinition());
		AttributesToReturn attrsToReturn = null;
		if (!ctx.isWildcard()) {
			attrsToReturn = ProvisioningUtil.createAttributesToReturn(ctx);
		}
		
		ConnectorInstance connector = ctx.getConnector(parentResult);
		
		// get changes from the connector
		List<Change<ShadowType>> changes = connector.fetchChanges(ctx.getObjectClassDefinition(), lastToken, attrsToReturn, parentResult);

		Iterator<Change<ShadowType>> iterator = changes.iterator();
		while (iterator.hasNext()) {
			Change<ShadowType> change = iterator.next();
			
			ProvisioningContext shadowCtx = ctx;
			AttributesToReturn shadowAttrsToReturn = attrsToReturn;
			PrismObject<ShadowType> currentShadow = change.getCurrentShadow();
			if (ctx.isWildcard()) {
				shadowCtx = ctx.spawn(change.getObjectClassDefinition().getTypeName());
				if (shadowCtx.isWildcard()) {
					String message = "Unkown object class "+change.getObjectClassDefinition().getTypeName()+" found in synchronization delta";
					parentResult.recordFatalError(message);
					throw new SchemaException(message);
				}
				change.setObjectClassDefinition(shadowCtx.getObjectClassDefinition());
				
				shadowAttrsToReturn = ProvisioningUtil.createAttributesToReturn(shadowCtx);
			}
			
			if (currentShadow == null) {
				// There is no current shadow in a change. Add it by fetching it explicitly.
				if (change.getObjectDelta() == null || !change.getObjectDelta().isDelete()) {						
					// but not if it is a delete event
					try {
						
						currentShadow = fetchResourceObject(shadowCtx, 
								change.getIdentifiers(), shadowAttrsToReturn, true, parentResult);	// todo consider whether it is always necessary to fetch the entitlements
						change.setCurrentShadow(currentShadow);
						
					} catch (ObjectNotFoundException ex) {
						parentResult.recordHandledError(
								"Object detected in change log no longer exist on the resource. Skipping processing this object.", ex);
						LOGGER.warn("Object detected in change log no longer exist on the resource. Skipping processing this object "
								+ ex.getMessage());
						// TODO: Maybe change to DELETE instead of this?
						iterator.remove();
						continue;
					}
				}
			} else {
				if (ctx.isWildcard()) {
					if (!MiscUtil.equals(shadowAttrsToReturn, attrsToReturn)) {
						// re-fetch the shadow if necessary (if attributesToGet does not match)
						ResourceObjectIdentification identification = new ResourceObjectIdentification(shadowCtx.getObjectClassDefinition(), change.getIdentifiers());
						currentShadow = connector.fetchObject(ShadowType.class, identification, shadowAttrsToReturn, parentResult);
					}
					
				}
						
				PrismObject<ShadowType> processedCurrentShadow = postProcessResourceObjectRead(shadowCtx,
						currentShadow, true, parentResult);
				change.setCurrentShadow(processedCurrentShadow);
			}
		}

		parentResult.recordSuccess();
		LOGGER.trace("END fetch changes ({} changes)", changes == null ? "null" : changes.size());
		return changes;
	}
	
	/**
	 * Process simulated activation, credentials and other properties that are added to the object by midPoint. 
	 */
	private PrismObject<ShadowType> postProcessResourceObjectRead(ProvisioningContext ctx,
			PrismObject<ShadowType> resourceObject, boolean fetchAssociations,
            OperationResult parentResult) throws SchemaException, CommunicationException, ObjectNotFoundException, ConfigurationException, SecurityViolationException {
		ResourceType resourceType = ctx.getResource();
		ConnectorInstance connector = ctx.getConnector(parentResult);
		
		ShadowType resourceObjectType = resourceObject.asObjectable();
		setProtectedFlag(ctx, resourceObject);
		
		// Simulated Activation
		// FIXME??? when there are not native capabilities for activation, the
		// resourceShadow.getActivation is null and the activation for the repo
		// shadow are not completed..therefore there need to be one more check,
		// we must check not only if the activation is null, but if it is, also
		// if the shadow doesn't have defined simulated activation capability
		if (resourceObjectType.getActivation() != null || ResourceTypeUtil.hasActivationCapability(resourceType)) {
			ActivationType activationType = completeActivation(resourceObject, resourceType, parentResult);
			LOGGER.trace("Determined activation, administrativeStatus: {}, lockoutStatus: {}",
					activationType == null ? "null activationType" : activationType.getAdministrativeStatus(),
					activationType == null ? "null activationType" : activationType.getLockoutStatus());
			resourceObjectType.setActivation(activationType);
		} else {
			resourceObjectType.setActivation(null);
		}
		
		// Entitlements
        if (fetchAssociations) {
            entitlementConverter.postProcessEntitlementsRead(ctx, resourceObject, parentResult);
        }
		
		return resourceObject;
	}
	
	public void setProtectedFlag(ProvisioningContext ctx, PrismObject<ShadowType> resourceObject) throws SchemaException, ConfigurationException, ObjectNotFoundException, CommunicationException {
		if (isProtectedShadow(ctx.getObjectClassDefinition(), resourceObject)) {
			resourceObject.asObjectable().setProtectedObject(true);
		}
	}

	/**
	 * Completes activation state by determinig simulated activation if
	 * necessary.
	 * 
	 * TODO: The placement of this method is not correct. It should go back to
	 * ShadowConverter
	 */
	private ActivationType completeActivation(PrismObject<ShadowType> shadow, ResourceType resource,
			OperationResult parentResult) {

		if (ResourceTypeUtil.hasResourceNativeActivationCapability(resource)) {
			return shadow.asObjectable().getActivation();
		} else if (ResourceTypeUtil.hasActivationCapability(resource)) {
			return convertFromSimulatedActivationAttributes(resource, shadow, parentResult);
		} else {
			// No activation capability, nothing to do
			return null;
		}
	}
	
	private static ActivationType convertFromSimulatedActivationAttributes(ResourceType resource,
			PrismObject<ShadowType> shadow, OperationResult parentResult) {
		// LOGGER.trace("Start converting activation type from simulated activation atribute");
		ActivationCapabilityType activationCapability = ResourceTypeUtil.getEffectiveCapability(resource,
				ActivationCapabilityType.class);
		if (activationCapability == null) {
			return null;
		}
		
		ActivationType activationType = new ActivationType();
		
		converFromSimulatedActivationAdministrativeStatus(activationType, activationCapability, resource, shadow, parentResult);
		converFromSimulatedActivationLockoutStatus(activationType, activationCapability, resource, shadow, parentResult);
		
		return activationType;
	}

	private static ActivationStatusCapabilityType getStatusCapability(ResourceType resource, ActivationCapabilityType activationCapability) {
		ActivationStatusCapabilityType statusCapabilityType = activationCapability.getStatus();
		if (statusCapabilityType != null) {
			return statusCapabilityType;
		}
		return null;
	}
	
	private static ActivationLockoutStatusCapabilityType getLockoutStatusCapability(ResourceType resource, ActivationCapabilityType activationCapability) {
		ActivationLockoutStatusCapabilityType statusCapabilityType = activationCapability.getLockoutStatus();
		if (statusCapabilityType != null) {
			return statusCapabilityType;
		}
		return null;
	}
	
	private static void converFromSimulatedActivationAdministrativeStatus(ActivationType activationType, ActivationCapabilityType activationCapability,
			ResourceType resource, PrismObject<ShadowType> shadow, OperationResult parentResult) {
		
		ActivationStatusCapabilityType statusCapabilityType = getStatusCapability(resource, activationCapability);
		if (statusCapabilityType == null) {
			return;
		}
		
		ResourceAttributeContainer attributesContainer = ShadowUtil.getAttributesContainer(shadow);		
		ResourceAttribute<?> activationProperty = null;
		if (statusCapabilityType != null && statusCapabilityType.getAttribute() != null) {
			activationProperty = attributesContainer.findAttribute(statusCapabilityType.getAttribute());
		}
		
		// LOGGER.trace("activation property: {}", activationProperty.dump());
		// if (activationProperty == null) {
		// LOGGER.debug("No simulated activation attribute was defined for the account.");
		// return null;
		// }

		Collection<Object> activationValues = null;
		if (activationProperty != null) {
			activationValues = activationProperty.getRealValues(Object.class);
		}
		
		converFromSimulatedActivationAdministrativeStatusInternal(activationType, statusCapabilityType, resource, activationValues, parentResult);
		
		LOGGER.trace(
				"Detected simulated activation administrativeStatus attribute {} on {} with value {}, resolved into {}",
				new Object[] { SchemaDebugUtil.prettyPrint(statusCapabilityType.getAttribute()),
						ObjectTypeUtil.toShortString(resource), activationValues,
						activationType == null ? "null" : activationType.getAdministrativeStatus() });
		
		// Remove the attribute which is the source of simulated activation. If we leave it there then we
		// will have two ways to set activation.
		if (statusCapabilityType.isIgnoreAttribute() == null
				|| statusCapabilityType.isIgnoreAttribute().booleanValue()) {
			if (activationProperty != null) {
				attributesContainer.remove(activationProperty);
			}
		}
	}
	
	/**
	 * Moved to a separate method especially to enable good logging (see above). 
	 */
	private static void converFromSimulatedActivationAdministrativeStatusInternal(ActivationType activationType, ActivationStatusCapabilityType statusCapabilityType,
				ResourceType resource, Collection<Object> activationValues, OperationResult parentResult) {
		
		List<String> disableValues = statusCapabilityType.getDisableValue();
		List<String> enableValues = statusCapabilityType.getEnableValue();		

		if (MiscUtil.isNoValue(activationValues)) {

			if (MiscUtil.hasNoValue(disableValues)) {
				activationType.setAdministrativeStatus(ActivationStatusType.DISABLED);
				return;
			}

			if (MiscUtil.hasNoValue(enableValues)) {
				activationType.setAdministrativeStatus(ActivationStatusType.ENABLED);
				return;
			}

			// No activation information.
			LOGGER.warn("The {} does not provide definition for null value of simulated activation attribute",
					ObjectTypeUtil.toShortString(resource));
			if (parentResult != null) {
				parentResult.recordPartialError("The " + ObjectTypeUtil.toShortString(resource)
						+ " has native activation capability but noes not provide value for DISABLE attribute");
			}

			return;

		} else {
			if (activationValues.size() > 1) {
				LOGGER.warn("The {} provides {} values for DISABLE attribute, expecting just one value",
						disableValues.size(), ObjectTypeUtil.toShortString(resource));
				if (parentResult != null) {
					parentResult.recordPartialError("The " + ObjectTypeUtil.toShortString(resource) + " provides "
							+ disableValues.size() + " values for DISABLE attribute, expecting just one value");
				}
			}
			Object disableObj = activationValues.iterator().next();

			for (String disable : disableValues) {
				if (disable.equals(String.valueOf(disableObj))) {
					activationType.setAdministrativeStatus(ActivationStatusType.DISABLED);
					return;
				}
			}

			for (String enable : enableValues) {
				if ("".equals(enable) || enable.equals(String.valueOf(disableObj))) {
					activationType.setAdministrativeStatus(ActivationStatusType.ENABLED);
					return;
				}
			}
		}

	}
	
	private static void converFromSimulatedActivationLockoutStatus(ActivationType activationType, ActivationCapabilityType activationCapability,
			ResourceType resource, PrismObject<ShadowType> shadow, OperationResult parentResult) {
		
		ActivationLockoutStatusCapabilityType statusCapabilityType = getLockoutStatusCapability(resource, activationCapability);
		if (statusCapabilityType == null) {
			return;
		}
		
		ResourceAttributeContainer attributesContainer = ShadowUtil.getAttributesContainer(shadow);		
		ResourceAttribute<?> activationProperty = null;
		if (statusCapabilityType != null && statusCapabilityType.getAttribute() != null) {
			activationProperty = attributesContainer.findAttribute(statusCapabilityType.getAttribute());
		}
		
		// LOGGER.trace("activation property: {}", activationProperty.dump());
		// if (activationProperty == null) {
		// LOGGER.debug("No simulated activation attribute was defined for the account.");
		// return null;
		// }

		Collection<Object> activationValues = null;
		if (activationProperty != null) {
			activationValues = activationProperty.getRealValues(Object.class);
		}
		
		converFromSimulatedActivationLockoutStatusInternal(activationType, statusCapabilityType, resource, activationValues, parentResult);
		
		LOGGER.trace(
				"Detected simulated activation lockout attribute {} on {} with value {}, resolved into {}",
				new Object[] { SchemaDebugUtil.prettyPrint(statusCapabilityType.getAttribute()),
						ObjectTypeUtil.toShortString(resource), activationValues,
						activationType == null ? "null" : activationType.getAdministrativeStatus() });
		
		// Remove the attribute which is the source of simulated activation. If we leave it there then we
		// will have two ways to set activation.
		if (statusCapabilityType.isIgnoreAttribute() == null
				|| statusCapabilityType.isIgnoreAttribute().booleanValue()) {
			if (activationProperty != null) {
				attributesContainer.remove(activationProperty);
			}
		}
	}
	
	/**
	 * Moved to a separate method especially to enable good logging (see above). 
	 */
	private static void converFromSimulatedActivationLockoutStatusInternal(ActivationType activationType, ActivationLockoutStatusCapabilityType statusCapabilityType,
				ResourceType resource, Collection<Object> activationValues, OperationResult parentResult) {
		
		List<String> lockedValues = statusCapabilityType.getLockedValue();
		List<String> normalValues = statusCapabilityType.getNormalValue();	

		if (MiscUtil.isNoValue(activationValues)) {

			if (MiscUtil.hasNoValue(lockedValues)) {
				activationType.setLockoutStatus(LockoutStatusType.LOCKED);
				return;
			}

			if (MiscUtil.hasNoValue(normalValues)) {
				activationType.setLockoutStatus(LockoutStatusType.NORMAL);
				return;
			}

			// No activation information.
			LOGGER.warn("The {} does not provide definition for null value of simulated activation lockout attribute",
					resource);
			if (parentResult != null) {
				parentResult.recordPartialError("The " + resource
						+ " has native activation capability but noes not provide value for lockout attribute");
			}

			return;

		} else {
			if (activationValues.size() > 1) {
				LOGGER.warn("The {} provides {} values for lockout attribute, expecting just one value",
						lockedValues.size(), resource);
				if (parentResult != null) {
					parentResult.recordPartialError("The " + resource + " provides "
							+ lockedValues.size() + " values for lockout attribute, expecting just one value");
				}
			}
			Object activationValue = activationValues.iterator().next();

			for (String lockedValue : lockedValues) {
				if (lockedValue.equals(String.valueOf(activationValue))) {
					activationType.setLockoutStatus(LockoutStatusType.LOCKED);
					return;
				}
			}

			for (String normalValue : normalValues) {
				if ("".equals(normalValue) || normalValue.equals(String.valueOf(activationValue))) {
					activationType.setLockoutStatus(LockoutStatusType.NORMAL);
					return;
				}
			}
		}

	}

	private ActivationStatusCapabilityType getActivationAdministrativeStatusFromSimulatedActivation(ProvisioningContext ctx,
			ShadowType shadow, OperationResult result) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException{
		ActivationCapabilityType activationCapability = ResourceTypeUtil.getEffectiveCapability(ctx.getResource(),
				ActivationCapabilityType.class);
		if (activationCapability == null) {
			result.recordWarning("Resource " + ctx.getResource()
					+ " does not have native or simulated activation capability. Processing of activation for "+ shadow +" was skipped");
			shadow.setFetchResult(result.createOperationResultType());
			return null;
		}

		ActivationStatusCapabilityType capActStatus = getStatusCapability(ctx.getResource(), activationCapability);
		if (capActStatus == null) {
			result.recordWarning("Resource " + ctx.getResource()
					+ " does not have native or simulated activation status capability. Processing of activation for "+ shadow +" was skipped");
			shadow.setFetchResult(result.createOperationResultType());
			return null;
		}
		return capActStatus;

	}
	
	private ResourceAttribute<?> getSimulatedActivationAdministrativeStatusAttribute(ProvisioningContext ctx,
			ShadowType shadow, ActivationStatusCapabilityType capActStatus, OperationResult result) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException {
		if (capActStatus == null){
			return null;
		}
		ResourceType resource = ctx.getResource();
		QName enableAttributeName = capActStatus.getAttribute();
		LOGGER.trace("Simulated attribute name: {}", enableAttributeName);
		if (enableAttributeName == null) {
			result.recordWarning("Resource "
							+ ObjectTypeUtil.toShortString(resource)
							+ " does not have attribute specification for simulated activation status capability. Processing of activation for "+ shadow +" was skipped");
			shadow.setFetchResult(result.createOperationResultType());
			return null;
		}

		ResourceAttributeDefinition enableAttributeDefinition = ctx.getObjectClassDefinition()
				.findAttributeDefinition(enableAttributeName);
		if (enableAttributeDefinition == null) {
			result.recordWarning("Resource " + ObjectTypeUtil.toShortString(resource)
					+ "  attribute for simulated activation/enableDisable capability" + enableAttributeName
					+ " in not present in the schema for objeclass " + ctx.getObjectClassDefinition()+". Processing of activation for "+ ObjectTypeUtil.toShortString(shadow)+" was skipped");
			shadow.setFetchResult(result.createOperationResultType());
			return null;
		}

		return enableAttributeDefinition.instantiate(enableAttributeName);
	}

	private PropertyModificationOperation convertToSimulatedActivationAdministrativeStatusAttribute(ProvisioningContext ctx, 
			PropertyDelta activationDelta, ShadowType shadow, ActivationStatusType status, OperationResult result)
			throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException {
		ResourceType resource = ctx.getResource();
		ActivationStatusCapabilityType capActStatus = getActivationAdministrativeStatusFromSimulatedActivation(ctx, shadow, result);
		if (capActStatus == null){
			throw new SchemaException("Attempt to modify activation on "+resource+" which does not have activation capability");
		}
		
		ResourceAttribute<?> activationAttribute = getSimulatedActivationAdministrativeStatusAttribute(ctx, shadow, capActStatus, result);
		if (activationAttribute == null){
			return null;
		}
		
		PropertyDelta<?> enableAttributeDelta = null;
		
		if (status == null && activationDelta.isDelete()){
			LOGGER.trace("deleting activation property.");
			enableAttributeDelta = PropertyDelta.createModificationDeleteProperty(new ItemPath(ShadowType.F_ATTRIBUTES, activationAttribute.getElementName()), activationAttribute.getDefinition(), activationAttribute.getRealValue());
			
		} else if (status == ActivationStatusType.ENABLED) {
			String enableValue = getEnableValue(capActStatus);
			enableAttributeDelta = createActivationPropDelta(activationAttribute.getElementName(), activationAttribute.getDefinition(), enableValue);
		} else {
			String disableValue = getDisableValue(capActStatus);
			enableAttributeDelta = createActivationPropDelta(activationAttribute.getElementName(), activationAttribute.getDefinition(), disableValue);
		}

		PropertyModificationOperation attributeChange = new PropertyModificationOperation(
				enableAttributeDelta);
		return attributeChange;
	}
	
	private PropertyModificationOperation convertToSimulatedActivationLockoutStatusAttribute(ProvisioningContext ctx,
			PropertyDelta activationDelta, ShadowType shadow, LockoutStatusType status, OperationResult result)
			throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException {

		ActivationLockoutStatusCapabilityType capActStatus = getActivationLockoutStatusFromSimulatedActivation(ctx, shadow, result);
		if (capActStatus == null){
			throw new SchemaException("Attempt to modify lockout on "+ctx.getResource()+" which does not have activation lockout capability");
		}
		
		ResourceAttribute<?> activationAttribute = getSimulatedActivationLockoutStatusAttribute(ctx, shadow, capActStatus, result);
		if (activationAttribute == null){
			return null;
		}
		
		PropertyDelta<?> lockoutAttributeDelta = null;
		
		if (status == null && activationDelta.isDelete()){
			LOGGER.trace("deleting activation property.");
			lockoutAttributeDelta = PropertyDelta.createModificationDeleteProperty(new ItemPath(ShadowType.F_ATTRIBUTES, activationAttribute.getElementName()), activationAttribute.getDefinition(), activationAttribute.getRealValue());
			
		} else if (status == LockoutStatusType.NORMAL) {
			String normalValue = getLockoutNormalValue(capActStatus);
			lockoutAttributeDelta = createActivationPropDelta(activationAttribute.getElementName(), activationAttribute.getDefinition(), normalValue);
		} else {
			String lockedValue = getLockoutLockedValue(capActStatus);
			lockoutAttributeDelta = createActivationPropDelta(activationAttribute.getElementName(), activationAttribute.getDefinition(), lockedValue);
		}

		PropertyModificationOperation attributeChange = new PropertyModificationOperation(lockoutAttributeDelta);
		return attributeChange;
	}
	
	private PropertyDelta<?> createActivationPropDelta(QName attrName, ResourceAttributeDefinition attrDef, String value) {
		if (StringUtils.isBlank(value)) {
			return PropertyDelta.createModificationReplaceProperty(new ItemPath(ShadowType.F_ATTRIBUTES, attrName), 
					attrDef);
		} else {
			return PropertyDelta.createModificationReplaceProperty(new ItemPath(ShadowType.F_ATTRIBUTES, attrName), 
					attrDef, value);
		}
	}

	private ActivationLockoutStatusCapabilityType getActivationLockoutStatusFromSimulatedActivation(ProvisioningContext ctx,
			ShadowType shadow, OperationResult result) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException{
		ActivationCapabilityType activationCapability = ResourceTypeUtil.getEffectiveCapability(ctx.getResource(),
				ActivationCapabilityType.class);
		if (activationCapability == null) {
			result.recordWarning("Resource " + ctx.getResource()
					+ " does not have native or simulated activation capability. Processing of activation for "+ shadow +" was skipped");
			shadow.setFetchResult(result.createOperationResultType());
			return null;
		}

		ActivationLockoutStatusCapabilityType capActStatus = getLockoutStatusCapability(ctx.getResource(), activationCapability);
		if (capActStatus == null) {
			result.recordWarning("Resource " + ctx.getResource()
					+ " does not have native or simulated activation lockout capability. Processing of activation for "+ shadow +" was skipped");
			shadow.setFetchResult(result.createOperationResultType());
			return null;
		}
		return capActStatus;

	}
	
	private ResourceAttribute<?> getSimulatedActivationLockoutStatusAttribute(ProvisioningContext ctx, 
			ShadowType shadow, ActivationLockoutStatusCapabilityType capActStatus, OperationResult result) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException{
		
		QName enableAttributeName = capActStatus.getAttribute();
		LOGGER.trace("Simulated lockout attribute name: {}", enableAttributeName);
		if (enableAttributeName == null) {
			result.recordWarning("Resource "
							+ ObjectTypeUtil.toShortString(ctx.getResource())
							+ " does not have attribute specification for simulated activation lockout capability. Processing of activation for "+ shadow +" was skipped");
			shadow.setFetchResult(result.createOperationResultType());
			return null;
		}

		ResourceAttributeDefinition enableAttributeDefinition = ctx.getObjectClassDefinition()
				.findAttributeDefinition(enableAttributeName);
		if (enableAttributeDefinition == null) {
			result.recordWarning("Resource " + ObjectTypeUtil.toShortString(ctx.getResource())
					+ "  attribute for simulated activation/lockout capability" + enableAttributeName
					+ " in not present in the schema for objeclass " + ctx.getObjectClassDefinition()+". Processing of activation for "+ ObjectTypeUtil.toShortString(shadow)+" was skipped");
			shadow.setFetchResult(result.createOperationResultType());
			return null;
		}

		return enableAttributeDefinition.instantiate(enableAttributeName);

	}
	

	private String getDisableValue(ActivationStatusCapabilityType capActStatus){
		//TODO some checks
		String disableValue = capActStatus.getDisableValue().iterator().next();
		return disableValue;
//		return new PrismPropertyValue(disableValue);
	}
	
	private String getEnableValue(ActivationStatusCapabilityType capActStatus){
		String enableValue = capActStatus.getEnableValue().iterator().next();
		return enableValue;
//		return new PrismPropertyValue(enableValue);
	}
	
	private String getLockoutNormalValue(ActivationLockoutStatusCapabilityType capActStatus) {
		String value = capActStatus.getNormalValue().iterator().next();
		return value;
	}
	
	private String getLockoutLockedValue(ActivationLockoutStatusCapabilityType capActStatus) {
		String value = capActStatus.getLockedValue().iterator().next();
		return value;
	}

	private RefinedObjectClassDefinition determineObjectClassDefinition(PrismObject<ShadowType> shadow, ResourceType resource) throws SchemaException, ConfigurationException {
		ShadowType shadowType = shadow.asObjectable();
		RefinedResourceSchema refinedSchema = RefinedResourceSchema.getRefinedSchema(resource, prismContext);
		if (refinedSchema == null) {
			throw new ConfigurationException("No schema definied for "+resource);
		}
		
		
		RefinedObjectClassDefinition objectClassDefinition = null;
		ShadowKindType kind = shadowType.getKind();
		String intent = shadowType.getIntent();
		QName objectClass = shadow.asObjectable().getObjectClass();
		if (kind != null) {
			objectClassDefinition = refinedSchema.getRefinedDefinition(kind, intent);
		} else {
			// Fallback to objectclass only
			if (objectClass == null) {
				throw new SchemaException("No kind nor objectclass definied in "+shadow);
			}
			objectClassDefinition = refinedSchema.findRefinedDefinitionByObjectClassQName(null, objectClass);
		}
		
		if (objectClassDefinition == null) {
			throw new SchemaException("Definition for "+shadow+" not found (objectClass=" + PrettyPrinter.prettyPrint(objectClass) +
					", kind="+kind+", intent='"+intent+"') in schema of " + resource);
		}		
		
		return objectClassDefinition;
	}
	
	private ObjectClassComplexTypeDefinition determineObjectClassDefinition(
			ResourceShadowDiscriminator discriminator, ResourceType resource) throws SchemaException {
		ResourceSchema schema = RefinedResourceSchema.getResourceSchema(resource, prismContext);
		// HACK FIXME
		ObjectClassComplexTypeDefinition objectClassDefinition = schema.findObjectClassDefinition(ShadowKindType.ACCOUNT, discriminator.getIntent());

		if (objectClassDefinition == null) {
			// Unknown objectclass
			throw new SchemaException("Account type " + discriminator.getIntent()
					+ " is not known in schema of " + resource);
		}
		
		return objectClassDefinition;
	}
	
	private void addExecuteScriptOperation(Collection<Operation> operations, ProvisioningOperationTypeType type,
			OperationProvisioningScriptsType scripts, ResourceType resource, OperationResult result) throws SchemaException {
		if (scripts == null) {
			// No warning needed, this is quite normal
			LOGGER.trace("Skipping creating script operation to execute. Scripts was not defined.");
			return;
		}

		for (OperationProvisioningScriptType script : scripts.getScript()) {
			for (ProvisioningOperationTypeType operationType : script.getOperation()) {
				if (type.equals(operationType)) {
					ExecuteProvisioningScriptOperation scriptOperation = ProvisioningUtil.convertToScriptOperation(
							script, "script value for " + operationType + " in " + resource, prismContext);

					scriptOperation.setScriptOrder(script.getOrder());

					LOGGER.trace("Created script operation: {}", SchemaDebugUtil.prettyPrint(scriptOperation));
					operations.add(scriptOperation);
				}
			}
		}
	}
	
	private boolean isProtectedShadow(RefinedObjectClassDefinition objectClassDefinition, PrismObject<ShadowType> shadow) throws SchemaException {
		boolean isProtected = false;
		if (objectClassDefinition == null) {
			isProtected = false;
		} else {
			Collection<ResourceObjectPattern> protectedAccountPatterns = objectClassDefinition.getProtectedObjectPatterns();
			if (protectedAccountPatterns == null) {
				isProtected = false;
			} else {
				isProtected = ResourceObjectPattern.matches(shadow, protectedAccountPatterns, matchingRuleRegistry);
			}
		}
		LOGGER.trace("isProtectedShadow: {}: {} = {}", new Object[] { objectClassDefinition,
				shadow, isProtected });
		return isProtected;
	}
}
