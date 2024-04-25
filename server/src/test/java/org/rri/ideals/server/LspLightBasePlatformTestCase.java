package org.rri.ideals.server;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public abstract class LspLightBasePlatformTestCase extends BasePlatformTestCase {

  @Override
  protected boolean isIconRequired() {
    return true;
  }
}
