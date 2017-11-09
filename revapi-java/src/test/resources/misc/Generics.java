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
public class Generics<T extends GenericsParams.TypeVar & GenericsParams.TypeVarIface, U extends Generics<GenericsParams.TypeVarImpl, ?>> {

    public java.util.Set<GenericsParams.TypeParam> field;

    public <X extends U> X method1() {return null;}

    public void method2(java.util.Set<? super GenericsParams.SuperBound> x) {}

    public <X extends T> X method3() {return null;}

    public <X extends GenericsParams.ExtendsBound> void method4(java.util.Set<X> x) {}
}
