package krasa.mavenhelper.analyzer;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import krasa.mavenhelper.Donate;
import krasa.mavenhelper.MavenHelperApplicationService;
import krasa.mavenhelper.MyProjectService;
import krasa.mavenhelper.analyzer.action.LeftTreePopupHandler;
import krasa.mavenhelper.analyzer.action.ListKeyStrokeAdapter;
import krasa.mavenhelper.analyzer.action.ListPopupHandler;
import krasa.mavenhelper.analyzer.action.RightTreePopupHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenArtifactNode;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * @author Vojtech Krasa
 */
public class GuiForm implements Disposable {
	private static final Logger LOG = Logger.getInstance("#krasa.mavenrun.analyzer.GuiForm");

	public static final String WARNING = "Your settings indicates, that conflicts will not be visible, see IDEA-133331\n"
		+ "If your project is Maven2 compatible, you could try one of the following:\n"
			+ "-use IJ 2016.1+ and configure it to use external Maven 3.1.1+ (File | Settings | Build, Execution, Deployment | Build Tools | Maven | Maven home directory)\n"
			+ "-press Apply Fix button to alter Maven VM options for importer (might cause trouble for IJ 2016.1+)\n"
			+ "-turn off File | Settings | Build, Execution, Deployment | Build Tools | Maven | Importing | Use Maven3 to import project setting\n";
	protected static final Comparator<MavenArtifactNode> BY_ARTIFACT_ID = new Comparator<MavenArtifactNode>() {
		@Override
		public int compare(MavenArtifactNode o1, MavenArtifactNode o2) {
			return o1.getArtifact().getArtifactId().compareToIgnoreCase(o2.getArtifact().getArtifactId());
		}
	};
	private static final String LAST_RADIO_BUTTON = "MavenHelperPro.lastRadioButton";
	private static final String LAST_SHOW_GROUP_ID_CHECKBOX = "MavenHelperPro.lastShowGroupIdCheckBox";
	private static final String LAST_SHOW_SIZE_CHECKBOX = "MavenHelperPro.lastShowSizeCheckBox";
	private static final String LAST_FILTER_CHECKBOX = "MavenHelperPro.lastFilterCheckBox";
	private static final String LAST_HIDE_TESTS_CHECKBOX = "MavenHelperPro.lastHideTestsCheckBox";
	public static final SimpleTextAttributes SIZE_ATTRIBUTES = SimpleTextAttributes.GRAY_ATTRIBUTES;

	private static final int UPDATE_DEBOUNCE_MS = 150;
	private static final int AUTO_EXPAND_NODE_LIMIT = 2000;
	private static final int RIGHT_TREE_UPDATE_DEBOUNCE_MS = 75;
	private static final int RIGHT_TREE_MAX_PATHS = 200;
	private static final int RIGHT_TREE_AUTO_EXPAND_NODE_LIMIT = 800;

	private final Project project;
	private final VirtualFile file;
	private final MavenHelperApplicationService applicationService = MavenHelperApplicationService.getInstance();
	private MavenProject mavenProject;
	private final Alarm updateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
	private final AtomicLong uiUpdateSeq = new AtomicLong();
	private final Alarm rightTreeUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
	private final AtomicLong rightTreeUpdateSeq = new AtomicLong();
	private volatile boolean modelLoading;
	protected JBList leftPanelList;
	private MyHighlightingTree rightTree;
	private JPanel rootPanel;

	private JRadioButton conflictsRadioButton;
	private JRadioButton allDependenciesAsListRadioButton;
	private JRadioButton allDependenciesAsTreeRadioButton;

	private JLabel noConflictsLabel;
	private JScrollPane noConflictsWarningLabelScrollPane;
	private JTextPane noConflictsWarningLabel;
	private JButton refreshButton;
	private JSplitPane splitPane;
	private SearchTextField searchField;
	private JPanel leftPanelWrapper;
	private MyHighlightingTree leftTree;
	private JCheckBox showGroupId;
	private JCheckBox showSize;
	private JCheckBox hideTests;
	private JPanel buttonsPanel;
	private JButton donate;
	private JButton reimport;
	protected JEditorPane intellijBugLabel;
	protected JEditorPane falsePositive;
	private JCheckBox filter;
	protected MyDefaultListModel listDataModel;
	protected Map<String, List<MavenArtifactNode>> allArtifactsMap;
	protected Map<String, List<MavenArtifactNode>> allArtifactsMapWithoutTests;
	protected DefaultTreeModel rightTreeModel;
	protected DefaultTreeModel leftTreeModel;
	protected MyDefaultMutableTreeNode rightTreeRoot;
	protected MyDefaultMutableTreeNode leftTreeRoot;
	protected ListSpeedSearch myListSpeedSearch;
	protected List<MavenArtifactNode> dependencyTree;
	protected List<MavenArtifactNode> dependencyTreeWithoutTests;
	protected CardLayout leftPanelLayout;
	private List<MyListNode> allArtifactsListNodes = Collections.emptyList();
	private List<MyListNode> allArtifactsListNodesWithoutTests = Collections.emptyList();
	private List<MyListNode> conflictArtifactsListNodes = Collections.emptyList();
	private List<MyListNode> allArtifactsListNodesByArtifactId = Collections.emptyList();
	private List<MyListNode> allArtifactsListNodesWithoutTestsByArtifactId = Collections.emptyList();
	private List<MyListNode> conflictArtifactsListNodesByArtifactId = Collections.emptyList();
	private volatile List<MyListNode> allArtifactsListNodesByDeepSize;
	private volatile List<MyListNode> allArtifactsListNodesWithoutTestsByDeepSize;
	private volatile List<MyListNode> conflictArtifactsListNodesByDeepSize;
	private volatile DependencySizeIndex dependencySizeIndex;

	private boolean notificationShown;

	private final SimpleTextAttributes errorBoldAttributes;

	private MyProjectService.MyEventListener myEventListener;
	private MavenProjectsManager mavenProjectsManager;
	private MyProjectService myProjectService;

