package org.rri.ideals.server.references;

import org.eclipse.lsp4j.TypeDefinitionParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.engine.TestEngine;
import org.rri.ideals.server.generator.IdeaOffsetPositionConverter;
import org.rri.ideals.server.references.generators.TypeDefinitionTestGenerator;

import java.util.HashSet;
import java.util.Set;

@RunWith(JUnit4.class)
public class TypeDefinitionCommandTest extends ReferencesCommandTestBase<TypeDefinitionTestGenerator, TypeDefinitionParams> {
  @Test
  public void testTypeDefinitionJava() {
    checkReferencesByDirectory("java/project-type-definition");
  }

  @Test
  public void testTypeDefinitionPython() {
    checkReferencesByDirectory("python/project-type-definition");
  }

  @Override
  protected @NotNull TypeDefinitionTestGenerator getGenerator(@NotNull TestEngine engine) {
    return new TypeDefinitionTestGenerator(engine, new IdeaOffsetPositionConverter(getProject()));
  }

  @Override
  protected @Nullable Set<?> getActuals(@NotNull TypeDefinitionParams params) {
    final var future = new FindTypeDefinitionCommand().runAsync(getProject(), params.getTextDocument(), params.getPosition());
    var actual = TestUtil.getNonBlockingEdt(future, 50000);
    if (actual == null) {
      return null;
    }
    return new HashSet<>(actual.getRight());
  }
}
