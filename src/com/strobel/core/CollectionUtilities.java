/*
 * CollectionUtilities.java
 *
 * Copyright (c) 2012 Mike Strobel
 *
 * This source code is subject to terms and conditions of the Apache License, Version 2.0.
 * A copy of the license can be found in the License.html file at the root of this distribution.
 * By using this source code in any fashion, you are agreeing to be bound by the terms of the
 * Apache License, Version 2.0.
 *
 * You must not remove this notice, or any other, from this software.
 */

package com.strobel.core;

import com.strobel.core.delegates.Func1;

import java.util.Iterator;
import java.util.List;

/**
 * @author Mike Strobel
 */
public final class CollectionUtilities {
    public static <T> T first(final Iterable<T> collection) {
        final Iterator<T> it = VerifyArgument.notNull(collection, "collection").iterator();
        if (it.hasNext()) {
            return it.next();
        }
        throw Error.sequenceHasNoElements();
    }

    public static <T> T firstOrDefault(final Iterable<T> collection) {
        final Iterator<T> it = VerifyArgument.notNull(collection, "collection").iterator();
        return it.hasNext() ? it.next() : null;
    }

    public static <T> boolean all(final Iterable<T> collection, final Func1<? super T, Boolean> predicate) {
        VerifyArgument.notNull(collection, "collection");
        VerifyArgument.notNull(predicate, "predicate");

        for (final T t : collection) {
            if (!predicate.apply(t)) {
                return false;
            }
        }

        return true;
    }

    public static int hashCode(final List<?> sequence) {
        VerifyArgument.notNull(sequence, "sequence");

        int hashCode = HashUtilities.NullHashCode;

        for (int i = 0; i < sequence.size(); i++) {
            final Object item = sequence.get(i);

            final int itemHashCode;

            if (item instanceof Iterable<?>) {
                itemHashCode = hashCode((Iterable<?>) item);
            }
            else {
                itemHashCode = item != null ? HashUtilities.hashCode(item)
                                            : HashUtilities.NullHashCode;
            }

            hashCode = HashUtilities.combineHashCodes(
                hashCode,
                itemHashCode
            );
        }

        return hashCode;
    }

    public static int hashCode(final Iterable<?> sequence) {
        if (sequence instanceof List<?>) {
            return hashCode((List<?>) sequence);
        }

        VerifyArgument.notNull(sequence, "sequence");

        int hashCode = HashUtilities.NullHashCode;

        for (final Object item : sequence) {
            final int itemHashCode;

            if (item instanceof Iterable<?>) {
                itemHashCode = hashCode((Iterable<?>) item);
            }
            else {
                itemHashCode = item != null ? HashUtilities.hashCode(item)
                                            : HashUtilities.NullHashCode;
            }

            hashCode = HashUtilities.combineHashCodes(
                hashCode,
                itemHashCode
            );
        }

        return hashCode;
    }

    public static <T> boolean sequenceEquals(final List<? extends T> first, final List<? extends T> second) {
        VerifyArgument.notNull(first, "first");
        VerifyArgument.notNull(second, "second");

        if (first == second) {
            return true;
        }

        if (first.size() != second.size()) {
            return false;
        }

        if (first.isEmpty()) {
            return true;
        }

        for (int i = 0, n = first.size(); i < n; i++) {
            if (!Comparer.equals(first.get(i), second.get(i))) {
                return false;
            }
        }

        return true;
    }
    public static <T> boolean sequenceEquals(final Iterable<? extends T> first, final Iterable<? extends T> second) {
        VerifyArgument.notNull(first, "first");
        VerifyArgument.notNull(second, "second");

        if (first == second) {
            return true;
        }

        if (first instanceof List<?> && second instanceof List<?>) {
            return sequenceDeepEquals((List<?>) first, (List<?>) second);
        }

        final Iterator<? extends T> firstIterator = first.iterator();
        final Iterator<? extends T> secondIterator = second.iterator();

        while (firstIterator.hasNext()) {
            if (!secondIterator.hasNext()) {
                return false;
            }

            if (!Comparer.equals(firstIterator.next(), secondIterator.next())) {
                return false;
            }
        }

        return !secondIterator.hasNext();
    }

    public static <T> boolean sequenceDeepEquals(final List<? extends T> first, final List<? extends T> second) {
        VerifyArgument.notNull(first, "first");
        VerifyArgument.notNull(second, "second");

        if (first == second) {
            return true;
        }

        if (first.size() != second.size()) {
            return false;
        }

        if (first.isEmpty()) {
            return true;
        }

        for (int i = 0, n = first.size(); i < n; i++) {
            if (!sequenceDeepEqualsCore(first.get(i), second.get(i))) {
                return false;
            }
        }

        return true;
    }

    public static <T> boolean sequenceDeepEquals(final Iterable<? extends T> first, final Iterable<? extends T> second) {
        VerifyArgument.notNull(first, "first");
        VerifyArgument.notNull(second, "second");

        if (first == second) {
            return true;
        }

        if (first instanceof List<?> && second instanceof List<?>) {
            return sequenceDeepEquals((List<?>) first, (List<?>) second);
        }

        final Iterator<? extends T> firstIterator = first.iterator();
        final Iterator<? extends T> secondIterator = second.iterator();

        while (firstIterator.hasNext()) {
            if (!secondIterator.hasNext()) {
                return false;
            }

            if (!sequenceDeepEqualsCore(firstIterator.next(), secondIterator.next())) {
                return false;
            }
        }

        return !secondIterator.hasNext();
    }

    private static boolean sequenceDeepEqualsCore(final Object first, final Object second) {
        if (first instanceof List<?>) {
            return second instanceof List<?> &&
                   sequenceDeepEquals((List<?>) first, (List<?>) second);
        }
        return Comparer.deepEquals(first, second);
    }
}
