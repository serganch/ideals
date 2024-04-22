package org.rri.ideals.server.commands;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final public class ExecutorContext {

  @NotNull
  private final PsiFile file;
  @NotNull
  private final Disposable disposable;
  @Nullable
  private final CancelChecker cancelToken;
  @Nullable
  private final Editor editor;

  public ExecutorContext(@NotNull PsiFile file, @NotNull Disposable disposable, @Nullable Editor editor, @Nullable CancelChecker cancelToken) {
    this.file = file;
    this.editor = editor;
    this.disposable = disposable;
    this.cancelToken = cancelToken;
  }

  public @NotNull PsiFile getPsiFile() {
    return file;
  }

  public @Nullable CancelChecker getCancelToken() {
    return cancelToken;
  }

  public @Nullable Editor getEditor() {
    return editor;
  }

  public @NotNull Disposable getDisposable() {
    return disposable;
  }
}
