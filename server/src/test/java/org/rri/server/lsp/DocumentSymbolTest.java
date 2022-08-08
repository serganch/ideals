package org.rri.server.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DocumentSymbolTest extends LspServerTestBase {
  @Override
  protected String getProjectRelativePath() {
    return "symbol/java/project1";
  }

  @Test
  public void documentSymbol() {
    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/DocumentSymbolIntegratingTest.java"));
    final var params = new DocumentSymbolParams(new TextDocumentIdentifier(filePath.toLspUri()));
    final var future = server().getTextDocumentService().documentSymbol(params);
    final var result = TestUtil.getNonBlockingEdt(future, 30000).stream()
        .map(Either::getRight)
        .collect(Collectors.toList());

    final var x = documentSymbol("x", SymbolKind.Variable, range(1, 43, 1, 44), null);
    final var DSITConstructor = documentSymbol("DocumentSymbolIntegratingTest(int)", SymbolKind.Constructor, range(1, 9, 1, 38), List.of(x));
    final var DSITClass = documentSymbol("DocumentSymbolIntegratingTest", SymbolKind.Class, range(0, 13, 0, 42), List.of(DSITConstructor));

    final var answer = List.of(DSITClass);

    assertEquals(answer, result);
  }

  @NotNull
  private static DocumentSymbol documentSymbol(@NotNull String name,
                                               @NotNull SymbolKind kind,
                                               @NotNull Range range,
                                               @Nullable List<@NotNull DocumentSymbol> children) {
    return new DocumentSymbol(name, kind, range, range, null, children == null ? null : new ArrayList<>(children));
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static Range range(int line1, int char1, int line2, int char2) {
    return new Range(new Position(line1, char1), new Position(line2, char2));
  }
}
