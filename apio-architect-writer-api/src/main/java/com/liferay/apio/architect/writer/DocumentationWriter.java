/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.apio.architect.writer;

import static com.liferay.apio.architect.operation.Method.DELETE;
import static com.liferay.apio.architect.operation.Method.GET;
import static com.liferay.apio.architect.operation.Method.POST;
import static com.liferay.apio.architect.operation.Method.PUT;

import com.google.gson.JsonObject;

import com.liferay.apio.architect.alias.RequestFunction;
import com.liferay.apio.architect.consumer.TriConsumer;
import com.liferay.apio.architect.documentation.Documentation;
import com.liferay.apio.architect.form.Form;
import com.liferay.apio.architect.form.FormField;
import com.liferay.apio.architect.message.json.DocumentationMessageMapper;
import com.liferay.apio.architect.message.json.JSONObjectBuilder;
import com.liferay.apio.architect.operation.Method;
import com.liferay.apio.architect.operation.Operation;
import com.liferay.apio.architect.related.RelatedCollection;
import com.liferay.apio.architect.related.RelatedModel;
import com.liferay.apio.architect.representor.BaseRepresentor;
import com.liferay.apio.architect.representor.Representor;
import com.liferay.apio.architect.representor.function.FieldFunction;
import com.liferay.apio.architect.representor.function.NestedFieldFunction;
import com.liferay.apio.architect.request.RequestInfo;
import com.liferay.apio.architect.routes.CollectionRoutes;
import com.liferay.apio.architect.routes.ItemRoutes;
import com.liferay.apio.architect.routes.NestedCollectionRoutes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Writes the documentation.
 *
 * @author Alejandro Hernández
 */
public class DocumentationWriter {

	/**
	 * Creates a new {@code DocumentationWriter} object, without creating the
	 * builder.
	 *
	 * @param  function the function that transforms a builder into the {@code
	 *         DocumentationWriter}
	 * @return the {@code DocumentationWriter} instance
	 */
	public static DocumentationWriter create(
		Function<Builder, DocumentationWriter> function) {

		return function.apply(new Builder());
	}

	public DocumentationWriter(Builder builder) {
		_documentation = builder._documentation;
		_documentationMessageMapper = builder._documentationMessageMapper;
		_requestInfo = builder._requestInfo;
	}

	/**
	 * Writes the {@link Documentation} to a string.
	 *
	 * @return the JSON representation of the {@code Documentation}
	 */
	public String write() {
		JSONObjectBuilder jsonObjectBuilder = new JSONObjectBuilder();

		RequestFunction<Optional<String>> apiTitleRequestFunction =
			_documentation.getAPITitleRequestFunction();

		apiTitleRequestFunction.apply(
			_requestInfo.getHttpServletRequest()
		).ifPresent(
			title -> _documentationMessageMapper.mapTitle(
				jsonObjectBuilder, title)
		);

		RequestFunction<Optional<String>> apiDescriptionRequestFunction =
			_documentation.getAPIDescriptionRequestFunction();

		apiDescriptionRequestFunction.apply(
			_requestInfo.getHttpServletRequest()
		).ifPresent(
			description -> _documentationMessageMapper.mapDescription(
				jsonObjectBuilder, description)
		);

		_documentationMessageMapper.onStart(
			jsonObjectBuilder, _documentation, _requestInfo.getHttpHeaders());

		Supplier<Map<String, Representor>> representorMapSupplier =
			_documentation.getRepresentorMapSupplier();

		Map<String, Representor> representorMap = representorMapSupplier.get();

		Supplier<Map<String, NestedCollectionRoutes>>
			nestedCollectionMapSupplier =
				_documentation.getNestedCollectionMapSupplier();

		Map<String, NestedCollectionRoutes> nestedCollectionRoutesMap =
			nestedCollectionMapSupplier.get();

		Supplier<Map<String, CollectionRoutes>> routesMapSupplier =
			_documentation.getRoutesMapSupplier();

		Map<String, CollectionRoutes> routesMap = routesMapSupplier.get();

		routesMap.forEach(
			(name, collectionRoutes) -> {
				_addNestedCollectionResources(
					jsonObjectBuilder, representorMap,
					nestedCollectionRoutesMap, name);

				_writeCollectionRoute(
					jsonObjectBuilder, representorMap.get(name), name,
					_documentationMessageMapper::mapResourceCollection,
					this::_writePageOperations);
			});

		Supplier<Map<String, ItemRoutes>> itemRoutesMapSupplier =
			_documentation.getItemRoutesMapSupplier();

		Map<String, ItemRoutes> itemRoutesMap = itemRoutesMapSupplier.get();

		itemRoutesMap.forEach(
			(name, itemRoutes) -> {
				_addNestedCollectionResources(
					jsonObjectBuilder, representorMap,
					nestedCollectionRoutesMap, name);

				_writeRoute(
					jsonObjectBuilder, representorMap.get(name),
					itemRoutes.getFormOptional(), name,
					_documentationMessageMapper::mapResource,
					this::_writeItemOperations);
			});

		_documentationMessageMapper.onFinish(
			jsonObjectBuilder, _documentation, _requestInfo.getHttpHeaders());

		JsonObject jsonObject = jsonObjectBuilder.build();

		return jsonObject.toString();
	}

