package org.rri.ideals.server;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManagerListener;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.util.Metrics;
import org.rri.ideals.server.util.MiscUtil;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LspServer implements LanguageServer, LanguageClientAware, LspSession, DumbService.DumbModeListener {
  private final static Logger LOG = Logger.getInstance(LspServer.class);
  private final MyTextDocumentService myTextDocumentService = new MyTextDocumentService(this);
  private final MyWorkspaceService myWorkspaceService = new MyWorkspaceService(this);

  @NotNull
  private final MessageBusConnection messageBusConnection;
  @Nullable
  private MyLanguageClient client = null;

  @Nullable
  private Project project = null;

  public LspServer() {
    messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
    messageBusConnection.subscribe(ProgressManagerListener.TOPIC, new WorkDoneProgressReporter());
  }

  @NotNull
  @Override
  public CompletableFuture<InitializeResult> initialize(@NotNull InitializeParams params) {
    return CompletableFuture.supplyAsync(() -> {
      final var workspaceFolders = params.getWorkspaceFolders();
      var oldProject = project;
      if(oldProject != null) {
        if(oldProject.isOpen()) {
          LOG.info("Closing old project: " + oldProject);
          ProjectService.getInstance().closeProject(oldProject);
        }
        project = null;
      }

      if (workspaceFolders == null) {
        return new InitializeResult(new ServerCapabilities());
      }

      //   // todo how about multiple folders
      final var projectRoot = LspPath.fromLspUri(workspaceFolders.get(0).getUri());

      Metrics.run(() -> "initialize: " + projectRoot, () -> {

        LOG.info("Opening project: " + projectRoot);
        project = ProjectService.getInstance().resolveProjectFromRoot(projectRoot);

        assert client != null;
        LspContext.createContext(project, client, params.getCapabilities());
        project.getMessageBus().connect().subscribe(DumbService.DUMB_MODE, this);

        LOG.info("LSP was initialized. Project: " + project);
      });

      return new InitializeResult(defaultServerCapabilities());
    });
  }

  @NotNull
  private CompletionOptions defaultCompletionOptions() {
    var completionOptions = new CompletionOptions(true, List.of(".", "@"));
    completionOptions.setResolveProvider(true);
    var completionItemOptions = new CompletionItemOptions();
    completionItemOptions.setLabelDetailsSupport(true);
    completionOptions.setCompletionItem(completionItemOptions);
    return completionOptions;
  }

  @NotNull
  private ServerCapabilities defaultServerCapabilities() {

    return MiscUtil.with(new ServerCapabilities(), it -> {
      it.setTextDocumentSync(MiscUtil.with(new TextDocumentSyncOptions(), (syncOptions) -> {
        syncOptions.setOpenClose(true);
        syncOptions.setChange(TextDocumentSyncKind.Incremental);
        syncOptions.setSave(new SaveOptions(true));
      }));

      it.setWorkspace(MiscUtil.with(new WorkspaceServerCapabilities(), wsc ->
          wsc.setFileOperations(MiscUtil.with(
              new FileOperationsServerCapabilities(),
              foc -> foc.setDidRename(new FileOperationOptions(
                  List.of(new FileOperationFilter(new FileOperationPattern("**/*"), "file"))
              ))
          ))
      ));

//      it.setHoverProvider(true);
      it.setCompletionProvider(defaultCompletionOptions());
      it.setSignatureHelpProvider(MiscUtil.with(new SignatureHelpOptions(), signatureHelpOptions -> signatureHelpOptions.setTriggerCharacters(List.of("(", "["))));
      it.setDefinitionProvider(true);
      it.setTypeDefinitionProvider(true);
      it.setImplementationProvider(true);
      it.setReferencesProvider(true);
      it.setDocumentHighlightProvider(true);
      it.setDocumentSymbolProvider(true);
      it.setWorkspaceSymbolProvider(true);
//      it.setCodeLensProvider(new CodeLensOptions(false));
      it.setDocumentFormattingProvider(true);
      it.setDocumentRangeFormattingProvider(true);
      it.setDocumentOnTypeFormattingProvider(defaultOnTypeFormattingOptions());

      it.setRenameProvider(true);
//      it.setDocumentLinkProvider(null);
//      it.setExecuteCommandProvider(new ExecuteCommandOptions());

      it.setCodeActionProvider(
          MiscUtil.with(
              new CodeActionOptions(List.of(CodeActionKind.QuickFix)),
              cao -> cao.setResolveProvider(true)
          )
      );
      it.setExperimental(null);

    });
  }

  @NotNull
  private static DocumentOnTypeFormattingOptions defaultOnTypeFormattingOptions() {
    return new DocumentOnTypeFormattingOptions(";",
        List.of( // "{", "(", "<",  "\"", "'", "[", todo decide how to handle this cases
            "}", ")", "]", ">", ":", ",", ".", "@", "#", "?", "=", "!", " ",
            "|", "&", "$", "^", "%", "*", "/")
    );
  }

  public CompletableFuture<Object> shutdown() {
    return CompletableFuture.supplyAsync(() -> {
      stop();
      return null;
    });
  }

  public void exit() {
    stop();
  }

  public void stop() {
    messageBusConnection.disconnect();

    if (project != null) {
      final var editorManager = FileEditorManager.getInstance(project);
      for (VirtualFile openFile : editorManager.getOpenFiles()) {
        editorManager.closeFile(openFile);
      }

      ProjectService.getInstance().closeProject(project);
      this.project = null;
    }
  }

  @Override
  public MyTextDocumentService getTextDocumentService() {
    return myTextDocumentService;
  }

  @Override
  public MyWorkspaceService getWorkspaceService() {
    return myWorkspaceService;
  }

  @Override
  public void connect(@NotNull LanguageClient client) {
    assert client instanceof MyLanguageClient;
    this.client = (MyLanguageClient) client;
  }

  @NotNull
  private MyLanguageClient getClient() {
    assert client != null;
    return client;
  }

  @NotNull
  @Override
  public Project getProject() {
    if (project == null)
      throw new IllegalStateException("LSP session is not yet initialized");
    return project;
  }

  @Override
  public void enteredDumbMode() {
    LOG.info("Entered dumb mode. Notifying client...");
    getClient().notifyIndexStarted();
  }

  @Override
  public void exitDumbMode() {
    LOG.info("Exited dumb mode. Refreshing diagnostics...");
    getClient().notifyIndexFinished();
    getTextDocumentService().refreshDiagnostics();
  }

  private class WorkDoneProgressReporter implements ProgressManagerListener {
    @Override
    public void afterTaskStart(@NotNull Task task, @NotNull ProgressIndicator indicator) {
      if(task.getProject() == null || !task.getProject().equals(project))
        return;

      var client = LspServer.this.client;

      if(client == null)
        return;

      final String token = calculateUniqueToken(task);
      try {
        client
            .createProgress(new WorkDoneProgressCreateParams(Either.forLeft(token)))
            .get(500, TimeUnit.MILLISECONDS);
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        LOG.warn("Could not get confirmation when creating work done progress; will act as if it's created", e);
      }

      final var progressBegin = new WorkDoneProgressBegin();
      progressBegin.setTitle(task.getTitle());
      progressBegin.setCancellable(false);
      progressBegin.setPercentage(0);
      client.notifyProgress(new ProgressParams(Either.forLeft(token), Either.forLeft(progressBegin)));
    }

    @Override
    public void afterTaskFinished(@NotNull Task task) {
      if(task.getProject() != null && !task.getProject().equals(project))
        return;

      var client = LspServer.this.client;

      if(client == null)
        return;

      final String token = calculateUniqueToken(task);
      client.notifyProgress(new ProgressParams(Either.forLeft(token), Either.forLeft(new WorkDoneProgressEnd())));
    }

    private String calculateUniqueToken(@NotNull Task task) {
      return task.getClass().getName() + '@' + System.identityHashCode(task);
    }
  }
}