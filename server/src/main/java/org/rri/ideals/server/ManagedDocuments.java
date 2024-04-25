package org.rri.ideals.server;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.util.MiscUtil;
import org.rri.ideals.server.util.TextUtil;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service(Service.Level.PROJECT)
final public class ManagedDocuments {
  private static final Logger LOG = Logger.getInstance(ManagedDocuments.class);
  private final ConcurrentHashMap<LspPath, VersionedTextDocumentIdentifier> docs = new ConcurrentHashMap<>();

  @NotNull
  private final Project project;

  public ManagedDocuments(@NotNull Project project) {
    this.project = project;
  }

  public void startManaging(@NotNull TextDocumentItem textDocument) {
    String uri = textDocument.getUri();

    if (!canAccept(uri)) {
      return;
    }

    final var path = LspPath.fromLspUri(uri);

    if (docs.containsKey(path)) {
      LOG.warn("URI was opened again without being closed, resetting: " + path);
      docs.remove(path);
    }
    LOG.debug("Handling textDocument/didOpen for: " + path);

    // forcibly refresh file system to handle newly created files
    final var virtualFile = path.refreshAndFindVirtualFile();
    if (virtualFile == null) {
      LOG.warn("Couldn't find virtual file: " + path);
      return;
    }

    ApplicationManager.getApplication().invokeAndWait(MiscUtil.asWriteAction(() -> {
      final var editor = getSelectedEditor(virtualFile);
      final var doc = Optional.ofNullable(editor)
          .map(Editor::getDocument)
          .orElse(null);

      if (doc == null)
        return; // todo handle

      if (doc.isWritable()) {
        // set IDEA's copy of the document to have the text with potential unsaved in-memory changes from the client
        doc.setText(normalizeText(textDocument.getText()));
        PsiDocumentManager.getInstance(project).commitDocument(doc);
      }

/*  todo not sure if we need this
        if (client != null) {
          server?.let { registerIndexNotifier(project, client, it) }
          val projectSdk = ProjectRootManager.getInstance(project).projectSdk
          if (projectSdk == null) {
            warnNoJdk(client)
          }
        }
        true
*/
    }));

    var docVersion = Optional.of(textDocument.getVersion())
        .filter(version -> version != 0)
        .orElse(null);
    docs.put(path, new VersionedTextDocumentIdentifier(uri, docVersion));

  }


  public void updateDocument(@NotNull DidChangeTextDocumentParams params) {
    var textDocument = params.getTextDocument();
    var contentChanges = params.getContentChanges();

    String uri = textDocument.getUri();
    if (!canAccept(uri))
      return;

    final var path = LspPath.fromLspUri(uri);

    var managedTextDocId = docs.get(path);
    if (managedTextDocId == null)
      throw new IllegalArgumentException("document isn't being managed: " + uri);

    // Version number of our document should be (theirs - number of content changes)
    // If stored version is null, this means the document has been just saved or opened
    if (managedTextDocId.getVersion() != null && managedTextDocId.getVersion() != (textDocument.getVersion() - contentChanges.size())) {
      LOG.warn(String.format("Version mismatch on document change - " +
          "ours: %d, theirs: %d", managedTextDocId.getVersion(), textDocument.getVersion()));
      return;
    }

    var file = MiscUtil.resolvePsiFile(project, path);

    if (file == null) {
      LOG.warn("Couldn't resolve PSI file at: " + path);
      return;
    }

    // all updates must go through CommandProcessor
    ApplicationManager.getApplication().invokeAndWait(() -> CommandProcessor.getInstance().executeCommand(
        project, MiscUtil.asWriteAction(() -> {
          var doc = MiscUtil.getDocument(file);

          if (doc == null) {
            LOG.warn("Attempted to get Document for updating but it was null: " + path);
            return;
          }

        /*  todo make it configurable
          if(managedTextDoc.contents != doc.text) {
            val change = Diff.buildChanges(managedTextDoc.contents, doc.text)
            LOG.error("Ground truth differed upon change! Old: \n${managedTextDoc.contents}\nNew: \n${doc.text}")
            return@Runnable
          }
          LOG.debug("Doc before:\n\n${doc.text}\n\n")
*/

          if (!doc.isWritable()) {
            LOG.warn("Document isn't writable: " + path);
            return;
          }

          try {
            applyContentChangeEventChanges(doc, contentChanges);
          } catch (Exception e) {
            LOG.error("Error on documentChange", e);
          }

          // Commit changes to the PSI tree, but not to disk
          PsiDocumentManager.getInstance(project).commitDocument(doc);

          // Update the ground truth
          docs.put(path, textDocument);

        }), "LSP: UpdateDocument", "", UndoConfirmationPolicy.REQUEST_CONFIRMATION));
  }

  public void syncDocument(@NotNull TextDocumentIdentifier textDocument) {
    String uri = textDocument.getUri();

    if (!canAccept(uri))
      return;

    var path = LspPath.fromLspUri(uri);

    if (!docs.containsKey(path)) {
      LOG.warn("Tried handling didSave, but the document isn't being managed: " + path);
      return;
    }

    ApplicationManager.getApplication().invokeAndWait(
        MiscUtil.asWriteAction(() -> MiscUtil.invokeWithPsiFileInReadAction(project, path, (psi) -> {
          var doc = MiscUtil.getDocument(psi);
          if (doc == null)
            return; // todo handle

          VirtualFileManager.getInstance().syncRefresh();
          FileDocumentManager.getInstance().reloadFromDisk(doc);
          PsiDocumentManager.getInstance(project).commitAllDocuments();
        })));

    // drop stored version to bring it in sync with the client (if there was any mismatch)
    docs.put(path, new VersionedTextDocumentIdentifier(uri, null));
  }

  public void stopManaging(@NotNull TextDocumentIdentifier textDocument) {
    String uri = textDocument.getUri();
    if (!canAccept(uri))
      return;

    var path = LspPath.fromLspUri(uri);

    final var virtualFile = path.findVirtualFile();
    if (virtualFile != null) {
      ApplicationManager.getApplication().invokeAndWait(() -> {
        FileEditorManager.getInstance(project).closeFile(virtualFile);
      });
    }

    if (docs.remove(path) == null) {
      LOG.warn("Attempted to close document without opening it at: " + path);
    }
  }

  public void forEach(@NotNull Consumer<LspPath> receiver) {
    docs.keySet().forEach(receiver);
  }

  public @Nullable Editor getSelectedEditor(VirtualFile virtualFile) {
    final var fileEditorManager = FileEditorManager.getInstance(project);

    return Optional.ofNullable(FileEditorManager.getInstance(project).getSelectedEditor(virtualFile))
        .map(fileEditor -> ((TextEditor) fileEditor).getEditor())
        .orElseGet(() -> fileEditorManager.openTextEditor(new OpenFileDescriptor(project, virtualFile, 0), false));
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private static boolean canAccept(@NotNull String uri) {
    return uri.startsWith("file:/");
  }

  private void applyContentChangeEventChanges(@NotNull Document doc, @NotNull List<TextDocumentContentChangeEvent> contentChanges) {
    contentChanges.forEach((it) -> applyChange(doc, it));
  }

  private static void applyChange(@NotNull Document doc, TextDocumentContentChangeEvent change) {
    final var text = normalizeText(change.getText());
    if (change.getRange() == null) {
      // Change is the full insertText of the document
      doc.setText(text);
    } else {
      var textRange = TextUtil.toTextRange(doc, change.getRange());

      doc.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), text);
    }
  }

  @NotNull
  private static String normalizeText(@NotNull String text) {
    return text.replace("\r\n", "\n");
  }
}
