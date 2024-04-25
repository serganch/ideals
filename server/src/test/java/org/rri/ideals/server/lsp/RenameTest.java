package org.rri.ideals.server.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Test;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;

import java.util.stream.Stream;

public class RenameTest extends LspServerTestBase {
  @Override
  protected String getProjectRelativePath() {
    return "rename/java/project1";
  }

  @Test
  public void rename() {
    final var newName = "aaa";
    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/RenameIntegrationTest.java"));
    final var renameParams = new RenameParams(new TextDocumentIdentifier(filePath.toLspUri()), new Position(2, 9), newName);
    createEditor(LspPath.fromLspUri(renameParams.getTextDocument().getUri()));
    final var future = server().getTextDocumentService().rename(renameParams);
    final var result = TestUtil.getNonBlockingEdt(future, 30000);

    final var textEdits = Stream.of(
        new TextEdit(new Range(new Position(2, 8), new Position(2, 11)), newName),
        new TextEdit(new Range(new Position(3, 14), new Position(3, 17)), newName))
        .toList();
    final var identifier = new VersionedTextDocumentIdentifier(filePath.toLspUri(), 1);
    final var documentEdit = Stream.of(Either.<TextDocumentEdit, ResourceOperation>forLeft(new TextDocumentEdit(identifier, textEdits))).toList();
    final var answer = new WorkspaceEdit(documentEdit);

    assertEquals(answer, result);
  }
}
