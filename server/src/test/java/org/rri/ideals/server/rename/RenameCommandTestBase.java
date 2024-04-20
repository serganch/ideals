package org.rri.ideals.server.rename;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspLightBasePlatformTestCase;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

abstract class RenameCommandTestBase extends LspLightBasePlatformTestCase {
  protected LspPath renameTestPath;
  protected String renameTestUri;
  protected String orgTestClassUri;

  @NotNull
  private static final Comparator<TextEdit> comparator = Comparator
      .<TextEdit>comparingInt(edit -> edit.getRange().getStart().getLine())
      .thenComparingInt(edit -> edit.getRange().getStart().getCharacter());


  @NotNull
  protected TextDocumentEdit textDocumentEdit(@NotNull String uri, @NotNull List<@NotNull TextEdit> edits) {
    return new TextDocumentEdit(new VersionedTextDocumentIdentifier(uri, 1), edits);
  }

  // RenameCommand returns ListN, but List.of can be List12 => Comparison Failure
  @NotNull
  protected List<@NotNull TextEdit> list(@NotNull TextEdit... textEdits) {
    return Stream.of(textEdits).toList();
  }

  protected void checkRename(@NotNull List<@NotNull TextDocumentEdit> edits, @NotNull Position pos, @NotNull String newName) {
    checkRename(edits, pos, newName, null);
  }

  protected void checkRename(@NotNull List<@NotNull TextDocumentEdit> edits, @NotNull Position pos, @NotNull String newName, @Nullable List<? extends ResourceOperation> operations) {
    final var changedEdits = edits.stream().map(Either::<TextDocumentEdit, ResourceOperation>forLeft).toList();
    WorkspaceEdit answer;
    if (operations == null) {
      answer = new WorkspaceEdit(changedEdits);
    } else {
      final var changedOperations = operations.stream().map(Either::<TextDocumentEdit, ResourceOperation>forRight).toList();
      answer = new WorkspaceEdit(Stream.concat(changedEdits.stream(), changedOperations.stream()).toList());
    }

    final var future = new RenameCommand(newName).runAsync(getProject(), renameTestPath.toLspUri(), pos);
    final var result = TestUtil.getNonBlockingEdt(future, 50000);

    assertNotNull(result);

    result.getDocumentChanges().forEach(either -> {
      final var docEdit = either.getLeft();
      final var sortedEdits = docEdit.getEdits().stream().sorted(comparator).toList();
      docEdit.setEdits(sortedEdits);
    });

    assertEquals(answer, result);
  }
}
