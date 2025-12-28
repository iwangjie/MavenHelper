package krasa.mavenhelper.analyzer;

import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenArtifactNode;

/**
 * @author Vojtech Krasa
 */
public class MyTreeUserObject {
	private static final long UNCOMPUTED = -2L;

	private MavenArtifactNode mavenArtifactNode;

	boolean showOnlyVersion = false;
	boolean highlight;
	private volatile long sizeKb = UNCOMPUTED;
	private volatile long totalSizeKb = UNCOMPUTED;

	public MyTreeUserObject(MavenArtifactNode mavenArtifactNode) {
		this.mavenArtifactNode = mavenArtifactNode;
	}


	public MavenArtifact getArtifact() {
		return mavenArtifactNode.getArtifact();
	}

	public MavenArtifactNode getMavenArtifactNode() {
		return mavenArtifactNode;
	}

	public boolean isHighlight() {
		return highlight;
	}

	@Override
	public String toString() {
		return mavenArtifactNode.getArtifact().getArtifactId();
	}

	public long getSize() {
		long cached = sizeKb;
		if (cached != UNCOMPUTED) {
			return cached;
		}

		long computed;
		if (getArtifact().getFile() == null) {
			computed = 0L;
		} else {
			computed = getArtifact().getFile().length() / 1024;
		}
		sizeKb = computed;
		return computed;
	}

	public long getTotalSize() {
		long cached = totalSizeKb;
		if (cached != UNCOMPUTED) {
			return cached;
		}
		return getSize();
	}

	void setSizes(long sizeKb, long totalSizeKb) {
		this.sizeKb = sizeKb;
		this.totalSizeKb = totalSizeKb;
	}
}
