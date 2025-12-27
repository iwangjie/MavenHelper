package krasa.mavenhelper.analyzer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenConstants;

/**
 * @author Vojtech Krasa
 */
public class MyFileEditorProvider implements FileEditorProvider, DumbAware {
	private static final Logger LOG = Logger.getInstance("#krasa.mavenrun.analyzer.MyFileEditorProvider");

	@Override
	public boolean accept(@NotNull final Project project, @NotNull final VirtualFile file) {
		if (project.isDisposed()) {
			return false;
		}
		if (!file.isInLocalFileSystem()) {
			return false;
		}
		return isPomFileName(file.getName());
	}


	private static boolean isPomFileName(@NotNull String name) {
		// IMPORTANT: keep this method cheap and avoid touching MavenProjectsManager here.
		// FileEditorProvider#accept may run inside a read action during startup/indexing;
		// triggering MavenProjectsManager initialization here can cause UI freezes.
		return MavenConstants.POM_XML.equalsIgnoreCase(name) || name.toLowerCase().endsWith(".pom");
	}

	@Override
	@NotNull
	public FileEditor createEditor(@NotNull final Project project, @NotNull final VirtualFile file) {
//		https://github.com/krasa/MavenHelper/issues/130
//		LOG.assertTrue(accept(project, file));

		return new UIFormEditor(project, file);
	}

	@Override
	public void disposeEditor(@NotNull final FileEditor editor) {
		Disposer.dispose(editor);
	}

	@Override
	@NotNull
	public FileEditorState readState(@NotNull final Element element, @NotNull final Project project,
									 @NotNull final VirtualFile file) {
		return UIFormEditor.MY_EDITOR_STATE;
	}

	@Override
	public void writeState(@NotNull final FileEditorState state, @NotNull final Project project,
						   @NotNull final Element element) {
	}

	@Override
	@NotNull
	public String getEditorTypeId() {
		return "MavenHelperProPluginDependencyAnalyzer";
	}

	@Override
	@NotNull
	public FileEditorPolicy getPolicy() {
		return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
	}

}
