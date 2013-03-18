/*
 * AstOptimizer.java
 *
 * Copyright (c) 2013 Mike Strobel
 *
 * This source code is subject to terms and conditions of the Apache License, Version 2.0.
 * A copy of the license can be found in the License.html file at the root of this distribution.
 * By using this source code in any fashion, you are agreeing to be bound by the terms of the
 * Apache License, Version 2.0.
 *
 * You must not remove this notice, or any other, from this software.
 */

package com.strobel.decompiler.ast;

import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.core.CollectionUtilities;
import com.strobel.core.MutableInteger;
import com.strobel.core.Predicate;
import com.strobel.core.StrongBox;
import com.strobel.decompiler.DecompilerContext;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static com.strobel.decompiler.ast.PatternMatching.*;

@SuppressWarnings("ConstantConditions")
public final class AstOptimizer {
    int _nextLabelIndex;
    DecompilerContext _context;
    MetadataSystem _metadataSystem;
    Block _method;

    public static void optimize(final DecompilerContext context, final Block method) {
        optimize(context, method, AstOptimizationStep.None);
    }

    public static void optimize(final DecompilerContext context, final Block method, final AstOptimizationStep abortBeforeStep) {
        if (abortBeforeStep == AstOptimizationStep.RemoveRedundantCode) {
            return;
        }

        final AstOptimizer optimizer = new AstOptimizer();

        optimizer._context = context;
        optimizer._metadataSystem = MetadataSystem.instance();
        optimizer._method = method;

        removeRedundantCode(method);

        if (abortBeforeStep == AstOptimizationStep.ReduceBranchInstructionSet) {
            return;
        }

        for (final Block block : method.getSelfAndChildrenRecursive(Block.class)) {
            reduceBranchInstructionSet(block);
        }

        if (abortBeforeStep == AstOptimizationStep.InlineVariables) {
            return;
        }

        final Inlining inliningPhase1 = new Inlining(method);

        inliningPhase1.inlineAllVariables();

        if (abortBeforeStep == AstOptimizationStep.CopyPropagation) {
            return;
        }

        inliningPhase1.copyPropagation();

        if (abortBeforeStep == AstOptimizationStep.SplitToMovableBlocks) {
            return;
        }

        for (final Block block : method.getSelfAndChildrenRecursive(Block.class)) {
            optimizer.splitToMovableBlocks(block);
        }

        if (abortBeforeStep == AstOptimizationStep.TypeInference) {
            return;
        }

        TypeAnalysis.run(context, method);
    }

    // <editor-fold defaultstate="collapsed" desc="RemoveRedundantCode Step">

