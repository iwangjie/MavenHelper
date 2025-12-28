package krasa.mavenhelper;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenPluginInfo;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches Maven plugin mojo metadata loaded from the local repository.
 * <p>
 * Reading plugin descriptors can be expensive in large projects; this service keeps the data in-memory per-project.
 */
@Service(Service.Level.PROJECT)
public final class MavenPluginMojoCacheService {
	private final ConcurrentHashMap<MavenId, List<String>> mojoDisplayNamesByPluginId = new ConcurrentHashMap<>();

	public static @NotNull MavenPluginMojoCacheService getInstance(@NotNull Project project) {
		return project.getService(MavenPluginMojoCacheService.class);
	}

	public MavenPluginMojoCacheService(@NotNull Project project) {
	}

	public @NotNull List<String> getMojoDisplayNames(@NotNull MavenProject mavenProject, @NotNull MavenPlugin plugin) {
		MavenId mavenId = plugin.getMavenId();
		if (mavenId == null) {
			return List.of();
		}
		return mojoDisplayNamesByPluginId.computeIfAbsent(mavenId, id -> loadMojoDisplayNames(mavenProject, id));
	}

	private static @NotNull List<String> loadMojoDisplayNames(@NotNull MavenProject mavenProject, @NotNull MavenId pluginId) {
		MavenPluginInfo pluginInfo = MavenArtifactUtil.readPluginInfo(mavenProject.getLocalRepository(), pluginId);
		if (pluginInfo == null) {
			return List.of();
		}
		return pluginInfo.getMojos().stream().map(MavenPluginInfo.Mojo::getDisplayName).toList();
	}
}
