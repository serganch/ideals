package org.rri.ideals.server.symbol;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspLightBasePlatformTestCase;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.commands.ExecutorContext;

import java.lang.String;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.eclipse.lsp4j.SymbolKind.Class;
import static org.eclipse.lsp4j.SymbolKind.Enum;
import static org.eclipse.lsp4j.SymbolKind.Object;
import static org.eclipse.lsp4j.SymbolKind.*;
import static org.rri.ideals.server.TestUtil.newRange;

@RunWith(JUnit4.class)
public class DocumentSymbolServiceTest extends LspLightBasePlatformTestCase {

  @Override
  protected String getTestDataPath() {
    return Paths.get("test-data/symbol").toAbsolutePath().toString();
  }

  @Test
  public void testDocumentSymbolJava() {
    var virtualFile = myFixture.copyDirectoryToProject("java/project1/src", "");
    virtualFile = virtualFile.findChild("org");
    assertNotNull(virtualFile);

    final var fieldIntX = documentSymbol("x: int", Field,
        newRange(5, 2, 5, 16),
        newRange(5, 14, 5, 14));

    final var fieldClass1Cls = documentSymbol("cls: Class1 = new Class1()", Field,
        newRange(6, 2, 6, 49),
        newRange(6, 30, 6, 30));

    final var docSymConstructor = documentSymbol("DocumentSymbol(int)", Method,
        newRange(8, 2, 10, 3),
        newRange(8, 9, 8, 9));

    final var methodFoo = documentSymbol("foo(int, String): int", Method,
        newRange(12, 2, 19, 3),
        newRange(13, 13, 13, 13));

    final var interMethodFoo = documentSymbol("foo(): void", Method,
        newRange(22, 4, 22, 15),
        newRange(22, 9, 22, 9));

    final var entryInterface = documentSymbol("EntryInter", Interface,
        newRange(21, 2, 23, 3),
        newRange(21, 19, 21, 19),
        arrayList(interMethodFoo));

    final var enumMemberA = documentSymbol("A: Letter", Field,
        newRange(26, 4, 26, 5),
        newRange(26, 4, 26, 4));

    final var enumMemberB = documentSymbol("B: Letter", Field,
        newRange(26, 7, 26, 8),
        newRange(26, 7, 26, 7));

    final var enumLetter = documentSymbol("Letter", Enum,
        newRange(25, 2, 27, 3),
        newRange(25, 14, 25, 14),
        arrayList(enumMemberA, enumMemberB));

    final var docSymClass = documentSymbol("DocumentSymbol", Class, newRange(4, 0, 28, 1),
        newRange(4, 13, 4, 13),
        arrayList(fieldIntX, fieldClass1Cls, docSymConstructor, methodFoo, entryInterface, enumLetter));
    final var docSymFile = documentSymbol("DocumentSymbol.java", File, newRange(0, 0, 28, 1),
        newRange(0, 0, 0, 0), arrayList(docSymClass));

    final var answers = arrayList(docSymFile);
    checkDocumentSymbols(answers, virtualFile.findChild("DocumentSymbol.java"));
  }

  @Test
  public void testDocumentSymbolPython() {
    final var virtualFile = myFixture.copyDirectoryToProject("python/project1", "");

    final var varStringP = documentSymbol("p", Variable,
        newRange(4, 0, 4, 1),
        newRange(4, 0, 4, 0));

    final var barBoolB = documentSymbol("b", Variable,
        newRange(5, 0, 5, 1),
        newRange(5, 0, 5, 0));

    final var docSymConstructor = documentSymbol("__init__(self)", Method,
        newRange(10, 4, 12, 27),
        newRange(10, 8, 10, 8));

    final var methodFoo = documentSymbol("foo(self, x, y)", Method,
        newRange(14, 4, 15, 12),
        newRange(14, 8, 14, 8));

    final var methodBar = documentSymbol("bar(self, *args, **kwargs)", Method,
        newRange(17, 4, 18, 12),
        newRange(17, 8, 17, 8));

    final var docSymVar__const = documentSymbol("__CONST", Field,
        newRange(12, 8, 12, 20),
        newRange(12, 13, 12, 13));
    final var docSymVarX = documentSymbol("x", Field,
        newRange(11,  8, 11, 14),
        newRange(11, 13, 11, 13));

    final var docSymClass = documentSymbol("Document_symbol(cls2.Class2)", Class,
        newRange(9, 0, 18, 12),
        newRange(9, 6, 9, 6),
        arrayList(docSymConstructor, methodFoo, methodBar, docSymVar__const, docSymVarX));

    final var functionFooBar = documentSymbol("foo_bar(x, /, y, *, z)", Function,
        newRange(21, 0, 23, 26),
        newRange(22, 4, 22, 4));
    final var docSymFile = documentSymbol("documentSymbol.py", File,
        newRange(0, 0, 24, 0),
        newRange(0, 0, 0, 0),
        arrayList(varStringP, barBoolB, docSymClass, functionFooBar));

    final var answers = arrayList(docSymFile);
    checkDocumentSymbols(answers, virtualFile.findChild("documentSymbol.py"));
  }

