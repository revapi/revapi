/*
 * Copyright 2014-2020 Lukas Krejci
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

public interface ToStrings {

    void methodWithTypeParamsInMethodParams(int i, java.util.function.Function<String, ?> f, java.util.HashMap<?, ?> h);

    public enum Enums {
        A, B;
        public void customMethod() {}
    }

    class Generic<T extends U, U extends Enum<U>, E extends Throwable> {
        public <X extends Enum<U> & Cloneable, Y extends Set<X>> X m1(U arg1, Map<Comparator<? super T>, String> arg2, X arg3) throws E {
            return null;
        }

        public E m2(U arg) {
            return null;
        }

        class Inner<I extends U> {
            public T m1(I arg) throws E {
                return null;
            }
        }
    }
}
