package krasa.mavenhelper.icon;

import com.intellij.icons.AllIcons;
import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;

/**
 * IntelliJ 2025.3 Maven plugin's {@code MavenIconProvider} calls {@code MavenProjectsManager.findProject(VirtualFile)}
 * for every file icon lookup. In IU-253 this can trigger {@code initProjectsTree()} which performs heavy cache
 * deserialization under a read action (e.g. editor tab icon computation), causing UI freezes due to RW-lock contention.
 * <p>
 * This provider returns a cheap, deterministic icon (based on file type) and is registered with {@code order="first"}
 * so Maven's provider is not consulted during icon computation.
 */
public final class SafeFileTypeIconProvider implements FileIconProvider, DumbAware {
	@Override
	public @Nullable Icon getIcon(@NotNull VirtualFile file, int flags, @Nullable Project project) {
		if (project == null) {
			return null;
		}
		if (!file.isValid()) {
			return null;
		}

		try {
			MavenProjectsManager manager = MavenProjectsManager.getInstanceIfCreated(project);
			if (manager != null && manager.isMavenizedProject()) {
				return null;
			}
		} catch (Throwable ignored) {
			// Be conservative: if we cannot determine Maven initialization state, return a cheap icon to avoid freezes.
		}

		if (file.isDirectory()) {
			return AllIcons.Nodes.Folder;
		}

		FileType fileType = file.getFileType();
		Icon icon = fileType.getIcon();
		return icon != null ? icon : AllIcons.FileTypes.Unknown;
	}
}
