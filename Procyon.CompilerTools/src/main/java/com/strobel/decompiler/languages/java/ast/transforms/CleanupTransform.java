/*
 * CleanupTransform.java
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

public class CleanupTransform extends ContextTrackingVisitor<Void> {

  private final static Logger LOG = Logger.getLogger(CleanupTransform.class.getSimpleName());

  public CleanupTransform(final DecompilerContext context) {
    super(context);
  }

  /*
   * remove BlockStatement if it just contains another BlockStatement
   */
  @Override
  public Void visitBlockStatement(final BlockStatement node, final Void data) {
    AstNode child = node.getFirstChild();
    if (child != null && child.equals(node.getLastChild()) && child instanceof BlockStatement) {
      node.replaceWith(child);
      return super.visitBlockStatement((BlockStatement) child, data);
    }

    if (!node.hasChildren() && node.getParent() instanceof BlockStatement) {
      node.remove();
      return null;
    }

    return super.visitBlockStatement(node, data);
  }

  /*
   * remove InitializerBlock if it just contains an empty BlockStatement
   */
  @Override
  public Void visitInitializerBlock(final InstanceInitializer node, final Void data) {
    Void result = super.visitInitializerBlock(node, data);

    // remove this node, if it only contains an empty BlockStatement
    AstNode firstChild = node.getFirstChild();
    if (firstChild != null
            && firstChild == node.getLastChild()
            && firstChild instanceof BlockStatement
            && !firstChild.hasChildren()) {
      node.remove();
      return null;
    }

    return result;
  }

  @Override
  public Void visitCastExpression(final CastExpression node, final Void data) {
    AstNode firstChild = node.getFirstChild();
    if (firstChild instanceof SimpleType) {
      SimpleType sType = (SimpleType) firstChild;
      if ("Object".equals(sType.getIdentifier())) {
        node.replaceWith(node.getExpression());
      }
    }
    return super.visitCastExpression(node, data);
  }
}
