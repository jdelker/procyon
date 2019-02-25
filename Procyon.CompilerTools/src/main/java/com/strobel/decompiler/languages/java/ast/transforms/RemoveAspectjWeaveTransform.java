/*
 * RemoveAspectjWeaveTransform.java
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

import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.MethodBodyParseException;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.core.StringUtilities;
import com.strobel.decompiler.DecompilerContext;
import com.strobel.decompiler.languages.java.ast.*;
import java.util.logging.Logger;

public class RemoveAspectjWeaveTransform extends ContextTrackingVisitor<Void> {

  private final static Logger LOG = Logger.getLogger(RemoveAspectjWeaveTransform.class.getSimpleName());

  private String _ajcReturnVariable = null;

  public RemoveAspectjWeaveTransform(final DecompilerContext context) {
    super(context);
  }

  @Override
  public Void visitFieldDeclaration(final FieldDeclaration node, final Void data) {
    final FieldDefinition field = node.getUserData(Keys.FIELD_DEFINITION);
    if (field.getName().startsWith("ajc$")) {
      node.remove();
      return null;
    }
    return super.visitFieldDeclaration(node, data);
  }

  @Override
  public Void visitVariableDeclaration(final VariableDeclarationStatement node, final Void p) {
    if (node.getText().startsWith("final JoinPoint jp = ")) {
      node.remove();
      return null;
    }

    return super.visitVariableDeclaration(node, p);
  }

  @Override
  public Void visitMethodDeclaration(final MethodDeclaration node, final Void p) {
    final MethodDefinition method = node.getUserData(Keys.METHOD_DEFINITION);
    String methodName = method.getName();
    if (methodName != null && methodName.matches("ajc\\$.*")) {
      node.remove();
      return null;
    }

    return super.visitMethodDeclaration(node, p);
  }

  @Override
  public Void visitExpressionStatement(final ExpressionStatement node, final Void data) {

    String text = node.getText();
    try {
      if (text.matches("ajc\\$.*trace\\w*Entry(.*);\n")
              || text.matches("ajc\\$.*initLogger.*\n")
              || text.matches("ajc\\$tjp_\\d+ = .*\n")
              || text.matches("\\w+\\.ajc\\$.*createAspectInstance.*\n")) {
        node.remove();
        return null;
      } else if (text.matches("ajc\\$.*trace\\w+Exit(.*);\n")) {
        // handle method returns
        handleReturn(node);
        return null;
      }
    } catch (Exception ex) {
      throw new RuntimeException("failed to process '" + text + "'", ex);
    }
    return super.visitExpressionStatement(node, data);
  }

  @Override
  public Void visitTryCatchStatement(final TryCatchStatement node, final Void data) {

    // check if it is a aspectJ try/catch block
    BlockStatement tryBlock = node.getTryBlock();
    AstNode firstChild = tryBlock.getFirstChild();
    if (firstChild.getText().startsWith("ajc$")) {
      node.replaceWith(tryBlock);
      return super.visitBlockStatement(tryBlock, data);
    }
    return super.visitTryCatchStatement(node, data);
  }

  @Override
  public Void visitBlockStatement(final BlockStatement node, final Void data) {
    // look for clinit
    AstNode first = node.getFirstChild();
    if (first instanceof ExpressionStatement && first.getText().matches("ajc\\$preClinit.*\n")) {
      handleClinitBlock(node);
    }
    return visitChildren(node, data);
  }

  @Override
  public Void visitAssignmentExpression(final AssignmentExpression node, final Void data) {
    // only process AssignmentExpression if we are transforming a return construct
    if (_ajcReturnVariable != null) {
      Expression leftExp = node.getLeft();
      if (leftExp instanceof IdentifierExpression) {
        String varName = ((IdentifierExpression) leftExp).getIdentifier();
        if (_ajcReturnVariable.equals(varName)) {
          // replace AssignmentExpression with rightside
          Expression rightExp = node.getRight();
          node.replaceWith(rightExp);
        }
      }
    }
    return super.visitAssignmentExpression(node, data);
  }

  @Override
  public Void visitImportDeclaration(final ImportDeclaration node, final Void data) {
    // remove org.aspectj imports
    if (node.getImport().contains("org.aspectj")) {
      node.remove();
      return null;
    }
    return super.visitImportDeclaration(node, data);
  }

  /* ==================================================================== */
  private void handleReturn(ExpressionStatement node) {
    Expression exp = node.getExpression();
    if (exp != null && exp.hasChildren()) {
      AstNode firstChild = exp.getFirstChild();
      AstNode lastChild = exp.getLastChild();

      if (firstChild instanceof MemberReferenceExpression) {
        MemberReferenceExpression mre = (MemberReferenceExpression) firstChild;
        if (mre.getText().contains("traceConstructorExit")) {
          node.remove();
        } else {
          if (lastChild instanceof CastExpression) {
            handleCastReturn(node, (CastExpression) lastChild);
          } else if (lastChild instanceof InvocationExpression) {
            handleInvokeReturn(node, (InvocationExpression) lastChild);
          } else if (lastChild instanceof AssignmentExpression) {
            handleAssignmentReturn(node, (AssignmentExpression) lastChild);
          } else if (lastChild instanceof IdentifierExpression) {
            // TODO: temporary workaround
            node.remove();
          } else if (lastChild instanceof ConditionalExpression) {
            handleConditionalReturn(node, (ConditionalExpression) lastChild);
          } else if (lastChild instanceof NullReferenceExpression) {
            node.remove();
          } else if (lastChild instanceof ThisReferenceExpression) {
            node.remove();
          } else {
            throw new MethodBodyParseException("unexpected expression type '" + lastChild.getClass() + "' in: " + node.getText());
          }
        }
      } else {
        throw new MethodBodyParseException("unexpected expression type '" + lastChild.getClass() + "' in: " + node.getText());
      }
    }
  }

  /**
   * Handle return in CastExpression
   *
   * Common format is this:
   * <pre>
   * {@code
   * final String s;
   * ajc$...traceMethodExit(jp, (Object)(s = "7.1"));
   * return s;
   * }
   * </pre>
   *
   * Shall be converted to just <code>return "7.1";</code>
   *
   * @param node ExressionStatement (middle line of above code sample)
   * @param castExp CastExpression (last expression of above)
   */
  private void handleCastReturn(ExpressionStatement node, CastExpression castExp) {
    Expression exp = castExp.getExpression();
    if (exp instanceof NullReferenceExpression) {
      node.remove();
    } else if (exp instanceof IdentifierExpression) {
      transformReturn(node, (IdentifierExpression) exp);
    } else if (exp instanceof ConditionalExpression) {
      handleConditionalReturn(node, (ConditionalExpression) exp);
    } else if (exp instanceof ThisReferenceExpression) {
      node.remove();
    } else if (!(exp instanceof AssignmentExpression)) {
      throw new MethodBodyParseException("unexpected expression type " + exp.getClass().getSimpleName() + " when handling cast: " + castExp.getText());
    } else {
      AssignmentExpression assignExp = (AssignmentExpression) exp;
      transformReturn(node, assignExp);
    }
  }

  /**
   * Handle return with InvocationExpression
   *
   * Formats are varying here. The Conversions invocation contains different
   * argument types:
   *
   * <pre>
   * For embedded IdentifierExpression:
   * {@code
   * b = (b2 = hasNext);
   * ajc$...traceMethodExit(jp, Conversions.booleanObject(b));
   * return b2;
   * }
   *
   * Shall be converted by simply removing the ajc line
   * </pre>
   *
   * <pre>
   * For embedded AssignmentExpression:
   * {@code
   * boolean b;
   * ajc$...traceMethodExit(jp, Conversions.booleanObject(b = (_checkForLast ? ((_result != null) ? _result.isLast() : true) : true)));
   * return b;
   * }
   *
   * Shall be converted to <code>return {expression}</code>
   * </pre>
   *
   * @param node
   * @param invokeExp
   */
  private void handleInvokeReturn(ExpressionStatement node, InvocationExpression invokeExp) {
    String target = invokeExp.getTarget().getText();

    if (target.startsWith("Conversions.")) {
      AstNodeCollection<Expression> expList = invokeExp.getArguments();
      assert expList != null;
      assert expList.size() == 1;
      Expression exp = expList.firstOrNullObject();
      if (exp instanceof AssignmentExpression) {
        transformReturn(node, (AssignmentExpression) exp);
      } else if (exp instanceof IdentifierExpression) {
        transformReturn(node, (IdentifierExpression) exp);
      }
    } else {
      throw new MethodBodyParseException("unexpected invocation '" + target + "' in return: " + node.getText());
    }
  }

  private void handleAssignmentReturn(ExpressionStatement node, AssignmentExpression assignExp) {
    transformReturn(node, assignExp);
  }

  /**
   * Handle return with ConditionalExpression
   *
   * <pre>
   * {@code
   * String name;
   * ajc$...traceMethodExit(jp, a == null ? name = null : b == null ? name = null : name = "blafasel");
   * return name;
   * }
   * </pre>
   *
   * Shall be converted to
   * <code>return a == null ? null : b == null ? null : name = "blafasel"</code>
   *
   * @param node
   * @param conditionalExp
   */
  private void handleConditionalReturn(ExpressionStatement node, ConditionalExpression conditionalExp) {
    String varName = null;
    AstNode prevNode = node.getPreviousSibling();
    AstNode nextNode = node.getNextSibling();

    // check that prevNode is a VariableDeclarationStatement
    if (prevNode instanceof VariableDeclarationStatement) {
      varName = getVariableName((VariableDeclarationStatement) prevNode);
    } else {
      throw new MethodBodyParseException("expected to find VariableDeclaration for '" + varName + "' in " + prevNode.getText());
    }

    //check that nextNode is a ReturnStatement
    if (nextNode instanceof ReturnStatement) {
      AstNode returnExp = ((ReturnStatement) nextNode).getExpression();
    } else {
      throw new MethodBodyParseException("expected to find ReturnStatement for '" + varName + "' in " + nextNode.getText());
    }

    // remove previous & next node
    prevNode.remove();
    nextNode.remove();

    // build new ReturnStatement node for replacement
    AstNode newNode = new ReturnStatement(conditionalExp.clone());

    // process childNodes in context of _ajcReturnVariable
    _ajcReturnVariable = varName;
    visitChildren(newNode, null);
    _ajcReturnVariable = null;

    // finally replace node
    node.replaceWith(newNode);
  }

  private void handleClinitBlock(BlockStatement node) {
    // iterate through all children and kick out everything except the try block
    for (final AstNode child : node.getChildren()) {
      if (child instanceof TryCatchStatement) {
        TryCatchStatement tcNode = (TryCatchStatement) child;
        child.replaceWith(tcNode.getTryBlock());
      } else {
        child.remove();
      }
    }
  }

  /*
   * Transform the code for a given AssignmentExpression
   * - remove preceeding VariableDeclaration
   * - remove succeding ReturnStatement
   * - transform AssignmentExpression into ReturnStatement
   */
  private void transformReturn(ExpressionStatement node, AssignmentExpression assignExp) {
    Expression leftExp = assignExp.getLeft();
    Expression rightExp = assignExp.getRight();
    assert leftExp instanceof IdentifierExpression;
    String varName = ((IdentifierExpression) leftExp).getIdentifier();

    AstNode prevNode = node.getPreviousSibling();
    AstNode nextNode = node.getNextSibling();

    // check that prevNode is a VariableDeclarationStatement for varName
    if (prevNode instanceof VariableDeclarationStatement) {
      VariableInitializer vi = ((VariableDeclarationStatement) prevNode).getVariable(varName);
      if (vi == null) {
        throw new MethodBodyParseException("expected to find '" + varName + "' in VariableDeclaration: " + prevNode.getText());
      }
      prevNode.remove();
    } else {
      throw new MethodBodyParseException("expected to find VariableDeclaration for '" + varName + "' before: " + prevNode.getText());
    }

    // check that nextNode is a ReturnStatement
    if (nextNode instanceof ReturnStatement) {
      Expression returnExp = ((ReturnStatement) nextNode).getExpression();
      if (returnExp instanceof CastExpression) {
        // ignore cast
        returnExp = ((CastExpression) returnExp).getExpression();
      }

      if (returnExp instanceof IdentifierExpression) {
        if (!varName.equals(((IdentifierExpression) returnExp).getIdentifier())) {
          throw new MethodBodyParseException("expected variable '" + varName + "' in ReturnStatement: " + nextNode.getText());
        }
        nextNode.remove();
      } else {
        throw new MethodBodyParseException("unexpted return type: " + returnExp.getClass());
      }
    } else {
      throw new MethodBodyParseException("expected to find ReturnStatement for '" + varName + "' after: " + node.getText());
    }

    node.replaceWith(new ReturnStatement(rightExp.clone()));
  }

  /*
   * Transform the code for a given IdentifierExpression
   * This is usually an odd situation, because the assignment is not within this
   * but burried somewhere in the preceeding code.
   * We just leave things alone in this case, and only get rid of the AspectJ 
   * stuff.
   */
  private void transformReturn(ExpressionStatement node, IdentifierExpression identExp) {
    LOG.warning("found odd situation in " + node);
    node.remove();
  }

  private String getVariableName(VariableDeclarationStatement varStmnt) {
    VariableInitializer vi = varStmnt.getVariables().firstOrNullObject();
    if (vi == null) {
      throw new MethodBodyParseException("expected to find variable for VariableDeclaration: " + varStmnt.getText());
    }
    return vi.getName();
  }

}
