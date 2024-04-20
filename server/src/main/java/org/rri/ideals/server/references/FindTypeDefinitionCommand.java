package org.rri.ideals.server.references;

import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.util.MiscUtil;

import java.util.function.Supplier;
import java.util.stream.Stream;

public class FindTypeDefinitionCommand extends FindDefinitionCommandBase {

  @Override
  protected @NotNull Supplier<@NotNull String> getMessageSupplier() {
    return () -> "TypeDefinition call";
  }

  @Override
  protected @NotNull Stream<PsiElement> findDefinitions(@NotNull Editor editor, int offset) {
    return MiscUtil.streamOf(GotoTypeDeclarationAction.findSymbolTypes(editor, offset));
  }
}
