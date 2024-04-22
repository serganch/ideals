package org.rri.ideals.server.formatting;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Disposer;
import com.jetbrains.python.PythonFileType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspLightBasePlatformTestCase;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.commands.ExecutorContext;
import org.rri.ideals.server.util.TextUtil;

import java.util.List;

@RunWith(JUnit4.class)
public class FormattingCommandTest extends LspLightBasePlatformTestCase {

  @Test
  public void testEmptyFileFormatting() {
    Assertions.assertEquals(getEditsByText("", "", PythonFileType.INSTANCE), List.of());
  }

  @Test
  public void testCorrectVarDecFormatting() {
    Assertions.assertEquals(getEditsByText("x = 10\n", "x = 10\n", PythonFileType.INSTANCE),
        List.of());
  }

  @Test
  public void testCorrectFuncDec() {
    Assertions.assertEquals(getEditsByText("def foo(x): x = 1\n", "def foo(x): x = 1\n",
        PythonFileType.INSTANCE), List.of());
  }

  @Test
  public void testExtraSpacesAndNewlinesVarDec() {
    Assertions.assertEquals(
        List.of(
            TestUtil.newTextEdit(0, 0, 1, 5, ""),
            TestUtil.newTextEdit(1, 6, 1, 6, " "),
            TestUtil.newTextEdit(1, 7, 1, 7, " ")),
        getEditsByText(
            """
                                
                     x=10
                """,
            """
                x = 10
                """,
            PythonFileType.INSTANCE)
    );
  }

  @Test
  public void testExtraSpaceBetweenFuncDecAndOtherStatements() {
    Assertions.assertEquals(
        List.of(
            TestUtil.newTextEdit(0, 4, 0, 5, ""),
            TestUtil.newTextEdit(0, 8, 0, 10, ""),
            TestUtil.newTextEdit(0, 13, 0, 16, ""),
            TestUtil.newTextEdit(1, 0, 1, 0, "\n\n")
        ),
        getEditsByText(
            """
                def  foo  (x)   : x = 1
                x = 1
                """,
            """
                def foo(x): x = 1
                                
                                
                x = 1
                """,
            PythonFileType.INSTANCE)
    );
  }

  @Test
  public void testEmptyForStatement() {
    Assertions.assertEquals(
        List.of(
            TestUtil.newTextEdit(1, 0, 1, 0, "    ")
        ),
        getEditsByText(
            """
                for i in range(10):
                i += 1
                """,
            """
                for i in range(10):
                    i += 1
                """,
            PythonFileType.INSTANCE));
  }

  @Test
  public void testExtraSpaceBetweenImportListAndOtherStatements() {
    Assertions.assertEquals(
        List.of(
            TestUtil.newTextEdit(0, 10, 0, 11, "\n\n")
        ),
        getEditsByText(
            """
                import sys x = 1
                """,
            """
                import sys
                        
                x = 1
                """,
            PythonFileType.INSTANCE)

    );

  }

  @Test
  public void testTextRangeFormatInSelectFormatting() {
    Assertions.assertEquals(
        List.of(
            TestUtil.newTextEdit(0, 0, 0, 4, "")
        ),
        getEditsByTextAndSelectRange(
            """
                    x = 1
                    x = 1
                """,
            """
                x = 1
                    x = 1
                """,
            PythonFileType.INSTANCE,
            new Range(new Position(0, 0), new Position(0, 13)))
    );

    Assertions.assertEquals(
        List.of(
            TestUtil.newTextEdit(0, 0, 0, 4, ""),
            TestUtil.newTextEdit(1, 0, 1, 4, "")
        ),
        getEditsByTextAndSelectRange(
            """
                    x = 1
                    x = 1
                """,
            """
                x = 1
                x = 1
                """,
            PythonFileType.INSTANCE,
            new Range(new Position(0, 0), new Position(0, 14)))
    );
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private List<@NotNull TextEdit> getEditsByText(@NotNull String actualText,
                                                 @NotNull String expectedText,
                                                 @NotNull FileType fileType) {
    return getEditsByTextAndSelectRange(actualText, expectedText, fileType, null);
  }

  private List<@NotNull TextEdit> getEditsByTextAndSelectRange(@NotNull String actualText,
                                                               @NotNull String expectedText,
                                                               @NotNull FileType fileType,
                                                               @Nullable Range lspRange) {
    final var actualPsiFile = myFixture.configureByText(fileType, actualText);

    var context = new ExecutorContext(actualPsiFile, Disposer.newDisposable(), null, new TestUtil.DumbCancelChecker());
    var command = new FormattingCommand(lspRange, FormattingTestUtil.defaultOptions());

    return TextUtil.differenceAfterAction(actualPsiFile, (copy) -> {
      command.reformatPsiFile(context, copy);
      Assertions.assertNotEquals(actualPsiFile, copy);
      Assertions.assertEquals(expectedText, copy.getText());
    });
  }


  @Override
  protected String getTestDataPath() {
    return "test-data/formatting/formatting-project";
  }
}
