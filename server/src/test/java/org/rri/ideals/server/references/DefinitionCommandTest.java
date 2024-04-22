package org.rri.ideals.server.references;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.LocationLink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.engine.TestEngine;
import org.rri.ideals.server.generator.IdeaOffsetPositionConverter;
import org.rri.ideals.server.references.generators.DefinitionTestGenerator;

import java.util.HashSet;
import java.util.Set;


@RunWith(JUnit4.class)
public class DefinitionCommandTest extends ReferencesCommandTestBase<DefinitionTestGenerator, DefinitionParams> {
  @Test
  public void definitionJavaTest() {
    checkReferencesByDirectory("java/project-definition");
  }

  @Test
  public void definitionPythonTest() {
    checkReferencesByDirectory("python/project-definition");
  }


  @Override
  protected @NotNull DefinitionTestGenerator getGenerator(@NotNull TestEngine engine) {
    return new DefinitionTestGenerator(engine, new IdeaOffsetPositionConverter(getProject()));
  }

  @Override
  @Nullable
  protected Set<? extends LocationLink> getActuals(@NotNull DefinitionParams params) {
    final var future = new FindDefinitionCommand().runAsync(getProject(), params.getTextDocument(), params.getPosition());
    var actual = TestUtil.getNonBlockingEdt(future, 50000);
    if (actual == null) {
      return null;
    }
    return new HashSet<>(actual.getRight());
  }
}
