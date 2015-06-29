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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.xml.namespace.QName;

import org.aspectj.weaver.patterns.NamePattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.common.refinery.RefinedAttributeDefinition;
import com.evolveum.midpoint.common.refinery.RefinedObjectClassDefinition;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.match.MatchingRule;
import com.evolveum.midpoint.prism.match.MatchingRuleRegistry;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.AndFilter;
import com.evolveum.midpoint.prism.query.EqualFilter;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.OrFilter;
import com.evolveum.midpoint.prism.query.RefFilter;
import com.evolveum.midpoint.prism.query.Visitor;
import com.evolveum.midpoint.provisioning.api.ResourceOperationDescription;
import com.evolveum.midpoint.provisioning.ucf.api.Change;
import com.evolveum.midpoint.provisioning.util.ProvisioningUtil;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SearchResultMetadata;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.processor.ResourceAttribute;
import com.evolveum.midpoint.schema.processor.ResourceAttributeContainer;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.schema.util.SchemaDebugUtil;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FailedOperationTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

/**
 * Responsibilities:
 *     Communicate with the repo
 *     Store results in the repo shadows
 *     Clean up shadow inconsistencies in repo
 *     
 * Limitations:
 *     Do NOT communicate with the resource
 *     means: do NOT do anything with the connector
 * 
 * @author Katarina Valalikova
 * @author Radovan Semancik
 *
 */
@Component
public class ShadowManager {
	
	@Autowired(required = true)
	@Qualifier("cacheRepositoryService")
	private RepositoryService repositoryService;
	@Autowired(required = true)
	private PrismContext prismContext;
	@Autowired(required = true)
	private TaskManager taskManager;
	@Autowired(required = true)
	private MatchingRuleRegistry matchingRuleRegistry;
	
	private static final Trace LOGGER = TraceManager.getTrace(ShadowManager.class);
		
	
	public void deleteConflictedShadowFromRepo(PrismObject<ShadowType> shadow, OperationResult parentResult){
		
		try{
			
			repositoryService.deleteObject(shadow.getCompileTimeClass(), shadow.getOid(), parentResult);
		
		} catch (Exception ex){
			throw new SystemException(ex.getMessage(), ex);
		}
		
	}
	
	public ResourceOperationDescription createResourceFailureDescription(
			PrismObject<ShadowType> conflictedShadow, ResourceType resource, OperationResult parentResult){
		ResourceOperationDescription failureDesc = new ResourceOperationDescription();
		failureDesc.setCurrentShadow(conflictedShadow);
		ObjectDelta<ShadowType> objectDelta = null;
		if (FailedOperationTypeType.ADD == conflictedShadow.asObjectable().getFailedOperationType()) {
			objectDelta = ObjectDelta.createAddDelta(conflictedShadow);
		} 
		failureDesc.setObjectDelta(objectDelta);
		failureDesc.setResource(resource.asPrismObject());
		failureDesc.setResult(parentResult);
		failureDesc.setSourceChannel(SchemaConstants.CHANGE_CHANNEL_DISCOVERY.getLocalPart());
		
		return failureDesc;
	}

	/**
	 * Locates the appropriate Shadow in repository that corresponds to the
	 * provided resource object.
	 * 
	 * @param parentResult
	 * 
	 * @return current unchanged shadow object that corresponds to provided
	 *         resource object or null if the object does not exist
	 */
	public PrismObject<ShadowType> lookupShadowInRepository(ProvisioningContext ctx, PrismObject<ShadowType> resourceShadow,
			OperationResult parentResult) 
					throws SchemaException, ConfigurationException, ObjectNotFoundException, CommunicationException {

		ObjectQuery query = createSearchShadowQuery(ctx, resourceShadow, prismContext,
				parentResult);
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Searching for shadow using filter:\n{}",
					query.debugDump());
		}
