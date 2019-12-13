/*
 * Framework.java
 *
 * Copyright (c) 2015 Mike Strobel, 2019 Joerg Delker
 *
 * This source code is subject to terms and conditions of the Apache License, Version 2.0.
 * A copy of the license can be found in the License.html file at the root of this distribution.
 * By using this source code in any fashion, you are agreeing to be bound by the terms of the
 * Apache License, Version 2.0.
 *
 * You must not remove this notice, or any other, from this software.
 */
package com.strobel;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Procyon {

  private final static String PROP_RESOURCE = "procyon.properties";
  private static String _version;

  public static String version() {
    if (_version == null) {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      Properties props = new Properties();
      try (InputStream resourceStream = loader.getResourceAsStream(PROP_RESOURCE)) {
        props.load(resourceStream);
      } catch (IOException ex) {
        Logger.getLogger(Procyon.class.getName()).log(Level.SEVERE, null, ex);
      }
      _version = props.getProperty("procyon.version", "undefined");
    }

    return _version;
  }
}
