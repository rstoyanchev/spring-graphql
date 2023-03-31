/*
 * Copyright 2002-2023 the original author or authors.
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

package org.springframework.graphql.data.query;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLTypeVisitor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.data.repository.query.QueryByExampleExecutor;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.util.TypeInformation;
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.GraphQlRepository;
import org.springframework.graphql.data.query.AutoRegistrationRuntimeWiringConfigurer.DataFetcherFactory;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.execution.SelfDescribingDataFetcher;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.validation.BindException;

/**
 * Main class to create a {@link DataFetcher} from a Query By Example repository.
 * To create an instance, use one of the following:
 * <ul>
 * <li>{@link #builder(QueryByExampleExecutor)}
 * <li>{@link #builder(ReactiveQueryByExampleExecutor)}
 * </ul>
 *
 * <p>For example:
 *
 * <pre class="code">
 * interface BookRepository extends
 *         Repository&lt;Book, String&gt;, QueryByExampleExecutor&lt;Book&gt;{}
 *
 * TypeRuntimeWiring wiring = … ;
 * BookRepository repository = … ;
 *
 * DataFetcher&lt;?&gt; forMany =
 *         wiring.dataFetcher("books", QueryByExampleDataFetcher.builder(repository).many());
 *
 * DataFetcher&lt;?&gt; forSingle =
 *         wiring.dataFetcher("book", QueryByExampleDataFetcher.builder(repository).single());
 * </pre>
 *
 * <p>See methods on {@link QueryByExampleDataFetcher.Builder} and
 * {@link QueryByExampleDataFetcher.ReactiveBuilder} for further options on
 * result projections and sorting.
 *
 * <p>{@code QueryByExampleDataFetcher} {@link #autoRegistrationConfigurer(List, List) exposes}
 * a {@link RuntimeWiringConfigurer} that can auto-register repositories
 * annotated with {@link GraphQlRepository @GraphQlRepository}.
 *
 * @param <T> returned result type
 * @author Greg Turnquist
 * @author Rossen Stoyanchev
 * @since 1.0.0
 *
 * @see GraphQlRepository
 * @see QueryByExampleExecutor
 * @see ReactiveQueryByExampleExecutor
 * @see Example
 * @see <a href="https://docs.spring.io/spring-data/commons/docs/current/reference/html/#query-by-example">
 * Spring Data Query By Example extension</a>
 */
public abstract class QueryByExampleDataFetcher<T> {

	private final static Log logger = LogFactory.getLog(QueryByExampleDataFetcher.class);


	private final TypeInformation<T> domainType;

	private final GraphQlArgumentBinder argumentBinder;


	QueryByExampleDataFetcher(TypeInformation<T> domainType) {
		this.domainType = domainType;
		this.argumentBinder = new GraphQlArgumentBinder();
	}


	/**
	 * Prepare an {@link Example} from GraphQL request arguments.
	 * @param environment contextual info for the GraphQL request
	 * @return the resulting example
	 */
	@SuppressWarnings({"ConstantConditions", "unchecked"})
	protected Example<T> buildExample(DataFetchingEnvironment environment) throws BindException {
		String name = getArgumentName(environment);
		ResolvableType targetType = ResolvableType.forClass(this.domainType.getType());
		return (Example<T>) Example.of(this.argumentBinder.bind(environment, name, targetType));
	}

	/**
	 * For a single argument that is a GraphQL input type, return the argument
	 * name, thereby nesting and having the example Object populated from the
	 * sub-map. Otherwise, {@code null} to bind using the top-level map.
	 */
	@Nullable
	private static String getArgumentName(DataFetchingEnvironment environment) {
		Map<String, Object> arguments = environment.getArguments();
		List<GraphQLArgument> definedArguments = environment.getFieldDefinition().getArguments();
		if (definedArguments.size() == 1) {
			String name = definedArguments.get(0).getName();
			if (arguments.get(name) instanceof Map<?,?>) {
				return name;
			}
		}
		return null;
	}

	protected boolean requiresProjection(Class<?> resultType) {
		return !resultType.equals(this.domainType.getType());
	}

