package krasa.mavenhelper.analyzer;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifactNode;
import org.jetbrains.idea.maven.model.MavenArtifactState;

import java.util.List;
import java.util.Map;

/**
 * @author Vojtech Krasa
 */
public class MyListNode {
	private static final Logger LOG = Logger.getInstance(MyListNode.class);
	private static final long UNCOMPUTED = -2L;

	protected final String artifactKey;
	private List<MavenArtifactNode> artifacts;
	@Nullable
	protected MavenArtifactNode rightArtifact;
	protected boolean conflict;
	private volatile long sizeKb = UNCOMPUTED;
	private volatile long totalSizeKb = UNCOMPUTED;
	private String groupId;
	private String artifactId;

	public MyListNode(Map.Entry<String, List<MavenArtifactNode>> s) {
		artifactKey = s.getKey();
		artifacts = s.getValue();
		initRightArtifact();
		initConflict();
	}

	public List<MavenArtifactNode> getArtifacts() {
		return artifacts;
	}

	@Nullable
	public MavenArtifactNode getRightArtifact() {
		return rightArtifact;
	}

	public long getSize() {
		long cached = sizeKb;
		if (cached != UNCOMPUTED) {
			return cached;
		}

		long computed;
		if (rightArtifact != null) {
			if (rightArtifact.getArtifact().getFile() == null) {
				computed = 0L;
			} else {
				computed = rightArtifact.getArtifact().getFile().length() / 1024;
			}
		} else {
			computed = -1L;
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

	boolean hasComputedTotalSize() {
		return totalSizeKb != UNCOMPUTED;
	}

	private void initRightArtifact() {
		if (artifacts != null && !artifacts.isEmpty()) {
			for (MavenArtifactNode mavenArtifactNode : artifacts) {

				groupId = mavenArtifactNode.getArtifact().getGroupId();
				artifactId = mavenArtifactNode.getArtifact().getArtifactId();

				if (mavenArtifactNode.getState() == MavenArtifactState.ADDED) {
					rightArtifact = mavenArtifactNode;
					break;
				}
			}
			if (LOG.isDebugEnabled()) {
				if (rightArtifact == null) {
					StringBuilder sb = new StringBuilder(artifactKey + "[");
					for (MavenArtifactNode artifact : artifacts) {
						sb.append(artifact.getArtifact());
						sb.append("-");
						sb.append(artifact.getState());
						sb.append(";");
					}
					sb.append("]");

					LOG.debug(sb.toString());
				}
			}

		}
	}

	private void initConflict() {
		if (artifacts != null && !artifacts.isEmpty()) {
			for (MavenArtifactNode mavenArtifactNode : artifacts) {
				if (Utils.isOmitted(mavenArtifactNode) || Utils.isConflictAlternativeMethod(mavenArtifactNode)) {
					conflict = true;
					break;
				}
			}
		}
	}

	public boolean isConflict() {
		return conflict;
	}

	@Nullable
	public String getRightVersion() {
		if (rightArtifact == null) {
			return null;
		}
		return rightArtifact.getArtifact().getVersion();
	}

	@Override
	public String toString() {
		return artifactKey;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		MyListNode that = (MyListNode) o;

		if (artifactKey != null ? !artifactKey.equals(that.artifactKey) : that.artifactKey != null)
			return false;

		return true;
	}

	@Override
	public int hashCode() {
		return artifactKey != null ? artifactKey.hashCode() : 0;
	}

	public String getGroupId() {
		return groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}
}
