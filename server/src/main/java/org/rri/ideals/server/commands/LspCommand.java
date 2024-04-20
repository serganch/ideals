package org.rri.ideals.server.commands;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.util.AsyncExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public abstract class LspCommand<R> {
  private static final Logger LOG = Logger.getInstance(LspCommand.class);

  @NotNull
  protected abstract Supplier<@NotNull String> getMessageSupplier();

  protected abstract boolean isCancellable();

  protected abstract R execute(@NotNull ExecutorContext ctx);

  public @NotNull CompletableFuture<@Nullable R> runAsync(@NotNull Project project, @NotNull TextDocumentIdentifier textDocumentIdentifier) {
    return runAsync(project, textDocumentIdentifier.getUri(), null);
  }

  public @NotNull CompletableFuture<@Nullable R> runAsync(@NotNull Project project, @NotNull TextDocumentIdentifier textDocumentIdentifier, @Nullable Position position) {
    return runAsync(project, textDocumentIdentifier.getUri(), position);
  }

  public @NotNull CompletableFuture<@Nullable R> runAsync(@NotNull Project project, @NotNull String uri, @Nullable Position position) {
    LOG.info(getMessageSupplier().get());
    var client = AsyncExecutor.<R>builder()
        .executorContext(project, uri, position)
        .cancellable(isCancellable())
        .runInEDT(true)
        .build();

    return client.compute(this::execute);
  }
}
