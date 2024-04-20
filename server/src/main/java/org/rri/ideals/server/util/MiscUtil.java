package org.rri.ideals.server.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspPath;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class MiscUtil {
  private static final Logger LOG = Logger.getInstance(MiscUtil.class);

  private MiscUtil() {
  }

  @NotNull
  public static <T> T with(@NotNull T object, @NotNull Consumer<T> block) {
    block.accept(object);
    return object;
  }

  @NotNull
  public static Position offsetToPosition(@NotNull Document doc, int offset) {
    if (offset == -1) {
      return new Position(0, 0);
    }
    var line = doc.getLineNumber(offset);
    var lineStartOffset = doc.getLineStartOffset(line);
    var column = offset - lineStartOffset;
    return new Position(line, column);
  }

  @Nullable
  public static PsiFile resolvePsiFile(@NotNull Project project, @NotNull LspPath path) {
    var result = new Ref<PsiFile>();
    invokeWithPsiFileInReadAction(project, path, result::set);
    return result.get();
  }

  @Nullable
  public static <T> T produceWithPsiFileInReadAction(@NotNull Project project,
                                                     @NotNull LspPath path,
                                                     @NotNull Function<@NotNull PsiFile, T> block) {
    final var virtualFile = path.findVirtualFile();

    if (virtualFile == null) {
      LOG.info("File not found: " + path);
      return null;
    }

    return ApplicationManager.getApplication().runReadAction((Computable<T>) () -> {
      final var psiFile = PsiManager.getInstance(project).findFile(virtualFile);

      if (psiFile == null) {
        LOG.info("Unable to get PSI for virtual file: " + virtualFile);
        return null;
      }

      return block.apply(psiFile);
    });

  }

  public static void invokeWithPsiFileInReadAction(@NotNull Project project, @NotNull LspPath path, @NotNull Consumer<@NotNull PsiFile> block) {
    produceWithPsiFileInReadAction(project, path,
        (psiFile) -> {
          block.accept(psiFile);
          return null;
        });
  }

  public static <T> T computeInEDTAndWait(@NotNull Supplier<T> action) {
    final var ref = new AtomicReference<T>();

    ApplicationManager.getApplication().invokeAndWait(() -> {
      ref.set(action.get());
    });

    return ref.get();
  }

  @Nullable
  public static Document getDocument(@NotNull PsiFile file) {
    var virtualFile = file.getVirtualFile();

    if (virtualFile == null)
      return file.getViewProvider().getDocument();

    var doc = FileDocumentManager.getInstance().getDocument(virtualFile);

    if (doc == null) {
      FileDocumentManagerImpl.registerDocument(
          new DocumentImpl(file.getViewProvider().getContents()),
          virtualFile);
      doc = FileDocumentManager.getInstance()
          .getDocument(virtualFile);
    }

    return doc;
  }

  @NotNull
  public static Runnable asWriteAction(@NotNull Runnable action) {
    return () -> ApplicationManager.getApplication().runWriteAction(action);
  }

  @NotNull
  public static RuntimeException wrap(@NotNull Throwable e) {
    return e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
  }

  public interface RunnableWithException {
    void run() throws Exception;
  }

  public static Runnable asRunnable(@NotNull MiscUtil.RunnableWithException action) {
    return () -> {
      try {
        action.run();
      } catch (Exception e) {
        throw wrap(e);
      }
    };
  }

  public static <T> T makeThrowsUnchecked(@NotNull Callable<T> block) {
    try {
      return block.call();
    } catch (Exception e) {
      throw wrap(e);
    }
  }

  @Nullable
  public static LocationLink psiElementToLocationLink(@NotNull PsiElement targetElem, @Nullable Document doc, @Nullable Range originalRange) {
    if (doc == null) {
      return null;
    }
    Range range = getPsiElementRange(doc, targetElem);
    String uri = LspPath.fromVirtualFile(targetElem.getContainingFile().getVirtualFile()).toLspUri();
    return range != null ? new LocationLink(uri, range, range, originalRange) : null;
  }

  @Nullable
  public static Location psiElementToLocation(@Nullable PsiElement elem) {
    if (elem == null) {
      return null;
    }
    var file = elem.getContainingFile();
    return psiElementToLocation(elem, file);
  }

  @Nullable
  public static Location psiElementToLocation(@Nullable PsiElement elem, @NotNull PsiFile file) {
    var doc = getDocument(file);
    if (doc == null) {
      return null;
    }
    var uri = LspPath.fromVirtualFile(file.getVirtualFile()).toLspUri();
    Range range = getPsiElementRange(doc, elem);
    return range != null ? new Location(uri, range) : null;
  }

  @Nullable
  public static Range getPsiElementRange(@NotNull Document doc, @Nullable PsiElement elem) {
    TextRange range = null;
    if (elem == null) {
      return null;
    }
    if (elem instanceof PsiNameIdentifierOwner) {
      PsiElement identifier = ((PsiNameIdentifierOwner) elem).getNameIdentifier();
      if (identifier != null) {
        range = identifier.getTextRange();
      }
    }
    if (range == null) {
      range = elem.getTextRange();
    }
    return range != null ? getRange(doc, range) : null;
  }

  @NotNull
  public static Range getRange(@NotNull Document doc, @NotNull Segment segment) {
    return new Range(offsetToPosition(doc, segment.getStartOffset()), offsetToPosition(doc, segment.getEndOffset()));
  }

  public static int positionToOffset(@NotNull Document doc, @NotNull Position pos) {
    return doc.getLineStartOffset(pos.getLine()) + pos.getCharacter();
  }

  @NotNull
  public static <T> Stream<T> streamOf(T @Nullable [] array) {
    return array != null ? Arrays.stream(array) : Stream.empty();
  }

  public interface ThrowingConsumer<T> {
    void accept(T t) throws Exception;
  }

  @NotNull
  public static <T> Consumer<T> toConsumer(@NotNull ThrowingConsumer<T> block) {
    return t -> {
      try {
        block.accept(t);
      } catch (Exception e) {
        throw wrap(e);
      }
    };
  }

  public interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  public static <T> Supplier<T> toSupplier(@NotNull ThrowingSupplier<T> block) {
    return () -> {
      try {
        return block.get();
      } catch (Exception e) {
        throw wrap(e);
      }
    };
  }

  public static <T> T uncheckExceptions(@NotNull ThrowingSupplier<T> block) {
    return toSupplier(block).get();
  }
}
