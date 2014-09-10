package org.revapi.java.compilation;

import javax.annotation.Nonnull;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
final class IgnoreCompletionFailures {

    private IgnoreCompletionFailures() {

    }

    private static boolean isCompletionFailure(@Nonnull Throwable t) {
        return t.getClass().getName().endsWith("CompletionFailure");
    }

    static <R, T> R call(Fn1<R, T> action, T arg) {
        Throwable fail;

        do {
            try {
                return action.call(arg);
            } catch (Exception e) {
                fail = e;
            }
        } while (isCompletionFailure(fail));

        throw new IllegalStateException(fail);
    }

    static <R, T1, T2> R call(Fn2<R, T1, T2> action,
        T1 arg1, T2 arg2) {
        Throwable fail;

        do {
            try {
                return action.call(arg1, arg2);
            } catch (Exception e) {
                fail = e;
            }
        } while (isCompletionFailure(fail));

        throw new IllegalStateException(fail);
    }

    static <R, T1, T2, T3> R call(Fn3<R, T1, T2, T3> action, T1 arg1, T2 arg2, T3 arg3) {
        Throwable fail;

        do {
            try {
                return action.call(arg1, arg2, arg3);
            } catch (Exception e) {
                fail = e;
            }
        } while (isCompletionFailure(fail));

        throw new IllegalStateException(fail);
    }

    static interface Fn1<R, T> {
        R call(T t) throws Exception;
    }

    static interface Fn2<R, T1, T2> {
        R call(T1 t1, T2 t2) throws Exception;
    }

    static interface Fn3<R, T1, T2, T3> {
        R call(T1 t1, T2 t2, T3 t3) throws Exception;
    }
}
