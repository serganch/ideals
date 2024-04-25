package org.rri.ideals.server.references;

import com.intellij.openapi.editor.LogicalPosition;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspLightBasePlatformTestCase;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.engine.IdeaTestFixture;
import org.rri.ideals.server.engine.TestEngine;
import org.rri.ideals.server.generator.TestGenerator;

import java.nio.file.Paths;

public abstract class ReferencesCommandTestBase<E extends TestGenerator<?
    extends TestGenerator.Test>, T extends TextDocumentPositionParams> extends LspLightBasePlatformTestCase {
  @Override
  protected String getTestDataPath() {
    return Paths.get("test-data/references").toAbsolutePath().toString();
  }

  protected abstract @NotNull E getGenerator(@NotNull final TestEngine engine);

  protected abstract @Nullable Object getActuals(@NotNull final T params);

  protected void checkReferencesByDirectory(@NotNull String testProjectRelativePath) {
      final var engine = new TestEngine(new IdeaTestFixture(myFixture));
      engine.initSandbox(testProjectRelativePath);
      final var generator = getGenerator(engine);
      final var referencesTests = generator.generateTests();
      for (final var test : referencesTests) {
        final var params = (T) test.params();
        myFixture.openFileInEditor(LspPath.fromLspUri(params.getTextDocument().getUri()).findVirtualFile());
        myFixture.getEditor().getCaretModel().moveToLogicalPosition(new LogicalPosition(params.getPosition().getLine(), params.getPosition().getCharacter()));

        final var expected = test.expected();

        final var actual = getActuals((T) params);

        assertEquals(expected, actual);
      }
  }
}
