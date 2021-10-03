/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.graphql.client;

import java.util.List;

import graphql.GraphQLError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.Nullable;

/**
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface GraphQlClient {

	/**
	 * Prepare to perform a GraphQL request with the given operation which may
	 * be a query, mutation, or a subscription.
	 * @param query the operation to be performed
	 * @return spec for response assertions
	 * @throws AssertionError if the response status is not 200 (OK)
	 */
	RequestSpec query(String query);


	/**
	 * Declare options to perform a GraphQL request.
	 */
	interface ExecuteSpec {

		Mono<ResponseSpec> execute();

		Flux<ResponseSpec> executeSubscription();

	}

	/**
	 * Declare options to gather input for a GraphQL request and execute it.
	 */
	interface RequestSpec<T extends RequestSpec<T>> extends ExecuteSpec {

		/**
		 * Set the operation name.
		 * @param name the operation name
		 * @return this request spec
		 */
		T operationName(@Nullable String name);

		/**
		 * Add a variable.
		 * @param name the variable name
		 * @param value the variable value
		 * @return this request spec
		 */
		T variable(String name, Object value);

	}


	/**
	 * Declare options to switch to different part of the GraphQL response.
	 */
	interface TraverseSpec {

		/**
		 * Switch to a path under the "data" section of the GraphQL response. The path can
		 * be an operation root type name, e.g. "project", or a nested path such as
		 * "project.name", or any
		 * <a href="https://github.com/jayway/JsonPath">JsonPath</a>.
		 * @param path the path to switch to
		 * @return spec for asserting the content under the given path
		 * @throws AssertionError if the GraphQL response contains
		 * <a href="https://spec.graphql.org/June2018/#sec-Errors">errors</a> that have
		 * not be checked via {@link ResponseSpec#errors()}
		 */
		PathSpec path(String path);

	}


	interface ResponseSpec extends TraverseSpec {

		/**
		 *
		 * @return
		 */
		List<GraphQLError> errors();

	}


	/**
	 * Declare options available to assert data at a given path.
	 */
	interface PathSpec extends TraverseSpec {

		/**
		 * Convert the data at the given path to the target type.
		 * @param entityType the type to convert to
		 * @param <D> the target entity type
		 * @return spec to assert the converted entity with
		 */
		<D> D toEntity(Class<D> entityType);

		/**
		 * Convert the data at the given path to the target type.
		 * @param entityType the type to convert to
		 * @param <D> the target entity type
		 * @return spec to assert the converted entity with
		 */
		<D> D toEntity(ParameterizedTypeReference<D> entityType);

		/**
		 * Convert the data at the given path to a List of the target type.
		 * @param elementType the type of element to convert to
		 * @param <D> the target entity type
		 * @return spec to assert the converted List of entities with
		 */
		<D> List<D> entityList(Class<D> elementType);

		/**
		 * Convert the data at the given path to a List of the target type.
		 * @param elementType the type to convert to
		 * @param <D> the target entity type
		 * @return spec to assert the converted List of entities with
		 */
		<D> List<D> entityList(ParameterizedTypeReference<D> elementType);

	}


}