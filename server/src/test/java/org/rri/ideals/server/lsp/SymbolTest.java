package org.rri.ideals.server.lsp;

import com.intellij.openapi.project.DumbService;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SymbolTest extends LspServerTestBase {
  @Override
  protected String getProjectRelativePath() {
    return "symbol/java/project1";
  }

  @Test
  public void documentSymbol() {
    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/DocumentSymbolIntegratingTest.java"));
    final var params = new DocumentSymbolParams(new TextDocumentIdentifier(filePath.toLspUri()));
    createEditor(LspPath.fromLspUri(params.getTextDocument().getUri()));
    final var future = server().getTextDocumentService().documentSymbol(params);
    final var result = TestUtil.getNonBlockingEdt(future, 30000).stream()
        .map(Either::getRight)
        .collect(Collectors.toList());

    final var testConstructor = documentSymbol("DocumentSymbolIntegratingTest(int)",
        SymbolKind.Method,
        range(1, 2, 1, 48),
        range(1, 9, 1, 9), List.of());
    final var testClass = documentSymbol("DocumentSymbolIntegratingTest",
        SymbolKind.Class,
        range(0, 0, 2, 1),
        range(0, 13, 0, 13),
        List.of(testConstructor));

    final var testFile = documentSymbol("DocumentSymbolIntegratingTest.java",
        SymbolKind.File,
        range(0, 0, 2, 1), range(0, 0, 0, 0),
        List.of(testClass));
    final var answer = List.of(testFile);

    assertEquals(answer, result);
  }

  @Test
  public void workspaceSymbol() {
    TestUtil.waitInEdtFor(() -> !DumbService.getInstance(server().getProject()).isDumb(), 30000);
    final var params = new WorkspaceSymbolParams("WorkspaceSymbolIntegratingTest");
    final var future = server().getWorkspaceService().symbol(params);
    final var result = TestUtil.getNonBlockingEdt(future, 30000).getRight();

    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/WorkspaceSymbolIntegratingTest.java"));
    final var workspaceSymbolIntegratingTest = new WorkspaceSymbol("WorkspaceSymbolIntegratingTest", SymbolKind.Class,
        Either.forLeft(new Location(filePath.toLspUri(), range(0, 13, 0, 43))));

    assertEquals(List.of(workspaceSymbolIntegratingTest), result);
  }

  @NotNull
  private static DocumentSymbol documentSymbol(@NotNull String name,
                                               @NotNull SymbolKind kind,
                                               @NotNull Range range,
                                               @NotNull Range selectionRange,
                                               @Nullable List<@NotNull DocumentSymbol> children) {
    return new DocumentSymbol(name, kind, range, selectionRange, null, children == null ? null :
        new ArrayList<>(children));
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static Range range(int line1, int char1, int line2, int char2) {
    return new Range(new Position(line1, char1), new Position(line2, char2));
  }
}
