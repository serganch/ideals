package org.rri.ideals.server;

import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.codeactions.ActionData;
import org.rri.ideals.server.codeactions.CodeActionService;
import org.rri.ideals.server.completions.CompletionService;
import org.rri.ideals.server.formatting.FormattingCommand;
import org.rri.ideals.server.formatting.OnTypeFormattingCommand;
import org.rri.ideals.server.references.*;
import org.rri.ideals.server.rename.RenameCommand;
import org.rri.ideals.server.signature.SignatureHelpService;
import org.rri.ideals.server.symbol.DocumentSymbolService;
import org.rri.ideals.server.util.AsyncExecutor;
import org.rri.ideals.server.util.Metrics;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class MyTextDocumentService implements TextDocumentService {

  private static final Logger LOG = Logger.getInstance(MyTextDocumentService.class);
  private final @NotNull LspSession session;

  public MyTextDocumentService(@NotNull LspSession session) {
    this.session = session;
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    final var textDocument = params.getTextDocument();

    final var path = LspPath.fromLspUri(textDocument.getUri());

    Metrics.run(() -> "didOpen: " + path, () -> {
      documents().startManaging(textDocument);

      if (DumbService.isDumb(session.getProject())) {
        LOG.debug("Sending indexing started: " + path);
        LspContext.getContext(session.getProject()).getClient().notifyIndexStarted();
      }
  /*  todo
        val projectSdk = ProjectRootManager.getInstance(project).projectSdk
        if (projectSdk == null) {
          warnNoJdk(client)
        }
*/
    });
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    final var path = LspPath.fromLspUri(params.getTextDocument().getUri());

    Metrics.run(() -> "didChange: " + path, () -> {
      documents().updateDocument(params);
    });
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    documents().stopManaging(params.getTextDocument());
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    documents().syncDocument(params.getTextDocument());
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
    return new FindDefinitionCommand()
        .runAsync(session.getProject(), params.getTextDocument(), params.getPosition());
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(TypeDefinitionParams params) {
    return new FindTypeDefinitionCommand()
        .runAsync(session.getProject(), params.getTextDocument(), params.getPosition());
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(ImplementationParams params) {
    return new FindImplementationCommand()
        .runAsync(session.getProject(), params.getTextDocument(), params.getPosition());
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    return new FindUsagesCommand()
        .runAsync(session.getProject(), params.getTextDocument(), params.getPosition());
  }

  @Override
  public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams params) {
    return new DocumentHighlightCommand()
        .runAsync(session.getProject(), params.getTextDocument(), params.getPosition());
  }

  @SuppressWarnings("deprecation")
  @Override
  public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
    final var client = AsyncExecutor.<List<Either<SymbolInformation, DocumentSymbol>>>builder()
        .cancellable(true)
        .executorContext(session.getProject(), params.getTextDocument().getUri(), null)
        .build();

    return client.compute((executorContext -> documentSymbols().computeDocumentSymbols(executorContext)));
  }

  @Override
  public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
    final var client = AsyncExecutor.<List<Either<Command, CodeAction>>>builder()
        .executorContext(session.getProject(), params.getTextDocument().getUri(), params.getRange().getStart())
        .build();

    return client.compute(executorContext ->
        codeActions().getCodeActions(params.getRange(), executorContext).stream()
            .map((Function<CodeAction, Either<Command, CodeAction>>) Either::forRight)
            .toList()
    );
  }


  @Override
  public CompletableFuture<CodeAction> resolveCodeAction(CodeAction unresolved) {
    final var actionData = new GsonBuilder().create()
        .fromJson(unresolved.getData().toString(), ActionData.class);
    final var client = AsyncExecutor.<CodeAction>builder()
        .executorContext(session.getProject(), actionData.getUri(), actionData.getRange().getStart())
        .build();

    return client.compute(executorContext -> {
      var edit = codeActions().applyCodeAction(actionData, unresolved.getTitle(), executorContext);
      unresolved.setEdit(edit);
      return unresolved;
    });
  }

  @NotNull
  private ManagedDocuments documents() {
    return session.getProject().getService(ManagedDocuments.class);
  }

  @NotNull
  private CodeActionService codeActions() {
    return session.getProject().getService(CodeActionService.class);
  }

  @NotNull
  private CompletionService completions() {
    return session.getProject().getService(CompletionService.class);
  }

  @NotNull
  private DocumentSymbolService documentSymbols() {
    return session.getProject().getService(DocumentSymbolService.class);
  }

  @NotNull
  private SignatureHelpService signature() {
    return session.getProject().getService(SignatureHelpService.class);
  }

  @Override
  @NotNull
  public CompletableFuture<CompletionItem> resolveCompletionItem(@NotNull CompletionItem unresolved) {
    return CompletableFutures.computeAsync(
        AppExecutorUtil.getAppExecutorService(),
        (cancelChecker) ->
            completions().resolveCompletion(unresolved, cancelChecker)
    );
  }

  @Override
  @NotNull
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(@NotNull CompletionParams params) {
    final var client = AsyncExecutor.<Either<List<CompletionItem>, CompletionList>>builder()
        .cancellable(true)
        .executorContext(session.getProject(), params.getTextDocument().getUri(), params.getPosition())
        .build();

    return client.compute((executorContext -> Either.forLeft(completions().computeCompletions(executorContext))));
  }

  @Override
  @NotNull
  public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) {
    final var client = AsyncExecutor.<SignatureHelp>builder()
        .cancellable(true)
        .executorContext(session.getProject(), params.getTextDocument().getUri(), params.getPosition())
        .build();
    final var signature = signature();

    return client.compute((signature::computeSignatureHelp));
  }


  @Override
  public CompletableFuture<List<? extends TextEdit>> formatting(@NotNull DocumentFormattingParams params) {
    return new FormattingCommand(null, params.getOptions())
        .runAsync(session.getProject(), params.getTextDocument());
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> rangeFormatting(@NotNull DocumentRangeFormattingParams params) {
    return new FormattingCommand(params.getRange(), params.getOptions())
        .runAsync(session.getProject(), params.getTextDocument());
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
    return new OnTypeFormattingCommand(params.getPosition(), params.getOptions(), params.getCh().charAt(0))
        .runAsync(session.getProject(), params.getTextDocument());
  }

  @Override
  public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
    return new RenameCommand(params.getNewName())
        .runAsync(session.getProject(), params.getTextDocument(), params.getPosition());
  }
}
