package de.foellix.aql.system.defaulttools.preprocessors.simplifier.storage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Data {
	private Map<String, Set<SimilarityHash>> excludedClasses;

	Data() {
		this.excludedClasses = new HashMap<>();
	}

	Data(List<String> lines) {
		this();
		for (final String line : lines) {
			final String name = line.substring(0, line.indexOf(": "));
			String hashCodes = line.substring(line.indexOf("[") + 1);
			hashCodes = hashCodes.substring(0, hashCodes.length() - 1);
			for (final String hashCode : hashCodes.split(", ")) {
				final String[] parts = hashCode.split("#");
				add(name, new SimilarityHash(parts[1], Float.parseFloat(parts[0])));
			}
		}
	}

	public Map<String, Set<SimilarityHash>> getExcludedClasses() {
		return this.excludedClasses;
	}

	public void add(String name, SimilarityHash sh) {
		if (!this.excludedClasses.containsKey(name)) {
			this.excludedClasses.put(name, new HashSet<>());
		}
		if (!this.excludedClasses.get(name).contains(sh)) {
			this.excludedClasses.get(name).add(sh);
		}
	}
}