	protected Collection<String> buildPropertyPaths(DataFetchingFieldSelectionSet selection, Class<?> resultType) {

		// Compute selection only for non-projections
		if (this.domainType.getType().equals(resultType) ||
				this.domainType.getType().isAssignableFrom(resultType) ||
				this.domainType.isSubTypeOf(resultType)) {
			return PropertySelection.create(this.domainType, selection).toList();
		}
		return Collections.emptyList();
	}


	/**
	 * Create a new {@link Builder} accepting {@link QueryByExampleExecutor}
	 * to build a {@link DataFetcher}.
	 *
	 * @param executor the QBE repository object to use
	 * @param <T> the domain type of the repository
	 * @return a new builder
	 */
	public static <T> Builder<T, T> builder(QueryByExampleExecutor<T> executor) {
		return new Builder<>(executor, RepositoryUtils.getDomainType(executor));
	}

	/**
	 * Create a new {@link ReactiveBuilder} accepting
	 * {@link ReactiveQueryByExampleExecutor} to build a {@link DataFetcher}.
	 *
	 * @param executor the QBE repository object to use
	 * @param <T> the domain type of the repository
	 * @return a new builder
	 */
	public static <T> ReactiveBuilder<T, T> builder(ReactiveQueryByExampleExecutor<T> executor) {
		return new ReactiveBuilder<>(executor, RepositoryUtils.getDomainType(executor));
	}

