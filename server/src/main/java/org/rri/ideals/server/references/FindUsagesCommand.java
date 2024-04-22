package org.rri.ideals.server.references;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.CustomUsageSearcher;
import com.intellij.find.findUsages.FindUsagesHandlerBase;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchSession;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageInfoToUsageConverter;
import com.intellij.usages.UsageSearcher;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.commands.ExecutorContext;
import org.rri.ideals.server.commands.LspCommand;
import org.rri.ideals.server.util.LspProgressIndicator;
import org.rri.ideals.server.util.MiscUtil;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FindUsagesCommand extends LspCommand<List<? extends Location>> {
  private static final Logger LOG = Logger.getInstance(FindUsagesCommand.class);

  @Override
  protected @NotNull Supplier<@NotNull String> getMessageSupplier() {
    return () -> "References (Find usages) call";
  }

  @Override
  protected boolean isCancellable() {
    return true;
  }

  @Override
  protected @NotNull List<? extends Location> execute(@NotNull ExecutorContext ctx) {
    final var editor = ctx.getEditor();
    assert editor != null;
    PsiFile file = ctx.getPsiFile();
    Document doc = MiscUtil.getDocument(file);
    if (doc == null) {
      return List.of();
    }

    return Optional.ofNullable(TargetElementUtil.findTargetElement(editor, TargetElementUtil.getInstance().getAllAccepted()))
        .map(target -> findUsages(file.getProject(), target, ctx.getCancelToken()))
        .orElse(List.of());
  }

  private static @NotNull List<@NotNull Location> findUsages(@NotNull Project project,
                                                             @NotNull PsiElement target,
                                                             @Nullable CancelChecker cancelToken) {
    var manager = ((FindManagerImpl) FindManager.getInstance(project)).getFindUsagesManager();
    var handler = manager.getFindUsagesHandler(target, FindUsagesHandlerFactory.OperationMode.USAGES_WITH_DEFAULT_OPTIONS);

    return ProgressManager.getInstance().runProcess(() -> {
      List<Location> result;

      if (handler != null) {
        var dialog = handler.getFindUsagesDialog(false, false, false);
        dialog.close(DialogWrapper.OK_EXIT_CODE);
        var options = dialog.calcFindUsagesOptions();
        PsiElement[] primaryElements = handler.getPrimaryElements();
        PsiElement[] secondaryElements = handler.getSecondaryElements();
        UsageSearcher searcher = createUsageSearcher(primaryElements, secondaryElements, handler, options, project);
        Set<Location> saver = ContainerUtil.newConcurrentSet();
        searcher.generate(usage -> {
          if (cancelToken != null) {
            try {
              cancelToken.checkCanceled();
            } catch (CancellationException e) {
              return false;
            }
          }
          if (usage instanceof final UsageInfo2UsageAdapter ui2ua && !ui2ua.isNonCodeUsage()) {
            var elem = ui2ua.getElement();
            var loc = MiscUtil.psiElementToLocation(elem);
            if (loc != null) {
              saver.add(loc);
            }
          }
          return true;
        });
        result = new ArrayList<>(saver);
      } else {
        result = ReferencesSearch.search(target).findAll().stream()
            .map(PsiReference::getElement)
            .map(MiscUtil::psiElementToLocation)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
      }

      return result;
    }, new LspProgressIndicator(cancelToken));
  }

  // Took this function from com.intellij.find.findUsages.FindUsagesManager.
  // Reference solution (Ruin0x11/intellij-lsp-server) used outdated constructor of FindUsagesManager.
  // Now this constructor is not exists.
  @NotNull
  private static UsageSearcher createUsageSearcher(PsiElement @NotNull [] primaryElements,
                                                   PsiElement @NotNull [] secondaryElements,
                                                   @NotNull FindUsagesHandlerBase handler,
                                                   @NotNull FindUsagesOptions options,
                                                   @NotNull Project project) throws PsiInvalidElementAccessException {
    FindUsagesOptions optionsClone = options.clone();
    return processor -> {
      Processor<UsageInfo> usageInfoProcessor = new CommonProcessors.UniqueProcessor<>(usageInfo -> {
        Usage usage = usageInfo != null ? UsageInfoToUsageConverter.convert(primaryElements, usageInfo) : null;
        return processor.process(usage);
      });
      PsiElement[] elements = ArrayUtil.mergeArrays(primaryElements, secondaryElements, PsiElement.ARRAY_FACTORY);

      optionsClone.fastTrack = new SearchRequestCollector(new SearchSession(elements));
      if (optionsClone.searchScope instanceof GlobalSearchScope) {
        // we will search in project scope always but warn if some usage is out of scope
        optionsClone.searchScope = optionsClone.searchScope.union(GlobalSearchScope.projectScope(project));
      }
      try {
        for (PsiElement element : elements) {
          if (!handler.processElementUsages(element, usageInfoProcessor, optionsClone)) {
            return;
          }

          for (CustomUsageSearcher searcher : CustomUsageSearcher.EP_NAME.getExtensionList()) {
            try {
              searcher.processElementUsages(element, processor, optionsClone);
            } catch (ProcessCanceledException e) {
              throw e;
            } catch (Exception e) {
              LOG.error(e);
            }
          }
        }

        PsiSearchHelper.getInstance(project).processRequests(optionsClone.fastTrack, ref -> {
          UsageInfo info = ref.getElement().isValid() ? new UsageInfo(ref) : null;
          return usageInfoProcessor.process(info);
        });
      } finally {
        optionsClone.fastTrack = null;
      }
    };
  }
}