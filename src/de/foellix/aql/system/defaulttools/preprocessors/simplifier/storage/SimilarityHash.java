package de.foellix.aql.system.defaulttools.preprocessors.simplifier.storage;

public class SimilarityHash {
	private String hash;
	private float similarity;

	public SimilarityHash(String hash, float similarity) {
		this.hash = hash;
		this.similarity = similarity;
	}

	public String getHash() {
		return this.hash;
	}

	public float getSimilarity() {
		return this.similarity;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	public void setSimilarity(float similarity) {
		this.similarity = similarity;
	}

	@Override
	public String toString() {
		return this.similarity + "#" + this.hash;
	}

	@Override
	public int hashCode() {
		return this.hash.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return this.hashCode() == obj.hashCode();
	}
}