/*
 * AssimilateStaticFieldsTransform.java
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
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.DecompilerContext;
import com.strobel.decompiler.languages.java.ast.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AssimilateStaticFieldsTransform extends ContextTrackingVisitor<Void> {

  private final static Logger LOG = Logger.getLogger(AssimilateStaticFieldsTransform.class.getSimpleName());

  private Map<String, FieldDeclaration> _valueFields = new LinkedHashMap<>();
  private Map<String, ObjectCreationExpression> _valueInitializers = new LinkedHashMap<>();
  private boolean _inClinit;

  public AssimilateStaticFieldsTransform(final DecompilerContext context) {
    super(context);
  }

  @Override
  public Void visitTypeDeclaration(final TypeDeclaration node, final Void data) {
    TypeDefinition definition = node.getUserData(Keys.TYPE_DEFINITION);
    List<FieldDefinition> fieldList = definition.getDeclaredFields();

    final Map<String, FieldDeclaration> oldValueFields = _valueFields;
    final Map<String, ObjectCreationExpression> oldValueInitializers = _valueInitializers;

    final LinkedHashMap<String, FieldDeclaration> valueFields = new LinkedHashMap<>();
    final LinkedHashMap<String, ObjectCreationExpression> valueInitializers = new LinkedHashMap<>();

    _valueFields = valueFields;
    _valueInitializers = valueInitializers;

    Void v = null;
    try {
      v = super.visitTypeDeclaration(node, data);
    } finally {
      _valueFields = oldValueFields;
      _valueInitializers = oldValueInitializers;
    }
    return v;
  }

  @Override
  public Void visitFieldDeclaration(final FieldDeclaration node, final Void data) {
    final TypeDefinition currentType = context.getCurrentType();

    final FieldDefinition field = node.getUserData(Keys.FIELD_DEFINITION);

    if (field != null) {
      _valueFields.put(field.getName(), node);
    }

    return super.visitFieldDeclaration(node, data);
  }

  @Override
  public Void visitMethodDeclaration(final MethodDeclaration node, final Void data) {
    final MethodDefinition method = node.getUserData(Keys.METHOD_DEFINITION);
    String methodName = method.getName();

    // look for a initialization block, parse all assignments and transfer them
    // to the field declarations
    if ("<clinit>".equals(methodName)) {
      BlockStatement bs = (BlockStatement) node.getLastChild();
      for (final AstNode child : bs.getChildren()) {
        if (child instanceof ExpressionStatement) {
          Expression exp = ((ExpressionStatement) child).getExpression();
          if (exp instanceof AssignmentExpression) {
            AssignmentExpression assingExp = (AssignmentExpression) exp;
            Expression leftExp = assingExp.getLeft();
            AssignmentOperatorType aot = assingExp.getOperator();
            Expression rightExp = assingExp.getRight();

            if (leftExp instanceof IdentifierExpression
                    && aot.equals(AssignmentOperatorType.ASSIGN)) {
              String fieldName = ((IdentifierExpression) leftExp).getIdentifier();
              if (_valueFields.containsKey(fieldName)) {
                FieldDeclaration fd = _valueFields.get(fieldName);
                fd.getVariables().firstOrNullObject().setInitializer(rightExp.clone());
                child.remove();
              }
            } else {
              LOG.log(Level.WARNING, "found unqualified AssignmentExpression in clinit: {0}", exp.getText());
            }
          } else {
            LOG.log(Level.WARNING, "found a non-AssignmentExpression in clinit: {0}", exp.getText());
          }
        }
      }
      if (!bs.hasChildren()) {
        node.remove();
      }
    }

    return super.visitMethodDeclaration(node, data);
  }

}
