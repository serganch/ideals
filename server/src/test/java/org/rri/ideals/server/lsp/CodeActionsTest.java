package org.rri.ideals.server.lsp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.psi.PsiManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Assert;
import org.junit.Test;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.util.MiscUtil;

import java.nio.file.Files;
import java.util.Objects;
import java.util.stream.Stream;

public class CodeActionsTest extends LspServerTestBase {
  @Override
  protected String getProjectRelativePath() {
    return "lsp/project1";
  }

  @Test
  public void testFindAndResolveCodeAction() {
    var invokedActionName = "Change variable 'a' type to 'String'";

    var expectedActions = Stream.of(
        "Copy string literal text to the clipboard",
        "Split into declaration and assignment",
        invokedActionName
    ).sorted().toList();


    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/CodeActions.java"));

    final var didOpenTextDocumentParams = MiscUtil.with(new DidOpenTextDocumentParams(),
        params -> params.setTextDocument(MiscUtil.with(new TextDocumentItem(), item -> {
          item.setUri(filePath.toLspUri());

          item.setText(MiscUtil.makeThrowsUnchecked(() -> Files.readString(filePath.toPath())));
          item.setVersion(1);
        })));

    server().getTextDocumentService().didOpen(didOpenTextDocumentParams);

    DaemonCodeAnalyzer.getInstance(myProject).restart();
    client().waitAndGetDiagnosticsPublished();

    final var params = new CodeActionParams();
    params.setTextDocument(new TextDocumentIdentifier(filePath.toLspUri()));
    params.setRange(TestUtil.newRange(3, 12, 3, 12));
    params.setContext(new CodeActionContext());

    final var codeActions =
        TestUtil.getNonBlockingEdt(server().getTextDocumentService().codeAction(params), 30000);

    Assert.assertEquals(expectedActions, codeActions.stream().map(it -> it.getRight().getTitle()).sorted().toList());

    var changeTypeAction = codeActions.stream()
        .map(Either::getRight)
        .filter(it -> it.getTitle().equals(invokedActionName))
        .findFirst()
        .orElseThrow();

    Gson gson = new GsonBuilder().create();
    changeTypeAction.setData(gson.fromJson(gson.toJson(changeTypeAction.getData()), JsonObject.class));

    final var resolvedAction = TestUtil.getNonBlockingEdt(server().getTextDocumentService().resolveCodeAction(changeTypeAction), 30000);

    final var edit = resolvedAction.getEdit();
    Assert.assertEquals(1, edit.getChanges().size());

    final var after = """
        // code for code action tests
        class CodeActions {
          public static void f() {
            java.lang.String a = "";
            System.out.println();
          }
        }""";

    var file = PsiManager.getInstance(server().getProject()).findFile(Objects.requireNonNull(filePath.findVirtualFile()));
    assert file != null;

    Assert.assertEquals(after, TestUtil.applyEdits(file.getText(), edit.getChanges().get(filePath.toLspUri())));
  }
}
