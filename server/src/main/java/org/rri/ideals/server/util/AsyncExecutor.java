package org.rri.ideals.server.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.commands.ExecutorContext;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

public class AsyncExecutor<R> {
  private final boolean cancellable;
  private final boolean runInEDT;
  private final Executor executor = AppExecutorUtil.getAppExecutorService();
  @Nullable
  private final PsiFile psiFile;
  private final Project project;
  @Nullable
  private final Position position;

  private AsyncExecutor(@NotNull Builder<R> builder) {
    this.cancellable = builder.cancellable;
    this.psiFile = builder.psiFile;
    this.project = builder.project;
    this.position = builder.position;
    this.runInEDT = builder.runInEDT;
  }

  public static <R> Builder<R> builder() {
    return new AsyncExecutor.Builder<R>();
  }

  public @NotNull CompletableFuture<@Nullable R> compute(@NotNull Function<ExecutorContext, R> action) {
    if (cancellable) {
      return CompletableFutures.computeAsync(executor, cancelToken -> getResult(action, cancelToken));
    } else {
      return CompletableFuture.supplyAsync(() -> getResult(action, null), executor);
    }
  }

  private @Nullable R getResult(@NotNull Function<ExecutorContext, R> action,
                                @Nullable CancelChecker cancelToken) {
    if (psiFile == null) {
      return null;
    }

    final var disposable = Disposer.newDisposable();
    final var editor = Optional.ofNullable(position)
        .map(pos -> MiscUtil.computeInEDTAndWait(() -> EditorUtil.createEditor(disposable, psiFile, pos)))
        .orElse(null);
    final var context = new ExecutorContext(psiFile, disposable, editor, cancelToken);

    try {
      if (runInEDT) {
        return MiscUtil.computeInEDTAndWait(() -> action.apply(context));
      } else {
        return action.apply(context);
      }
    } finally {
      ApplicationManager.getApplication().invokeAndWait(() -> Disposer.dispose(disposable));
      if (cancelToken != null) {
        cancelToken.checkCanceled();
      }
    }
  }

  public static class Builder<R> {
    private boolean cancellable = false;
    private boolean runInEDT = false;
    private Project project;
    private Position position;
    private PsiFile psiFile;

    public Builder<R> cancellable(boolean cancellable) {
      this.cancellable = cancellable;
      return this;
    }

    public Builder<R> executorContext(@NotNull Project project, @NotNull String uri, @Nullable Position position) {
      this.project = project;
      this.psiFile = MiscUtil.resolvePsiFile(project, LspPath.fromLspUri(uri));
      this.position = position;
      return this;
    }

    public Builder<R> runInEDT(boolean runInEDT) {
      this.runInEDT = runInEDT;
      return this;
    }

    public AsyncExecutor<R> build() {
      return new AsyncExecutor<>(this);
    }
  }
}
