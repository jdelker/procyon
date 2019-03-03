/*
 * LineSorter.java
 *
 * Copyright (c) 2019 Joerg Delker
 *
 * This source code is subject to terms and conditions of the Apache License, Version 2.0.
 * A copy of the license can be found in the License.html file at the root of this distribution.
 * By using this source code in any fashion, you are agreeing to be bound by the terms of the
 * Apache License, Version 2.0.
 *
 * You must not remove this notice, or any other, from this software.
 */
package com.strobel.decompiler.utilities;

import com.strobel.assembler.ir.attributes.AttributeNames;
import com.strobel.assembler.ir.attributes.LineNumberTableAttribute;
import com.strobel.assembler.ir.attributes.SourceAttribute;
import com.strobel.assembler.metadata.MemberReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.core.VerifyArgument;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/**
 *
 * @author delker
 */
public class LineSorter {

    private final List<MemberReference> memberList;
    private final IdentityHashMap<MemberReference, Integer> memberPos;

    public LineSorter() {
        memberList = new ArrayList<>();
        memberPos = new IdentityHashMap<>();
    }

    public void add(MemberReference ref) {
        VerifyArgument.notNull(ref, "ref");

        int lineNumber = getLineNumber(ref);

        if (lineNumber == Integer.MAX_VALUE) {
            // simply append
            memberList.add(ref);
        } else {
            // insert element just before any higher line number element
            int i = 0;
            while (i < memberList.size()
                    && (!memberPos.containsKey(memberList.get(i))
                    || memberPos.get(memberList.get(i)) < lineNumber)) {
                i++;
            }
            memberList.add(i, ref);
            memberPos.put(ref, lineNumber);
        }
    }

    public List<MemberReference> getList() {
        return Collections.unmodifiableList(memberList);
    }

    private int getLineNumber(MemberReference ref) {
        int lineNumber = Integer.MAX_VALUE;

        if (ref instanceof TypeDefinition) {
            lineNumber = findFirstLineNumber((TypeDefinition) ref);
        } else if (ref instanceof MethodDefinition) {
            lineNumber = findLineNumber((MethodDefinition) ref);
        }

        return lineNumber;
    }

    private static Integer findFirstLineNumber(final TypeDefinition def) {
        int minLineNumber = Integer.MAX_VALUE;

        for (final MethodDefinition method : def.getDeclaredMethods()) {
            final int firstLineNumber = findLineNumber(method);

            if (firstLineNumber < minLineNumber) {
                minLineNumber = firstLineNumber;
            }
        }

        return minLineNumber;
    }

    private static Integer findLineNumber(final MethodDefinition def) {
        int lineNumber = Integer.MAX_VALUE;
        final LineNumberTableAttribute attribute
                = SourceAttribute.find(AttributeNames.LineNumberTable, def.getSourceAttributes());
        if (attribute != null && !attribute.getEntries().isEmpty()) {
            lineNumber = attribute.getEntries().get(0).getLineNumber();
        }
        return lineNumber;
    }
}
