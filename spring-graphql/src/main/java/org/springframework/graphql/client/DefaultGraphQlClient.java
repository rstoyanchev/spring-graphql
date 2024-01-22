/*
 * Copyright 2002-2024 the original author or authors.
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

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.support.DocumentSource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Default, final {@link GraphQlClient} implementation for use with any transport.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultGraphQlClient implements GraphQlClient {

	private final DocumentSource documentSource;

	private final SyncGraphQlClientInterceptor.Chain blockingChain;

	private final GraphQlClientInterceptor.Chain nonBlockingChain;

	private final GraphQlClientInterceptor.SubscriptionChain executeSubscriptionChain;

	@Nullable
	private final Duration blockingTimeout;


	DefaultGraphQlClient(
			DocumentSource documentSource, SyncGraphQlClientInterceptor.Chain blockingChain,
			Scheduler scheduler, @Nullable Duration blockingTimeout) {

		Assert.notNull(documentSource, "DocumentSource is required");
		Assert.notNull(blockingChain, "Execution chain is required");
		Assert.notNull(scheduler, "Scheduler is required");

		this.documentSource = documentSource;
		this.blockingChain = blockingChain;
		this.nonBlockingChain = adaptToNonBlockingChain(blockingChain, scheduler);
		this.executeSubscriptionChain = request -> Flux.error(new IllegalStateException("Subscriptions on supported"));
		this.blockingTimeout = blockingTimeout;
	}

	DefaultGraphQlClient(
			DocumentSource documentSource,
			GraphQlClientInterceptor.Chain nonBlockingChain,
			GraphQlClientInterceptor.SubscriptionChain subscriptionChain,
			@Nullable Duration blockingTimeout) {

		Assert.notNull(documentSource, "DocumentSource is required");
		Assert.notNull(nonBlockingChain, "Execution chain is required");
		Assert.notNull(subscriptionChain, "Subscription execution chain is required");

		this.documentSource = documentSource;
		this.blockingChain = adaptToBlockingChain(nonBlockingChain, blockingTimeout);
		this.nonBlockingChain = nonBlockingChain;
		this.executeSubscriptionChain = subscriptionChain;
		this.blockingTimeout = blockingTimeout;
	}

	private static GraphQlClientInterceptor.Chain adaptToNonBlockingChain(
			SyncGraphQlClientInterceptor.Chain blockingChain, Scheduler scheduler) {

		return request -> Mono.fromCallable(() -> blockingChain.next(request)).subscribeOn(scheduler);
	}

	@SuppressWarnings("DataFlowIssue")
	private static SyncGraphQlClientInterceptor.Chain adaptToBlockingChain(
			GraphQlClientInterceptor.Chain executeChain, @Nullable Duration blockingTimeout) {

		return (request -> blockingTimeout != null ?
				executeChain.next(request).block(blockingTimeout) : executeChain.next(request).block());
	}


	@Override
	public RequestSpec document(String document) {
		return new DefaultRequestSpec(Mono.just(document));
	}

	@Override
	public RequestSpec documentName(String name) {
		return new DefaultRequestSpec(this.documentSource.getDocument(name));
	}

	/**
	 * The default client is unaware of transport details, and cannot implement
	 * mutate directly. It should be wrapped from transport aware extensions via
	 * {@link AbstractDelegatingGraphQlClient} that also implement mutate.
	 */
	@Override
	public Builder<?> mutate() {
		throw new UnsupportedOperationException();
	}


	/**
	 * Default {@link RequestSpec} implementation.
	 */
	private final class DefaultRequestSpec implements RequestSpec {

		private final Mono<String> documentMono;

		@Nullable
		private String operationName;

		private final Map<String, Object> variables = new LinkedHashMap<>();

		private final Map<String, Object> attributes = new LinkedHashMap<>();

		private final Map<String, Object> extensions = new LinkedHashMap<>();

		DefaultRequestSpec(Mono<String> documentMono) {
			Assert.notNull(documentMono, "'document' is required");
			this.documentMono = documentMono;
		}

		@Override
		public DefaultRequestSpec operationName(@Nullable String operationName) {
			this.operationName = operationName;
			return this;
		}

		@Override
		public DefaultRequestSpec variable(String name, @Nullable Object value) {
			this.variables.put(name, value);
			return this;
		}

		@Override
		public RequestSpec variables(Map<String, Object> variables) {
			this.variables.putAll(variables);
			return this;
		}

		@Override
		public RequestSpec extension(String name, @Nullable Object value) {
			this.extensions.put(name, value);
			return this;
		}

		@Override
		public RequestSpec extensions(Map<String, Object> extensions) {
			this.extensions.putAll(extensions);
			return this;
		}

		@Override
		public RequestSpec attribute(String name, Object value) {
			this.attributes.put(name, value);
			return this;
		}

		@Override
		public RequestSpec attributes(Consumer<Map<String, Object>> attributesConsumer) {
			attributesConsumer.accept(this.attributes);
			return this;
		}

		@Override
		public RetrieveSyncSpec retrieveSync(String path) {
			ClientGraphQlResponse response = executeSync();
			return new DefaultRetrieveSyncSpec(response, path);
		}

		@Override
		public RetrieveSpec retrieve(String path) {
			return new DefaultRetrieveSpec(execute(), path);
		}

		@Override
		public RetrieveSubscriptionSpec retrieveSubscription(String path) {
			return new DefaultRetrieveSubscriptionSpec(executeSubscription(), path);
		}

		@SuppressWarnings("DataFlowIssue")
		@Override
		public ClientGraphQlResponse executeSync() {
			Mono<ClientGraphQlRequest> mono = initRequest();
			ClientGraphQlRequest request = (blockingTimeout != null ? mono.block(blockingTimeout) : mono.block());
			return blockingChain.next(request);
		}

		@Override
		public Mono<ClientGraphQlResponse> execute() {
			return initRequest().flatMap(request -> nonBlockingChain.next(request)
					.onErrorResume(
							ex -> !(ex instanceof GraphQlClientException),
							ex -> Mono.error(new GraphQlTransportException(ex, request))));
		}

		@Override
		public Flux<ClientGraphQlResponse> executeSubscription() {
			return initRequest().flatMapMany(request -> executeSubscriptionChain.next(request)
					.onErrorResume(
							ex -> !(ex instanceof GraphQlClientException),
							ex -> Mono.error(new GraphQlTransportException(ex, request))));
		}

		private Mono<ClientGraphQlRequest> initRequest() {
			return this.documentMono.map(document -> new DefaultClientGraphQlRequest(
					document, this.operationName, this.variables, this.extensions, this.attributes));
		}

	}


	private static class RetrieveSpecSupport {

		private final String path;

		protected RetrieveSpecSupport(String path) {
			this.path = path;
		}

		/**
		 * Return the field or {@code null}, but only if the response is valid and
		 * there are no field errors. Raise {@link FieldAccessException} otherwise.
		 * @throws FieldAccessException in case of an invalid response or any
		 * field error at, above or below the field path
		 */
		@Nullable
		protected ClientResponseField getValidField(ClientGraphQlResponse response) {
			ClientResponseField field = response.field(this.path);
			if (!response.isValid() || !field.getErrors().isEmpty()) {
				throw new FieldAccessException(
						((DefaultClientGraphQlResponse) response).getRequest(), response, field);
			}
			return (field.getValue() != null ? field : null);
		}

	}


	private static class DefaultRetrieveSyncSpec extends RetrieveSpecSupport implements RetrieveSyncSpec {

		private final ClientGraphQlResponse response;

		DefaultRetrieveSyncSpec(ClientGraphQlResponse response, String path) {
			super(path);
			this.response = response;
		}

		@Override
		public <D> D toEntity(Class<D> entityType) {
			ClientResponseField field = getValidField(this.response);
			return (field != null ? field.toEntity(entityType) : null);
		}

		@Override
		public <D> D toEntity(ParameterizedTypeReference<D> entityType) {
			ClientResponseField field = getValidField(this.response);
			return (field != null ? field.toEntity(entityType) : null);
		}

		@Override
		public <D> List<D> toEntityList(Class<D> elementType) {
			ClientResponseField field = getValidField(this.response);
			return (field != null ? field.toEntityList(elementType) : Collections.emptyList());
		}

		@Override
		public <D> List<D> toEntityList(ParameterizedTypeReference<D> elementType) {
			ClientResponseField field = getValidField(this.response);
			return (field != null ? field.toEntityList(elementType) : Collections.emptyList());
		}

	}


	private static class DefaultRetrieveSpec extends RetrieveSpecSupport implements RetrieveSpec {

		private final Mono<ClientGraphQlResponse> responseMono;

		DefaultRetrieveSpec(Mono<ClientGraphQlResponse> responseMono, String path) {
			super(path);
			this.responseMono = responseMono;
		}

		@Override
		public <D> Mono<D> toEntity(Class<D> entityType) {
			return this.responseMono.mapNotNull(this::getValidField).mapNotNull(field -> field.toEntity(entityType));
		}

		@Override
		public <D> Mono<D> toEntity(ParameterizedTypeReference<D> entityType) {
			return this.responseMono.mapNotNull(this::getValidField).mapNotNull(field -> field.toEntity(entityType));
		}

		@Override
		public <D> Mono<List<D>> toEntityList(Class<D> elementType) {
			return this.responseMono.map(response -> {
				ClientResponseField field = getValidField(response);
				return (field != null ? field.toEntityList(elementType) : Collections.emptyList());
			});
		}

		@Override
		public <D> Mono<List<D>> toEntityList(ParameterizedTypeReference<D> elementType) {
			return this.responseMono.map(response -> {
				ClientResponseField field = getValidField(response);
				return (field != null ? field.toEntityList(elementType) : Collections.emptyList());
			});
		}

	}


	private static class DefaultRetrieveSubscriptionSpec extends RetrieveSpecSupport implements RetrieveSubscriptionSpec {

		private final Flux<ClientGraphQlResponse> responseFlux;

		DefaultRetrieveSubscriptionSpec(Flux<ClientGraphQlResponse> responseFlux, String path) {
			super(path);
			this.responseFlux = responseFlux;
		}

		@Override
		public <D> Flux<D> toEntity(Class<D> entityType) {
			return this.responseFlux.mapNotNull(this::getValidField).mapNotNull(field -> field.toEntity(entityType));
		}

		@Override
		public <D> Flux<D> toEntity(ParameterizedTypeReference<D> entityType) {
			return this.responseFlux.mapNotNull(this::getValidField).mapNotNull(field -> field.toEntity(entityType));
		}

		@Override
		public <D> Flux<List<D>> toEntityList(Class<D> elementType) {
			return this.responseFlux.map(response -> {
				ClientResponseField field = getValidField(response);
				return (field != null ? field.toEntityList(elementType) : Collections.emptyList());
			});
		}

		@Override
		public <D> Flux<List<D>> toEntityList(ParameterizedTypeReference<D> elementType) {
			return this.responseFlux.map(response -> {
				ClientResponseField field = getValidField(response);
				return (field != null ? field.toEntityList(elementType) : Collections.emptyList());
			});
		}

	}

}