    private static void removeRedundantCode(final Block method) {
        final Map<Label, MutableInteger> labelReferenceCount = new IdentityHashMap<>();

        final List<Expression> branchExpressions = method.getSelfAndChildrenRecursive(
            Expression.class,
            new Predicate<Expression>() {
                @Override
                public boolean test(final Expression e) {
                    return e.isBranch();
                }
            }
        );

        for (final Expression e : branchExpressions) {
            for (final Label branchTarget : e.getBranchTargets()) {
                final MutableInteger referenceCount = labelReferenceCount.get(branchTarget);

                if (referenceCount == null) {
                    labelReferenceCount.put(branchTarget, new MutableInteger(1));
                }
                else {
                    referenceCount.increment();
                }
            }
        }

        for (final Block block : method.getSelfAndChildrenRecursive(Block.class)) {
            final List<Node> body = block.getBody();
            final List<Node> newBody = new ArrayList<>(body.size());

            for (int i = 0, n = body.size(); i < n; i++) {
                final Node node = body.get(i);
                final StrongBox<Label> target = new StrongBox<>();
                final StrongBox<Expression> popExpression = new StrongBox<>();

                if (PatternMatching.matchGetOperand(node, AstCode.Goto, target) &&
                    i + 1 < body.size() &&
                    body.get(i + 1) == target.get()) {

                    //
                    // Ignore the branch.
                    //
                    if (labelReferenceCount.get(target.get()).getValue() == 1) {
                        //
                        // Ignore the label as well.
                        //
                        i++;
                    }
                }
                else if (match(node, AstCode.Nop)) {
                    //
                    // Ignore NOP.
                    //
                }
                else if (PatternMatching.matchGetArgument(node, AstCode.Pop, popExpression)) {
                    final StrongBox<Variable> variable = new StrongBox<>();

                    if (!PatternMatching.matchGetOperand(popExpression.get(), AstCode.Load, variable)) {
                        throw new IllegalStateException("Pop should just have Load at this stage.");
                    }

                    //
                    // Best effort to move bytecode range to previous statement.
                    //

                    final StrongBox<Variable> previousVariable = new StrongBox<>();
                    final StrongBox<Expression> previousExpression = new StrongBox<>();

                    if (i - 1 >= 0 &&
                        matchGetArgument(body.get(i - 1), AstCode.Store, previousVariable, previousExpression) &&
                        previousVariable.get() == variable.get()) {

                        previousExpression.get().getRanges().addAll(((Expression) node).getRanges());

                        //
                        // Ignore POP.
                        //
                    }
                }
                else if (node instanceof Label) {
                    final Label label = (Label) node;
                    final MutableInteger referenceCount = labelReferenceCount.get(label);

                    if (referenceCount != null && referenceCount.getValue() > 0) {
                        newBody.add(label);
                    }
                }
                else {
                    newBody.add(node);
                }
            }

            body.clear();
            body.addAll(newBody);
        }

        //
        // DUP removal.
        //
        final StrongBox<Expression> child = new StrongBox<>();

        for (final Expression e : method.getSelfAndChildrenRecursive(Expression.class)) {
            final List<Expression> arguments = e.getArguments();

            for (int i = 0, n = arguments.size(); i < n; i++) {
                final Expression argument = arguments.get(i);

                if (PatternMatching.matchGetArgument(e, AstCode.Dup, child)) {
                    child.get().getRanges().addAll(argument.getRanges());
                    arguments.set(i, child.get());
                }
            }
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="ReduceBranchInstructionSet Step">

    private static void reduceBranchInstructionSet(final Block block) {
        final List<Node> body = block.getBody();

        for (int i = 0; i < body.size(); i++) {
            final Node node = body.get(i);

            if (!(node instanceof Expression)) {
                continue;
            }

            final Expression e = (Expression) node;
            final AstCode code;

            switch (e.getCode()) {
                case TableSwitch:
                case LookupSwitch: {
                    e.getArguments().get(0).getRanges().addAll(e.getRanges());
                    e.getRanges().clear();
                    continue;
                }

                case __LCmp:
                case __FCmpL:
                case __FCmpG:
                case __DCmpL:
                case __DCmpG: {
                    if (i == body.size() - 1 || !(body.get(i + 1) instanceof Expression)) {
                        continue;
                    }

                    final Expression next = (Expression) body.get(i + 1);

                    switch (next.getCode()) {
                        case __IfEq:
                            code = AstCode.CmpEq;
                            break;
                        case __IfNe:
                            code = AstCode.CmpNe;
                            break;
                        case __IfLt:
                            code = AstCode.CmpLt;
                            break;
                        case __IfGe:
                            code = AstCode.CmpGe;
                            break;
                        case __IfGt:
                            code = AstCode.CmpGt;
                            break;
                        case __IfLe:
                            code = AstCode.CmpLe;
                            break;
                        default:
                            continue;
                    }

                    body.remove(i);
                    break;
                }

                case __IfEq:
                    code = AstCode.LogicalNot;
                    break;
                case __IfNe:
                    e.setCode(AstCode.IfTrue);
                    continue;

                case __IfLt:
                    e.getArguments().add(new Expression(AstCode.LdC, 0));
                    code = AstCode.CmpLt;
                    break;
                case __IfGe:
                    e.getArguments().add(new Expression(AstCode.LdC, 0));
                    code = AstCode.CmpGe;
                    break;
                case __IfGt:
                    e.getArguments().add(new Expression(AstCode.LdC, 0));
                    code = AstCode.CmpGt;
                    break;
                case __IfLe:
                    e.getArguments().add(new Expression(AstCode.LdC, 0));
                    code = AstCode.CmpLe;
                    break;

                case __IfICmpEq:
                    code = AstCode.CmpEq;
                    break;
                case __IfICmpNe:
                    code = AstCode.CmpNe;
                    break;
                case __IfICmpLt:
                    code = AstCode.CmpLt;
                    break;
                case __IfICmpGe:
                    code = AstCode.CmpGe;
                    break;
                case __IfICmpGt:
                    code = AstCode.CmpGt;
                    break;
                case __IfICmpLe:
                    code = AstCode.CmpLe;
                    break;
                case __IfACmpEq:
                    code = AstCode.CmpEq;
                    break;
                case __IfACmpNe:
                    code = AstCode.CmpNe;
                    break;

                case __IfNull:
                    e.getArguments().add(new Expression(AstCode.AConstNull, null));
                    code = AstCode.CmpEq;
                    break;
                case __IfNonNull:
                    e.getArguments().add(new Expression(AstCode.AConstNull, null));
                    code = AstCode.CmpNe;
                    break;

                default:
                    continue;
            }

            final Expression newExpression = new Expression(code, null, e.getArguments());

            body.set(i, new Expression(AstCode.IfTrue, e.getOperand(), newExpression));
            newExpression.getRanges().addAll(e.getRanges());
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="SplitToMovableBlocks Step">

    private void splitToMovableBlocks(final Block block) {
        final List<Node> basicBlocks = new ArrayList<>();

        final List<Node> body = block.getBody();
        final Object firstNode = CollectionUtilities.firstOrDefault(body);

        final Label entryLabel;

        if (firstNode instanceof Label) {
            entryLabel = (Label) firstNode;
        }
        else {
            entryLabel = new Label();
            entryLabel.setName("Block_" + (_nextLabelIndex++));
        }

        BasicBlock basicBlock = new BasicBlock();
        List<Node> basicBlockBody = basicBlock.getBody();

        basicBlocks.add(basicBlock);
        basicBlockBody.add(entryLabel);

        block.setEntryGoto(new Expression(AstCode.Goto, entryLabel));

        if (!body.isEmpty()) {
            if (body.get(0) != entryLabel) {
                basicBlockBody.add(body.get(0));
            }

            for (int i = 1; i < body.size(); i++) {
                final Node lastNode = body.get(i - 1);
                final Node currentNode = body.get(i);

                //
                // Start a new basic block if necessary.
                //
                if (currentNode instanceof Label ||
                    currentNode instanceof TryCatchBlock ||
                    lastNode.isConditionalControlFlow() ||
                    lastNode.isUnconditionalControlFlow()) {

                    //
                    // Try to reuse the label.
                    //
                    final Label label = currentNode instanceof Label ? (Label) currentNode
                                                                     : new Label("Block_" + (_nextLabelIndex++));

                    //
                    // Terminate the last block.
                    //
                    if (!lastNode.isUnconditionalControlFlow()) {
                        basicBlockBody.add(new Expression(AstCode.Goto, label));
                    }

                    //
                    // Start the new block.
                    //
                    basicBlock = new BasicBlock();
                    basicBlocks.add(basicBlock);
                    basicBlockBody = basicBlock.getBody();
                    basicBlockBody.add(label);

                    //
                    // Add the node to the basic block.
                    //
                    if (currentNode != label) {
                        basicBlockBody.add(currentNode);
                    }
                }
                else  {
                   basicBlockBody.add(currentNode);
                }
            }
        }

        body.clear();
        body.addAll(basicBlocks);
    }

    // </editor-fold>
}