	/**
	 * Creates {@code DocumentationWriter} instances.
	 */
	public static class Builder {

		/**
		 * Add information about the documentation being written to the builder.
		 *
		 * @param  documentation the documentation being written
		 * @return the updated builder
		 */
		public DocumentationMessageMapperStep documentation(
			Documentation documentation) {

			_documentation = documentation;

			return new DocumentationMessageMapperStep();
		}

		public class BuildStep {

			/**
			 * Constructs and returns a {@code DocumentationWriter} instance
			 * with the information provided to the builder.
			 *
			 * @return the {@code DocumentationWriter} instance
			 */
			public DocumentationWriter build() {
				return new DocumentationWriter(Builder.this);
			}

		}

		public class DocumentationMessageMapperStep {

			/**
			 * Adds information to the builder about the {@link
			 * DocumentationMessageMapper}.
			 *
			 * @param  documentationMessageMapper the {@code
			 *         DocumentationMessageMapper}
			 * @return the updated builder
			 */
			public RequestInfoStep documentationMessageMapper(
				DocumentationMessageMapper documentationMessageMapper) {

				_documentationMessageMapper = documentationMessageMapper;

				return new RequestInfoStep();
			}

		}

		public class RequestInfoStep {

			/**
			 * Adds information to the builder about the request.
			 *
			 * @param  requestInfo the information obtained from the request. It
			 *         can be created by using a {@link RequestInfo.Builder}.
			 * @return the updated builder
			 */
			public BuildStep requestInfo(RequestInfo requestInfo) {
				_requestInfo = requestInfo;

				return new BuildStep();
			}

		}

		private Documentation _documentation;
		private DocumentationMessageMapper _documentationMessageMapper;
		private RequestInfo _requestInfo;

	}

	private void _addNestedCollectionResources(
		JSONObjectBuilder jsonObjectBuilder,
		Map<String, Representor> representorMap, Map<String, ?> nestedRoutesMap,
		String name) {

		Set<String> nestedRoutes = nestedRoutesMap.keySet();

		nestedRoutes.forEach(
			nestedRoute -> {
				String[] routes = nestedRoute.split(name + "-");

				if (routes.length == 2) {
					String route = routes[0];
					String nestedResourceName = routes[1];

					if (route.equals("") && !route.equals(nestedResourceName) &&
						representorMap.containsKey(nestedResourceName)) {

						Representor representor = representorMap.get(
							nestedResourceName);

						_writeCollectionRoute(
							jsonObjectBuilder, representor, nestedResourceName,
							_documentationMessageMapper::mapResourceCollection,
							this::_writePageOperations);
					}
				}
			});
	}

	private Stream _extractKeys(List<FieldFunction> fieldFunctions) {
		Stream<FieldFunction> stream = fieldFunctions.stream();

		return stream.map(fieldFunction -> fieldFunction.key);
	}

	private Optional<FormField> _getFormField(
		String fieldName, Form<FormField> formFieldForm) {

		List<FormField> formFields = formFieldForm.getFormFields();

		Stream<FormField> stream = formFields.stream();

		return stream.filter(
			formField -> formField.name.equals(fieldName)
		).findFirst();
	}

	private Operation _getOperation(
		String operationName, Optional<Form> formOptional, Method method) {

		return formOptional.map(
			form -> new Operation(form, method, operationName)
		).orElse(
			new Operation(method, operationName)
		);
	}

