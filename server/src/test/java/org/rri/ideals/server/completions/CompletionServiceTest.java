package org.rri.ideals.server.completions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestModeFlags;
import com.jetbrains.python.PythonFileType;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspLightBasePlatformTestCase;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.commands.ExecutorContext;
import org.rri.ideals.server.completions.generators.CompletionTestGenerator;
import org.rri.ideals.server.engine.IdeaTestFixture;
import org.rri.ideals.server.engine.TestEngine;
import org.rri.ideals.server.generator.IdeaOffsetPositionConverter;
import org.rri.ideals.server.util.EditorUtil;
import org.rri.ideals.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class CompletionServiceTest extends LspLightBasePlatformTestCase {
  private static final Key<Boolean> ourShowTemplatesInTests = Key.create("ShowTemplatesInTests");
  private static final Key<Boolean> ourTemplateTesting = Key.create("TemplateTesting");
  private final Gson gson = new GsonBuilder().create();

  @Override
  protected String getTestDataPath() {
    return "test-data/completion";
  }


  @Test
  public void testCompletionForStaticImport() {
    testWithEngine(new CompletionTestParams("import-static-project", completionItem -> true,
        new MarkupContent(MarkupKind.MARKDOWN,
            """
                \s[`ImportClass`](psi_element://ImportClass)
                                            
                _@Contract(pure = true)__i_[](inferred.annotations) public static void methodToImport()"""), null));
  }

  @Test
  public void testTemplateCompletion() {
    runWithTemplateFlags(() -> testWithEngine(new CompletionTestParams("template-main-project", completionItem -> true,
        new MarkupContent(MarkupKind.MARKDOWN,
            """
                public static void main(String\\[\\] args){ $END$ }

                main() method declaration"""), null)));
  }

  private record CompletionTestParams(@NotNull String relativePathToTestProject,
                                      @Nullable Predicate<? super CompletionItem> finder,
                                      @Nullable MarkupContent documentation,
                                      @Nullable Set<CompletionItem> expectedItems) {
  }

  @Test
  public void testCompletionForKeywordAndFunctionPython() {
    var expected = Set.of(CompletionServiceTestUtil.createCompletionItem(
        "for",
        "",
        null,
        new ArrayList<>(),
        "for",
        CompletionItemKind.Keyword
    ), CompletionServiceTestUtil.createCompletionItem(
        "formula",
        "(x)",
        null,
        new ArrayList<>(),
        "formula",
        CompletionItemKind.Function));

    testWithEngine(new CompletionTestParams("python-function-and-keyword-project", null, null, expected));
  }

  @Test
  public void testCompletionForKeywordAndFunctionJava() {
    var expected = Set.of(CompletionServiceTestUtil.createCompletionItem(
        "formula",
        "()",
        "void",
        new ArrayList<>(),
        "formula",
        CompletionItemKind.Method
    ), CompletionServiceTestUtil.createCompletionItem(
        "for",
        "",
        null,
        new ArrayList<>(),
        "for",
        CompletionItemKind.Keyword
    ));
    testWithEngine(new CompletionTestParams("java-function-and-keyword-project", null, null, expected));
  }

  @Test
  public void testCompletionForImportedClass() {
    var expected = Set.of(CompletionServiceTestUtil.createCompletionItem(
        "ImportClass",
        " default package",
        null,
        new ArrayList<>(),
        "ImportClass",
        CompletionItemKind.Class
    ));
    testWithEngine(new CompletionTestParams("java-class", null, null, expected));
  }

  @Test
  public void testCompletionResolveFunctionsWithParameters() {
    //noinspection UnnecessaryUnicodeEscape
    testWithEngine(new CompletionTestParams("python-function-with-parameter-project",
        completionItem -> Objects.equals(completionItem.getLabel(), "foo"),
        new MarkupContent(MarkupKind.MARKDOWN,
            """
                 `/src/python-function-with-parameter-project/src/test.py`

                def foo(x: Any) -> None

                Unittest placeholder

                Params:

                `x` \u2013 real human bean

                Returns:

                actual real hero"""), null));
  }

  @Test
  public void testCompletionResolveFunctionsWithoutParameters() {
    testWithEngine(new CompletionTestParams("python-function-without-parameter-project", completionItem -> Objects.equals(completionItem.getLabel(), "foo"),
        new MarkupContent(MarkupKind.MARKDOWN,
            """
                  `/src/python-function-without-parameter-project/src/test.py`

                 def foo() -> None"""), null));
  }

  @Test
  public void testPythonIterLiveTemplate() {
    runWithTemplateFlags(() -> testWithEngine(new CompletionTestParams("python-iter",
        completionItem -> Objects.equals(completionItem.getLabel(), "iter"),
        new MarkupContent(MarkupKind.MARKDOWN,
            """
                for $VAR$ in $ITERABLE$: $END$

                Iterate (for ... in ...)"""), null)));
  }

  @Test
  public void testPythonIterePostfixTemplate() {
    runWithTemplateFlags(() -> testWithEngine(new CompletionTestParams("python-itere",
        completionItem -> Objects.equals(completionItem.getLabel(), "itere"),
        new MarkupContent(MarkupKind.MARKDOWN,
            """
                for $INDEX$, $VAR$ in enumerate($ITERABLE$): $END$
                
                Iterate (for ... in enumerate)"""), null)));
  }

  @Test
  public void testPythonIfPostfixTemplate() {
    runWithTemplateFlags(() -> testWithEngine(new CompletionTestParams("python-if",
        completionItem -> Objects.equals(completionItem.getLabel(), "if"), null, null)));
  }

  @Test
  public void testJavaForiLiveTemplate() {
    runWithTemplateFlags(() -> testWithEngine(new CompletionTestParams("java-fori",
        completionItem -> Objects.equals(completionItem.getLabel(), "fori"),
        new MarkupContent(MarkupKind.MARKDOWN,
            """
                for(int $INDEX$ = 0; $INDEX$ < $LIMIT$; $INDEX$++) { $END$ }

                Create iteration loop"""
        ), null)));
  }

  @Test
  public void testJavaItcoLiveTemplate() {
    runWithTemplateFlags(() -> testWithEngine(new CompletionTestParams("java-itco",
        completionItem -> Objects.equals(completionItem.getLabel(), "itco"),
        new MarkupContent(MarkupKind.MARKDOWN,
            """
                for($ITER\\_TYPE$ $ITER$ = $COLLECTION$.iterator(); $ITER$.hasNext(); ) { $ELEMENT\\_TYPE$ $VAR$ =$CAST$ $ITER$.next(); $END$ }
                                         
                Iterate elements of java.util.Collection"""
        ), null)));
  }

  @Test
  public void testJavaIterWithLookupItemLiveTemplate() {
    runWithTemplateFlags(() -> testWithEngine(new CompletionTestParams("java-iter-with-lookup-item",
        completionItem -> Objects.equals(completionItem.getLabel(), "iter"),
        new MarkupContent(MarkupKind.MARKDOWN,
            """
                for ($ELEMENT\\_TYPE$ $VAR$ : $ITERABLE\\_TYPE$) { $END$ }
                
                Iterate Iterable or array"""
        ), null)));
  }

  @Test
  public void testJavaItli() {
    runWithTemplateFlags(() -> testWithEngine(new CompletionTestParams("java-itli",
        completionItem -> Objects.equals(completionItem.getLabel(), "itli"),
        new MarkupContent(MarkupKind.MARKDOWN,
            """
                for (int $INDEX$ = 0; $INDEX$ < $LIST$.size(); $INDEX$++) { $ELEMENT\\_TYPE$ $VAR$ = $CAST$ $LIST$.get($INDEX$); $END$ }
                                
                Iterate elements of java.util.List"""
        ), null)));
  }

  @Test
  public void testJavaItarWithLookupItem() {
    runWithTemplateFlags(() -> testWithEngine(new CompletionTestParams(
        "java-itar-with-lookup-item",
        completionItem -> Objects.equals(completionItem.getLabel(), "itar"),
        new MarkupContent(MarkupKind.MARKDOWN,
            """
                for(int $INDEX$ = 0; $INDEX$ < $ARRAY$.length; $INDEX$++) { $ELEMENT\\_TYPE$ $VAR$ = $ARRAY$\\[$INDEX$\\]; $END$ }
                
                Iterate elements of array"""
        ), null)));
  }

  @Test
  public void testJavaLambdaPostfixTemplate() {
    runWithTemplateFlags(() -> runWithTemplateFlags(() -> testWithEngine(new CompletionTestParams(
        "java-lambda", completionItem -> Objects.equals(completionItem.getLabel(), "lambda"),
        null, null))));
  }

  private void testWithEngine(@NotNull CompletionTestParams completionTestParams) {
    final var engine = new TestEngine(new IdeaTestFixture(myFixture));
    engine.initSandbox(completionTestParams.relativePathToTestProject());
    final var generator = new CompletionTestGenerator(engine, new IdeaOffsetPositionConverter(getProject()));

    final var completionTest = generator.generateTests();
    final var test = completionTest.get(0);
    final var params = test.params();
    final var expectedText = test.expected();
    final var cs = getProject().getService(CompletionService.class);
    final var disposable = Disposer.newDisposable();
    final var psiFile = MiscUtil.resolvePsiFile(getProject(), LspPath.fromLspUri(params.getTextDocument().getUri()));

    EditorUtil.withEditor(disposable, psiFile, params.getPosition(), editor -> {
      var completionItems = cs.computeCompletions(new ExecutorContext(psiFile, disposable, editor, new TestUtil.DumbCancelChecker()));
      if (completionTestParams.finder != null) {
        var compItem = completionItems.stream().filter(completionTestParams.finder).findFirst().orElseThrow();
        compItem.setData(gson.fromJson(gson.toJson(compItem.getData()), JsonObject.class));
        var resolved = cs.resolveCompletion(compItem, new TestUtil.DumbCancelChecker());
        assertNotNull(expectedText);
        assertNotNull(test.getSourceText());
        var allEdits = new ArrayList<TextEdit>();
        allEdits.add(resolved.getTextEdit().getLeft());
        allEdits.addAll(resolved.getAdditionalTextEdits());
        assertEquals(expectedText, TestUtil.applyEdits(test.getSourceText(), allEdits));

        if (completionTestParams.documentation != null) {
          assertEquals(completionTestParams.documentation, compItem.getDocumentation().getRight());
        } else {
          assertNull(compItem.getDocumentation());
        }
      }
      if (completionTestParams.expectedItems != null) {
        assertEquals(completionTestParams.expectedItems(),
            completionItems.stream().map(CompletionServiceTestUtil::removeResolveInfo).collect(Collectors.toSet()));
      }
    });
  }

  @Test
  public void testCompletionCancellation() {
    var cancelChecker = new AlwaysTrueCancelChecker();

    var psiFile = myFixture.configureByText(
        PythonFileType.INSTANCE,
        "");
    Assertions.assertThrows(
        CancellationException.class,
        () -> getCompletionListAtPosition(psiFile, new Position(0, 0), cancelChecker));
  }

  @Test
  public void testResolveCancellation() {

    var psiFile = myFixture.configureByText(
        JavaFileType.INSTANCE,
        """
            """);

    var completionList = Assertions.assertDoesNotThrow(
        () -> getCompletionListAtPosition(psiFile, new Position(0, 0)));

    var cancelChecker = new AlwaysTrueCancelChecker();

    var targetCompletionItem =
        completionList.stream()
            .filter(completionItem -> completionItem.getLabel().equals("class"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("completion item not found"));

    targetCompletionItem.setData(gson.fromJson(
        gson.toJson(targetCompletionItem.getData()),
        JsonObject.class));
    Assertions.assertThrows(CancellationException.class,
        () -> getProject()
            .getService(CompletionService.class)
            .resolveCompletion(targetCompletionItem, cancelChecker));
  }

  private static class AlwaysTrueCancelChecker implements CancelChecker {
    @Override
    public void checkCanceled() {
      throw new CancellationException();
    }
  }

  @NotNull
  private List<@NotNull CompletionItem> getCompletionListAtPosition(@NotNull PsiFile file,
                                                                    @NotNull Position position) {
    return getCompletionListAtPosition(file, position, new TestUtil.DumbCancelChecker());
  }

  @NotNull
  private List<@NotNull CompletionItem> getCompletionListAtPosition(@NotNull PsiFile file,
                                                                    @NotNull Position position,
                                                                    @NotNull CancelChecker cancelChecker) {
    final var disposable = Disposer.newDisposable();
    return EditorUtil.computeWithEditor(disposable, file, position, editor ->
        getProject().getService(CompletionService.class).computeCompletions(
            new ExecutorContext(file, disposable, editor, cancelChecker)));
  }

  static private void runWithTemplateFlags(@NotNull Runnable action) {
    TestModeFlags.set(ourShowTemplatesInTests, true);
    TestModeFlags.set(ourTemplateTesting, true);
    try {
      action.run();
    } finally {
      TestModeFlags.set(ourShowTemplatesInTests, false);
      TestModeFlags.set(ourTemplateTesting, false);
    }
  }
}
