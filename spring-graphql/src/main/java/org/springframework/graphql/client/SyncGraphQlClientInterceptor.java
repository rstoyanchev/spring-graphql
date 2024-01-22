/*
 * Copyright 2020-2024 the original author or authors.
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

/**
 * Synchronous interceptor of {@link GraphQlClient} requests.
 *
 * @author Rossen Stoyanchev
 * @since 1.3
 */
public interface SyncGraphQlClientInterceptor {

	/**
	 * Intercept a single response request (query and mutation operations), and
	 * delegate to the rest of the chain including other interceptors followed
	 * by the {@link GraphQlTransport}.
	 * @param request the request to perform
	 * @param chain the rest of the chain to perform the request
	 * @return the response
	 * @see GraphQlClient.RequestSpec#execute()
	 */
	default ClientGraphQlResponse intercept(ClientGraphQlRequest request, Chain chain) {
		return chain.next(request);
	}

	/**
	 * Return a new {@link SyncGraphQlClientInterceptor} that invokes the current
	 * interceptor first and then the one that is passed in.
	 * @param interceptor the interceptor to delegate to after "this"
	 * @return the new interceptor instance
	 */
	default SyncGraphQlClientInterceptor andThen(SyncGraphQlClientInterceptor interceptor) {
		return new SyncGraphQlClientInterceptor() {

			@Override
			public ClientGraphQlResponse intercept(ClientGraphQlRequest request, Chain chain) {
				return SyncGraphQlClientInterceptor.this.intercept(
						request, nextRequest -> interceptor.intercept(nextRequest, chain));
			}
		};
	}


	/**
	 * Contract to delegate to the rest of the chain.
	 */
	interface Chain {

		/**
		 * Delegate to the rest of the chain to perform the request.
		 * @param request the request to perform
		 * @return the GraphQL response
		 * @throws GraphQlTransportException in case of errors due to transport or
		 * other issues related to encoding and decoding the request and response.
		 */
		ClientGraphQlResponse next(ClientGraphQlRequest request);

	}

}