	private void _writeAllFields(
		Representor representor, JSONObjectBuilder resourceJsonObjectBuilder,
		Optional<Form<FormField>> formOptional) {

		_writeNestableFields(
			representor, resourceJsonObjectBuilder, formOptional);

		Stream<RelatedCollection> relatedCollections =
			representor.getRelatedCollections();

		Stream<String> relatedCollectionsKeys = relatedCollections.map(
			RelatedCollection::getKey);

		_writeFields(
			relatedCollectionsKeys, resourceJsonObjectBuilder, formOptional);

		List<NestedFieldFunction> nestedFieldFunctions =
			representor.getNestedFieldFunctions();

		nestedFieldFunctions.forEach(
			nestedFieldFunction -> _writeNestableFields(
				nestedFieldFunction.nestedRepresentor,
				resourceJsonObjectBuilder, formOptional));
	}

	private void _writeCollectionRoute(
		JSONObjectBuilder jsonObjectBuilder, Representor representor,
		String name,
		BiConsumer<JSONObjectBuilder, String> writeResourceBiconsumer,
		TriConsumer<String, String, JSONObjectBuilder>
			writeOperationsBiConsumer) {

		JSONObjectBuilder resourceJsonObjectBuilder = new JSONObjectBuilder();

		List<String> types = representor.getTypes();

		types.forEach(
			type -> {
				_documentationMessageMapper.onStartResource(
					jsonObjectBuilder, resourceJsonObjectBuilder, type);

				writeResourceBiconsumer.accept(resourceJsonObjectBuilder, type);

				writeOperationsBiConsumer.accept(
					name, type, resourceJsonObjectBuilder);

				_documentationMessageMapper.onFinishResource(
					jsonObjectBuilder, resourceJsonObjectBuilder, type);
			});
	}

	private void _writeFields(
		Stream<String> fields, JSONObjectBuilder resourceJsonObjectBuilder,
		Optional<Form<FormField>> formOptional) {

		fields.forEach(
			field -> {
				Optional<FormField> formFieldOptional = formOptional.flatMap(
					formFieldForm -> _getFormField(field, formFieldForm));

				_writeFormField(
					resourceJsonObjectBuilder, field, formFieldOptional);
			});
	}

	private void _writeFormField(
		JSONObjectBuilder resourceJsonObjectBuilder, String fieldName,
		Optional<FormField> formField) {

		JSONObjectBuilder jsonObjectBuilder = new JSONObjectBuilder();

		_documentationMessageMapper.onStartProperty(
			resourceJsonObjectBuilder, jsonObjectBuilder, fieldName);

		boolean required = formField.map(
			field -> field.required
		).orElse(
			false
		);

		_documentationMessageMapper.mapProperty(
			jsonObjectBuilder, fieldName, required);

		_documentationMessageMapper.onFinishProperty(
			resourceJsonObjectBuilder, jsonObjectBuilder, fieldName);
	}

	private void _writeItemOperations(
		String name, String type, JSONObjectBuilder resourceJsonObjectBuilder) {

		Supplier<Map<String, ItemRoutes>> itemRoutesMapSupplier =
			_documentation.getItemRoutesMapSupplier();

		Map<String, ItemRoutes> itemRoutesMap = itemRoutesMapSupplier.get();

		Optional.ofNullable(
			itemRoutesMap.getOrDefault(name, null)
		).ifPresent(
			itemRoutes -> {
				String getOperationName = name + "/retrieve";

				Operation getOperation = new Operation(
					GET, getOperationName, false);

				_writeOperation(
					getOperation, resourceJsonObjectBuilder, name, type);

				String updateOperationName = name + "/update";

				Operation updateOperation = _getOperation(
					updateOperationName, itemRoutes.getFormOptional(), PUT);

				_writeOperation(
					updateOperation, resourceJsonObjectBuilder, name, type);

				String deleteOperationName = name + "/delete";

				Operation deleteOperation = new Operation(
					DELETE, deleteOperationName);

				_writeOperation(
					deleteOperation, resourceJsonObjectBuilder, name, type);
			}
		);
	}

