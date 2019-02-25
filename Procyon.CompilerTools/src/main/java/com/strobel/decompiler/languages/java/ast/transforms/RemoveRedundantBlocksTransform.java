/*
 * RemoveRedundantBlocksTransform.java
 *
 * Copyright (c) 2018 Joerg Delker
 *
 * This source code is based on Mono.Cecil from Jb Evain, Copyright (c) Jb Evain;
 * and ILSpy/ICSharpCode from SharpDevelop, Copyright (c) AlphaSierraPapa.
 *
 * This source code is subject to terms and conditions of the Apache License, Version 2.0.
 * A copy of the license can be found in the License.html file at the root of this distribution.
 * By using this source code in any fashion, you are agreeing to be bound by the terms of the
 * Apache License, Version 2.0.
 *
 * You must not remove this notice, or any other, from this software.
 */
package com.strobel.decompiler.languages.java.ast.transforms;

import com.strobel.decompiler.DecompilerContext;
import com.strobel.decompiler.languages.java.ast.*;
import java.util.logging.Logger;

public class RemoveRedundantBlocksTransform extends ContextTrackingVisitor<Void> {

  private final static Logger LOG = Logger.getLogger(RemoveRedundantBlocksTransform.class.getSimpleName());

  public RemoveRedundantBlocksTransform(final DecompilerContext context) {
    super(context);
  }

  /*
   * Delete Block if it just contains another block
   */
  @Override
  public Void visitBlockStatement(final BlockStatement node, final Void data) {
    AstNode child = node.getFirstChild();
    if (child != null && child.equals(node.getLastChild()) && child instanceof BlockStatement) {
      node.replaceWith(child);
    }
    
    if (!node.hasChildren() && node.getParent() instanceof BlockStatement) {
      node.remove();
      return null;
    }
    return super.visitBlockStatement(node, data);
  }

}
