/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.graphql.data.method.annotation.support;


import java.lang.reflect.Method;
import java.util.Map;

import com.querydsl.core.util.StringUtils;
import graphql.schema.DataFetchingEnvironment;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.projection.Accessor;
import org.springframework.data.projection.MethodInterceptorFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.web.ProjectedPayload;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * Resolver to obtain an {@link ProjectedPayload @ProjectedPayload},
 * either based on the complete {@link  DataFetchingEnvironment#getArguments()}
 * map, or based on a specific argument within the map when the method
 * parameter is annotated with {@code @Argument}.
 *
 * <p>Projected payloads consist of the projection interface and accessor
 * methods. Projections can be closed or open projections. Closed projections
 * use interface getter methods to access underlying properties directly.
 * Open projection methods make use of the {@code @Value} annotation to
 * evaluate SpEL expressions against the underlying {@code target} object.
 *
 * <p>For example:
 * <pre class="code">
 * &#064;ProjectedPayload
 * interface BookProjection {
 *   String getName();
 * }
 *
 * &#064;ProjectedPayload
 * interface BookProjection {
 *   &#064;Value("#{target.author + ' '  + target.name}")
 *   String getAuthorAndName();
 * }
 * </pre>
 *
 * @author Mark Paluch
 * @since 1.0.0
 */
public class ProjectedPayloadMethodArgumentResolver implements HandlerMethodArgumentResolver,
		BeanFactoryAware, BeanClassLoaderAware {

	private final SpelAwareProxyProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();


	public ProjectedPayloadMethodArgumentResolver() {
		projectionFactory.registerMethodInvokerFactory(new ExtendedMapAccessingMethodInterceptorFactory());
	}


	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.projectionFactory.setBeanFactory(beanFactory);
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.projectionFactory.setBeanClassLoader(classLoader);
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> type = parameter.getParameterType();

		if (!type.isInterface()) {
			return false;
		}

		return AnnotatedElementUtils.findMergedAnnotation(type, ProjectedPayload.class) != null;
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {
		String name = (parameter.hasParameterAnnotation(Argument.class) ?
				ArgumentMethodArgumentResolver.getArgumentName(parameter) : null);

		Object projectionSource = (name != null ?
				environment.getArgument(name) : environment.getArguments());

		return project(parameter.getParameterType(), projectionSource);
	}

	protected Object project(Class<?> projectionType, Object projectionSource){
		return this.projectionFactory.createProjection(projectionType, projectionSource);
	}



	@SuppressWarnings("unchecked")
	private static class ExtendedMapAccessingMethodInterceptorFactory implements MethodInterceptorFactory {

		@Override
		public MethodInterceptor createMethodInterceptor(Object source, Class<?> targetType) {
			return new ExtendedMapAccessingMethodInterceptor((Map<String, Object>) source);
		}

		@Override
		public boolean supports(Object source, Class<?> targetType) {
			return source instanceof Map;
		}

	}


	private static class ExtendedMapAccessingMethodInterceptor implements MethodInterceptor {

		private final Map<String, Object> map;

		ExtendedMapAccessingMethodInterceptor(Map<String, Object> map) {
			Assert.notNull(map, "`map` is required");
			this.map = map;
		}

		@Nullable
		@Override
		public Object invoke(@SuppressWarnings("null") MethodInvocation invocation) throws Throwable {

			Method method = invocation.getMethod();

			if (ReflectionUtils.isObjectMethod(method)) {
				return invocation.proceed();
			}

			String methodName = method.getName();
			if (method.getReturnType() == boolean.class &&
					methodName.startsWith("is") && methodName.endsWith("Defined")) {

				String key = methodName.substring(2, methodName.length() - 7);
				key = StringUtils.uncapitalize(key);
				return map.containsKey(key);
			}

			Accessor accessor = new Accessor(method);

			if (accessor.isGetter()) {
				return map.get(accessor.getPropertyName());
			}
			else if (accessor.isSetter()) {
				map.put(accessor.getPropertyName(), invocation.getArguments()[0]);
				return null;
			}

			throw new IllegalStateException("Should never get here!");
		}
	}

}