	private void _writeNestableFields(
		BaseRepresentor representor,
		JSONObjectBuilder resourceJsonObjectBuilder,
		Optional<Form<FormField>> formOptional) {

		_writeFields(
			_extractKeys(representor.getBinaryFunctions()),
			resourceJsonObjectBuilder, formOptional);

		_writeFields(
			_extractKeys(representor.getBooleanFunctions()),
			resourceJsonObjectBuilder, formOptional);

		_writeFields(
			_extractKeys(representor.getBooleanListFunctions()),
			resourceJsonObjectBuilder, formOptional);

		_writeFields(
			_extractKeys(representor.getLinkFunctions()),
			resourceJsonObjectBuilder, formOptional);

		_writeFields(
			_extractKeys(representor.getLocalizedStringFunctions()),
			resourceJsonObjectBuilder, formOptional);

		_writeFields(
			_extractKeys(representor.getNestedFieldFunctions()),
			resourceJsonObjectBuilder, formOptional);

		_writeFields(
			_extractKeys(representor.getNumberFunctions()),
			resourceJsonObjectBuilder, formOptional);

		_writeFields(
			_extractKeys(representor.getNumberListFunctions()),
			resourceJsonObjectBuilder, formOptional);

		_writeFields(
			_extractKeys(representor.getStringFunctions()),
			resourceJsonObjectBuilder, formOptional);

		_writeFields(
			_extractKeys(representor.getStringListFunctions()),
			resourceJsonObjectBuilder, formOptional);

		List<RelatedModel> relatedModels = representor.getRelatedModels();

		Stream<RelatedModel> stream = relatedModels.stream();

		Stream<String> relatedModelKeys = stream.map(RelatedModel::getKey);

		_writeFields(relatedModelKeys, resourceJsonObjectBuilder, formOptional);
	}

	private void _writeOperation(
		Operation operation, JSONObjectBuilder jsonObjectBuilder,
		String resourceName, String type) {

		JSONObjectBuilder operationJsonObjectBuilder = new JSONObjectBuilder();

		_documentationMessageMapper.onStartOperation(
			jsonObjectBuilder, operationJsonObjectBuilder, operation);

		_documentationMessageMapper.mapOperation(
			operationJsonObjectBuilder, resourceName, type, operation);

		_documentationMessageMapper.onFinishOperation(
			jsonObjectBuilder, operationJsonObjectBuilder, operation);
	}

	private void _writePageOperations(
		String resource, String type,
		JSONObjectBuilder resourceJsonObjectBuilder) {

		Supplier<Map<String, CollectionRoutes>> routesMapSupplier =
			_documentation.getRoutesMapSupplier();

		Map<String, CollectionRoutes> collectionRoutesMap =
			routesMapSupplier.get();

		Optional.ofNullable(
			collectionRoutesMap.getOrDefault(resource, null)
		).ifPresent(
			collectionRoutes -> {
				_writeOperation(
					new Operation(GET, resource, true),
					resourceJsonObjectBuilder, resource, type);

				String operationName = resource + "/create";

				Operation createOperation = _getOperation(
					operationName, collectionRoutes.getFormOptional(), POST);

				_writeOperation(
					createOperation, resourceJsonObjectBuilder, resource, type);
			}
		);
	}

	private void _writeRoute(
		JSONObjectBuilder jsonObjectBuilder, Representor representor,
		Optional<Form<FormField>> formOptional, String name,
		BiConsumer<JSONObjectBuilder, String> writeResourceBiConsumer,
		TriConsumer<String, String, JSONObjectBuilder>
			writeOperationsBiConsumer) {

		JSONObjectBuilder resourceJsonObjectBuilder = new JSONObjectBuilder();

		List<String> types = representor.getTypes();

		types.forEach(
			type -> {
				_documentationMessageMapper.onStartResource(
					jsonObjectBuilder, resourceJsonObjectBuilder, type);

				writeResourceBiConsumer.accept(resourceJsonObjectBuilder, type);

				writeOperationsBiConsumer.accept(
					name, type, resourceJsonObjectBuilder);

				_writeAllFields(
					representor, resourceJsonObjectBuilder, formOptional);

				_documentationMessageMapper.onFinishResource(
					jsonObjectBuilder, resourceJsonObjectBuilder, type);
			});
	}

	private final Documentation _documentation;
	private final DocumentationMessageMapper _documentationMessageMapper;
	private final RequestInfo _requestInfo;

}