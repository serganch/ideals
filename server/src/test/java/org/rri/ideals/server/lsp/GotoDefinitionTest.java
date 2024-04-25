package org.rri.ideals.server.lsp;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.generator.IdeaOffsetPositionConverter;
import org.rri.ideals.server.references.generators.DefinitionTestGenerator;

import java.util.HashSet;

public class GotoDefinitionTest extends LspServerTestWithEngineBase {


  @Override
  @NotNull
  protected String getTestDataRelativePath() {
    return "references/java/project-definition-integration";
  }

  @Test
  public void definition() {
    final var generator = new DefinitionTestGenerator(getEngine(), new IdeaOffsetPositionConverter(server().getProject()));
    final var definitionTests = generator.generateTests();
    for (final var test : definitionTests) {
      final var params = test.params();
      final var answer = test.expected();

      createEditor(LspPath.fromLspUri(test.params().getTextDocument().getUri()));
      final var future = server().getTextDocumentService().definition(params);
      final var actual = TestUtil.getNonBlockingEdt(future, 50000);
      assertNotNull(actual);
      assertEquals(answer, new HashSet<>(actual.getRight()));
    }
  }

}
