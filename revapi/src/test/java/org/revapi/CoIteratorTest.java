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
package org.revapi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class CoIteratorTest {

    @Test
    public void testSameCollections() throws Exception {
        List<Integer> l1 = Arrays.asList(1, 2, 3);

        CoIterator<Integer> coit = new CoIterator<>(l1.iterator(), l1.iterator());

        int cnt = 0;
        while (coit.hasNext()) {
            coit.next();
            int l = coit.getLeft();
            int r = coit.getRight();

            Assert.assertEquals(l, r);
            cnt++;
        }

        Assert.assertEquals(l1.size(), cnt);
    }

    @Test
    public void testDifferentSizes() throws Exception {
        List<Integer> l1 = Arrays.asList(1, 2, 3);
        List<Integer> l2 = Arrays.asList(1, 2, 3, 4);

        CoIterator<Integer> coit = new CoIterator<>(l1.iterator(), l2.iterator());

        int cnt = 0;
        while (coit.hasNext()) {
            coit.next();
            Integer l = coit.getLeft();
            Integer r = coit.getRight();

            Assert.assertNotNull(r);
            if (r == 4) {
                Assert.assertNull(l);
            } else {
                Assert.assertEquals(l, r);
            }

            cnt++;
        }

        Assert.assertEquals(l2.size(), cnt);

        List<Integer> tmp = l1;
        l1 = l2;
        l2 = tmp;

        coit = new CoIterator<>(l1.iterator(), l2.iterator());

        cnt = 0;
        while (coit.hasNext()) {
            coit.next();
            Integer l = coit.getLeft();
            Integer r = coit.getRight();

            Assert.assertNotNull(l);
            if (l == 4) {
                Assert.assertNull(r);
            } else {
                Assert.assertEquals(l, r);
            }

            cnt++;
        }

        Assert.assertEquals(l1.size(), cnt);

    }

    @Test
    public void testOneOrBothEmpty() throws Exception {
        List<Integer> l1 = Arrays.asList();
        List<Integer> l2 = Arrays.asList(1, 2, 3, 4);

        CoIterator<Integer> coit = new CoIterator<>(l1.iterator(), l2.iterator());

        int cnt = 0;
        while (coit.hasNext()) {
            coit.next();
            Integer l = coit.getLeft();
            Integer r = coit.getRight();

            Assert.assertNotNull(r);
            Assert.assertNull(l);

            cnt++;
        }

        Assert.assertEquals(l2.size(), cnt);

        List<Integer> tmp = l1;
        l1 = l2;
        l2 = tmp;

        coit = new CoIterator<>(l1.iterator(), l2.iterator());

        cnt = 0;
        while (coit.hasNext()) {
            coit.next();
            Integer l = coit.getLeft();
            Integer r = coit.getRight();

            Assert.assertNotNull(l);
            Assert.assertNull(r);

            cnt++;
        }

        Assert.assertEquals(l1.size(), cnt);

        List<Integer> empty = Collections.emptyList();

        coit = new CoIterator<>(empty.iterator(), empty.iterator());

        Assert.assertFalse(coit.hasNext());

        try {
            coit.next();
            Assert.fail();
        } catch (NoSuchElementException e) {
            //expected
        }
    }

    @Test
    public void testOrdering() throws Exception {
        List<Integer> l1 = Arrays.asList(1, 2, 3, 3);
        List<Integer> l2 = Arrays.asList(0, 2, 4, 4, 5);

        CoIterator<Integer> coit = new CoIterator<>(l1.iterator(), l2.iterator());

        coit.next();
        Assert.assertNull(coit.getLeft());
        Assert.assertEquals(Integer.valueOf(0), coit.getRight());

        coit.next();
        Assert.assertEquals(Integer.valueOf(1), coit.getLeft());
        Assert.assertNull(coit.getRight());

        coit.next();
        Assert.assertEquals(Integer.valueOf(2), coit.getLeft());
        Assert.assertEquals(Integer.valueOf(2), coit.getRight());

        coit.next();
        Assert.assertEquals(Integer.valueOf(3), coit.getLeft());
        Assert.assertNull(coit.getRight());

        coit.next();
        Assert.assertEquals(Integer.valueOf(3), coit.getLeft());
        Assert.assertNull(coit.getRight());

        coit.next();
        Assert.assertNull(coit.getLeft());
        Assert.assertEquals(Integer.valueOf(4), coit.getRight());

        coit.next();
        Assert.assertNull(coit.getLeft());
        Assert.assertEquals(Integer.valueOf(4), coit.getRight());

        coit.next();
        Assert.assertNull(coit.getLeft());
        Assert.assertEquals(Integer.valueOf(5), coit.getRight());
    }
}
