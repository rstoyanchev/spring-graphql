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
package org.springframework.graphql.test.integration.webmvc.springio.hateoas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer;
import org.springframework.graphql.test.tester.WebGraphQlTester;
import org.springframework.graphql.web.webmvc.GraphQlHttpHandler;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.servlet.function.RequestPredicates.accept;
import static org.springframework.web.servlet.function.RequestPredicates.contentType;

/**
 * Tests exercising queries in {@link ProjectController}.
 *
 * @author Rossen Stoyanchev
 */
public class ProjectTests {

	private WebGraphQlTester graphQlTester;


	@BeforeEach
	void setUp() {
		AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
		context.setServletContext(new MockServletContext());
		context.register(ProjectConfig.class);
		context.refresh();

		WebTestClient webTestClient = MockMvcWebTestClient.bindToApplicationContext(context)
				.configureClient().baseUrl("/")
				.build();

		this.graphQlTester = WebGraphQlTester.builder(webTestClient).build();
	}

	
	@Test
	void jsonPath() {
		this.graphQlTester.queryName("webmvc/springio/projectReleases")
				.variable("slug", "spring-framework")
				.execute()
				.path("project.releases[*].version")
				.entityList(String.class)
				.hasSizeGreaterThan(0);

	}

	@Test
	void jsonContent() {
		this.graphQlTester.queryName("webmvc/springio/projectRepositoryUrl")
				.variable("slug", "spring-framework")
				.execute()
				.path("project")
				.matchesJson("{\"repositoryUrl\":\"http://github.com/spring-projects/spring-framework\"}");
	}

	@Test
	void decodedResponse() {
		this.graphQlTester.queryName("webmvc/springio/projectReleases")
				.variable("slug", "spring-framework")
				.execute()
				.path("project")
				.entity(Project.class)
				.satisfies(project -> assertThat(project.getReleases()).hasSizeGreaterThan(0));
	}


	@Configuration
	@EnableWebMvc
	@ComponentScan(basePackageClasses = ProjectTests.class)
	static class ProjectConfig {

		private final Resource schemaResource = new ClassPathResource("graphql/webmvc/springio/schema.graphqls");

		@Bean
		public RouterFunction<ServerResponse> graphQlRouterFunction() {
			GraphQlHttpHandler handler = GraphQlSetup.schemaResource(schemaResource)
					.runtimeWiring(annotatedControllerConfigurer())
					.toHttpHandler();

			return RouterFunctions.route()
					.POST("/",
							contentType(MediaType.APPLICATION_JSON).and(accept(MediaType.APPLICATION_JSON)),
							handler::handleRequest)
					.build();
		}

		@Bean
		public AnnotatedControllerConfigurer annotatedControllerConfigurer() {
			return new AnnotatedControllerConfigurer();
		}

	}

}
