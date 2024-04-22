package org.rri.ideals.server.codeactions;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.commands.ExecutorContext;
import org.rri.ideals.server.diagnostics.DiagnosticsService;
import org.rri.ideals.server.util.MiscUtil;
import org.rri.ideals.server.util.TextUtil;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service(Service.Level.PROJECT)
public final class CodeActionService {
  private static final Logger LOG = Logger.getInstance(CodeActionService.class);

  private final @NotNull Project project;

  public CodeActionService(@NotNull Project project) {
    this.project = project;
  }

  @NotNull
  private static CodeAction toCodeAction(@NotNull LspPath path,
                                         @NotNull Range range,
                                         @NotNull HighlightInfo.IntentionActionDescriptor descriptor,
                                         @NotNull String kind) {
    return MiscUtil.with(new CodeAction(ReadAction.compute(() -> descriptor.getAction().getText())), ca -> {
      ca.setKind(kind);
      ca.setData(new ActionData(path.toLspUri(), range));
    });
  }

  @NotNull
  private static <T> Predicate<T> distinctByKey(@NotNull Function<? super T, ?> keyExtractor) {
    Set<Object> seen = new HashSet<>();
    return t -> seen.add(keyExtractor.apply(t));
  }

  @NotNull
  public List<CodeAction> getCodeActions(@NotNull Range range, @NotNull ExecutorContext executorContext) {

    final var result = new Ref<List<CodeAction>>();
    final var file = executorContext.getPsiFile();
    final var path = LspPath.fromVirtualFile(file.getVirtualFile());
    final var editor = executorContext.getEditor();
    assert editor != null;

    final var actionInfo = MiscUtil.computeInEDTAndWait(() ->
        ShowIntentionsPass.getActionsToShow(editor, file, true)
    );

    final var quickFixes = diagnostics().getQuickFixes(path, range).stream()
        .map(it -> toCodeAction(path, range, it, CodeActionKind.QuickFix));

    final var intentionActions = Stream.of(
            actionInfo.errorFixesToShow,
            actionInfo.inspectionFixesToShow,
            actionInfo.intentionsToShow)
        .flatMap(Collection::stream)
        .map(it -> toCodeAction(path, range, it, CodeActionKind.Refactor));

    final var actions = Stream.concat(quickFixes, intentionActions)
        .filter(distinctByKey(CodeAction::getTitle))
        .collect(Collectors.toList());

    result.set(actions);

    return Optional.ofNullable(result.get()).orElse(Collections.emptyList());
  }

  @NotNull
  public WorkspaceEdit applyCodeAction(@NotNull ActionData actionData, String title, ExecutorContext executorContext) {
    final var result = new WorkspaceEdit();
    final var editor = executorContext.getEditor();
    assert editor != null;
    final var psiFile = executorContext.getPsiFile();
    final var path = LspPath.fromVirtualFile(psiFile.getVirtualFile());
    final var oldCopy = ((PsiFile) psiFile.copy());

    ApplicationManager.getApplication().invokeAndWait(() -> {
      final var quickFixes = diagnostics().getQuickFixes(path, actionData.getRange());
      final var actionInfo = ShowIntentionsPass.getActionsToShow(editor, psiFile, true);

      var actionFound = Stream.of(
              quickFixes,
              actionInfo.errorFixesToShow,
              actionInfo.inspectionFixesToShow,
              actionInfo.intentionsToShow)
          .flatMap(Collection::stream)
          .map(HighlightInfo.IntentionActionDescriptor::getAction)
          .filter(it -> it.getText().equals(title))
          .findFirst()
          .orElse(null);

      if (actionFound == null) {
        LOG.warn("No action descriptor found: " + title);
        return;
      }

      CommandProcessor.getInstance().executeCommand(project, () -> {
        if (actionFound.startInWriteAction()) {
          WriteAction.run(() -> actionFound.invoke(project, editor, psiFile));
        } else {
          actionFound.invoke(project, editor, psiFile);
        }
      }, title, null);
    });

    final var oldDoc = ReadAction.compute(() -> MiscUtil.getDocument(oldCopy));
    final var newDoc = editor.getDocument();

    final var edits = TextUtil.textEditFromDocs(oldDoc, newDoc);

    WriteCommandAction.runWriteCommandAction(project, () -> {
      newDoc.setText(oldDoc.getText());
      PsiDocumentManager.getInstance(project).commitDocument(newDoc);
    });

    if (!edits.isEmpty()) {
      diagnostics().haltDiagnostics(path);  // all cached quick fixes are no longer valid
      result.setChanges(Map.of(actionData.getUri(), edits));
    }

    diagnostics().launchDiagnostics(path);
    return result;
  }

  @NotNull
  private DiagnosticsService diagnostics() {
    return project.getService(DiagnosticsService.class);
  }

}
