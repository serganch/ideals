package org.rri.ideals.server.commands;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final public class ExecutorContext {

  @NotNull
  private final PsiFile file;
  @Nullable
  private final CancelChecker cancelToken;
  @NotNull
  private final Editor editor;

  public ExecutorContext(@NotNull PsiFile file, @NotNull Editor editor, @Nullable CancelChecker cancelToken) {
    this.file = file;
    this.editor = editor;
    this.cancelToken = cancelToken;
  }

  public @NotNull PsiFile getPsiFile() {
    return file;
  }

  public @Nullable CancelChecker getCancelToken() {
    return cancelToken;
  }

  public @NotNull Editor getEditor() {
    return editor;
  }
}
