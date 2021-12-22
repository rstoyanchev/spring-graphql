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

import java.util.Map;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.RequestInput;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class WebClientGraphQlTransport implements GraphQlTransport {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_PARAMETERIZED_TYPE_REF =
			new ParameterizedTypeReference<Map<String, Object>>() {};


	private final WebClient webClient;


	public WebClientGraphQlTransport(WebClient webClient) {
		this.webClient = webClient;
	}


	@Override
	public Mono<Map<String, Object>> execute(RequestInput input) {
			return this.webClient.post()
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.bodyValue(input.toMap())
					.retrieve()
					.bodyToMono(MAP_PARAMETERIZED_TYPE_REF);
	}

	@Override
	public Flux<Map<String, Object>> executeSubscription(RequestInput input) {
		return this.webClient.post()
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.bodyValue(input.toMap())
				.retrieve()
				.bodyToFlux(MAP_PARAMETERIZED_TYPE_REF);
	}

}
