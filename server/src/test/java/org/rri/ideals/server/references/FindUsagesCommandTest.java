package org.rri.ideals.server.references;

import org.eclipse.lsp4j.ReferenceParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.engine.TestEngine;
import org.rri.ideals.server.generator.IdeaOffsetPositionConverter;
import org.rri.ideals.server.references.generators.FindUsagesTestGenerator;

import java.util.HashSet;

@RunWith(JUnit4.class)
public class FindUsagesCommandTest extends ReferencesCommandTestBase<FindUsagesTestGenerator, ReferenceParams> {
  @Test
  public void testFindUsagesJava() {
    checkReferencesByDirectory("java/project-find-usages");
  }

  @Test
  public void testFindUsagesPython() {
    checkReferencesByDirectory("python/project-find-usages");
  }

  @Override
  protected @NotNull FindUsagesTestGenerator getGenerator(@NotNull TestEngine engine) {
    return new FindUsagesTestGenerator(engine, new IdeaOffsetPositionConverter(getProject()));
  }

  @Override
  protected @Nullable Object getActuals(@NotNull ReferenceParams params) {
    final var future = new FindUsagesCommand().runAsync(getProject(), params.getTextDocument(), params.getPosition());
    return new HashSet<>(TestUtil.getNonBlockingEdt(future, 50000));
  }
}
