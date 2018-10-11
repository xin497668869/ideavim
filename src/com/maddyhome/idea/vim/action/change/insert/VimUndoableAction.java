package com.maddyhome.idea.vim.action.change.insert;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author linxixin@cvte.com
 * @since 1.0
 */
public class VimUndoableAction implements UndoableAction {

  private DocumentReference[] myDocumentReferences;

  private String name;

  public VimUndoableAction(DocumentReference[] documentReferences, String name) {
    this.myDocumentReferences = documentReferences;
    this.name = name;
  }

  public String getName() {
    return name;
  }

  @Override
  public void undo() throws UnexpectedUndoException {

  }

  @Override
  public void redo() throws UnexpectedUndoException {

  }


  @Nullable
  @Override
  public DocumentReference[] getAffectedDocuments() {
    return myDocumentReferences;
  }

  @Override
  public boolean isGlobal() {
    return false;
  }

  @Override
  public String toString() {
    final StringBuffer sb = new StringBuffer("VimUndoableAction{");
    sb.append("myDocumentReferences=")
      .append(myDocumentReferences == null ? "null" : Arrays.asList(myDocumentReferences).toString());
    sb.append(", name='").append(name).append('\'');
    sb.append('}');
    return sb.toString();
  }
}
