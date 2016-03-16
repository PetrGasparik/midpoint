/*
 * Copyright (c) 2010-2016 Evolveum
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

package com.evolveum.midpoint.model.impl.visualizer;

import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.evolveum.midpoint.schema.GetOperationOptions.createNoFetch;
import static com.evolveum.midpoint.schema.SelectorOptions.createCollection;
import static com.evolveum.midpoint.schema.util.ObjectTypeUtil.*;

/**
 * Resolves definitions and old values.
 * Currently NOT references.
 *
 * @author mederly
 */
@Component
public class Resolver {

	private static final Trace LOGGER = TraceManager.getTrace(Resolver.class);

	public static final String CLASS_DOT = Resolver.class.getName() + ".";
	private static final String OP_RESOLVE = CLASS_DOT + "resolve";

	@Autowired
	private PrismContext prismContext;

	@Autowired
	private ModelService modelService;

	@Autowired
	private Visualizer visualizer;

	public <O extends ObjectType> void resolve(PrismObject<O> object, Task task, OperationResult result) throws SchemaException {
		/*if (object.getDefinition() == null) */{
			Class<O> clazz = object.getCompileTimeClass();
			if (clazz == null) {
				warn(result, "Compile time class for " + toShortString(object) + " is not known");
			} else {
				PrismObjectDefinition<O> def = prismContext.getSchemaRegistry().findObjectDefinitionByCompileTimeClass(clazz);
				if (def != null) {
					object.applyDefinition(def);
				} else {
					warn(result, "Definition for " + toShortString(object) + " couldn't be found");
				}
			}
		}
	}

	public <O extends ObjectType> void resolve(ObjectDelta<O> objectDelta, Task task, OperationResult result) throws SchemaException {
		if (objectDelta.isAdd()) {
			resolve(objectDelta.getObjectToAdd(), task, result);
		} else if (objectDelta.isDelete()) {
			// nothing to do
		} else {
			PrismObject<O> originalObject = null;
			boolean originalObjectFetched = false;
			final Class<O> objectTypeClass = objectDelta.getObjectTypeClass();
			PrismObjectDefinition<O> objectDefinition = prismContext.getSchemaRegistry().findObjectDefinitionByCompileTimeClass(objectTypeClass);
			if (objectDefinition == null) {
				warn(result, "Definition for " + objectTypeClass + " couldn't be found");
			}
			for (ItemDelta itemDelta : objectDelta.getModifications()) {
				/*if (itemDelta.getDefinition() == null)*/ {
					ItemDefinition<?> def = objectDefinition.findItemDefinition(itemDelta.getPath());
					if (def != null) {
						itemDelta.applyDefinition(def);
					}
				}
				if (itemDelta.getEstimatedOldValues() == null) {
					if (!originalObjectFetched) {
						final String oid = objectDelta.getOid();
						try {
							originalObject = modelService.getObject(objectTypeClass, oid, createCollection(createNoFetch()), task, result);
						} catch (RuntimeException|SchemaException|ConfigurationException |CommunicationException |SecurityViolationException e) {
							LoggingUtils.logUnexpectedException(LOGGER, "Couldn't resolve object {}", e, oid);
							warn(result, "Couldn't resolve object " + oid + ": " + e.getMessage(), e);
						} catch (ObjectNotFoundException e) {
							LoggingUtils.logUnexpectedException(LOGGER, "Couldn't resolve object {}", e, oid);
							warn(result, "Couldn't resolve object " + oid + ": " + e.getMessage(), e);
						}
						originalObjectFetched = true;
					}
					if (originalObject != null) {
						Item<?,?> originalItem = originalObject.findItem(itemDelta.getPath());
						if (originalItem != null) {
							itemDelta.setEstimatedOldValues(new ArrayList(originalItem.getValues()));
						}
					}
				}
			}
		}
	}

	private void warn(OperationResult result, String text, Exception e) {
		result.createSubresult(OP_RESOLVE).recordWarning(text, e);
	}

	private void warn(OperationResult result, String text) {
		result.createSubresult(OP_RESOLVE).recordWarning(text);
	}

	// TODO caching retrieved objects
	public void resolve(List<ObjectDelta<? extends ObjectType>> deltas, Task task, OperationResult result) throws SchemaException {
		for (ObjectDelta<? extends ObjectType> delta : deltas) {
			resolve(delta, task, result);
		}
	}
}