  @Test
  public void testDocumentSymbolKotlin() {
    // kotlin icons use not idea standard icons
    var virtualFile = myFixture.copyDirectoryToProject("kotlin/project1/src", "");
    virtualFile = virtualFile.findChild("org");
    assertNotNull(virtualFile);

    final var enumMemberA = documentSymbol("A", Object,
        newRange(5, 2, 5, 4),
        newRange(5, 2, 5, 2));
    final var enumMemberB = documentSymbol("B", Object,
        newRange(5, 5, 5, 6),
        newRange(5, 5, 5, 5));
    final var enumLetters = documentSymbol("Letter", Object,
        newRange(4, 0, 6, 1),
        newRange(4, 11, 4, 11),
        arrayList(enumMemberA, enumMemberB));

    final var interMethodFoo = documentSymbol("foo(Int, String): Int", Method,
        newRange(9, 2, 9, 35),
        newRange(9, 6, 9, 6));
    final var interInterface = documentSymbol("Interface", Object,
        newRange(8, 0, 10, 1),
        newRange(8, 10, 8, 10),
        arrayList(interMethodFoo));

    final var annotationClassForTest = documentSymbol("ForTest", Object,
        newRange(12, 0, 12, 24),
        newRange(12, 17, 12, 17));

    final var docSymConstructor = documentSymbol("constructor DocumentSymbol(Int)", Method,
        newRange(14, 34, 14, 42),
        newRange(14, 34, 14, 34));
    final var docSymClassFieldX = documentSymbol("x: Int", Object,
        newRange(15, 2, 15, 19),
        newRange(15, 14, 15, 14));
    final var docSymCLassFieldCls = documentSymbol("cls: Class1", Object,
        newRange(16, 2, 16, 28),
        newRange(16, 14, 16, 14));

    final var methodFoo = documentSymbol("foo(Int, String): Int", Method,
        newRange(18, 2, 23, 3),
        newRange(18, 15, 18, 15));

    final var methodBar = documentSymbol("bar(): Int", Method,
        newRange(25, 2, 25, 21),
        newRange(25, 6, 25, 6));

    final var docSymClass = documentSymbol("DocumentSymbol", Object,
        newRange(14, 0, 26, 1),
        newRange(14, 20, 14, 20),
        arrayList(docSymConstructor, docSymClassFieldX, docSymCLassFieldCls, methodFoo, methodBar));

    final var funcBuz = documentSymbol("buz(Int): Int", Function,
        newRange(28, 0, 28, 28),
        newRange(28, 4, 28, 4));

    final var docSymFile = documentSymbol("DocumentSymbol.kt", File,
        newRange(0, 0, 29, 0  ),
        newRange(0, 0, 0, 0),
        arrayList(enumLetters, interInterface, annotationClassForTest, docSymClass, funcBuz));

    List<DocumentSymbol> answers = arrayList(docSymFile);
    checkDocumentSymbols(answers, virtualFile.findChild("DocumentSymbol.kt"));
  }

  private void checkDocumentSymbols(@NotNull List<@NotNull DocumentSymbol> answers, @Nullable VirtualFile virtualFile) {
    assertNotNull(virtualFile);
    final var service = getProject().getService(DocumentSymbolService.class);
    final var disposable = Disposer.newDisposable();
    try {
      final var psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
      assertNotNull(psiFile);
      var actual = service.computeDocumentSymbols(
              new ExecutorContext(psiFile, disposable, null, new TestUtil.DumbCancelChecker()))
          .stream().map(Either::getRight).toList();
      assertEquals(answers, actual);
    } finally {
      Disposer.dispose(disposable);
    }
  }

  @NotNull
  private static ArrayList<DocumentSymbol> arrayList(DocumentSymbol... symbols) {
    return new ArrayList<>(List.of(symbols));
  }

  @NotNull
  private static DocumentSymbol documentSymbol(@NotNull String name,
                                               @NotNull SymbolKind kind,
                                               @NotNull Range range,
                                               @NotNull Range selectionRange) {
    return documentSymbol(name, kind, range, selectionRange, arrayList());
  }

  @NotNull
  private static DocumentSymbol documentSymbol(@NotNull String name,
                                               @NotNull SymbolKind kind,
                                               @NotNull Range range,
                                               @NotNull Range selectionRange,
                                               @Nullable List<@NotNull DocumentSymbol> children) {
    return new DocumentSymbol(name, kind, range, selectionRange, null, children);
  }
}
