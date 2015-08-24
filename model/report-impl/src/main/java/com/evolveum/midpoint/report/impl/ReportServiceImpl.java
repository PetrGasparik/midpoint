package com.evolveum.midpoint.report.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.audit.api.AuditEventRecord;
import com.evolveum.midpoint.audit.api.AuditService;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.model.common.expression.ExpressionFactory;
import com.evolveum.midpoint.model.common.expression.ExpressionUtil;
import com.evolveum.midpoint.model.common.expression.ExpressionVariables;
import com.evolveum.midpoint.model.common.expression.functions.FunctionLibrary;
import com.evolveum.midpoint.model.common.expression.script.jsr223.Jsr223ScriptEvaluator;
import com.evolveum.midpoint.model.impl.expr.ModelExpressionThreadLocalHolder;
import com.evolveum.midpoint.prism.Objectable;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.parser.QueryConvertor;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.TypeFilter;
import com.evolveum.midpoint.report.api.ReportService;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectResolver;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.prism.xml.ns._public.query_3.SearchFilterType;

@Component
public class ReportServiceImpl implements ReportService {

	private static final transient Trace LOGGER = TraceManager.getTrace(ReportServiceImpl.class);

	@Autowired(required = true)
	private ModelService model;

	@Autowired(required = true)
	private TaskManager taskManager;

	@Autowired(required = true)
	private PrismContext prismContext;

	@Autowired(required = true)
	private ExpressionFactory expressionFactory;

	@Autowired(required = true)
	private ObjectResolver objectResolver;

	@Autowired(required = true)
	private AuditService auditService;

	@Override
	public ObjectQuery parseQuery(String query, Map<QName, Object> parameters) throws SchemaException,
			ObjectNotFoundException, ExpressionEvaluationException {
		if (StringUtils.isBlank(query)) {
			return null;
		}

		ObjectQuery parsedQuery = null;
		try {
			SearchFilterType filter = (SearchFilterType) prismContext.parseAtomicValue(query,
					SearchFilterType.COMPLEX_TYPE);
			LOGGER.trace("filter {}", filter);
			ObjectFilter f = QueryConvertor.parseFilter(filter, UserType.class, prismContext);
			LOGGER.trace("f {}", f.debugDump());
			if (!(f instanceof TypeFilter)) {
				throw new IllegalArgumentException(
						"Defined query must contain type. Use 'type filter' in your report query.");
			}

			ObjectFilter subFilter = ((TypeFilter) f).getFilter();
			ObjectQuery q = ObjectQuery.createObjectQuery(subFilter);
			Task task = taskManager.createTaskInstance();
			ExpressionVariables variables = new ExpressionVariables();
			variables.addVariableDefinitions(parameters);

			ModelExpressionThreadLocalHolder.pushCurrentResult(task.getResult());
			ModelExpressionThreadLocalHolder.pushCurrentTask(task);
			q = ExpressionUtil.evaluateQueryExpressions(q, variables, expressionFactory, prismContext,
					"parsing expression values for report", task, task.getResult());
			((TypeFilter) f).setFilter(q.getFilter());
			parsedQuery = ObjectQuery.createObjectQuery(f);

			LOGGER.trace("query dump {}", parsedQuery.debugDump());
		} catch (SchemaException | ObjectNotFoundException | ExpressionEvaluationException e) {
			// TODO Auto-generated catch block
			throw e;
		} finally {
			ModelExpressionThreadLocalHolder.popCurrentResult();
			ModelExpressionThreadLocalHolder.popCurrentTask();
		}
		return parsedQuery;

	}

	@Override
	public Collection<PrismObject<? extends ObjectType>> searchObjects(ObjectQuery query,
			Collection<SelectorOptions<GetOperationOptions>> options) throws SchemaException,
			ObjectNotFoundException, SecurityViolationException, CommunicationException,
			ConfigurationException {
		// List<PrismObject<? extends ObjectType>> results = new ArrayList<>();

		// GetOperationOptions options = GetOperationOptions.createRaw();

		if (!(query.getFilter() instanceof TypeFilter)) {
			throw new IllegalArgumentException("Query must contain type filter.");
		}

		TypeFilter typeFilter = (TypeFilter) query.getFilter();
		QName type = typeFilter.getType();
		Class clazz = prismContext.getSchemaRegistry().determineCompileTimeClass(type);
		if (clazz == null) {
			clazz = prismContext.getSchemaRegistry().findObjectDefinitionByType(type).getCompileTimeClass();
		}

		ObjectQuery queryForSearch = ObjectQuery.createObjectQuery(typeFilter.getFilter());

		Task task = taskManager.createTaskInstance(ReportService.class.getName() + ".searchObjects()");
		OperationResult parentResult = task.getResult();

		// options.add(new
		// SelectorOptions(GetOperationOptions.createResolveNames()));
		GetOperationOptions getOptions = GetOperationOptions.createResolveNames();
		getOptions.setRaw(Boolean.TRUE);
		options = SelectorOptions.createCollection(getOptions);
		List<PrismObject<? extends ObjectType>> results;
		try {
			results = model.searchObjects(clazz, queryForSearch, options, task, parentResult);
			return results;
		} catch (SchemaException | ObjectNotFoundException | SecurityViolationException
				| CommunicationException | ConfigurationException e) {
			// TODO Auto-generated catch block
			throw e;
		}

	}

