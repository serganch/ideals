package org.rri.ideals.server.util;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

public class EditorUtil {
  private EditorUtil() {
  }

  @NotNull
  public static Editor createEditor(@NotNull Disposable context,
                                    @NotNull PsiFile file,
                                    @NotNull Position position) {
    Document doc = MiscUtil.getDocument(file);
    EditorFactory editorFactory = EditorFactory.getInstance();

    assert doc != null;
    Editor created = editorFactory.createEditor(doc, file.getProject());
    created.getCaretModel().moveToLogicalPosition(new LogicalPosition(position.getLine(), position.getCharacter()));

    Disposer.register(context, () -> {
      if (!created.isDisposed()) {
        editorFactory.releaseEditor(created);
      }
    });

    return created;
  }


  public static void withEditor(@NotNull Disposable context,
                                @NotNull PsiFile file,
                                @NotNull Position position,
                                @NotNull Consumer<Editor> callback) {
    computeWithEditor(context, file, position, editor -> {
      callback.accept(editor);
      return null;
    });
  }

  public static <T> T computeWithEditor(@NotNull Disposable context,
                                        @NotNull PsiFile file,
                                        @NotNull Position position,
                                        @NotNull Function<Editor, T> callback) {
    Editor editor = createEditor(context, file, position);

    try {
      return callback.apply(editor);
    } catch (Exception e) {
      throw MiscUtil.wrap(e);
    } finally {
      Disposer.dispose(context);
    }
  }

  public static @Nullable PsiElement findTargetElement(@NotNull Editor editor) {
    return TargetElementUtil.findTargetElement(editor, TargetElementUtil.getInstance().getAllAccepted());
  }
}