//		PagingType paging = new PagingType();

		// TODO: check for errors
		 List<PrismObject<ShadowType>> results = repositoryService.searchObjects(ShadowType.class, query, null, parentResult);
		 MiscSchemaUtil.reduceSearchResult(results);

		LOGGER.trace("lookupShadow found {} objects", results.size());

		if (results.size() == 0) {
			return null;
		}
		if (results.size() > 1) {
			for (PrismObject<ShadowType> result : results) {
				LOGGER.trace("Search result:\n{}", result.debugDump());
			}
			LOGGER.error("More than one shadow found for " + resourceShadow);
			// TODO: Better error handling later
			throw new IllegalStateException("More than one shadow found for " + resourceShadow);
		}

		return results.get(0);
	}

	public PrismObject<ShadowType> lookupShadowInRepository(ProvisioningContext ctx, ResourceAttributeContainer identifierContainer,
			OperationResult parentResult) 
					throws SchemaException, ConfigurationException, ObjectNotFoundException, CommunicationException {

		ObjectQuery query = createSearchShadowQuery(ctx, identifierContainer.getValue().getItems(), prismContext,
				parentResult);
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Searching for shadow using filter (repo):\n{}",
					query.debugDump());
		}
//		PagingType paging = new PagingType();

		// TODO: check for errors
		List<PrismObject<ShadowType>> results;

		results = repositoryService.searchObjects(ShadowType.class, query, null, parentResult);
		MiscSchemaUtil.reduceSearchResult(results);

		LOGGER.trace("lookupShadow found {} objects", results.size());

		if (results.size() == 0) {
			return null;
		}
		if (results.size() > 1) {
			LOGGER.error("More than one shadow found in repository for " + identifierContainer);
			if (LOGGER.isDebugEnabled()) {
				for (PrismObject<ShadowType> result : results) {
					LOGGER.debug("Conflicting shadow (repo):\n{}", result.debugDump());
				}
			}
			// TODO: Better error handling later
			throw new IllegalStateException("More than one shadows found in repository for " + identifierContainer);
		}

		return results.get(0);
	}

	public PrismObject<ShadowType> lookupShadowBySecondaryIdentifiers( 
			ProvisioningContext ctx, PrismObject<ShadowType> resourceShadow, OperationResult parentResult) 
					throws SchemaException, ConfigurationException, ObjectNotFoundException, CommunicationException {

		Collection<ResourceAttribute<?>> secondaryIdentifiers = ShadowUtil.getSecondaryIdentifiers(resourceShadow);
//		ResourceAttribute<?> secondaryIdentifier = null;
		if (secondaryIdentifiers.size() < 1){
			LOGGER.trace("Shadow does not contain secondary idetifier. Skipping lookup shadows according to name.");
			return null;
		}
		
		List<EqualFilter> secondaryEquals = new ArrayList<>();
		for (ResourceAttribute<?> secondaryIdentifier : secondaryIdentifiers){
			secondaryEquals.add(EqualFilter.createEqual(secondaryIdentifier.getPath(), secondaryIdentifier.getDefinition(),
					getNormalizedValue(secondaryIdentifier, ctx.getObjectClassDefinition())));
		}
		
		ObjectFilter secondaryIdentifierFilter = null;
		if (secondaryEquals.size() > 1){
			secondaryIdentifierFilter = OrFilter.createOr((List) secondaryEquals);
		} else {
			secondaryIdentifierFilter = secondaryEquals.iterator().next();
		}
				
		AndFilter filter = AndFilter.createAnd(
				RefFilter.createReferenceEqual(ShadowType.F_RESOURCE_REF, ShadowType.class, ctx.getResource()), secondaryIdentifierFilter);
		ObjectQuery query = ObjectQuery.createObjectQuery(filter);
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Searching for shadow using filter on secondary identifier:\n{}",
					query.debugDump());
		}

		// TODO: check for errors
		List<PrismObject<ShadowType>> results = repositoryService.searchObjects(ShadowType.class, query, null, parentResult);
		MiscSchemaUtil.reduceSearchResult(results);

		LOGGER.trace("lookupShadow found {} objects", results.size());

		if (results.size() == 0) {
			return null;
		}

		List<PrismObject<ShadowType>> conflictingShadows = new ArrayList<PrismObject<ShadowType>>();
		for (PrismObject<ShadowType> shadow: results){
			ShadowType repoShadowType = shadow.asObjectable();
			if (shadow != null) {
				if (repoShadowType.getFailedOperationType() == null){
					LOGGER.trace("Found shadow is ok, returning null");
					continue;
				} 
				if (repoShadowType.getFailedOperationType() != null && FailedOperationTypeType.ADD != repoShadowType.getFailedOperationType()){
					continue;
				}
				conflictingShadows.add(shadow);
			}
		}
		
		if (conflictingShadows.isEmpty()){
			return null;
		}
		
		if (conflictingShadows.size() > 1) {
			for (PrismObject<ShadowType> result : conflictingShadows) {
				LOGGER.trace("Search result:\n{}", result.debugDump());
			}
			LOGGER.error("More than one shadow found for " + resourceShadow);
			if (LOGGER.isDebugEnabled()) {
				for (PrismObject<ShadowType> conflictingShadow: conflictingShadows) {
					LOGGER.debug("Conflicting shadow:\n{}", conflictingShadow.debugDump());
				}
			}
			// TODO: Better error handling later
			throw new IllegalStateException("More than one shadows found for " + resourceShadow);
		}

		return conflictingShadows.get(0);
	}

	private <T> List<PrismPropertyValue<T>> getNormalizedValue(PrismProperty<T> attr, RefinedObjectClassDefinition rObjClassDef) throws SchemaException {
		RefinedAttributeDefinition refinedAttributeDefinition = rObjClassDef.findAttributeDefinition(attr.getElementName());
		QName matchingRuleQName = refinedAttributeDefinition.getMatchingRuleQName();
		MatchingRule<T> matchingRule = matchingRuleRegistry.getMatchingRule(matchingRuleQName, refinedAttributeDefinition.getTypeName());
		List<PrismPropertyValue<T>> normalized = new ArrayList<>();
		for (PrismPropertyValue<T> origPValue : attr.getValues()){
			if (matchingRule != null) {
				T normalizedValue = matchingRule.normalize(origPValue.getValue());
				PrismPropertyValue<T> normalizedPValue = origPValue.clone();
				normalizedPValue.setValue(normalizedValue);
				normalized.add(normalizedPValue);
			} else {
				normalized.add(origPValue);
			}
		}
		return normalized;
		
	}


    // beware, may return null if an shadow that was to be marked as DEAD, was deleted in the meantime
	public PrismObject<ShadowType> findOrCreateShadowFromChange(ProvisioningContext ctx, Change<ShadowType> change,
			OperationResult parentResult) throws SchemaException, CommunicationException,
			ConfigurationException, SecurityViolationException, ObjectNotFoundException {

		// Try to locate existing shadow in the repository
		List<PrismObject<ShadowType>> accountList = searchShadowByIdenifiers(ctx, change, parentResult);

		if (accountList.size() > 1) {
			String message = "Found more than one shadow with the identifier " + change.getIdentifiers() + ".";
			LOGGER.error(message);
			parentResult.recordFatalError(message);
			throw new IllegalArgumentException(message);
		}

		PrismObject<ShadowType> newShadow = null;

		if (accountList.isEmpty()) {
			// account was not found in the repository, create it now

			if (change.getObjectDelta() == null || change.getObjectDelta().getChangeType() != ChangeType.DELETE) {
				newShadow = createNewAccountFromChange(ctx, change, parentResult);

				try {
					ConstraintsChecker.onShadowAddOperation(newShadow.asObjectable());
					String oid = repositoryService.addObject(newShadow, null, parentResult);
					newShadow.setOid(oid);
					if (change.getObjectDelta() != null && change.getObjectDelta().getOid() == null) {
						change.getObjectDelta().setOid(oid);
					}
				} catch (ObjectAlreadyExistsException e) {
					parentResult.recordFatalError("Can't add " + SchemaDebugUtil.prettyPrint(newShadow)
							+ " to the repository. Reason: " + e.getMessage(), e);
					throw new IllegalStateException(e.getMessage(), e);
				}
				LOGGER.trace("Created shadow object: {}", newShadow);
			}

		} else {
			// Account was found in repository
			newShadow = accountList.get(0);
			
            if (change.getObjectDelta() != null && change.getObjectDelta().getChangeType() == ChangeType.DELETE) {
					Collection<? extends ItemDelta> deadDeltas = PropertyDelta
							.createModificationReplacePropertyCollection(ShadowType.F_DEAD,
									newShadow.getDefinition(), true);
					try {
						ConstraintsChecker.onShadowModifyOperation(deadDeltas);
						repositoryService.modifyObject(ShadowType.class, newShadow.getOid(), deadDeltas,
								parentResult);
					} catch (ObjectAlreadyExistsException e) {
						parentResult.recordFatalError(
								"Can't add " + SchemaDebugUtil.prettyPrint(newShadow)
										+ " to the repository. Reason: " + e.getMessage(), e);
						throw new IllegalStateException(e.getMessage(), e);
					} catch (ObjectNotFoundException e) {
						parentResult.recordWarning("Shadow " + SchemaDebugUtil.prettyPrint(newShadow)
								+ " was probably deleted from the repository in the meantime. Exception: "
								+ e.getMessage(), e);
						return null;
					}
				} 
				
			
			
		}
		

		return newShadow;
	}
	
	private PrismObject<ShadowType> createNewAccountFromChange(ProvisioningContext ctx, Change<ShadowType> change, 
			OperationResult parentResult) throws SchemaException,
			CommunicationException, ConfigurationException,
			SecurityViolationException, ObjectNotFoundException {

		PrismObject<ShadowType> shadow = change.getCurrentShadow();
		
		if (shadow == null){
			//try to look in the delta, if there exists some account to be added
			if (change.getObjectDelta() != null && change.getObjectDelta().isAdd()){
				shadow = (PrismObject<ShadowType>) change.getObjectDelta().getObjectToAdd();
			}
		}
		
		if (shadow == null){
			throw new IllegalStateException("Could not create shadow from change description. Neither current shadow, nor delta containing shadow exits.");
		}
		
		try {
			shadow = createRepositoryShadow(ctx, shadow);
		} catch (SchemaException ex) {
			parentResult.recordFatalError("Can't create shadow from identifiers: "
					+ change.getIdentifiers());
			throw new SchemaException("Can't create shadow from identifiers: "
					+ change.getIdentifiers());
		}

		parentResult.recordSuccess();
		return shadow;
	}
	
	private List<PrismObject<ShadowType>> searchShadowByIdenifiers(ProvisioningContext ctx, Change<ShadowType> change, OperationResult parentResult)
			throws SchemaException, ConfigurationException, ObjectNotFoundException, CommunicationException {

		ObjectQuery query = createSearchShadowQuery(ctx, change.getIdentifiers(), prismContext, parentResult);

		List<PrismObject<ShadowType>> accountList = null;
		try {
			accountList = repositoryService.searchObjects(ShadowType.class, query, null, parentResult);
		} catch (SchemaException ex) {
			parentResult.recordFatalError(
					"Failed to search shadow according to the identifiers: " + change.getIdentifiers() + ". Reason: "
							+ ex.getMessage(), ex);
			throw new SchemaException("Failed to search shadow according to the identifiers: "
					+ change.getIdentifiers() + ". Reason: " + ex.getMessage(), ex);
		}
		MiscSchemaUtil.reduceSearchResult(accountList);
		return accountList;
	}
	
	private ObjectQuery createSearchShadowQuery(ProvisioningContext ctx, Collection<ResourceAttribute<?>> identifiers,
			PrismContext prismContext, OperationResult parentResult) throws SchemaException, ConfigurationException, ObjectNotFoundException, CommunicationException {
		List<ObjectFilter> conditions = new ArrayList<ObjectFilter>();
		for (PrismProperty<?> identifier : identifiers) {
			PrismPropertyValue<?> identifierValue = identifier.getValue();
			RefinedAttributeDefinition rAttrDef = ctx.getObjectClassDefinition().findAttributeDefinition(identifier.getElementName());
			Object normalizedIdentifierValue = getNormalizedAttributeValue(identifierValue, rAttrDef);
			//new ItemPath(ShadowType.F_ATTRIBUTES)
			PrismPropertyDefinition def = identifier.getDefinition();
			EqualFilter filter = EqualFilter.createEqual(new ItemPath(ShadowType.F_ATTRIBUTES, def.getName()), def, new PrismPropertyValue(normalizedIdentifierValue));
			conditions.add(filter);
		}

		if (conditions.size() < 1) {
			throw new SchemaException("Identifier not specified. Cannot create search query by identifier.");
		}
		
		RefFilter resourceRefFilter = RefFilter.createReferenceEqual(ShadowType.F_RESOURCE_REF, ShadowType.class, ctx.getResource());
		conditions.add(resourceRefFilter);

		ObjectFilter filter = null;
		if (conditions.size() > 1) {
			filter = AndFilter.createAnd(conditions);
		} else {
			filter = conditions.get(0);
		}

		ObjectQuery query = ObjectQuery.createObjectQuery(filter);
		return query;
	}

	private ObjectQuery createSearchShadowQuery(ProvisioningContext ctx, PrismObject<ShadowType> resourceShadow, 
			PrismContext prismContext, OperationResult parentResult) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException {
		ResourceAttributeContainer attributesContainer = ShadowUtil
				.getAttributesContainer(resourceShadow);
		PrismProperty identifier = attributesContainer.getIdentifier();

		Collection<PrismPropertyValue<Object>> idValues = identifier.getValues();
		// Only one value is supported for an identifier
		if (idValues.size() > 1) {
			// TODO: This should probably be switched to checked exception later
			throw new IllegalArgumentException("More than one identifier value is not supported");
		}
		if (idValues.size() < 1) {
			// TODO: This should probably be switched to checked exception later
			throw new IllegalArgumentException("The identifier has no value");
		}

		// We have all the data, we can construct the filter now
		ObjectFilter filter = null;
		try {
			// TODO TODO TODO TODO: set matching rule instead of null
			PrismPropertyDefinition def = identifier.getDefinition();
			filter = AndFilter.createAnd(
					RefFilter.createReferenceEqual(ShadowType.F_RESOURCE_REF, ShadowType.class, ctx.getResource()), 
					EqualFilter.createEqual(new ItemPath(ShadowType.F_ATTRIBUTES, def.getName()), def, getNormalizedValue(identifier, ctx.getObjectClassDefinition())));
		} catch (SchemaException e) {
			throw new SchemaException("Schema error while creating search filter: " + e.getMessage(), e);
		}

		ObjectQuery query = ObjectQuery.createObjectQuery(filter);

		return query;
	}
	
	public SearchResultMetadata searchObjectsIterativeRepository(
			ProvisioningContext ctx, ObjectQuery query,
			Collection<SelectorOptions<GetOperationOptions>> options,
			com.evolveum.midpoint.schema.ResultHandler<ShadowType> repoHandler, OperationResult parentResult) throws SchemaException, ConfigurationException, ObjectNotFoundException, CommunicationException {
		
		ObjectQuery repoQuery = query.clone();
		processQueryMatchingRules(repoQuery, ctx.getObjectClassDefinition());
		
		return repositoryService.searchObjectsIterative(ShadowType.class, repoQuery, repoHandler, options, parentResult);
	}

	/**
	 * Visit the query and normalize values (or set matching rules) as needed
	 */
	private void processQueryMatchingRules(ObjectQuery repoQuery, final RefinedObjectClassDefinition objectClassDef) {
		ObjectFilter filter = repoQuery.getFilter();
		Visitor visitor = new Visitor() {
			@Override
			public void visit(ObjectFilter filter) {
				try {
					processQueryMatchingRuleFilter(filter, objectClassDef);
				} catch (SchemaException e) {
					throw new SystemException(e);
				}
			}
		};
		filter.accept(visitor);
	}
	
	private <T> void processQueryMatchingRuleFilter(ObjectFilter filter, RefinedObjectClassDefinition objectClassDef) throws SchemaException {
		if (!(filter instanceof EqualFilter)) {
			return;
		}
		EqualFilter<T> eqFilter = (EqualFilter)filter;
		ItemPath parentPath = eqFilter.getParentPath();
		if (parentPath == null || !parentPath.equivalent(SchemaConstants.PATH_ATTRIBUTES)) {
			return;
		}
		QName attrName = eqFilter.getElementName();
		RefinedAttributeDefinition rAttrDef = objectClassDef.findAttributeDefinition(attrName);
		if (rAttrDef == null) {
			throw new SchemaException("Unknown attribute "+attrName+" in filter "+filter);
		}
		QName matchingRuleQName = rAttrDef.getMatchingRuleQName();
		if (matchingRuleQName == null) {
			return;
		}
		MatchingRule<T> matchingRule = matchingRuleRegistry.getMatchingRule(matchingRuleQName, rAttrDef.getTypeName());
		if (matchingRule == null) {
			// TODO: warning?
			return;
		}
		List<PrismValue> newValues = new ArrayList<PrismValue>();
		for (PrismPropertyValue<T> ppval: eqFilter.getValues()) {
			T normalizedRealValue = matchingRule.normalize(ppval.getValue());
			PrismPropertyValue<T> newPPval = ppval.clone();
			newPPval.setValue(normalizedRealValue);
			newValues.add(newPPval);
		}
		eqFilter.getValues().clear();
		eqFilter.getValues().addAll((Collection) newValues);
		LOGGER.trace("Replacing values for attribute {} in search filter with normalized values because there is a matching rule, normalized values: {}",
				attrName, newValues);
		if (eqFilter.getMatchingRule() == null) {
			eqFilter.setMatchingRule(matchingRuleQName);
			LOGGER.trace("Setting matching rule to {}", matchingRuleQName);
		}
	}

	/**
	 * Create a copy of a shadow that is suitable for repository storage.
	 */
	public PrismObject<ShadowType> createRepositoryShadow(ProvisioningContext ctx, PrismObject<ShadowType> shadow)
			throws SchemaException, ConfigurationException, ObjectNotFoundException, CommunicationException {

		ResourceAttributeContainer attributesContainer = ShadowUtil.getAttributesContainer(shadow);
		
		PrismObject<ShadowType> repoShadow = shadow.clone();
		ResourceAttributeContainer repoAttributesContainer = ShadowUtil
				.getAttributesContainer(repoShadow);

		// Clean all repoShadow attributes and add only those that should be
		// there
		repoAttributesContainer.clear();
		Collection<ResourceAttribute<?>> identifiers = attributesContainer.getIdentifiers();
		for (PrismProperty<?> p : identifiers) {
			repoAttributesContainer.add(p.clone());
		}

		Collection<ResourceAttribute<?>> secondaryIdentifiers = attributesContainer.getSecondaryIdentifiers();
		for (PrismProperty<?> p : secondaryIdentifiers) {
			repoAttributesContainer.add(p.clone());
		}

		ShadowType repoShadowType = repoShadow.asObjectable();

        setKindIfNecessary(repoShadowType, ctx.getObjectClassDefinition());
//        setIntentIfNecessary(repoShadowType, objectClassDefinition);

        // We don't want to store credentials in the repo
		repoShadowType.setCredentials(null);

		// additional check if the shadow doesn't contain resource, if yes,
		// convert to the resource reference.
		if (repoShadowType.getResource() != null) {
			repoShadowType.setResource(null);
			repoShadowType.setResourceRef(ObjectTypeUtil.createObjectRef(ctx.getResource()));
		}

		// if shadow does not contain resource or resource reference, create it
		// now
		if (repoShadowType.getResourceRef() == null) {
			repoShadowType.setResourceRef(ObjectTypeUtil.createObjectRef(ctx.getResource()));
		}

		if (repoShadowType.getName() == null) {
			repoShadowType.setName(new PolyStringType(ProvisioningUtil.determineShadowName(shadow)));
		}

		if (repoShadowType.getObjectClass() == null) {
			repoShadowType.setObjectClass(attributesContainer.getDefinition().getTypeName());
		}
		
		if (repoShadowType.isProtectedObject() != null){
			repoShadowType.setProtectedObject(null);
		}
		
		normalizeAttributes(repoShadow, ctx.getObjectClassDefinition());
		
		repoShadowType.setCachingMetadata(null);
	
		return repoShadow;
	}

    public void setKindIfNecessary(ShadowType repoShadowType, RefinedObjectClassDefinition objectClassDefinition) {
        if (repoShadowType.getKind() == null && objectClassDefinition != null) {
            repoShadowType.setKind(objectClassDefinition.getKind());
        }
    }
    
    public void setIntentIfNecessary(ShadowType repoShadowType, RefinedObjectClassDefinition objectClassDefinition) {
        if (repoShadowType.getIntent() == null && objectClassDefinition.getIntent() != null) {
            repoShadowType.setIntent(objectClassDefinition.getIntent());
        }
    }

    public void normalizeAttributes(PrismObject<ShadowType> shadow, RefinedObjectClassDefinition objectClassDefinition) throws SchemaException {
		for (ResourceAttribute<?> attribute: ShadowUtil.getAttributes(shadow)) {
			RefinedAttributeDefinition rAttrDef = objectClassDefinition.findAttributeDefinition(attribute.getElementName());
			normalizeAttribute(attribute, rAttrDef);			
		}
	}

	private <T> void normalizeAttribute(ResourceAttribute<T> attribute, RefinedAttributeDefinition rAttrDef) throws SchemaException {
		MatchingRule<T> matchingRule = matchingRuleRegistry.getMatchingRule(rAttrDef.getMatchingRuleQName(), rAttrDef.getTypeName());
		if (matchingRule != null) {
			for (PrismPropertyValue<T> pval: attribute.getValues()) {
				T normalizedRealValue = matchingRule.normalize(pval.getValue());
				pval.setValue(normalizedRealValue);
			}
		}
	}
	
	public <T> void normalizeDeltas(Collection<? extends ItemDelta<PrismPropertyValue<T>,PrismPropertyDefinition<T>>> deltas,
			RefinedObjectClassDefinition objectClassDefinition) throws SchemaException {
		// TODO Auto-generated method stub
		for (ItemDelta<PrismPropertyValue<T>,PrismPropertyDefinition<T>> delta : deltas){
			if (!ShadowType.F_ATTRIBUTES.equals(ItemPath.getName(delta.getPath().first()))){
				continue;
			}
			RefinedAttributeDefinition rAttrDef = objectClassDefinition.findAttributeDefinition(delta.getElementName());
			if (rAttrDef == null){
				throw new SchemaException("Failed to normalize attribute: " + delta.getElementName()+ ". Definition for this attribute doesn't exist.");
			}
			normalizeDelta(delta, rAttrDef);
		}
		
		
	}
	
	private <T> void normalizeDelta(ItemDelta<PrismPropertyValue<T>,PrismPropertyDefinition<T>> delta, RefinedAttributeDefinition rAttrDef) throws SchemaException{
		MatchingRule<T> matchingRule = matchingRuleRegistry.getMatchingRule(rAttrDef.getMatchingRuleQName(), rAttrDef.getTypeName());
		if (matchingRule != null) {
			if (delta.getValuesToReplace() != null){
				normalizeValues(delta.getValuesToReplace(), matchingRule);
			}
			if (delta.getValuesToAdd() != null){
				normalizeValues(delta.getValuesToAdd(), matchingRule);
			}
			
			if (delta.getValuesToDelete() != null){
				normalizeValues(delta.getValuesToDelete(), matchingRule);
			}
		}
	}
	
	private <T> void normalizeValues(Collection<PrismPropertyValue<T>> values, MatchingRule<T> matchingRule){
		for (PrismPropertyValue<T> pval: values) {
			T normalizedRealValue = matchingRule.normalize(pval.getValue());
			pval.setValue(normalizedRealValue);
		}
	}
	
	<T> T getNormalizedAttributeValue(PrismPropertyValue<T> pval, RefinedAttributeDefinition rAttrDef) throws SchemaException {
		MatchingRule<T> matchingRule = matchingRuleRegistry.getMatchingRule(rAttrDef.getMatchingRuleQName(), rAttrDef.getTypeName());
		if (matchingRule != null) {
			T normalizedRealValue = matchingRule.normalize(pval.getValue());
			return normalizedRealValue;
		} else {
			return pval.getValue();
		}
	}
	
	private <T> Collection<T> getNormalizedAttributeValues(ResourceAttribute<T> attribute, RefinedAttributeDefinition rAttrDef) throws SchemaException {
		MatchingRule<T> matchingRule = matchingRuleRegistry.getMatchingRule(rAttrDef.getMatchingRuleQName(), rAttrDef.getTypeName());
		if (matchingRule == null) {
			return attribute.getRealValues();
		} else {
			Collection<T> normalizedValues = new ArrayList<T>();
			for (PrismPropertyValue<T> pval: attribute.getValues()) {
				T normalizedRealValue = matchingRule.normalize(pval.getValue());
				normalizedValues.add(normalizedRealValue);
			}
			return normalizedValues;
		}
	}

	public <T> boolean compareAttribute(RefinedObjectClassDefinition refinedObjectClassDefinition,
			ResourceAttribute<T> attributeA, T... valuesB) throws SchemaException {
		RefinedAttributeDefinition refinedAttributeDefinition = refinedObjectClassDefinition.findAttributeDefinition(attributeA.getElementName());
		Collection<T> valuesA = getNormalizedAttributeValues(attributeA, refinedAttributeDefinition);
		return MiscUtil.unorderedCollectionEquals(valuesA, Arrays.asList(valuesB));
	}
	
	public <T> boolean compareAttribute(RefinedObjectClassDefinition refinedObjectClassDefinition,
			ResourceAttribute<T> attributeA, ResourceAttribute<T> attributeB) throws SchemaException {
		RefinedAttributeDefinition refinedAttributeDefinition = refinedObjectClassDefinition.findAttributeDefinition(attributeA.getElementName());
		Collection<T> valuesA = getNormalizedAttributeValues(attributeA, refinedAttributeDefinition);
		
		refinedAttributeDefinition = refinedObjectClassDefinition.findAttributeDefinition(attributeA.getElementName());
		Collection<T> valuesB = getNormalizedAttributeValues(attributeB, refinedAttributeDefinition);
		return MiscUtil.unorderedCollectionEquals(valuesA, valuesB);
	}

	

}
