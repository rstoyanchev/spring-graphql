/*
 * Copyright 2002-2022 the original author or authors.
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
package org.springframework.graphql.execution;

import java.util.Map;

import io.micrometer.context.ThreadLocalAccessor;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link ThreadLocalAccessor} to extract and restore security context through
 * {@link SecurityContextHolder}.
 *
 * @author Rob Winch
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
public class SecurityContextThreadLocalAccessor implements ThreadLocalAccessor<SecurityContext>,
		org.springframework.graphql.execution.ThreadLocalAccessor {

	@Override
	public Object key() {
		return SecurityContext.class.getName();
	}

	@Override
	public SecurityContext getValue() {
		return SecurityContextHolder.getContext();
	}

	@Override
	public void setValue(SecurityContext value) {
		SecurityContextHolder.setContext(value);
	}

	@Override
	public void reset() {
		SecurityContextHolder.clearContext();
	}


	// Temporary implementation of deprecated ThreadLocalAccessor while it is still used
	// in the Boot starter. If registered as such, it is ignored.

	@Override
	public void extractValues(Map<String, Object> container) {
		container.put((String) key(), SecurityContextHolder.getContext());
	}

	@Override
	public void restoreValues(Map<String, Object> values) {
		if (values.containsKey((String) key())) {
			SecurityContextHolder.setContext((SecurityContext) values.get((String) key()));
		}
	}

	@Override
	public void resetValues(Map<String, Object> values) {
		SecurityContextHolder.clearContext();
	}

}