	/**
	 * Return a {@link RuntimeWiringConfigurer} that installs a
	 * {@link graphql.schema.idl.WiringFactory} to find queries with a return
	 * type whose name matches to the domain type name of the given repositories
	 * and registers {@link DataFetcher}s for them.
	 *
	 * <p><strong>Note:</strong> This applies only to top-level queries and
	 * repositories annotated with {@link GraphQlRepository @GraphQlRepository}.
	 *
	 * @param executors repositories to consider for registration
	 * @param reactiveExecutors reactive repositories to consider for registration
	 * @return the created configurer
	 */
	public static RuntimeWiringConfigurer autoRegistrationConfigurer(
			List<QueryByExampleExecutor<?>> executors,
			List<ReactiveQueryByExampleExecutor<?>> reactiveExecutors) {

		Map<String, DataFetcherFactory> factories = new HashMap<>();

		for (QueryByExampleExecutor<?> executor : executors) {
			String typeName = RepositoryUtils.getGraphQlTypeName(executor);
			if (typeName != null) {
				Builder<?, ?> builder = customize(executor, builder(executor));
				factories.put(typeName, new DataFetcherFactory() {
					@Override
					public DataFetcher<?> single() {
						return builder.single();
					}

					@Override
					public DataFetcher<?> many() {
						return builder.many();
					}
				});
			}
		}

		for (ReactiveQueryByExampleExecutor<?> executor : reactiveExecutors) {
			String typeName = RepositoryUtils.getGraphQlTypeName(executor);
			if (typeName != null) {
				ReactiveBuilder<?, ?> builder = customize(executor, builder(executor));
				factories.put(typeName, new DataFetcherFactory() {
					@Override
					public DataFetcher<?> single() {
						return builder.single();
					}

					@Override
					public DataFetcher<?> many() {
						return builder.many();
					}
				});
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace("Auto-registration candidate typeNames " + factories.keySet());
		}

		return new AutoRegistrationRuntimeWiringConfigurer(factories);
	}

	/**
	 * Create a {@link GraphQLTypeVisitor} that finds queries with a return type
	 * whose name matches to the domain type name of the given repositories and
	 * registers {@link DataFetcher}s for those queries.
	 * <p><strong>Note:</strong> currently, this method will match only to
	 * queries under the top-level "Query" type in the GraphQL schema.
	 *
	 * @param executors repositories to consider for registration
	 * @param reactiveExecutors reactive repositories to consider for registration
	 * @return the created visitor
	 * @deprecated in favor of {@link #autoRegistrationConfigurer(List, List)}
	 */
	@Deprecated
	public static GraphQLTypeVisitor autoRegistrationTypeVisitor(
			List<QueryByExampleExecutor<?>> executors,
			List<ReactiveQueryByExampleExecutor<?>> reactiveExecutors) {

		Map<String, Function<Boolean, DataFetcher<?>>> factories = new HashMap<>();

		for (QueryByExampleExecutor<?> executor : executors) {
			String typeName = RepositoryUtils.getGraphQlTypeName(executor);
			if (typeName != null) {
				Builder<?, ?> builder = customize(executor, builder(executor));
				factories.put(typeName, single -> single ? builder.single() : builder.many());
			}
		}

		for (ReactiveQueryByExampleExecutor<?> executor : reactiveExecutors) {
			String typeName = RepositoryUtils.getGraphQlTypeName(executor);
			if (typeName != null) {
				ReactiveBuilder<?, ?> builder = customize(executor, builder(executor));
				factories.put(typeName, single -> single ? builder.single() : builder.many());
			}
		}

		return new AutoRegistrationTypeVisitor(factories);
	}


	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Builder customize(QueryByExampleExecutor<?> executor, Builder builder) {
		if(executor instanceof QueryByExampleBuilderCustomizer<?> customizer){
			return customizer.customize(builder);
		}
		return builder;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static ReactiveBuilder customize(ReactiveQueryByExampleExecutor<?> executor, ReactiveBuilder builder) {
		if(executor instanceof ReactiveQueryByExampleBuilderCustomizer<?> customizer){
			return customizer.customize(builder);
		}
		return builder;
	}


	/**
	 * Builder for a Query by Example-based {@link DataFetcher}. Note that builder
	 * instances are immutable and return a new instance of the builder
	 * when calling configuration methods.
	 *
	 * @param <T> domain type
	 * @param <R> result type
	 */
	public static class Builder<T, R> {

		private final QueryByExampleExecutor<T> executor;

		private final TypeInformation<T> domainType;

		private final Class<R> resultType;

		private final Sort sort;

		@SuppressWarnings("unchecked")
		Builder(QueryByExampleExecutor<T> executor, Class<R> domainType) {
			this(executor, TypeInformation.of((Class<T>) domainType), domainType, Sort.unsorted());
		}

		Builder(QueryByExampleExecutor<T> executor, TypeInformation<T> domainType, Class<R> resultType, Sort sort) {
			this.executor = executor;
			this.domainType = domainType;
			this.resultType = resultType;
			this.sort = sort;
		}

		/**
		 * Project results returned from the {@link QueryByExampleExecutor}
		 * into the target {@code projectionType}. Projection types can be
		 * either interfaces with property getters to expose or regular classes
		 * outside the entity type hierarchy for DTO projections.
		 * @param projectionType projection type
		 * @return a new {@link Builder} instance with all previously
		 * configured options and {@code projectionType} applied
		 */
		public <P> Builder<T, P> projectAs(Class<P> projectionType) {
			Assert.notNull(projectionType, "Projection type must not be null");
			return new Builder<>(this.executor, this.domainType, projectionType, this.sort);
		}

		/**
		 * Apply a {@link Sort} order.
		 * @param sort the default sort order
		 * @return a new {@link Builder} instance with all previously configured
		 * options and {@code Sort} applied
		 */
		public Builder<T, R> sortBy(Sort sort) {
			Assert.notNull(sort, "Sort must not be null");
			return new Builder<>(this.executor, this.domainType, this.resultType, sort);
		}

		/**
		 * Build a {@link DataFetcher} to fetch single object instances.
		 */
		public DataFetcher<R> single() {
			return new SingleEntityFetcher<>(this.executor, this.domainType, this.resultType, this.sort);
		}

		/**
		 * Build a {@link DataFetcher} to fetch many object instances.
		 */
		public DataFetcher<Iterable<R>> many() {
			return new ManyEntityFetcher<>(this.executor, this.domainType, this.resultType, this.sort);
		}

	}

	/**
	 * Callback interface that can be used to customize QueryByExampleDataFetcher
	 * {@link Builder} to change its configuration.
	 * <p>This is supported by {@link #autoRegistrationConfigurer(List, List)
	 * Auto-registration}, which detects if a repository implements this
	 * interface and applies it accordingly.
	 *
	 * @param <T>
	 * @since 1.1.1
	 */
	public interface QueryByExampleBuilderCustomizer<T> {

		/**
		 * Callback to customize a {@link Builder} instance.
		 * @param builder builder to customize
		 */
		Builder<T, ?> customize(Builder<T, ?> builder);

	}


	/**
	 * Builder for a reactive Query by Example-based {@link DataFetcher}.
	 * Note that builder instances are immutable and return a new instance of
	 * the builder when calling configuration methods.
	 *
	 * @param <T> domain type
	 * @param <R> result type
	 */
	public static class ReactiveBuilder<T, R> {

		private final ReactiveQueryByExampleExecutor<T> executor;

		private final TypeInformation<T> domainType;

		private final Class<R> resultType;

		private final Sort sort;

		@SuppressWarnings("unchecked")
		ReactiveBuilder(ReactiveQueryByExampleExecutor<T> executor, Class<R> domainType) {
			this(executor, TypeInformation.of((Class<T>) domainType), domainType, Sort.unsorted());
		}

		ReactiveBuilder(
				ReactiveQueryByExampleExecutor<T> executor, TypeInformation<T> domainType,
				Class<R> resultType, Sort sort) {

			this.executor = executor;
			this.domainType = domainType;
			this.resultType = resultType;
			this.sort = sort;
		}

		/**
		 * Project results returned from the {@link ReactiveQueryByExampleExecutor}
		 * into the target {@code projectionType}. Projection types can be
		 * either interfaces with property getters to expose or regular classes
		 * outside the entity type hierarchy for DTO projections.
		 * @param projectionType projection type
		 * @return a new {@link ReactiveBuilder} instance with all previously
		 * configured options and {@code projectionType} applied
		 */
		public <P> ReactiveBuilder<T, P> projectAs(Class<P> projectionType) {
			Assert.notNull(projectionType, "Projection type must not be null");
			return new ReactiveBuilder<>(this.executor, this.domainType, projectionType, this.sort);
		}

		/**
		 * Apply a {@link Sort} order.
		 * @param sort the default sort order
		 * @return a new {@link ReactiveBuilder} instance with all previously configured
		 * options and {@code Sort} applied
		 */
		public ReactiveBuilder<T, R> sortBy(Sort sort) {
			Assert.notNull(sort, "Sort must not be null");
			return new ReactiveBuilder<>(this.executor, this.domainType, this.resultType, sort);
		}

		/**
		 * Build a {@link DataFetcher} to fetch single object instances.
		 */
		public DataFetcher<Mono<R>> single() {
			return new ReactiveSingleEntityFetcher<>(this.executor, this.domainType, this.resultType, this.sort);
		}

		/**
		 * Build a {@link DataFetcher} to fetch many object instances.
		 */
		public DataFetcher<Flux<R>> many() {
			return new ReactiveManyEntityFetcher<>(this.executor, this.domainType, this.resultType, this.sort);
		}

	}

	/**
	 * Callback interface that can be used to customize QueryByExampleDataFetcher
	 * {@link ReactiveBuilder} to change its configuration.
	 * <p>This is supported by {@link #autoRegistrationConfigurer(List, List)
	 * Auto-registration}, which detects if a repository implements this
	 * interface and applies it accordingly.
	 *
	 * @param <T>
	 * @since 1.1.1
	 */
	public interface ReactiveQueryByExampleBuilderCustomizer<T> {

		/**
		 * Callback to customize a {@link ReactiveBuilder} instance.
		 * @param builder builder to customize
		 */
		ReactiveBuilder<T, ?> customize(ReactiveBuilder<T, ?> builder);

	}


	private static class SingleEntityFetcher<T, R> extends QueryByExampleDataFetcher<T> implements SelfDescribingDataFetcher<R> {

		private final QueryByExampleExecutor<T> executor;

		private final Class<R> resultType;

		private final Sort sort;

		SingleEntityFetcher(
				QueryByExampleExecutor<T> executor, TypeInformation<T> domainType, Class<R> resultType, Sort sort) {

			super(domainType);
			this.executor = executor;
			this.resultType = resultType;
			this.sort = sort;
		}

		@Override
		@SuppressWarnings({"ConstantConditions", "unchecked"})
		public R get(DataFetchingEnvironment env) throws BindException {
			return this.executor.findBy(buildExample(env), query -> {
				FluentQuery.FetchableFluentQuery<R> queryToUse = (FluentQuery.FetchableFluentQuery<R>) query;

				if (this.sort.isSorted()) {
					queryToUse = queryToUse.sortBy(this.sort);
				}

				Class<R> resultType = this.resultType;
				if (requiresProjection(resultType)) {
					queryToUse = queryToUse.as(resultType);
				}
				else {
					queryToUse = queryToUse.project(buildPropertyPaths(env.getSelectionSet(), resultType));
				}

				return queryToUse.first();
			}).orElse(null);
		}

		@Override
		public ResolvableType getReturnType() {
			return ResolvableType.forClass(this.resultType);
		}
	}


	private static class ManyEntityFetcher<T, R> extends QueryByExampleDataFetcher<T> implements SelfDescribingDataFetcher<Iterable<R>> {

		private final QueryByExampleExecutor<T> executor;

		private final Class<R> resultType;

		private final Sort sort;

		ManyEntityFetcher(
				QueryByExampleExecutor<T> executor, TypeInformation<T> domainType,
				Class<R> resultType, Sort sort) {

			super(domainType);
			this.executor = executor;
			this.resultType = resultType;
			this.sort = sort;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Iterable<R> get(DataFetchingEnvironment env) throws BindException {
			return this.executor.findBy(buildExample(env), query -> {
				FluentQuery.FetchableFluentQuery<R> queryToUse = (FluentQuery.FetchableFluentQuery<R>) query;

				if (this.sort.isSorted()) {
					queryToUse = queryToUse.sortBy(this.sort);
				}

				if (requiresProjection(this.resultType)) {
					queryToUse = queryToUse.as(this.resultType);
				}
				else {
					queryToUse = queryToUse.project(buildPropertyPaths(env.getSelectionSet(), this.resultType));
				}

				return queryToUse.all();
			});
		}

		@Override
		public ResolvableType getReturnType() {
			return ResolvableType.forClassWithGenerics(Iterable.class, this.resultType);
		}

	}


	private static class ReactiveSingleEntityFetcher<T, R> extends QueryByExampleDataFetcher<T> implements SelfDescribingDataFetcher<Mono<R>> {

		private final ReactiveQueryByExampleExecutor<T> executor;

		private final Class<R> resultType;

		private final Sort sort;

		ReactiveSingleEntityFetcher(
				ReactiveQueryByExampleExecutor<T> executor, TypeInformation<T> domainType,
				Class<R> resultType, Sort sort) {

			super(domainType);
			this.executor = executor;
			this.resultType = resultType;
			this.sort = sort;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Mono<R> get(DataFetchingEnvironment env) throws BindException {
			return this.executor.findBy(buildExample(env), query -> {
				FluentQuery.ReactiveFluentQuery<R> queryToUse = (FluentQuery.ReactiveFluentQuery<R>) query;

				if (this.sort.isSorted()) {
					queryToUse = queryToUse.sortBy(this.sort);
				}

				if (requiresProjection(this.resultType)) {
					queryToUse = queryToUse.as(this.resultType);
				}
				else {
					queryToUse = queryToUse.project(buildPropertyPaths(env.getSelectionSet(), this.resultType));
				}

				return queryToUse.first();
			});
		}

		@Override
		public ResolvableType getReturnType() {
			return ResolvableType.forClassWithGenerics(Mono.class, this.resultType);
		}

	}


	private static class ReactiveManyEntityFetcher<T, R> extends QueryByExampleDataFetcher<T> implements SelfDescribingDataFetcher<Flux<R>> {

		private final ReactiveQueryByExampleExecutor<T> executor;

		private final Class<R> resultType;

		private final Sort sort;

		ReactiveManyEntityFetcher(
				ReactiveQueryByExampleExecutor<T> executor, TypeInformation<T> domainType,
				Class<R> resultType, Sort sort) {

			super(domainType);
			this.executor = executor;
			this.resultType = resultType;
			this.sort = sort;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Flux<R> get(DataFetchingEnvironment env) throws BindException {
			return this.executor.findBy(buildExample(env), query -> {
				FluentQuery.ReactiveFluentQuery<R> queryToUse = (FluentQuery.ReactiveFluentQuery<R>) query;

				if (this.sort.isSorted()) {
					queryToUse = queryToUse.sortBy(this.sort);
				}

				if (requiresProjection(this.resultType)) {
					queryToUse = queryToUse.as(this.resultType);
				}
				else {
					queryToUse = queryToUse.project(buildPropertyPaths(env.getSelectionSet(), this.resultType));
				}

				return queryToUse.all();
			});
		}

		@Override
		public ResolvableType getReturnType() {
			return ResolvableType.forClassWithGenerics(Flux.class, this.resultType);
		}

	}

}
