package org.rri.ideals.server.lsp;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.LspServer;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.mocks.MockLanguageClient;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RunWith(JUnit4.class)
public abstract class LspServerTestBase extends HeavyPlatformTestCase {

  private LspServer server;

  private MockLanguageClient client;

  @Override
  protected void setUp() throws Exception {
    System.setProperty("idea.log.debug.categories", "#org.rri");
    super.setUp();
  }

  @NotNull
  protected Path getTestDataRoot() {
    return Paths.get("test-data").toAbsolutePath();
  }

  @NotNull
  final protected Path getProjectPath() {
    return getTestDataRoot().resolve(getProjectRelativePath());
  }

  protected abstract String getProjectRelativePath();

  protected String getSourceRoot() {
    return "src";
  }

  protected Editor createEditor(@NotNull LspPath path) {
    final var instance = FileEditorManager.getInstance(myProject);
    final var file = path.findVirtualFile();

    if (file == null || file.getFileType().isBinary()) return null;

    return instance.openTextEditor(new OpenFileDescriptor(myProject, file, 0), false);
  }

  @NotNull
  protected final LspServer server() {
    return server;
  }

  @NotNull
  protected final MockLanguageClient client() {
    return client;
  }

  @Override
  protected void setUpProject() {
    // no IDEA project is created by default
  }

  protected void setupInitializeParams(@NotNull InitializeParams params) {
    Path projectPath = getProjectPath();

    params.setWorkspaceFolders(List.of(new WorkspaceFolder(projectPath.toUri().toString())));

    final var clientCapabilities = new ClientCapabilities();
    setupClientCapabilities(clientCapabilities);
    params.setCapabilities(clientCapabilities);
  }

  @SuppressWarnings("unused")
  protected void setupClientCapabilities(@NotNull ClientCapabilities clientCapabilities) {
    // do nothing by default
  }

  protected void initializeServer() {
    final var initializeParams = new InitializeParams();
    setupInitializeParams(initializeParams);
    TestUtil.getNonBlockingEdt(server.initialize(initializeParams), 30000);
    myProject = server.getProject();

    WriteAction.runAndWait(() ->
        ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(() -> {
          setUpModule();
          setUpJdk();
        })
    );

    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(getProjectPath().resolve(getSourceRoot()).toString());
    PsiTestUtil.addSourceContentToRoots(myModule, vDir);
  }

  @Before
  public void setupServer() {
    server = new LspServer();
    client = new MockLanguageClient();
    server.connect(client);
    initializeServer();
  }

  @After
  public void stopServer() {
    server.stop();
    ProjectManagerEx.getInstanceEx().closeAndDisposeAllProjects(false);
  }

  @Override
  protected boolean isIconRequired() {
    return true;
  }
}
