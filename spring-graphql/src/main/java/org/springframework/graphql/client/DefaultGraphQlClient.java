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

import java.util.LinkedHashMap;
import java.util.Map;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

/**
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultGraphQlClient implements GraphQlClient {

	private final WebClient webClient;


	DefaultGraphQlClient(WebClient webClient) {
		Assert.notNull(webClient, "WebClient is required");
		this.webClient = webClient;
	}


	@Override
	public RequestSpec query(String query) {
		return null;
	}


	private static final class DefaultRequestSpec implements RequestSpec<DefaultRequestSpec> {

		private final WebClient webClient;

		private final String query;

		@Nullable
		private String operationName;

		private final Map<String, Object> variables = new LinkedHashMap<>();

		DefaultRequestSpec(WebClient webClient, String query) {
			Assert.notNull(query, "`query` is required");
			this.webClient = webClient;
			this.query = query;
		}

		@Override
		public DefaultRequestSpec operationName(String name) {
			this.operationName = name;
			return this;
		}

		@Override
		public DefaultRequestSpec variable(String name, Object value) {
			this.variables.put(name, value);
			return this;
		}

		@Override
		public Mono<ResponseSpec> execute() {
			EntityExchangeResult<byte[]> result = this.webClient.post()
					.contentType(MediaType.APPLICATION_JSON)
					.headers(headers -> headers.putAll(webInput.getHeaders()))
					.bodyValue(webInput.toMap())
					.exchange()
					.expectStatus()
					.isOk()
					.expectHeader()
					.contentType(MediaType.APPLICATION_JSON)
					.expectBody()
					.returnResult();
			return null;
		}

		@Override
		public Flux<ResponseSpec> executeSubscription() {
			return null;
		}

	}



}
