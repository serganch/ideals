package org.rri.ideals.server.references;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetUtil;
import com.intellij.util.containers.ContainerUtil;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.commands.ExecutorContext;
import org.rri.ideals.server.commands.LspCommand;
import org.rri.ideals.server.util.EditorUtil;
import org.rri.ideals.server.util.MiscUtil;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocumentHighlightCommand extends LspCommand<List<? extends DocumentHighlight>> {

  @Override
  protected @NotNull Supplier<@NotNull String> getMessageSupplier() {
    return () -> "DocumentHighlight call";
  }

  @Override
  protected boolean isCancellable() {
    return false;
  }

  @Override
  protected @NotNull List<? extends DocumentHighlight> execute(@NotNull ExecutorContext ctx) {
    try {
      return findHighlights(ctx.getPsiFile().getProject(), ctx.getEditor(), ctx.getPsiFile());
    } catch (IndexNotReadyException e) {
      return List.of();
    }
  }

  private @NotNull List<@NotNull DocumentHighlight> findHighlights(@NotNull Project project,
                                                                   @NotNull Editor editor,
                                                                   @NotNull PsiFile file) {
    final HighlightUsagesHandlerBase<PsiElement> handler = HighlightUsagesHandler.createCustomHandler(editor, file);
    return handler != null ? getHighlightsFromHandler(handler, editor) : getHighlightsFromUsages(project, editor, file);
  }

  private @NotNull List<@NotNull DocumentHighlight> getHighlightsFromHandler(@NotNull HighlightUsagesHandlerBase<PsiElement> handler,
                                                                             @NotNull Editor editor) {
    final var featureId = handler.getFeatureId();

    if (featureId != null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(featureId);
    }

    // NOTE: Not able to use handler.selectTargets()
    handler.computeUsages(handler.getTargets());

    final var reads = textRangesToHighlights(handler.getReadUsages(), editor, DocumentHighlightKind.Read);
    final var writes = textRangesToHighlights(handler.getWriteUsages(), editor, DocumentHighlightKind.Write);

    return Stream.concat(reads.stream(), writes.stream()).collect(Collectors.toList());
  }

  private @NotNull List<@NotNull DocumentHighlight> textRangesToHighlights(@NotNull List<@NotNull TextRange> usages,
                                                                           @NotNull Editor editor,
                                                                           @NotNull DocumentHighlightKind kind) {
    return usages.stream().map(textRange -> new DocumentHighlight(textRangeToRange(editor, textRange), kind))
        .collect(Collectors.toList());
  }

  private @NotNull List<@NotNull DocumentHighlight> getHighlightsFromUsages(@NotNull Project project,
                                                                            @NotNull Editor editor,
                                                                            @NotNull PsiFile file) {
    final Ref<List<DocumentHighlight>> ref = new Ref<>();
    DumbService.getInstance(project).withAlternativeResolveEnabled(() -> {
      final var usageTargets = getUsageTargets(editor, file);
      if (usageTargets == null) {
        ref.set(List.of());
      } else {
        final var result = Arrays.stream(usageTargets)
            .map(usage -> extractDocumentHighlightFromRaw(project, file, editor, usage))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
        ref.set(result);
      }
    });
    return ref.get();
  }


  private @NotNull UsageTarget @Nullable [] getUsageTargets(@NotNull Editor editor, @NotNull PsiFile file) {
    var usageTargets = UsageTargetUtil.findUsageTargets(editor, file);
    if (Arrays.equals(usageTargets, UsageTarget.EMPTY_ARRAY)) {
      usageTargets = getUsageTargetsFromNavItem(editor, file);
    }
    if (usageTargets == null) {
      usageTargets = getUsageTargetsFromPolyVariantReference(editor);
    }
    return usageTargets;
  }

  private @NotNull UsageTarget @Nullable [] getUsageTargetsFromNavItem(@NotNull Editor editor, @NotNull PsiFile file) {
    var targetElement = EditorUtil.findTargetElement(editor);
    if (targetElement == null) {
      return null;
    }
    if (targetElement != file) { // Compare references
      if (!(targetElement instanceof NavigationItem)) {
        targetElement = targetElement.getNavigationElement();
      }
      if (targetElement instanceof NavigationItem) {
        return new UsageTarget[]{new PsiElement2UsageTargetAdapter(targetElement, true)};
      }
    }
    return null;
  }

  private @NotNull UsageTarget @Nullable [] getUsageTargetsFromPolyVariantReference(@NotNull Editor editor) {
    final var ref = TargetElementUtil.findReference(editor);

    if (ref instanceof PsiPolyVariantReference) {
      final var results = ((PsiPolyVariantReference) ref).multiResolve(false);

      if (results.length > 0) {
        return ContainerUtil.mapNotNull(results, result -> {
          final var element = result.getElement();
          return element == null ? null : new PsiElement2UsageTargetAdapter(element, true);
        }, UsageTarget.EMPTY_ARRAY);
      }
    }
    return null;
  }

  private @NotNull List<@NotNull DocumentHighlight> extractDocumentHighlightFromRaw(@NotNull Project project,
                                                                                    @NotNull PsiFile file,
                                                                                    @NotNull Editor editor,
                                                                                    @NotNull UsageTarget usage) {
    if (usage instanceof PsiElement2UsageTargetAdapter) {
      final var target = ((PsiElement2UsageTargetAdapter) usage).getTargetElement();
      if (target == null) {
        return List.of();
      }
      final var refs = findRefsToElement(target, project, file);
      return refsToHighlights(target, file, editor, refs);
    } else {
      return List.of();
    }
  }

  private @NotNull Collection<@NotNull PsiReference> findRefsToElement(@NotNull PsiElement target,
                                                                       @NotNull Project project,
                                                                       @NotNull PsiFile file) {
    final var findUsagesManager = ((FindManagerImpl) FindManager.getInstance(project)).getFindUsagesManager();
    final var handler = findUsagesManager.getFindUsagesHandler(target, true);

    // in case of injected file, use host file to highlight all occurrences of the target in each injected file
    final var context = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);

    final var searchScope = new LocalSearchScope(context);
    final var result = handler == null
        ? ReferencesSearch.search(target, searchScope, false).findAll()
        : handler.findReferencesToHighlight(target, searchScope);
    return result.stream().filter(Objects::nonNull).toList();
  }

  private @NotNull List<@NotNull DocumentHighlight> refsToHighlights(@NotNull PsiElement element,
                                                                     @NotNull PsiFile file,
                                                                     @NotNull Editor editor,
                                                                     @NotNull Collection<@NotNull PsiReference> refs) {
    final var detector = ReadWriteAccessDetector.findDetector(element);
    final var highlights = new ArrayList<DocumentHighlight>();

    if (detector != null) {
      final var readRefs = new ArrayList<PsiReference>();
      final var writeRefs = new ArrayList<PsiReference>();
      for (final var ref : refs) {
        if (detector.getReferenceAccess(element, ref) == ReadWriteAccessDetector.Access.Read) {
          readRefs.add(ref);
        } else {
          writeRefs.add(ref);
        }
      }
      addHighlights(highlights, readRefs, editor, DocumentHighlightKind.Read);
      addHighlights(highlights, writeRefs, editor, DocumentHighlightKind.Write);
    } else {
      addHighlights(highlights, refs, editor, DocumentHighlightKind.Text);
    }

    final var range = HighlightUsagesHandler.getNameIdentifierRange(file, element);
    if (range != null) {
      final var kind = (detector != null && detector.isDeclarationWriteAccess(element))
          ? DocumentHighlightKind.Write
          : DocumentHighlightKind.Text;
      highlights.add(new DocumentHighlight(textRangeToRange(editor, range), kind));
    }
    return highlights;
  }

  private void addHighlights(@NotNull List<@NotNull DocumentHighlight> highlights,
                             @NotNull Collection<@NotNull PsiReference> refs,
                             @NotNull Editor editor,
                             @NotNull DocumentHighlightKind kind) {
    final var textRanges = new ArrayList<TextRange>(refs.size());
    for (final var ref : refs) {
      HighlightUsagesHandler.collectRangesToHighlight(ref, textRanges);
    }
    final var toAdd = textRanges.stream()
        .map(textRange -> new DocumentHighlight(textRangeToRange(editor, textRange), kind)).toList();
    highlights.addAll(toAdd);
  }

  private @NotNull Range textRangeToRange(@NotNull Editor editor, @NotNull TextRange range) {
    final var doc = editor.getDocument();
    return new Range(MiscUtil.offsetToPosition(doc, range.getStartOffset()), MiscUtil.offsetToPosition(doc, range.getEndOffset()));
  }
}
