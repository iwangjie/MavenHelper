package krasa.mavenhelper.analyzer;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import krasa.mavenhelper.MyProjectService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UIFormEditor extends UserDataHolderBase implements /* Navigatable */FileEditor {
	private static final Logger log = LoggerFactory.getLogger(UIFormEditor.class);

	public static final FileEditorState MY_EDITOR_STATE = new FileEditorState() {
		@Override
		public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
			return false;
		}
	};
	private final Project project;
	private final VirtualFile file;
	private final JPanel rootPanel = new JPanel(new BorderLayout());
	private final AtomicBoolean disposed = new AtomicBoolean();
	private final AtomicBoolean initializationStarted = new AtomicBoolean();
	private GuiForm myEditor;
	private volatile MyProjectService.MyEventListener myListener;
	private volatile boolean selected;

	public UIFormEditor(@NotNull Project project, final VirtualFile file) {
		this.project = project;
		this.file = file;

		rootPanel.add(new JLabel("Dependency Analyzer is waiting for Maven import..."), BorderLayout.CENTER);
	}

	private void ensureInitialized() {
		if (myEditor != null) {
			return;
		}
		if (!initializationStarted.compareAndSet(false, true)) {
			return;
		}

		// Do not call MavenProjectsManager.findProject() until Maven is initialized; in recent IDEs it triggers
		// initProjectsTree() (heavy cache deserialization). Instead, listen for Maven resolve events and use a fast-path
		// lookup only when Maven is already initialized.
		MyProjectService service = MyProjectService.getInstance(project);
		MyProjectService.MyEventListener listener = (projectWithChanges, nativeMavenProject) -> {
			MavenProject resolved = projectWithChanges.getFirst();
			if (resolved != null && file.equals(resolved.getFile())) {
				initEditor(resolved);
			}
		};
		myListener = listener;
		service.register(listener);

		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			MavenProject mavenProject = null;
			try {
				MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
				if (manager != null && manager.isMavenizedProject()) {
					mavenProject = manager.findProject(file);
				}
			} catch (Throwable t) {
				log.warn("Failed to resolve MavenProject for file " + file.getPath(), t);
			}
			if (mavenProject != null) {
				initEditor(mavenProject);
			}
		});
	}

	private void initEditor(@NotNull MavenProject resolvedProject) {
		ApplicationManager.getApplication().invokeLater(() -> {
			if (disposed.get() || project.isDisposed() || !file.isValid() || myEditor != null) {
				return;
			}

			MyProjectService.MyEventListener listener = myListener;
			if (listener != null) {
				myListener = null;
				MyProjectService.getInstance(project).unregister(listener);
			}

			myEditor = new GuiForm(project, file, resolvedProject);
			rootPanel.removeAll();
			rootPanel.add(myEditor.getRootComponent(), BorderLayout.CENTER);
			rootPanel.revalidate();
			rootPanel.repaint();
			if (selected) {
				myEditor.selectNotify();
			}
		}, ModalityState.any());
	}

	@Override
	@NotNull
	public JComponent getComponent() {
		ensureInitialized();
		return rootPanel;
	}

	@Override
	public void dispose() {
		disposed.set(true);
		MyProjectService.MyEventListener listener = myListener;
		if (listener != null) {
			myListener = null;
			MyProjectService.getInstance(project).unregister(listener);
		}
		GuiForm editor = myEditor;
		if (editor != null) {
			editor.dispose();
		}
	}

	@Override
	public VirtualFile getFile() {
		return file;
	}

	@Override
	public JComponent getPreferredFocusedComponent() {
		ensureInitialized();
		if (myEditor != null) {
			return myEditor.getPreferredFocusedComponent();
		}
		return null;
	}

	@Override
	@NotNull
	public String getName() {
		return "Dependency Analyzer";
	}

	@Override
	public boolean isModified() {
		return false;
	}

	@Override
	public boolean isValid() {
		return true;
	}

	@Override
	public void selectNotify() {
		selected = true;
		ensureInitialized();
		if (myEditor != null) {
			myEditor.selectNotify();
		}
	}

	@Override
	public void deselectNotify() {
		selected = false;
	}

	@Override
	public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener) {
	}

	@Override
	public void removePropertyChangeListener(@NotNull final PropertyChangeListener listener) {
	}

	@Override
	public BackgroundEditorHighlighter getBackgroundHighlighter() {
		return null;
	}

	@Override
	public FileEditorLocation getCurrentLocation() {
		return null;
	}

	@Override
	@NotNull
	public FileEditorState getState(@NotNull final FileEditorStateLevel ignored) {
		return MY_EDITOR_STATE;
	}

	@Override
	public void setState(@NotNull final FileEditorState state) {
	}

	@Override
	public StructureViewBuilder getStructureViewBuilder() {
		return null;
	}
}
