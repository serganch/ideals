package org.rri.ideals.server.references;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;
import java.util.stream.Stream;


public class FindDefinitionCommand extends FindDefinitionCommandBase {

  @Override
  protected @NotNull Supplier<@NotNull String> getMessageSupplier() {
    return () -> "Definition call";
  }

  @Override
  protected @NotNull Stream<PsiElement> findDefinitions(@NotNull Editor editor, int offset) {
    final var reference = TargetElementUtil.findReference(editor, offset);
    final var flags = TargetElementUtil.getInstance().getDefinitionSearchFlags();
    final var targetElement = TargetElementUtil.getInstance().findTargetElement(editor, flags, offset);
    return targetElement != null ? Stream.of(targetElement)
        : reference != null ? TargetElementUtil.getInstance().getTargetCandidates(reference).stream()
        : Stream.empty();
  }
}
