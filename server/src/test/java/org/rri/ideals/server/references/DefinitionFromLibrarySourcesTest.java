package org.rri.ideals.server.references;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;


@RunWith(JUnit4.class)
public class DefinitionFromLibrarySourcesTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return Paths.get("test-data/references/java/project-definition-from-lib-sources").toAbsolutePath().toString();
  }

  @Test
  public void definitionJavaTest() {
    var disposable = Disposer.newDisposable();
    try {
      WriteAction.run(() -> {
        var libPath = Paths.get(getTestDataPath()).resolve("libs").toString();
        var jarName = "test-library.jar";
        VfsRootAccess.allowRootAccess(disposable, libPath);
        PsiTestUtil.addLibrary(
            getTestRootDisposable(),
            myFixture.getModule(),
            "test-lib",
            libPath,
            jarName
        );
      });

      myFixture.copyDirectoryToProject("", "");
      final var path =
          LspPath.fromLocalPath(
              Paths.get(getTestDataPath()).resolve("src/DefinitionFromJar.java"));

      final var future = new FindDefinitionCommand().runAsync(getProject(), path.toLspUri(), new Position(3, 8));
      var actual = TestUtil.getNonBlockingEdt(future, 5000);

      assertNotNull(actual);
      var uri = LspPath.fromLspUri(
          Paths.get(getTestDataPath()).toUri().toString().replaceFirst("file:", "jar:") + "libs/test-library.jar!/test/ideals/TestLibClass.class").toLspUri();
      assertEquals(
          Set.of(
              new LocationLink(
                  uri,
                  new Range(new Position(6, 13), new Position(6, 25)),
                  new Range(new Position(6, 13), new Position(6, 25)),
                  new Range(new Position(3, 4), new Position(3, 16))
              )
          ),
          new HashSet<>(actual.getRight()));
    } finally {
      Disposer.dispose(disposable);
    }
  }
}
