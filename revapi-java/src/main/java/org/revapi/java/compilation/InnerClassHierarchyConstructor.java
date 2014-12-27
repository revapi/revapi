package org.revapi.java.compilation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import javax.annotation.Nonnull;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
final class InnerClassHierarchyConstructor {
    private final TreeSet<OuterNameInnerNamePair> innerNames = new TreeSet<>();

    public void addName(String outerName, String innerName) {
        innerNames.add(new OuterNameInnerNamePair(outerName, innerName));
    }

    public List<InnerClass> process() {
        if (innerNames.isEmpty()) {
            return Collections.emptyList();
        }

        String rootOwner;

        Iterator<OuterNameInnerNamePair> it = innerNames.iterator();
        OuterNameInnerNamePair names = it.next();

        if (names.outerName != null && names.innerName != null) {
            //only take into account non-anonymous, non-local inner classes
            rootOwner = names.outerName.replace('/', '.');
        } else {
            return Collections.emptyList();
        }

        StringBuilder binaryName = new StringBuilder(rootOwner);
        StringBuilder canonicalName = new StringBuilder(rootOwner);

        ArrayList<InnerClass> result = new ArrayList<>();
        result.add(new InnerClass(binaryName.toString(), canonicalName.toString()));

        do {
            if (names.outerName == null || names.innerName == null) {
                //we only process non-anonymous, non-local inner classes
                return Collections.emptyList();
            }

            String name = names.innerName;

            binaryName.append('$').append(name);
            canonicalName.append('.').append(name);

            result.add(new InnerClass(binaryName.toString(), canonicalName.toString()));

            //yes, the first element needs to be processed twice...
            if (it.hasNext()) {
                names = it.next();
            } else {
                break;
            }
        } while (true);

        return result;
    }

    private static class OuterNameInnerNamePair implements Comparable<OuterNameInnerNamePair> {
        final String outerName;
        final String innerName;

        private OuterNameInnerNamePair(String outerName, String innerName) {
            this.outerName = outerName;
            this.innerName = innerName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            OuterNameInnerNamePair that = (OuterNameInnerNamePair) o;

            if (outerName != null ? !outerName.equals(that.outerName) : that.outerName != null) {
                return false;
            }

            if (innerName != null ? !innerName.equals(that.innerName) : that.innerName != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = outerName != null ? outerName.hashCode() : 0;
            result = 31 * result + (innerName != null ? innerName.hashCode() : 0);
            return result;
        }

        @Override
        public int compareTo(@Nonnull OuterNameInnerNamePair o) {
            int ret = safeCompare(outerName, o.outerName);

            return ret != 0 ? ret : safeCompare(innerName, o.innerName);
        }

        private static int safeCompare(String a, String b) {
            int ret;
            if (a == null) {
                ret = b == null ? 0 : 1;
            } else if (b == null) {
                ret = -1;
            } else {
                ret = a.compareTo(b);
            }

            return ret;
        }
    }
}
