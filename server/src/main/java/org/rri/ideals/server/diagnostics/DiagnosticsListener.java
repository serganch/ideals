package org.rri.ideals.server.diagnostics;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspContext;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.MyLanguageClient;
import org.rri.ideals.server.util.MiscUtil;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final public class DiagnosticsListener implements DaemonCodeAnalyzer.DaemonListener, Disposable {

  private static final Map<HighlightSeverity, DiagnosticSeverity> severityMap = Map.of(
      HighlightSeverity.INFORMATION, DiagnosticSeverity.Information,
      HighlightSeverity.WARNING, DiagnosticSeverity.Warning,
      HighlightSeverity.ERROR, DiagnosticSeverity.Error
  );
  @NotNull
  private final Project project;
  @NotNull
  private final MessageBusConnection bus;
  @NotNull
  private final MyLanguageClient client;

  public DiagnosticsListener(@NotNull Project project) {
    this.project = project;
    this.bus = project.getMessageBus().connect();
    this.client = LspContext.getContext(project).getClient();
    bus.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, this);
  }

  @Override
  public void daemonFinished(@NotNull Collection<? extends FileEditor> fileEditors) {
    fileEditors
        .forEach(fileEditor -> {
          var virtualFile = fileEditor.getFile();
          var document = ((TextEditor) fileEditor).getEditor().getDocument();
          var path = LspPath.fromVirtualFile(virtualFile);
          var diags = DaemonCodeAnalyzerImpl.getHighlights(document, null, project)
              .stream()
              .map(highlightInfo -> toDiagnostic(highlightInfo, document))
              .filter(Objects::nonNull)
              .toList();
          client.publishDiagnostics(new PublishDiagnosticsParams(path.toLspUri(), diags));
        });
  }

  @Override
  public void dispose() {
    bus.disconnect();
  }

  private Diagnostic toDiagnostic(@NotNull HighlightInfo info, @NotNull Document doc) {
    if (info.getDescription() == null)
      return null;

    final var range = MiscUtil.getRange(doc, info);
    final var severity = Optional.ofNullable(severityMap.get(info.getSeverity()))
        .orElse(DiagnosticSeverity.Hint);

    return new Diagnostic(range, info.getDescription(), severity, "ideals");
  }
}
