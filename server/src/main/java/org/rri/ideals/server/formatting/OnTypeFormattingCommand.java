package org.rri.ideals.server.formatting;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.commands.ExecutorContext;
import org.rri.ideals.server.util.EditorUtil;
import org.rri.ideals.server.util.TextUtil;

import java.util.List;
import java.util.function.Supplier;

public class OnTypeFormattingCommand extends FormattingCommandBase {
  private static final Logger LOG = Logger.getInstance(OnTypeFormattingCommand.class);
  @NotNull
  private final Position position;
  private final char triggerCharacter;

  public OnTypeFormattingCommand(@NotNull Position position,
                                 @NotNull FormattingOptions formattingOptions,
                                 char character) {
    super(formattingOptions);
    this.position = position;
    this.triggerCharacter = character;
  }

  @Override
  protected @NotNull Supplier<@NotNull String> getMessageSupplier() {
    return () -> "on type formatting";
  }

  @Override
  protected boolean isCancellable() {
    return false;
  }

  @Override
  protected List<? extends TextEdit> execute(@NotNull ExecutorContext ctx) {
    LOG.info(getMessageSupplier().get());
    return TextUtil.differenceAfterAction(
        ctx.getPsiFile(),
        this::typeAndReformatIfNeededInFile
    );
  }

  void typeAndReformatIfNeededInFile(@NotNull PsiFile psiFile) {
    EditorUtil.withEditor(Disposer.newDisposable(), psiFile, position, editor -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        if (!deleteTypedChar(editor)) {
          return;
        }
        PsiDocumentManager.getInstance(psiFile.getProject()).commitDocument(editor.getDocument());

        if (editor instanceof EditorEx) {
          ((EditorEx) editor).setHighlighter(
              HighlighterFactory.createHighlighter(psiFile.getProject(), psiFile.getFileType()));
        }
        doWithTemporaryCodeStyleSettingsForFile(
            psiFile,
            () -> TypedAction.getInstance().actionPerformed(
                editor,
                triggerCharacter,
                com.intellij.openapi.editor.ex.util.EditorUtil.getEditorDataContext(editor)));
      });
    });
  }

  private boolean deleteTypedChar(@NotNull Editor editor) {
    var insertedCharPos = editor.getCaretModel().getOffset() - 1;

    if (editor.getDocument().getText().charAt(insertedCharPos) != triggerCharacter) {
      // if triggered character and actual are not the same
      LOG.warn("Inserted and triggered characters are not the same");
      return false;
    }

    editor.getSelectionModel().setSelection(
        insertedCharPos,
        insertedCharPos + 1);
    ApplicationManager.getApplication().runWriteAction(
        () -> WriteCommandAction.runWriteCommandAction(
            editor.getProject(),
            () -> EditorModificationUtilEx.deleteSelectedText(editor)
        ));

    editor.getCaretModel().moveToLogicalPosition(
        new LogicalPosition(position.getLine(), position.getCharacter() - 1));
    return true;
  }
}
