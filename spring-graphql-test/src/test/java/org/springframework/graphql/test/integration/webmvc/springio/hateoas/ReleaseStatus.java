package org.springframework.graphql.test.integration.webmvc.springio.hateoas;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ReleaseStatus {

	GENERAL_AVAILABILITY, MILESTONE, SNAPSHOT;

	@JsonCreator
	public static ReleaseStatus fromName(String name) {
		return Arrays.stream(ReleaseStatus.values())
				.filter(type -> type.name().equals(name))
				.findFirst()
				.orElse(ReleaseStatus.GENERAL_AVAILABILITY);
	}

}