	private boolean manualReimport;
	private RightTreePopupHandler rightTreePopupHandler;
	private LeftTreePopupHandler leftTreePopupHandler;
	private ListPopupHandler leftPanelListPopupHandler;

	public GuiForm(@NotNull Project project, VirtualFile file, @NotNull MavenProject mavenProject) {
		this.project = project;
		this.file = file;
		mavenProjectsManager = MavenProjectsManager.getInstance(project);
		myProjectService = MyProjectService.getInstance(project);
		this.mavenProject = mavenProject;

		intellijBugLabel.setText("<html>\n" +
				"  <head>\n" +
				"\n" +
				"  </head>\n" +
			"  <body>\n" +
			"      1) An artifact is in conflict, its version is probably wrongly resolved due to a <a href=\"https://youtrack.jetbrains.com/issue/IDEA-215596\">bug in IntelliJ</a>." +
			"  </body>\n" +
			"</html>\n");
		intellijBugLabel.setBackground(rootPanel.getBackground());
		intellijBugLabel.setForeground(applicationService.getState().getErrorAttributes().getFgColor());
		intellijBugLabel.setVisible(false);
		intellijBugLabel.addHyperlinkListener(e -> {
			if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
				BrowserUtil.browse(e.getURL());
			}
		});


		falsePositive.setText("<html>\n" +
			"  <head>\n" +
			"\n" +
			"  </head>\n" +
			"  <body>\n" +
			"      2) Probably a false positive, this should not happen, please report it at <a href=\"https://github.com/krasa/MavenHelper/issues/\">GitHub</a>." +
			"  </body>\n" +
			"</html>\n");
		falsePositive.setBackground(rootPanel.getBackground());
		falsePositive.setForeground(applicationService.getState().getErrorAttributes().getFgColor());
		falsePositive.setVisible(false);
		falsePositive.addHyperlinkListener(e -> {
			if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
				BrowserUtil.browse(e.getURL());
			}
		});
		
		final ActionListener radioButtonListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				scheduleUpdateLeftPanel();

				String value = null;
				if (allDependenciesAsListRadioButton.isSelected()) {
					value = "list";
				} else if (allDependenciesAsTreeRadioButton.isSelected()) {
					value = "tree";
				}
				PropertiesComponent.getInstance().setValue(LAST_RADIO_BUTTON, value);
			}
		};
		conflictsRadioButton.addActionListener(radioButtonListener);
		allDependenciesAsListRadioButton.addActionListener(radioButtonListener);
		allDependenciesAsTreeRadioButton.addActionListener(radioButtonListener);

		refreshButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				refreshButton.setToolTipText(null);
				refreshButton.setIcon(null);

				initializeModel();
				rootPanel.requestFocus();
			}
		});

		myListSpeedSearch = new ListSpeedSearch(leftPanelList);
		searchField.addDocumentListener(new DocumentAdapter() {
			@Override
			protected void textChanged(DocumentEvent documentEvent) {
				scheduleUpdateLeftPanel();
			}
		});
		try {
			Method searchField = this.searchField.getClass().getMethod("getTextEditor");
			JTextField invoke = (JTextField) searchField.invoke(this.searchField);
			invoke.addFocusListener(new FocusAdapter() {
				@Override
				public void focusLost(FocusEvent e) {
					if (GuiForm.this.searchField.getText() != null) {
						GuiForm.this.searchField.addCurrentTextToHistory();
					}
				}
			});
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		noConflictsWarningLabel.setBackground(null);
		noConflictsWarningLabel.setText(WARNING);
		noConflictsWarningLabel.setForeground(applicationService.getState().getErrorAttributes().getFgColor());

		leftPanelLayout = (CardLayout) leftPanelWrapper.getLayout();

		rightTreeRoot = new MyDefaultMutableTreeNode();
		rightTreeModel = new DefaultTreeModel(rightTreeRoot);
		rightTree.setModel(rightTreeModel);
		rightTree.setRootVisible(false);
		rightTree.setShowsRootHandles(true);
		rightTree.expandPath(new TreePath(rightTreeRoot.getPath()));
		rightTree.setCellRenderer(new TreeRenderer(showGroupId, showSize, this));
		rightTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		rightTreePopupHandler = new RightTreePopupHandler(project, mavenProject, rightTree, this);
		rightTree.addMouseListener(rightTreePopupHandler);
		rightTree.setMavenProject(mavenProject);
		rightTree.setProject(project);

		leftTree.addTreeSelectionListener(new LeftTreeSelectionListener());
		leftTreeRoot = new MyDefaultMutableTreeNode();
		leftTreeModel = new DefaultTreeModel(leftTreeRoot);
		leftTree.setModel(leftTreeModel);
		leftTree.setRootVisible(false);
		leftTree.setShowsRootHandles(true);
		leftTree.expandPath(new TreePath(leftTreeRoot.getPath()));
		leftTree.setCellRenderer(new TreeRenderer(showGroupId, showSize, this));
		leftTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		leftTreePopupHandler = new LeftTreePopupHandler(project, mavenProject, leftTree);
		leftTree.addMouseListener(leftTreePopupHandler);
		leftTree.setMavenProject(mavenProject);
		leftTree.setProject(project);


		leftPanelListPopupHandler = new ListPopupHandler(project, mavenProject, leftPanelList, this);
		leftPanelList.addMouseListener(leftPanelListPopupHandler);
		leftPanelList.addKeyListener(new ListKeyStrokeAdapter(this));

		showGroupId.addActionListener((event) -> {
			final RestoreSelection restoreSelection = new RestoreSelection(leftPanelList, leftTree);
			PropertiesComponent.getInstance().setValue(LAST_SHOW_GROUP_ID_CHECKBOX, showGroupId.isSelected());
			scheduleUpdateLeftPanel(restoreSelection::restore);
		});

		showSize.addActionListener((event) -> {
			RestoreSelection restoreSelection = new RestoreSelection(leftPanelList, leftTree);
			PropertiesComponent.getInstance().setValue(LAST_SHOW_SIZE_CHECKBOX, showSize.isSelected());
			scheduleUpdateLeftPanel(restoreSelection::restore);
		});

		filter.addActionListener((event) -> {
			RestoreSelection restoreSelection = new RestoreSelection(leftPanelList, leftTree);
			PropertiesComponent.getInstance().setValue(LAST_FILTER_CHECKBOX, filter.isSelected());
			scheduleUpdateLeftPanel(restoreSelection::restore);
		});

		hideTests.addActionListener((event) -> {
			RestoreSelection restoreSelection = new RestoreSelection(leftPanelList, leftTree);
			PropertiesComponent.getInstance().setValue(LAST_HIDE_TESTS_CHECKBOX, hideTests.isSelected());
			scheduleUpdateLeftPanel(restoreSelection::restore);
		});

		final DefaultTreeExpander treeExpander = new DefaultTreeExpander(leftTree);
		DefaultActionGroup actionGroup = new DefaultActionGroup();
		actionGroup.add(CommonActionsManager.getInstance().createExpandAllAction(treeExpander, leftTree));
		actionGroup.add(CommonActionsManager.getInstance().createCollapseAllAction(treeExpander, leftTree));
		ActionToolbar actionToolbar = ActionManagerEx.getInstance().createActionToolbar("krasa.MavenHelperPro.buttons", actionGroup, true);
		actionToolbar.setTargetComponent(rootPanel);
		buttonsPanel.add(actionToolbar.getComponent(), "1");
		errorBoldAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, applicationService.getState().getErrorAttributes().getFgColor());

		String lastRadioButton = PropertiesComponent.getInstance().getValue(LAST_RADIO_BUTTON);
		if ("tree".equals(lastRadioButton)) {
			allDependenciesAsTreeRadioButton.setSelected(true);
		} else if ("list".equals(lastRadioButton)) {
			allDependenciesAsListRadioButton.setSelected(true);
		} else {
			conflictsRadioButton.setSelected(true);
		}
		showGroupId.setSelected(Boolean.parseBoolean(PropertiesComponent.getInstance().getValue(LAST_SHOW_GROUP_ID_CHECKBOX)));
		showSize.setSelected(Boolean.parseBoolean(PropertiesComponent.getInstance().getValue(LAST_SHOW_SIZE_CHECKBOX)));
		filter.setSelected(Boolean.parseBoolean(PropertiesComponent.getInstance().getValue(LAST_FILTER_CHECKBOX)));
		hideTests.setSelected(Boolean.parseBoolean(PropertiesComponent.getInstance().getValue(LAST_HIDE_TESTS_CHECKBOX)));
		Donate.init(donate);


		myEventListener = new MyProjectService.MyEventListener() {

			@Override
			public void projectResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges, @Nullable NativeMavenProjectHolder nativeMavenProject) {
				if (projectWithChanges.first == mavenProject) {
					if (refreshButton.isShowing() && manualReimport) {
						manualReimport = false;
						refreshButton.doClick();
					} else {
						refreshButton.setIcon(AllIcons.General.BalloonWarning);
						refreshButton.setToolTipText("Maven model changed, refresh UI");
					}
				}
			}
		};
		myProjectService.register(myEventListener);
		reimport.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				manualReimport = true;
				mavenProjectsManager.forceUpdateAllProjectsOrFindAllAvailablePomFiles();
			}
		});
	}

	private void createUIComponents() {
		listDataModel = new MyDefaultListModel();
		leftPanelList = new JBList((ListModel) listDataModel);
		leftPanelList.addListSelectionListener(new MyListSelectionListener());
		// no generics in IJ12
		leftPanelList.setCellRenderer(new ColoredListCellRenderer() {
			@Override
			protected void customizeCellRenderer(JList jList, Object o, int i, boolean b, boolean b2) {
				MyListNode value = (MyListNode) o;
				String rightVersion = value.getRightVersion();
				final String[] split = value.artifactKey.split(":");
				boolean conflict = value.isConflict();

				SimpleTextAttributes attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
				SimpleTextAttributes boldAttributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
				if (conflict && allDependenciesAsListRadioButton.isSelected()) {
					attributes = applicationService.getState().getErrorAttributes();
					boldAttributes = errorBoldAttributes;
				}
				if (showSize.isSelected()) {
					Utils.appendSize(this, value.getSize(), value.getTotalSize());
				}
				if (showGroupId.isSelected()) {
					append(split[0] + " : ", attributes);
				}
				append(split[1], boldAttributes);
				if (rightVersion != null) {
					append(" : " + rightVersion, attributes);
				}
			}

		});
		rightTree = new MyHighlightingTree();
		leftTree = new MyHighlightingTree();
	}

	@Override
	public void dispose() {
		myProjectService.unregister(myEventListener);
	}

	public void switchToLeftTree(MavenArtifactNode myArtifact) {
		allDependenciesAsTreeRadioButton.setSelected(true);
		searchField.setText(myArtifact.getArtifact().getArtifactId());
		updateAlarm.cancelAllRequests();
		startLeftPanelUpdate(captureLeftPanelState(), () -> {
			TreeUtils.selectRows(leftTree, leftTreeRoot, myArtifact);
			leftTree.requestFocus();
		});
	}

	public void switchToLeftTree() {
		MyListNode selectedValue = (MyListNode) leftPanelList.getSelectedValue();
		MavenArtifactNode rightArtifact = selectedValue.getRightArtifact();
		if (rightArtifact != null) {
			switchToLeftTree(rightArtifact);
		}
	}

	private class LeftTreeSelectionListener implements TreeSelectionListener {
		@Override
		public void valueChanged(TreeSelectionEvent e) {
			TreePath selectionPath = e.getPath();
			if (selectionPath != null) {
				DefaultMutableTreeNode lastPathComponent = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
				Object selectedUserObject = lastPathComponent.getUserObject();
				if (!(selectedUserObject instanceof MyTreeUserObject userObject)) {
					return;
				}

				final String key = getArtifactKey(userObject.getArtifact());
				Map<String, List<MavenArtifactNode>> map = hideTests.isSelected() ? allArtifactsMapWithoutTests : allArtifactsMap;
				if (map == null) { // can be null while refreshing
					return;
				}
				List<MavenArtifactNode> mavenArtifactNodes = map.get(key);
				if (mavenArtifactNodes != null) {// can be null while refreshing
					scheduleUpdateRightTree(mavenArtifactNodes);
				}
			}
		}
	}

	private class MyListSelectionListener implements ListSelectionListener {
		@Override
		public void valueChanged(ListSelectionEvent e) {
			if (e.getValueIsAdjusting()) {
				return;
			}
			if (listDataModel.isEmpty() || leftPanelList.getSelectedValue() == null) {
				return;
			}

			final MyListNode myListNode = (MyListNode) leftPanelList.getSelectedValue();
			scheduleUpdateRightTree(myListNode.getArtifacts());
		}
	}

	private void scheduleUpdateRightTree(@NotNull List<MavenArtifactNode> mavenArtifactNodes) {
		long seq = rightTreeUpdateSeq.incrementAndGet();
		rightTreeUpdateAlarm.cancelAllRequests();
		if (mavenArtifactNodes.isEmpty()) {
			rightTreeRoot.removeAllChildren();
			rightTreeModel.nodeStructureChanged(rightTreeRoot);
			return;
		}
		rightTreeUpdateAlarm.addRequest(() -> startRightTreeUpdate(mavenArtifactNodes, seq), RIGHT_TREE_UPDATE_DEBOUNCE_MS);
	}

	private void startRightTreeUpdate(@NotNull List<MavenArtifactNode> mavenArtifactNodes, long seq) {
		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			RightTreeUpdateResult result = computeRightTreeUpdate(mavenArtifactNodes, seq);
			ApplicationManager.getApplication().invokeLater(() -> applyRightTreeUpdate(result, seq), ModalityState.any());
		});
	}

	@Nullable
	private RightTreeUpdateResult computeRightTreeUpdate(@NotNull List<MavenArtifactNode> mavenArtifactNodes, long seq) {
		if (isRightTreeUpdateCancelled(seq)) {
			return null;
		}

		int totalPaths = mavenArtifactNodes.size();
		int shownPaths = Math.min(totalPaths, RIGHT_TREE_MAX_PATHS);

		NodeCounter nodeCounter = new NodeCounter();
		List<DefaultMutableTreeNode> children = new ArrayList<>(shownPaths + 1);

		if (totalPaths > shownPaths) {
			children.add(new DefaultMutableTreeNode("Too many paths (" + totalPaths + "), showing first " + shownPaths + ". Refine search to narrow results."));
		}

		for (int i = 0; i < shownPaths; i++) {
			if (isRightTreeUpdateCancelled(seq)) {
				return null;
			}
			MavenArtifactNode mavenArtifactNode = mavenArtifactNodes.get(i);
			MyTreeUserObject userObject = new MyTreeUserObject(mavenArtifactNode);
			userObject.showOnlyVersion = true;
			DefaultMutableTreeNode newNode = new MyDefaultMutableTreeNode(userObject);
			nodeCounter.count++;
			fillRightTree(mavenArtifactNode, newNode, seq, nodeCounter);
			children.add(newNode);
		}

		boolean autoExpand = nodeCounter.count <= RIGHT_TREE_AUTO_EXPAND_NODE_LIMIT;
		return new RightTreeUpdateResult(children, autoExpand);
	}

	private void applyRightTreeUpdate(@Nullable RightTreeUpdateResult result, long seq) {
		if (result == null) {
			return;
		}
		if (isRightTreeUpdateCancelled(seq)) {
			return;
		}

		rightTreeRoot.removeAllChildren();
		for (DefaultMutableTreeNode node : result.children) {
			rightTreeRoot.add(node);
		}
		rightTreeModel.nodeStructureChanged(rightTreeRoot);
		rightTree.expandPath(new TreePath(rightTreeRoot.getPath()));
		if (result.autoExpand) {
			TreeUtils.expandAll(rightTree);
		}
	}

	private boolean isRightTreeUpdateCancelled(long seq) {
		return seq != rightTreeUpdateSeq.get() || project.isDisposed();
	}

	private void fillRightTree(@NotNull MavenArtifactNode mavenArtifactNode, @NotNull DefaultMutableTreeNode node, long seq, @NotNull NodeCounter nodeCounter) {
		if (isRightTreeUpdateCancelled(seq)) {
			return;
		}
		MavenArtifactNode parent = mavenArtifactNode.getParent();
		if (parent == null) {
			return;
		}
		DefaultMutableTreeNode parentDependencyNode = new MyDefaultMutableTreeNode(new MyTreeUserObject(parent));
		node.add(parentDependencyNode);
		nodeCounter.count++;
		fillRightTree(parent, parentDependencyNode, seq, nodeCounter);
	}

	private record RightTreeUpdateResult(@NotNull List<DefaultMutableTreeNode> children, boolean autoExpand) {
	}

	private void initializeModel() {
		if (modelLoading) {
			return;
		}
		updateAlarm.cancelAllRequests();
		rightTreeUpdateAlarm.cancelAllRequests();
		rightTreeUpdateSeq.incrementAndGet();

		intellijBugLabel.setVisible(false);
		falsePositive.setVisible(false);
		rightTreePopupHandler.hidePopup();
		leftTreePopupHandler.hidePopup();

		final Object selectedValue = leftPanelList.getSelectedValue();

		modelLoading = true;
		setLoadingUi(true);
		ProgressManager.getInstance().run(new Task.Backgroundable(project, "Maven Helper Pro: Building dependency model", true) {
			private List<MavenArtifactNode> newDependencyTree;
			private List<MavenArtifactNode> newDependencyTreeWithoutTests;
			private Map<String, List<MavenArtifactNode>> newAllArtifactsMap;
			private Map<String, List<MavenArtifactNode>> newAllArtifactsMapWithoutTests;
			private List<MyListNode> newAllArtifactsListNodes;
			private List<MyListNode> newAllArtifactsListNodesWithoutTests;
			private List<MyListNode> newConflictArtifactsListNodes;
			private List<MyListNode> newAllArtifactsListNodesByArtifactId;
			private List<MyListNode> newAllArtifactsListNodesWithoutTestsByArtifactId;
			private List<MyListNode> newConflictArtifactsListNodesByArtifactId;
			private long buildNanos;

			@Override
			public void run(@NotNull ProgressIndicator indicator) {
				long start = System.nanoTime();

				newDependencyTree = mavenProject.getDependencyTree();
				indicator.checkCanceled();

				newDependencyTreeWithoutTests = newDependencyTree.stream()
					.filter(n -> !"test".equals(n.getArtifact().getScope()))
					.toList();
				indicator.checkCanceled();

				newAllArtifactsMap = createAllArtifactsMap(newDependencyTree, false);
				indicator.checkCanceled();
				newAllArtifactsMapWithoutTests = createAllArtifactsMap(newDependencyTree, true);
				indicator.checkCanceled();

				newAllArtifactsListNodes = createListNodes(newAllArtifactsMap);
				newAllArtifactsListNodesWithoutTests = createListNodes(newAllArtifactsMapWithoutTests);
				newConflictArtifactsListNodes = createVersionConflictListNodes(newAllArtifactsListNodes);

				newAllArtifactsListNodesByArtifactId = sortedCopy(newAllArtifactsListNodes, MyDefaultListModel.ARTIFACT_ID);
				newAllArtifactsListNodesWithoutTestsByArtifactId = sortedCopy(newAllArtifactsListNodesWithoutTests, MyDefaultListModel.ARTIFACT_ID);
				newConflictArtifactsListNodesByArtifactId = sortedCopy(newConflictArtifactsListNodes, MyDefaultListModel.ARTIFACT_ID);

				buildNanos = System.nanoTime() - start;
			}

			@Override
			public void onSuccess() {
				modelLoading = false;
				setLoadingUi(false);

				if (project.isDisposed()) {
					return;
				}

				dependencyTree = newDependencyTree;
				dependencyTreeWithoutTests = newDependencyTreeWithoutTests;
				allArtifactsMap = newAllArtifactsMap;
				allArtifactsMapWithoutTests = newAllArtifactsMapWithoutTests;
				allArtifactsListNodes = newAllArtifactsListNodes;
				allArtifactsListNodesWithoutTests = newAllArtifactsListNodesWithoutTests;
				conflictArtifactsListNodes = newConflictArtifactsListNodes;
				allArtifactsListNodesByArtifactId = newAllArtifactsListNodesByArtifactId;
				allArtifactsListNodesWithoutTestsByArtifactId = newAllArtifactsListNodesWithoutTestsByArtifactId;
				conflictArtifactsListNodesByArtifactId = newConflictArtifactsListNodesByArtifactId;
				allArtifactsListNodesByDeepSize = null;
				allArtifactsListNodesWithoutTestsByDeepSize = null;
				conflictArtifactsListNodesByDeepSize = null;
				dependencySizeIndex = null;

				updateAlarm.cancelAllRequests();
				startLeftPanelUpdate(captureLeftPanelState(), null);

				rightTreeRoot.removeAllChildren();
				rightTreeModel.reload();
				leftPanelWrapper.revalidate();

				if (selectedValue != null) {
					leftPanelList.setSelectedValue(selectedValue, true);
				}

				if (LOG.isDebugEnabled()) {
					LOG.debug("Dependency model built in " + (buildNanos / 1_000_000) + " ms for " + file.getPath());
				}
			}

			@Override
			public void onCancel() {
				modelLoading = false;
				setLoadingUi(false);
			}

			@Override
			public void onThrowable(@NotNull Throwable error) {
				modelLoading = false;
				setLoadingUi(false);
				LOG.warn("Failed to build Maven Helper Pro dependency model for " + file.getPath(), error);
			}
		});
	}

	private void setLoadingUi(boolean loading) {
		rootPanel.setCursor(loading ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
		refreshButton.setEnabled(!loading);
		searchField.setEnabled(!loading);
	}

	private void scheduleUpdateLeftPanel() {
		scheduleUpdateLeftPanel(null);
	}

	private void scheduleUpdateLeftPanel(@Nullable Runnable afterUpdate) {
		if (modelLoading || dependencyTree == null) {
			return;
		}
		LeftPanelState state = captureLeftPanelState();
		updateAlarm.cancelAllRequests();
		updateAlarm.addRequest(() -> startLeftPanelUpdate(state, afterUpdate), UPDATE_DEBOUNCE_MS);
	}

	@NotNull
	private LeftPanelState captureLeftPanelState() {
		LeftPanelMode mode;
		if (conflictsRadioButton.isSelected()) {
			mode = LeftPanelMode.CONFLICTS;
		} else if (allDependenciesAsListRadioButton.isSelected()) {
			mode = LeftPanelMode.ALL_AS_LIST;
		} else {
			mode = LeftPanelMode.ALL_AS_TREE;
		}
		return new LeftPanelState(
			mode,
			StringUtil.notNullize(searchField.getText()),
			hideTests.isSelected(),
			filter.isSelected(),
			showGroupId.isSelected(),
			showSize.isSelected()
		);
	}

	private void startLeftPanelUpdate(@NotNull LeftPanelState state, @Nullable Runnable afterUpdate) {
		long seq = uiUpdateSeq.incrementAndGet();
		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			LeftPanelUpdateResult result = computeLeftPanelUpdate(state, seq);
			ApplicationManager.getApplication().invokeLater(() -> applyLeftPanelUpdate(state, result, seq, afterUpdate), ModalityState.any());
		});
	}

	private static List<MyListNode> createListNodes(@Nullable Map<String, List<MavenArtifactNode>> map) {
		if (map == null || map.isEmpty()) {
			return Collections.emptyList();
		}
		List<MyListNode> result = new ArrayList<>(map.size());
		for (Map.Entry<String, List<MavenArtifactNode>> entry : map.entrySet()) {
			result.add(new MyListNode(entry));
		}
		return result;
	}

	private static <T> @NotNull List<T> sortedCopy(@NotNull List<T> source, @NotNull Comparator<? super T> comparator) {
		if (source.isEmpty()) {
			return Collections.emptyList();
		}
		List<T> copy = new ArrayList<>(source);
		copy.sort(comparator);
		return copy;
	}

	private List<MyListNode> createVersionConflictListNodes(@Nullable List<MyListNode> listNodes) {
		if (listNodes == null || listNodes.isEmpty()) {
			return Collections.emptyList();
		}
		List<MyListNode> result = new ArrayList<>();
		for (MyListNode node : listNodes) {
			List<MavenArtifactNode> nodes = node.getArtifacts();
			if (nodes != null && nodes.size() > 1 && hasConflicts(nodes)) {
				result.add(node);
			}
		}
		return result;
	}

	@Nullable
	private LeftPanelUpdateResult computeLeftPanelUpdate(@NotNull LeftPanelState state, long seq) {
		if (allArtifactsMap == null || dependencyTree == null) {
			return null;
		}
		if (isLeftPanelUpdateCancelled(seq)) {
			return null;
		}

		DependencySizeIndex sizeIndex = null;
		if (state.showSize) {
			sizeIndex = getOrComputeDependencySizeIndex(seq);
			if (sizeIndex == null) {
				return null;
			}
		}

		String searchText = state.searchText;
		if (state.mode == LeftPanelMode.ALL_AS_TREE) {
			NodeCounter nodeCounter = new NodeCounter();
			MyDefaultMutableTreeNode root = new MyDefaultMutableTreeNode();
			fillLeftTree(root, state.hideTests ? dependencyTreeWithoutTests : dependencyTree, searchText, false, nodeCounter, state.filterTree, sizeIndex, seq);
			sortTree(root, state);
			return new TreeResult(root, nodeCounter.count);
		}

		boolean showNoConflictsLabel = state.mode == LeftPanelMode.CONFLICTS && isNoConflicts();

		List<MyListNode> source = getListSourceSorted(state, sizeIndex, seq);
		if (source == null) {
			return null;
		}

		if (StringUtil.isEmptyOrSpaces(searchText)) {
			return new ListResult(source, showNoConflictsLabel);
		}

		List<MyListNode> items = new ArrayList<>();
		for (MyListNode node : source) {
			if (isLeftPanelUpdateCancelled(seq)) {
				return null;
			}
			if (contains(searchText, node.artifactKey)) {
				items.add(node);
			}
		}
		return new ListResult(items, showNoConflictsLabel);
	}

	@Nullable
	private List<MyListNode> getListSourceSorted(@NotNull LeftPanelState state, @Nullable DependencySizeIndex sizeIndex, long seq) {
		if (state.mode == LeftPanelMode.CONFLICTS) {
			if (state.showSize) {
				if (sizeIndex == null) {
					return null;
				}
				return getOrComputeDeepSizeListNodes(DeepSizeList.CONFLICTS, sizeIndex, seq);
			}
			if (state.showGroupId) {
				return conflictArtifactsListNodes;
			}
			return conflictArtifactsListNodesByArtifactId;
		}

		if (state.showSize) {
			if (sizeIndex == null) {
				return null;
			}
			return getOrComputeDeepSizeListNodes(state.hideTests ? DeepSizeList.ALL_WITHOUT_TESTS : DeepSizeList.ALL, sizeIndex, seq);
		}
		if (state.showGroupId) {
			return state.hideTests ? allArtifactsListNodesWithoutTests : allArtifactsListNodes;
		}
		return state.hideTests ? allArtifactsListNodesWithoutTestsByArtifactId : allArtifactsListNodesByArtifactId;
	}

	@Nullable
	private List<MyListNode> getOrComputeDeepSizeListNodes(@NotNull DeepSizeList list, @NotNull DependencySizeIndex sizeIndex, long seq) {
		List<MyListNode> cached = getDeepSizeListCache(list);
		if (cached != null) {
			return cached;
		}

		synchronized (this) {
			cached = getDeepSizeListCache(list);
			if (cached != null) {
				return cached;
			}

			List<MyListNode> base = switch (list) {
				case ALL -> allArtifactsListNodes;
				case ALL_WITHOUT_TESTS -> allArtifactsListNodesWithoutTests;
				case CONFLICTS -> conflictArtifactsListNodes;
			};

			if (!ensureListNodeSizes(base, sizeIndex, seq)) {
				return null;
			}
			if (isLeftPanelUpdateCancelled(seq)) {
				return null;
			}

			List<MyListNode> sorted = sortedCopy(base, MyDefaultListModel.DEEP_SIZE);
			if (isLeftPanelUpdateCancelled(seq)) {
				return null;
			}
			setDeepSizeListCache(list, sorted);
			return sorted;
		}
	}

	private boolean ensureListNodeSizes(@NotNull List<MyListNode> nodes, @NotNull DependencySizeIndex sizeIndex, long seq) {
		for (MyListNode node : nodes) {
			if (isLeftPanelUpdateCancelled(seq)) {
				return false;
			}
			if (!node.hasComputedTotalSize()) {
				sizeIndex.apply(node);
			}
		}
		return !isLeftPanelUpdateCancelled(seq);
	}

	@Nullable
	private List<MyListNode> getDeepSizeListCache(@NotNull DeepSizeList list) {
		return switch (list) {
			case ALL -> allArtifactsListNodesByDeepSize;
			case ALL_WITHOUT_TESTS -> allArtifactsListNodesWithoutTestsByDeepSize;
			case CONFLICTS -> conflictArtifactsListNodesByDeepSize;
		};
	}

	private void setDeepSizeListCache(@NotNull DeepSizeList list, @NotNull List<MyListNode> value) {
		switch (list) {
			case ALL -> allArtifactsListNodesByDeepSize = value;
			case ALL_WITHOUT_TESTS -> allArtifactsListNodesWithoutTestsByDeepSize = value;
			case CONFLICTS -> conflictArtifactsListNodesByDeepSize = value;
		}
	}

	private enum DeepSizeList {
		ALL,
		ALL_WITHOUT_TESTS,
		CONFLICTS,
	}

	private void applyLeftPanelUpdate(@NotNull LeftPanelState state, @Nullable LeftPanelUpdateResult result, long seq, @Nullable Runnable afterUpdate) {
		if (result == null) {
			return;
		}
		if (isLeftPanelUpdateCancelled(seq)) {
			return;
		}
		if (modelLoading || dependencyTree == null || allArtifactsMap == null) {
			return;
		}

		intellijBugLabel.setVisible(false);
		falsePositive.setVisible(false);
		boolean conflictsWarning = false;
		boolean showNoConflictsLabel;

		if (result instanceof ListResult listResult) {
			showNoConflictsLabel = listResult.showNoConflictsLabel;
			listDataModel.setItems(listResult.items);
			leftPanelLayout.show(leftPanelWrapper, "list");
		} else if (result instanceof TreeResult treeResult) {
			leftTreeRoot = treeResult.root;
			leftTreeModel.setRoot(leftTreeRoot);
			leftTreeModel.reload();
			if (treeResult.nodeCount <= AUTO_EXPAND_NODE_LIMIT) {
				TreeUtils.expandAll(leftTree);
			}
			showNoConflictsLabel = false;
			leftPanelLayout.show(leftPanelWrapper, "allAsTree");
		} else {
			return;
		}

		if (conflictsWarning) {
			javax.swing.SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					noConflictsWarningLabelScrollPane.getVerticalScrollBar().setValue(0);
				}
			});
			leftPanelLayout.show(leftPanelWrapper, "noConflictsWarningLabel");
		}
		buttonsPanel.setVisible(allDependenciesAsTreeRadioButton.isSelected());
		filter.setVisible(allDependenciesAsTreeRadioButton.isSelected());
		noConflictsWarningLabelScrollPane.setVisible(conflictsWarning);
		noConflictsLabel.setVisible(showNoConflictsLabel);

		if (afterUpdate != null) {
			afterUpdate.run();
		}
	}

	private boolean isNoConflicts() {
		return conflictArtifactsListNodes.isEmpty();
	}

	private boolean isLeftPanelUpdateCancelled(long seq) {
		return seq != uiUpdateSeq.get() || project.isDisposed();
	}

	@Nullable
	private DependencySizeIndex getOrComputeDependencySizeIndex(long seq) {
		DependencySizeIndex cached = dependencySizeIndex;
		if (cached != null) {
			return cached;
		}

		DependencySizeIndex computed = DependencySizeIndex.compute(dependencyTree, () -> isLeftPanelUpdateCancelled(seq));
		if (computed == null) {
			return null;
		}
		dependencySizeIndex = computed;
		return computed;
	}

	private void sortTree(@NotNull MyDefaultMutableTreeNode root, @NotNull LeftPanelState state) {
		if (state.showSize) {
			root.sortBySize(MyDefaultMutableTreeNode.DEEP_SIZE);
		} else if (state.showGroupId) {
			root.sortBySize(MyDefaultMutableTreeNode.GROUP_ID);
		} else {
			root.sortBySize(MyDefaultMutableTreeNode.ARTIFACT_ID);
		}
	}

	private static final class NodeCounter {
		private int count;
	}

	private static final class DependencySizeIndex {
		private final IdentityHashMap<MavenArtifactNode, Long> sizeKbByNode;
		private final IdentityHashMap<MavenArtifactNode, Long> totalKbByNode;

		private DependencySizeIndex(
			@NotNull IdentityHashMap<MavenArtifactNode, Long> sizeKbByNode,
			@NotNull IdentityHashMap<MavenArtifactNode, Long> totalKbByNode
		) {
			this.sizeKbByNode = sizeKbByNode;
			this.totalKbByNode = totalKbByNode;
		}

		@Nullable
		static DependencySizeIndex compute(@NotNull List<MavenArtifactNode> roots, @NotNull BooleanSupplier cancelled) {
			IdentityHashMap<MavenArtifactNode, Boolean> visited = new IdentityHashMap<>();
			ArrayDeque<MavenArtifactNode> stack = new ArrayDeque<>();
			ArrayDeque<MavenArtifactNode> postOrder = new ArrayDeque<>();

			for (MavenArtifactNode root : roots) {
				if (cancelled.getAsBoolean()) {
					return null;
				}
				if (root == null) {
					continue;
				}
				stack.push(root);
				while (!stack.isEmpty()) {
					if (cancelled.getAsBoolean()) {
						return null;
					}
					MavenArtifactNode node = stack.pop();
					if (visited.put(node, Boolean.TRUE) != null) {
						continue;
					}
					postOrder.push(node);
					for (MavenArtifactNode dep : node.getDependencies()) {
						if (dep != null) {
							stack.push(dep);
						}
					}
				}
			}

			IdentityHashMap<MavenArtifactNode, Long> sizeKbByNode = new IdentityHashMap<>(visited.size() * 2);
			IdentityHashMap<MavenArtifactNode, Long> totalKbByNode = new IdentityHashMap<>(visited.size() * 2);

			while (!postOrder.isEmpty()) {
				if (cancelled.getAsBoolean()) {
					return null;
				}
				MavenArtifactNode node = postOrder.pop();
				long sizeKb = computeSizeKb(node);
				sizeKbByNode.put(node, sizeKb);

				long totalKb = sizeKb;
				for (MavenArtifactNode dep : node.getDependencies()) {
					Long depTotal = totalKbByNode.get(dep);
					if (depTotal != null) {
						totalKb += depTotal;
					}
				}
				totalKbByNode.put(node, totalKb);
			}

			return new DependencySizeIndex(sizeKbByNode, totalKbByNode);
		}

		private static long computeSizeKb(@NotNull MavenArtifactNode node) {
			File file = node.getArtifact().getFile();
			if (file == null) {
				return 0;
			}
			return file.length() / 1024;
		}

		void apply(@NotNull MyListNode node) {
			MavenArtifactNode right = node.getRightArtifact();
			if (right == null) {
				node.setSizes(0, 0);
				return;
			}
			node.setSizes(sizeKbOf(right), totalKbOf(right));
		}

		void apply(@NotNull MyTreeUserObject userObject, @NotNull MavenArtifactNode node) {
			userObject.setSizes(sizeKbOf(node), totalKbOf(node));
		}

		private long totalKbOf(@NotNull MavenArtifactNode node) {
			Long total = totalKbByNode.get(node);
			return total != null ? total : sizeKbOf(node);
		}

		private long sizeKbOf(@NotNull MavenArtifactNode node) {
			Long size = sizeKbByNode.get(node);
			return size != null ? size : computeSizeKb(node);
		}
	}

	private boolean fillLeftTree(DefaultMutableTreeNode parent, List<MavenArtifactNode> dependencyTree, String searchFieldText, boolean parentMatched, NodeCounter nodeCounter, boolean filterTree, @Nullable DependencySizeIndex sizeIndex, long seq) {
		boolean search = !StringUtil.isEmptyOrSpaces(searchFieldText);
		boolean hasAddedNodes = false;

		for (MavenArtifactNode mavenArtifactNode : dependencyTree) {
			if (isLeftPanelUpdateCancelled(seq)) {
				return false;
			}
			boolean directMatch = false;
			MyTreeUserObject treeUserObject = new MyTreeUserObject(mavenArtifactNode);
			if (sizeIndex != null) {
				sizeIndex.apply(treeUserObject, mavenArtifactNode);
			}
			if (search && contains(searchFieldText, mavenArtifactNode)) {
				directMatch = true;
				treeUserObject.highlight = true;
			}
			final DefaultMutableTreeNode newNode = new MyDefaultMutableTreeNode(treeUserObject);
			boolean childAdded = fillLeftTree(newNode, mavenArtifactNode.getDependencies(), searchFieldText, directMatch || parentMatched, nodeCounter, filterTree, sizeIndex, seq);

			if (!search || !filterTree || directMatch || childAdded || parentMatched) {
				parent.add(newNode);
				nodeCounter.count++;
				hasAddedNodes = true;
			}
		}
		return hasAddedNodes;
	}

	private enum LeftPanelMode {
		CONFLICTS,
		ALL_AS_LIST,
		ALL_AS_TREE,
	}

	private record LeftPanelState(
		@NotNull LeftPanelMode mode,
		@NotNull String searchText,
		boolean hideTests,
		boolean filterTree,
		boolean showGroupId,
		boolean showSize
	) {
	}

	private sealed interface LeftPanelUpdateResult permits ListResult, TreeResult {
	}

	private record ListResult(@NotNull List<MyListNode> items, boolean showNoConflictsLabel) implements LeftPanelUpdateResult {
	}

	private record TreeResult(@NotNull MyDefaultMutableTreeNode root, int nodeCount) implements LeftPanelUpdateResult {
	}

	private boolean contains(String searchFieldText, MavenArtifactNode mavenArtifactNode) {
		MavenArtifact artifact = mavenArtifactNode.getArtifact();
		return contains(searchFieldText, getArtifactKey(artifact));
	}

	private boolean contains(String searchFieldText, String artifactKey) {
		return StringUtil.isEmptyOrSpaces(searchFieldText) || StringUtil.containsIgnoreCase(artifactKey, searchFieldText);
	}

	private boolean hasConflicts(List<MavenArtifactNode> nodes) {
		String version = null;
		for (MavenArtifactNode node : nodes) {
			if (version != null && !version.equals(node.getArtifact().getVersion())) {
				return true;
			}
			version = node.getArtifact().getVersion();
		}
		return false;
	}

	private Map<String, List<MavenArtifactNode>> createAllArtifactsMap(List<MavenArtifactNode> dependencyTree, boolean ignoreTestScope) {
		final Map<String, List<MavenArtifactNode>> map = new TreeMap<String, List<MavenArtifactNode>>();
		addAll(map, dependencyTree, ignoreTestScope, 0);
		return map;
	}

	private void addAll(Map<String, List<MavenArtifactNode>> map, List<MavenArtifactNode> artifactNodes, boolean ignoreTestScope, int i) {
		if (map == null) {
			return;
		}
		if (i > 100) {
			final StringBuilder stringBuilder = new StringBuilder();
			for (MavenArtifactNode s : artifactNodes) {
				final String s1 = s.getArtifact().toString();
				stringBuilder.append(s1);
				stringBuilder.append(" ");
			}
			LOG.error("Recursion aborted, artifactNodes = [" + stringBuilder + "]");
			return;
		}
		for (MavenArtifactNode mavenArtifactNode : artifactNodes) {
			final MavenArtifact artifact = mavenArtifactNode.getArtifact();
			if (ignoreTestScope && i == 0 && "test".equals(artifact.getScope())) {
				continue;
			}
			final String key = getArtifactKey(artifact);
			final List<MavenArtifactNode> mavenArtifactNodes = map.get(key);
			if (mavenArtifactNodes == null) {
				final ArrayList<MavenArtifactNode> value = new ArrayList<MavenArtifactNode>(1);
				value.add(mavenArtifactNode);
				map.put(key, value);
			} else {
				mavenArtifactNodes.add(mavenArtifactNode);
			}
			addAll(map, mavenArtifactNode.getDependencies(), ignoreTestScope, i + 1);
		}
	}

	@NotNull
	private String getArtifactKey(MavenArtifact artifact) {
		return artifact.getGroupId() + " : " + artifact.getArtifactId();
	}

	public JComponent getRootComponent() {
		return rootPanel;
	}

	public JComponent getPreferredFocusedComponent() {
		return rootPanel;
	}

	public void selectNotify() {
		if (dependencyTree == null) {
			initializeModel();
			splitPane.setDividerLocation(0.5);
		}
	}

}
