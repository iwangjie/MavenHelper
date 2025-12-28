package krasa.mavenhelper;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MyProjectService {
	private static final int RESOLVE_DEBOUNCE_MS = 250;

	private final List<MyEventListener> myEventListeners = new CopyOnWriteArrayList<>();
	private final Map<VirtualFile, ProjectResolvedEvent> pendingResolvedEvents = new ConcurrentHashMap<>();
	private final Alarm resolveAlarm;

	public static MyProjectService getInstance(Project project) {
		return project.getService(MyProjectService.class);
	}

	public MyProjectService(Project project) {
		resolveAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
		MavenProjectsManager.getInstance(project).addProjectsTreeListener(new MavenProjectsTree.Listener() {
			@Override
			public void profilesChanged() {

			}

			@Override
			public void projectsIgnoredStateChanged(@NotNull List<MavenProject> ignored, @NotNull List<MavenProject> unignored, boolean fromImport) {

			}

			@Override
			public void projectsUpdated(@NotNull List<? extends Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
			}

			@Override
			public void projectResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges, @Nullable NativeMavenProjectHolder nativeMavenProject) {
				if (myEventListeners.isEmpty()) {
					return;
				}

				VirtualFile projectFile = projectWithChanges.getFirst().getFile();
				if (projectFile != null) {
					pendingResolvedEvents.put(projectFile, new ProjectResolvedEvent(projectWithChanges, nativeMavenProject));
				}

				resolveAlarm.cancelAllRequests();
				resolveAlarm.addRequest(() -> flushResolvedEvents(project), RESOLVE_DEBOUNCE_MS);
			}

			@Override
			public void pluginsResolved(@NotNull MavenProject project) {

			}

			@Override
			public void foldersResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges) {

			}

			@Override
			public void artifactsDownloaded(@NotNull MavenProject project) {

			}
		});
	}

	private void flushResolvedEvents(@NotNull Project project) {
		if (project.isDisposed()) {
			return;
		}
		if (myEventListeners.isEmpty()) {
			pendingResolvedEvents.clear();
			return;
		}

		List<ProjectResolvedEvent> events = List.copyOf(pendingResolvedEvents.values());
		pendingResolvedEvents.clear();

		for (ProjectResolvedEvent event : events) {
			for (MyEventListener listener : myEventListeners) {
				listener.projectResolved(event.projectWithChanges, event.nativeMavenProject);
			}
		}
	}

	public void register(MyEventListener myEventListener) {
		myEventListeners.add(myEventListener);
	}

	public void unregister(MyEventListener myEventListener) {
		if (myEventListener != null) {
			myEventListeners.remove(myEventListener);
		}
	}

	public interface MyEventListener {
		void projectResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges, @Nullable NativeMavenProjectHolder nativeMavenProject);
	}

	private record ProjectResolvedEvent(
		@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges,
		@Nullable NativeMavenProjectHolder nativeMavenProject
	) {
	}
}
