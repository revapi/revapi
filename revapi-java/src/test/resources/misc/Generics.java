/*
 * Copyright 2014-2025 Lukas Krejci
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
package a;

import sup.GenericsParams.ExtendsBound;
import sup.GenericsParams.SuperBound;
import sup.GenericsParams.TypeVar;
import sup.GenericsParams.TypeVarIface;
import sup.GenericsParams.TypeVarImpl;
import sup.GenericsParams.TypeParam;

/*
 * Copyright 2014-2017 Lukas Krejci
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
public class Generics<T extends TypeVar & TypeVarIface, U extends Generics<TypeVarImpl, ?>> {

    public java.util.Set<TypeParam> field;

    public <X extends U> X method1() {return null;}

    public void method2(java.util.Set<? super SuperBound> x) {}

    public <X extends T> X method3() {return null;}

    public <X extends ExtendsBound> void method4(java.util.Set<X> x) {}
}
