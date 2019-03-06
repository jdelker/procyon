/*
 * AspectJUnweaveUtilities.java
 *
 * Copyright (c) 2019 Joerg Delker
 *
 * This source code is based on DeobfuscationUtilities from Mike Strobel, 
 * Copyright (c) Mike Strobel; Mono.Cecil from Jb Evain, Copyright (c) Jb Evain;
 * and ILSpy/ICSharpCode from SharpDevelop, Copyright (c) AlphaSierraPapa.
 *
 * This source code is subject to terms and conditions of the Apache License, Version 2.0.
 * A copy of the license can be found in the License.html file at the root of this distribution.
 * By using this source code in any fashion, you are agreeing to be bound by the terms of the
 * Apache License, Version 2.0.
 *
 * You must not remove this notice, or any other, from this software.
 */
package com.strobel.assembler.metadata;

import com.strobel.annotations.NotNull;
import com.strobel.assembler.ir.attributes.AttributeNames;
import com.strobel.assembler.ir.attributes.SourceAttribute;
import com.strobel.core.VerifyArgument;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class file pre-processor to remove some AspectJ stuff that gets in the way
 * for proper element sorting.
 */
public class AspectJUnweaveUtilities {

    private final static Logger LOG = Logger.getLogger(AspectJUnweaveUtilities.class.getSimpleName());
    public final static String ASPECTJ_PREFIX = "ajc$";

    public static void processType(@NotNull final TypeDefinition type) {
        VerifyArgument.notNull(type, "type");

        processFields(type);
        processMethods(type);
        processNestedTypes(type);
    }

    private static void processFields(final TypeDefinition type) {
        List<FieldDefinition> fieldDefs = type.getDeclaredFieldsInternal();

        if (fieldDefs != null) {
            for (Iterator<FieldDefinition> it = fieldDefs.iterator(); it.hasNext();) {
                FieldDefinition fd = it.next();

                // remove field if it is synthetic
                if (SourceAttribute.find(AttributeNames.Synthetic, fd.getSourceAttributes()) != null) {
                    LOG.log(Level.FINE, "removing field: {0}", fd.getName());
                    it.remove();
                }
            }
        }
    }

    private static void processMethods(TypeDefinition type) {
        List<MethodDefinition> methodDefs = type.getDeclaredMethodsInternal();
        if (methodDefs != null) {
            for (Iterator<MethodDefinition> it = methodDefs.iterator(); it.hasNext();) {
                MethodDefinition md = it.next();
                String methodName = md.getName();

                // remove aspectj methods
                if (methodName.startsWith(ASPECTJ_PREFIX)) {
                    LOG.log(Level.FINE, "removing method: {0}", md.getName());
                    it.remove();
                }
            }
        }
    }

    private static void processNestedTypes(TypeDefinition type) {
        List<TypeDefinition> nestedTypes = type.getDeclaredTypes();
        for (TypeDefinition def : nestedTypes) {
            processType(def);
        }
    }
}
