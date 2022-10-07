package org.rri.ideals.server.lsp;

import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Test;
import org.rri.ideals.server.DefaultTestFixture;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.references.engines.DefinitionTestEngine;

import java.io.IOException;
import java.util.Optional;

public class GotoDefinitionTest extends LspServerTestBase {

  @Override
  protected String getProjectRelativePath() {
    return "sandbox";
  }

  @Test
  public void definition() {
    try {
      final var dirPath =  getTestDataRoot().resolve("references/java/project-definition-integration");
      final var engine = new DefinitionTestEngine(dirPath, server().getProject());
      final var definitionTests = engine.generateTests(new DefaultTestFixture(getProjectPath(), dirPath));
      for (final var test : definitionTests) {
        final var params = test.getParams();
        final var answer = test.getAnswer();

        final var future = server().getTextDocumentService().definition(params);
        final var actual = Optional.ofNullable(TestUtil.getNonBlockingEdt(future, 50000)).map(Either::getRight);

        assertTrue(actual.isPresent());
        assertEquals(answer, actual.get());
      }
    } catch (IOException | RuntimeException e) {
      e.printStackTrace();
      fail();
    }
  }
}
