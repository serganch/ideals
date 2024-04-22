package org.rri.ideals.server.codeactions;

import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.commands.ExecutorContext;
import org.rri.ideals.server.diagnostics.DiagnosticsTestBase;
import org.rri.ideals.server.util.EditorUtil;

import java.util.stream.Stream;

@RunWith(JUnit4.class)
public class CodeActionServiceTest extends DiagnosticsTestBase {

  @Test
  public void testGetCodeActions() {
    final var text = """
        class A {
          public static void main() {
            int a = "";
            
            System.out.println();
          }
        }
        """;

    final var expectedIntentions = Stream.of(
        "Convert to atomic",
        "Split into declaration and assignment"
    ).sorted().toList();

    final var expectedQuickFixes = Stream.of(
        "Wrap using 'java.util.Optional'",
        "Wrap using 'null()'",
        "Adapt using call or new object",
        "Change variable 'a' type to 'String'"
    ).sorted().toList();

    final var file = myFixture.configureByText("test.java", text);
    final var orExpressionRange = TestUtil.newRange(2, 8, 2, 8);
    final var codeActionService = getProject().getService(CodeActionService.class);
    final var disposable = Disposer.newDisposable();

    EditorUtil.withEditor(disposable, file, orExpressionRange.getStart(), editor -> {
      final var executorContext = new ExecutorContext(file, disposable, editor, null);
      final var codeActionsBeforeDiagnostic = codeActionService.getCodeActions(orExpressionRange, executorContext);

      Assert.assertTrue(codeActionsBeforeDiagnostic.stream().allMatch(it -> it.getKind().equals(CodeActionKind.Refactor)));
      Assert.assertEquals(expectedIntentions, codeActionsBeforeDiagnostic.stream().map(CodeAction::getTitle).sorted().toList());

      runAndGetDiagnostics(file);

      final var quickFixes = codeActionService.getCodeActions(orExpressionRange, executorContext);
      quickFixes.removeAll(codeActionsBeforeDiagnostic);

      Assert.assertTrue(quickFixes.stream().allMatch(it -> it.getKind().equals(CodeActionKind.QuickFix)));
      Assert.assertEquals(expectedQuickFixes, quickFixes.stream().map(CodeAction::getTitle).sorted().toList());
    });
  }


  @Test
  public void testQuickFixFoundAndApplied() {
    final var before = """
        class A {
           final int x = "a";
        }
        """;

    final var after = """
        class A {
           final java.lang.String x = "a";
        }
        """;

    final var actionTitle = "Change field 'x' type to 'String'";

    final var file = myFixture.configureByText("test.java", before);
    final var xVariableRange = TestUtil.newRange(1, 13, 1, 13);
    var path = LspPath.fromVirtualFile(file.getVirtualFile());
    final var codeActionService = getProject().getService(CodeActionService.class);
    final var disposable = Disposer.newDisposable();

    EditorUtil.withEditor(disposable, file, xVariableRange.getStart(), editor -> {
      final var executorContext = new ExecutorContext(file, disposable, editor, null);

      runAndGetDiagnostics(file);

      final var codeActions = codeActionService.getCodeActions(xVariableRange, executorContext);

      var action = codeActions.stream()
          .filter(it -> it.getTitle().equals(actionTitle))
          .findFirst()
          .orElseThrow(() -> new AssertionError("action not found"));

      final var edit = codeActionService.applyCodeAction((ActionData) action.getData(), actionTitle, executorContext);
      Assert.assertEquals(after, TestUtil.applyEdits(file.getText(), edit.getChanges().get(path.toLspUri())));
    });

    // checking the quick fix doesn't actually change the file
    final var reloaded = PsiManager.getInstance(getProject()).findFile(file.getVirtualFile());
    Assert.assertNotNull(reloaded);
    Assert.assertEquals(before, reloaded.getText());
    final var reloadedDoc = PsiDocumentManager.getInstance(getProject()).getDocument(reloaded);
    Assert.assertNotNull(reloadedDoc);
    Assert.assertEquals(before, reloadedDoc.getText());

  }
}