	public Collection<PrismObject<? extends ObjectType>> evaluateScript(String script,
			Map<QName, Object> parameters) throws SchemaException, ExpressionEvaluationException,
			ObjectNotFoundException {
		List<PrismObject<? extends ObjectType>> results = new ArrayList<>();

		ExpressionVariables variables = new ExpressionVariables();
		variables.addVariableDefinitions(parameters);

		// special variable for audit report
		variables.addVariableDefinition(new QName("auditParams"), getConvertedParams(parameters));

		Task task = taskManager.createTaskInstance(ReportService.class.getName() + ".searchObjects()");
		OperationResult parentResult = task.getResult();

		Collection<FunctionLibrary> functions = createFunctionLibraries();

		Jsr223ScriptEvaluator scripts = new Jsr223ScriptEvaluator("Groovy", prismContext,
				prismContext.getDefaultProtector());
		ModelExpressionThreadLocalHolder.pushCurrentResult(task.getResult());
		ModelExpressionThreadLocalHolder.pushCurrentTask(task);
		Object o = null;
		try{
			o = scripts.evaluateReportScript(script, variables, objectResolver, functions, "desc",
				parentResult);
		} finally{
			ModelExpressionThreadLocalHolder.popCurrentResult();
			ModelExpressionThreadLocalHolder.popCurrentTask();
		}
		if (o != null) {

			if (Collection.class.isAssignableFrom(o.getClass())) {
				Collection resultSet = (Collection) o;
				if (resultSet != null && !resultSet.isEmpty()) {
					for (Object obj : resultSet) {
						if (obj instanceof PrismObject) {
							results.add((PrismObject) obj);
						} else if (obj instanceof Objectable) {
							results.add(((Objectable) obj).asPrismObject());
						}
					}
				}

			} else {
				results.add((PrismObject) o);
			}
		}

		return results;
	}

	public Collection<AuditEventRecord> evaluateAuditScript(String script, Map<QName, Object> parameters)
			throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException {
		Collection<AuditEventRecord> results = new ArrayList<AuditEventRecord>();

		ExpressionVariables variables = new ExpressionVariables();
			variables.addVariableDefinition(new QName("auditParams"), getConvertedParams(parameters));

		Task task = taskManager.createTaskInstance(ReportService.class.getName() + ".searchObjects()");
		OperationResult parentResult = task.getResult();

		Collection<FunctionLibrary> functions = createFunctionLibraries();

		Jsr223ScriptEvaluator scripts = new Jsr223ScriptEvaluator("Groovy", prismContext,
				prismContext.getDefaultProtector());
		ModelExpressionThreadLocalHolder.pushCurrentResult(task.getResult());
		ModelExpressionThreadLocalHolder.pushCurrentTask(task);
		Object o = null;
		try{
			o = scripts.evaluateReportScript(script, variables, objectResolver, functions, "desc",
				parentResult);
		} finally {
			ModelExpressionThreadLocalHolder.popCurrentResult();
			ModelExpressionThreadLocalHolder.popCurrentTask();
		}
		if (o != null) {

			if (Collection.class.isAssignableFrom(o.getClass())) {
				Collection resultSet = (Collection) o;
				if (resultSet != null && !resultSet.isEmpty()) {
					for (Object obj : resultSet) {
						if (!(obj instanceof AuditEventRecord)) {
							LOGGER.warn("Skipping result, not an audit event record " + obj);
							continue;
						}
						results.add((AuditEventRecord) obj);
					}

				}

			} else {
				results.add((AuditEventRecord) o);
			}
		}

		return results;
	}

	private Map<String, Object> getConvertedParams(Map<QName, Object> parameters) {
		if (parameters == null) {
			return null;
		}

		Map<String, Object> resultParams = new HashMap<String, Object>();
		Set<Entry<QName, Object>> paramEntries = parameters.entrySet();
		for (Entry<QName, Object> e : paramEntries) {
			if (e.getValue() instanceof PrismPropertyValue) {
				resultParams.put(e.getKey().getLocalPart(), ((PrismPropertyValue) e.getValue()).getValue());
			} else {
				resultParams.put(e.getKey().getLocalPart(), e.getValue());
			}
		}

		return resultParams;
	}

	private Collection<FunctionLibrary> createFunctionLibraries() {
		FunctionLibrary functionLib = ExpressionUtil.createBasicFunctionLibrary(prismContext,
				prismContext.getDefaultProtector());
		FunctionLibrary midPointLib = new FunctionLibrary();
		midPointLib.setVariableName("report");
		midPointLib.setNamespace("http://midpoint.evolveum.com/xml/ns/public/function/report-3");
		ReportFunctions reportFunctions = new ReportFunctions(prismContext, model, taskManager, auditService);
		midPointLib.setGenericFunctions(reportFunctions);

		Collection<FunctionLibrary> functions = new ArrayList<>();
		functions.add(functionLib);
		functions.add(midPointLib);
		return functions;
	}

}
