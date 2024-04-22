package org.rri.ideals.server.signature;

import com.intellij.codeInsight.hint.ParameterInfoListener;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.Disposer;
import com.jetbrains.python.PythonFileType;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Tuple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspLightBasePlatformTestCase;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.commands.ExecutorContext;
import org.rri.ideals.server.util.EditorUtil;

import java.util.List;

@RunWith(JUnit4.class)
public class SignatureHelpServiceTest extends LspLightBasePlatformTestCase {

  @Test
  public void testPythonFunctionWithNoParameters() {
    final var text = """
        def foo():
            return 42
           
        foo()
        """;
    var expected =
        List.of(
            createSignatureInformation("<no parameters>", List.of(createParameterInformation(0, 15)), null));

    testSignatureHelp(text, PythonFileType.INSTANCE, new Position(3, 4), null,
        new TestUtil.DumbCancelChecker(), expected);
  }

  @Test
  public void testPythonFunctionWithParameter() {
    final var text = """
        def foo(x):
            return 42
           
        foo()
        """;
    var expected =
        List.of(
            createSignatureInformation("x", List.of(createParameterInformation(0, 1)), 0));

    testSignatureHelp(text, PythonFileType.INSTANCE, new Position(3, 4), null,
        new TestUtil.DumbCancelChecker(), expected);
  }

  @Test
  public void testPythonFunctionWithAnnotatedParameter() {
    final var text = """
        def foo(x: int):
            return x + 7
           
        foo()
        """;
    var expected =
        List.of(
            createSignatureInformation("x", List.of(createParameterInformation(0, 1)), 0));

    testSignatureHelp(text, PythonFileType.INSTANCE, new Position(3, 4), null,
        new TestUtil.DumbCancelChecker(), expected);
  }

  @Test
  public void testPythonFunctionWithCurrentParameter() {
    final var text = """
        def foo(x: int, y: int):
            return x + y
        
        foo(1, )
        """;
    var expected =
        List.of(
            createSignatureInformation("x, y",
                List.of(
                    createParameterInformation(0, 3),
                    createParameterInformation(3, 4)), 0));

    testSignatureHelp(text, PythonFileType.INSTANCE, new Position(3, 4), null,
        new TestUtil.DumbCancelChecker(), expected);
  }

  @Test
  public void testJavaFunctionWithNoParameters() {
    final var text = """
        class A {
            void foo() {
            }
            void test() {
                foo();
            }
        }
        """;
    var expected =
        List.of(
            createSignatureInformation(
                "<no parameters>",
                List.of(createParameterInformation(0, 15)), null));

    testSignatureHelp(text, JavaFileType.INSTANCE, new Position(4, 12), null,
        new TestUtil.DumbCancelChecker(), expected);
  }

  @Test
  public void testJavaOverloadedFunction() {
    final var text = """
        class A {
            void foo(String s) {
            }
            void foo(int x) {
            }
            void test() {
                foo(1);
            }
        }
        """;
    var expected =
        List.of(
            createSignatureInformation("String s",
                List.of(createParameterInformation(0, 8)),
                0),
            createSignatureInformation("int x",
                List.of(createParameterInformation(0, 5)), 0)
        );

    testSignatureHelp(text, JavaFileType.INSTANCE, new Position(6, 12), 1,
        new TestUtil.DumbCancelChecker(), expected);
  }

  @Test
  public void testJavaFunctionWithAnnotation() {
    final var text = """
        class A {
            void foo() {
            }
            void foo(@TestType A a) {
            }
            void test() {
                foo();
            }
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.Type)
            public @interface TestType {
            }
        }
        """;
    var expected =
        List.of(
            createSignatureInformation("<no parameters>",
                List.of(createParameterInformation(0, 15)),
                0),
            createSignatureInformation("A a",
                List.of(createParameterInformation(0, 3)), 0)
        );
    testSignatureHelp(text, JavaFileType.INSTANCE, new Position(6, 12), 0,
        new TestUtil.DumbCancelChecker(), expected);
  }

  @Test
  public void testJavaFunctionWithUnknownAnnotation() {
    final var text = """
        class A {
            void foo() {
            }
            void foo(@NotNull A a) {
            }
            void test() {
                foo();
            }
        }
        """;
    var expected =
        List.of(
            createSignatureInformation("<no parameters>",
                List.of(createParameterInformation(0, 15)),
                0),
            createSignatureInformation("@NotNull A a",
                List.of(createParameterInformation(0, 12)), 0)
        );
    testSignatureHelp(text, JavaFileType.INSTANCE, new Position(6, 12), 0,
        new TestUtil.DumbCancelChecker(), expected);
  }

  private void testSignatureHelp(@NotNull String text, @NotNull LanguageFileType fileType,
                                 @NotNull Position pos, @Nullable Integer activeSignature,
                                 @NotNull CancelChecker cancelChecker,
                                 @NotNull List<SignatureInformation> expected) {
    final var file = myFixture.configureByText(fileType, text);
    final var signatureHelpService = getProject().getService(SignatureHelpService.class);
    signatureHelpService.setEdtFlushRunnable(defaultFlushRunnable());
    final var disposable = Disposer.newDisposable();

    EditorUtil.withEditor(disposable, file, pos, editor -> {
      var signatureHelp =
          signatureHelpService.computeSignatureHelp(new ExecutorContext(file, disposable, editor, cancelChecker));
      assertNotNull(signatureHelp);
      assertEquals(activeSignature, signatureHelp.getActiveSignature());
      assertEquals(expected, signatureHelp.getSignatures());
    });
  }

  @NotNull
  private SignatureInformation createSignatureInformation(
      @NotNull String label, @NotNull List<ParameterInformation> parameterInformationList,
      @Nullable Integer activeParameter) {
    var ans = new SignatureInformation(
        label,
        (String) null,
        parameterInformationList
        );
    ans.setActiveParameter(activeParameter);
    return ans;
  }

  @NotNull
  private ParameterInformation createParameterInformation(int labelFirst, int labelSecond) {
    var ans = new ParameterInformation();
    ans.setLabel(new Tuple.Two<>(labelFirst, labelSecond));
    return ans;
  }

  private static Runnable defaultFlushRunnable() {
    return () -> TestUtil.waitInEdtFor(() -> {
          for (var listener : ParameterInfoListener.EP_NAME.getExtensionList()) {
            if (listener instanceof MyParameterInfoListener myListener) {
              return !myListener.queue.isEmpty();
            }
          }
          return false;
        },
        5000);
  }
}
