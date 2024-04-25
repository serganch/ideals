package org.rri.ideals.server.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Tuple;
import org.junit.Test;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;

import java.util.List;

public class SignatureHelpTest extends LspServerTestBase {
  @Override
  protected String getProjectRelativePath() {
    return "signature-help";
  }

  @Test
  public void signatureHelp() {
    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/Test.java"));
    SignatureHelpParams params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(filePath.toLspUri()));
    params.setPosition(new Position(4, 8));
    createEditor(LspPath.fromLspUri(params.getTextDocument().getUri()));
    var actual =
        TestUtil.getNonBlockingEdt(server().getTextDocumentService().signatureHelp(params), 30000);
    var expected = new SignatureHelp();
    var firstSignatureInformation = new SignatureInformation();
    firstSignatureInformation.setActiveParameter(0);
    firstSignatureInformation.setLabel("int x");

    var firstParameterInformation = new ParameterInformation();
    firstParameterInformation.setLabel(Tuple.two(0, 5));

    firstSignatureInformation.setParameters(List.of(firstParameterInformation));

    expected.setSignatures(List.of(firstSignatureInformation));
    assertEquals(expected, actual);
  }
}
