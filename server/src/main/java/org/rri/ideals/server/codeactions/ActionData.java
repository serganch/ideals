package org.rri.ideals.server.codeactions;

import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class ActionData {
  private String uri;
  private Range range;

  ActionData(@NotNull String uri, @NotNull Range range) {
    this.uri = uri;
    this.range = range;
  }

  public String getUri() {
    return uri;
  }

  @SuppressWarnings("unused") // used via reflection
  public void setUri(@NotNull String uri) {
    this.uri = uri;
  }

  public Range getRange() {
    return range;
  }

  public void setRange(@NotNull Range range) {
    this.range = range;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ActionData) obj;
    return Objects.equals(this.uri, that.uri) &&
        Objects.equals(this.range, that.range);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uri, range);
  }

  @Override
  public String toString() {
    return "ActionData[" +
        "uri=" + uri + ", " +
        "range=" + range + ']';
  }
}
