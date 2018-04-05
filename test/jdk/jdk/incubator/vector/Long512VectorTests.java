/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have
 * questions.
 */

/*
 * @test
 * @modules jdk.incubator.vector
 * @run testng Long512VectorTests
 *
 */

import jdk.incubator.vector.Shapes;
import jdk.incubator.vector.Vector;

import jdk.incubator.vector.LongVector;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Test
public class Long512VectorTests extends AbstractVectorTest {

    static final LongVector.LongSpecies<Shapes.S512Bit> SPECIES = (LongVector.LongSpecies<Shapes.S512Bit>)
                Vector.speciesInstance(Long.class, Shapes.S_512_BIT);


    interface FUnOp {
        long apply(long a);
    }

    static void assertArraysEquals(long[] a, long[] r, FUnOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(f.apply(a[i]), r[i]);
            }
        } catch (AssertionError e) {
            Assert.assertEquals(f.apply(a[i]), r[i], "at index #" + i);
        }
    }

    static void assertArraysEquals(long[] a, long[] r, boolean[] mask, FUnOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(mask[i % SPECIES.length()] ? f.apply(a[i]) : a[i], r[i]);
            }
        } catch (AssertionError e) {
            Assert.assertEquals(mask[i % SPECIES.length()] ? f.apply(a[i]) : a[i], r[i], "at index #" + i);
        }
    }

    interface FReductionOp {
      long apply(long[] a, int idx);
    }

    static void assertReductionArraysEquals(long[] a, long[] b, FReductionOp f) {
      int i = 0;
      try {
        for (; i < a.length; i += SPECIES.length()) {
          Assert.assertEquals(f.apply(a, i), b[i]);
        }
      } catch (AssertionError e) {
        Assert.assertEquals(f.apply(a, i), b[i], "at index #" + i);
      }
    }

    interface FBinOp {
        long apply(long a, long b);
    }

    interface FBinMaskOp {
        long apply(long a, long b, boolean m);

        static FBinMaskOp lift(FBinOp f) {
            return (a, b, m) -> m ? f.apply(a, b) : a;
        }
    }

    static void assertArraysEquals(long[] a, long[] b, long[] r, FBinOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(f.apply(a[i], b[i]), r[i]);
            }
        } catch (AssertionError e) {
            Assert.assertEquals(f.apply(a[i], b[i]), r[i], "at index #" + i);
        }
    }

    static void assertArraysEquals(long[] a, long[] b, long[] r, boolean[] mask, FBinOp f) {
        assertArraysEquals(a, b, r, mask, FBinMaskOp.lift(f));
    }

    static void assertArraysEquals(long[] a, long[] b, long[] r, boolean[] mask, FBinMaskOp f) {
        int i = 0;
        try {
            for (; i < a.length; i++) {
                Assert.assertEquals(f.apply(a[i], b[i], mask[i % SPECIES.length()]), r[i]);
            }
        } catch (AssertionError err) {
            Assert.assertEquals(f.apply(a[i], b[i], mask[i % SPECIES.length()]), r[i], "at index #" + i + ", a[i] = " + a[i] + ", b[i] = " + b[i] + ", mask = " + mask[i % SPECIES.length()]);
        }
    }
    
    static final List<IntFunction<long[]>> LONG_GENERATORS = List.of(
            withToString("long[i * 5]", (int s) -> {
                return fill(s * 1000,
                            i -> (long)(i * 5));
            }),
            withToString("long[i + 1]", (int s) -> {
                return fill(s * 1000,
                            i -> (((long)(i + 1) == 0) ? 1 : (long)(i + 1)));
            })
    );

    // Create combinations of pairs
    // @@@ Might be sensitive to order e.g. div by 0
    static final List<List<IntFunction<long[]>>> LONG_GENERATOR_PAIRS =
        Stream.of(LONG_GENERATORS.get(0)).
                flatMap(fa -> LONG_GENERATORS.stream().skip(1).map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] longBinaryOpProvider() {
        return LONG_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] longBinaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> LONG_GENERATOR_PAIRS.stream().map(lfa -> {
                    return Stream.concat(lfa.stream(), Stream.of(fm)).toArray();
                })).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] longUnaryOpProvider() {
        return LONG_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] longUnaryOpMaskProvider() {
        return BOOLEAN_MASK_GENERATORS.stream().
                flatMap(fm -> LONG_GENERATORS.stream().map(fa -> {
                    return new Object[] {fa, fm};
                })).
                toArray(Object[][]::new);
    }

    static final List<IntFunction<long[]>> LONG_COMPARE_GENERATORS = List.of(
            withToString("long[i]", (int s) -> {
                return fill(s * 1000,
                            i -> (long)i);
            }),
            withToString("long[i + 1]", (int s) -> {
                return fill(s * 1000,
                            i -> (long)(i + 1));
            }),
            withToString("long[i - 2]", (int s) -> {
                return fill(s * 1000,
                            i -> (long)(i - 2));
            }),
            withToString("long[zigZag(i)]", (int s) -> {
                return fill(s * 1000,
                            i -> i%3 == 0 ? (long)i : (i%3 == 1 ? (long)(i + 1) : (long)(i - 2)));
            }),
            withToString("long[cornerComparisonCase(i)]", (int s) -> {
                return fill(s * 1000,
                            i -> cornerComparisonCase(i));
            })
    );

    static final List<List<IntFunction<long[]>>> LONG_COMPARE_GENERATOR_PAIRS =
        LONG_COMPARE_GENERATORS.stream().
                flatMap(fa -> LONG_COMPARE_GENERATORS.stream().map(fb -> List.of(fa, fb))).
                collect(Collectors.toList());

    @DataProvider
    public Object[][] longCompareOpProvider() {
        return LONG_COMPARE_GENERATOR_PAIRS.stream().map(List::toArray).
                toArray(Object[][]::new);
    }

    interface ToLongF {
        long apply(int i);
    }

    static long[] fill(int s , ToLongF f) {
        return fill(new long[s], f);
    }

    static long[] fill(long[] a, ToLongF f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }
    
    static long cornerComparisonCase(int i) {
        switch(i % 5) {
            case 0:
                return Long.MAX_VALUE;
            case 1:
                return Long.MIN_VALUE;
            case 2:
                return Long.MIN_VALUE;
            case 3:
                return Long.MAX_VALUE;
            default:
                return (long)0;
        }
    }


    static long add(long a, long b) {
        return (long)(a + b);
    }

    @Test(dataProvider = "longBinaryOpProvider", invocationCount = 10)
    static void addLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());
        long[] r = new long[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            av.add(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Long512VectorTests::add);
    }

    @Test(dataProvider = "longBinaryOpMaskProvider", invocationCount = 10)
    static void addLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb,
                                          IntFunction<boolean[]> fm) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());
        long[] r = new long[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Long, Shapes.S512Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            av.add(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Long512VectorTests::add);
    }

    static long sub(long a, long b) {
        return (long)(a - b);
    }

    @Test(dataProvider = "longBinaryOpProvider", invocationCount = 10)
    static void subLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());
        long[] r = new long[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            av.sub(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Long512VectorTests::sub);
    }

    @Test(dataProvider = "longBinaryOpMaskProvider", invocationCount = 10)
    static void subLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb,
                                          IntFunction<boolean[]> fm) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());
        long[] r = new long[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Long, Shapes.S512Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            av.sub(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Long512VectorTests::sub);
    }




    static long mul(long a, long b) {
        return (long)(a * b);
    }

    @Test(dataProvider = "longBinaryOpProvider", invocationCount = 10)
    static void mulLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());
        long[] r = new long[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            av.mul(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Long512VectorTests::mul);
    }

    @Test(dataProvider = "longBinaryOpMaskProvider", invocationCount = 10)
    static void mulLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb,
                                          IntFunction<boolean[]> fm) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());
        long[] r = new long[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Long, Shapes.S512Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            av.mul(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Long512VectorTests::mul);
    }


    static long and(long a, long b) {
        return (long)(a & b);
    }



    @Test(dataProvider = "longBinaryOpProvider", invocationCount = 10)
    static void andLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());
        long[] r = new long[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            av.and(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Long512VectorTests::and);
    }



    @Test(dataProvider = "longBinaryOpMaskProvider", invocationCount = 10)
    static void andLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb,
                                          IntFunction<boolean[]> fm) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());
        long[] r = new long[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Long, Shapes.S512Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            av.and(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Long512VectorTests::and);
    }



    static long or(long a, long b) {
        return (long)(a | b);
    }



    @Test(dataProvider = "longBinaryOpProvider", invocationCount = 10)
    static void orLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());
        long[] r = new long[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            av.or(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Long512VectorTests::or);
    }



    @Test(dataProvider = "longBinaryOpMaskProvider", invocationCount = 10)
    static void orLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb,
                                          IntFunction<boolean[]> fm) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());
        long[] r = new long[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Long, Shapes.S512Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            av.or(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Long512VectorTests::or);
    }



    static long xor(long a, long b) {
        return (long)(a ^ b);
    }



    @Test(dataProvider = "longBinaryOpProvider", invocationCount = 10)
    static void xorLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());
        long[] r = new long[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            av.xor(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Long512VectorTests::xor);
    }



    @Test(dataProvider = "longBinaryOpMaskProvider", invocationCount = 10)
    static void xorLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb,
                                          IntFunction<boolean[]> fm) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());
        long[] r = new long[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Long, Shapes.S512Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            av.xor(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Long512VectorTests::xor);
    }


    static long max(long a, long b) {
        return (long)(Math.max(a, b));
    }

    @Test(dataProvider = "longBinaryOpProvider", invocationCount = 10)
    static void maxLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());
        long[] r = new long[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            av.max(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Long512VectorTests::max);
    }

    static long min(long a, long b) {
        return (long)(Math.min(a, b));
    }

    @Test(dataProvider = "longBinaryOpProvider", invocationCount = 10)
    static void minLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());
        long[] r = new long[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            av.min(bv).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, Long512VectorTests::min);
    }

    static long andAll(long[] a, int idx) {
        long res = -1;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
          res &= a[i];
        }

        return res;
    }



    @Test(dataProvider = "longUnaryOpProvider", invocationCount = 10)
    static void andAllLong512VectorTests(IntFunction<long[]> fa) {
      long[] a = fa.apply(SPECIES.length());
      long[] r = new long[a.length];

      for (int i = 0; i < a.length; i += SPECIES.length()) {
        LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
        r[i] = av.andAll();
      }

      assertReductionArraysEquals(a, r, Long512VectorTests::andAll);
    }



    static long orAll(long[] a, int idx) {
        long res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
          res |= a[i];
        }

        return res;
    }



    @Test(dataProvider = "longUnaryOpProvider", invocationCount = 10)
    static void orAllLong512VectorTests(IntFunction<long[]> fa) {
      long[] a = fa.apply(SPECIES.length());
      long[] r = new long[a.length];

      for (int i = 0; i < a.length; i += SPECIES.length()) {
        LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
        r[i] = av.orAll();
      }

      assertReductionArraysEquals(a, r, Long512VectorTests::orAll);
    }



    static long xorAll(long[] a, int idx) {
        long res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
          res ^= a[i];
        }

        return res;
    }



    @Test(dataProvider = "longUnaryOpProvider", invocationCount = 10)
    static void xorAllLong512VectorTests(IntFunction<long[]> fa) {
      long[] a = fa.apply(SPECIES.length());
      long[] r = new long[a.length];

      for (int i = 0; i < a.length; i += SPECIES.length()) {
        LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
        r[i] = av.xorAll();
      }

      assertReductionArraysEquals(a, r, Long512VectorTests::xorAll);
    }


    static long subAll(long[] a, int idx) {
        long res = 0;
        for (int i = idx; i < (idx + SPECIES.length()); i++) {
          res -= a[i];
        }

        return res;
    }

    @Test(dataProvider = "longUnaryOpProvider", invocationCount = 10)
    static void subAllLong512VectorTests(IntFunction<long[]> fa) {
      long[] a = fa.apply(SPECIES.length());
      long[] r = new long[a.length];

      for (int i = 0; i < a.length; i += SPECIES.length()) {
        LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
        r[i] = av.subAll();
      }

      assertReductionArraysEquals(a, r, Long512VectorTests::subAll);
    }


    @Test(dataProvider = "longCompareOpProvider", invocationCount = 10)
    static void lessThanLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            Vector.Mask<Long, Shapes.S512Bit> mv = av.lessThan(bv);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.getElement(j), a[i + j] < b[i + j]);
            }
        }
    }
    @Test(dataProvider = "longCompareOpProvider", invocationCount = 10)
    static void greaterThanLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            Vector.Mask<Long, Shapes.S512Bit> mv = av.greaterThan(bv);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.getElement(j), a[i + j] > b[i + j]);
            }
        }
    }
    @Test(dataProvider = "longCompareOpProvider", invocationCount = 10)
    static void equalLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            Vector.Mask<Long, Shapes.S512Bit> mv = av.equal(bv);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.getElement(j), a[i + j] == b[i + j]);
            }
        }
    }
    @Test(dataProvider = "longCompareOpProvider", invocationCount = 10)
    static void notEqualLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            Vector.Mask<Long, Shapes.S512Bit> mv = av.notEqual(bv);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.getElement(j), a[i + j] != b[i + j]);
            }
        }
    }
    @Test(dataProvider = "longCompareOpProvider", invocationCount = 10)
    static void lessThanEqLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            Vector.Mask<Long, Shapes.S512Bit> mv = av.lessThanEq(bv);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.getElement(j), a[i + j] <= b[i + j]);
            }
        }
    }
    @Test(dataProvider = "longCompareOpProvider", invocationCount = 10)
    static void greaterThanEqLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            Vector.Mask<Long, Shapes.S512Bit> mv = av.greaterThanEq(bv);

            // Check results as part of computation.
            for (int j = 0; j < SPECIES.length(); j++) {
                Assert.assertEquals(mv.getElement(j), a[i + j] >= b[i + j]);
            }
        }
    }
    static long blend(long a, long b, boolean mask) {
        return mask ? b : a;
    }

    @Test(dataProvider = "longBinaryOpMaskProvider", invocationCount = 10)
    static void blendLong512VectorTests(IntFunction<long[]> fa, IntFunction<long[]> fb,
                                          IntFunction<boolean[]> fm) {
        long[] a = fa.apply(SPECIES.length());
        long[] b = fb.apply(SPECIES.length());
        long[] r = new long[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Long, Shapes.S512Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            LongVector<Shapes.S512Bit> bv = SPECIES.fromArray(b, i);
            av.blend(bv, vmask).intoArray(r, i);
        }

        assertArraysEquals(a, b, r, mask, Long512VectorTests::blend);
    }

    static long neg(long a) {
        return (long)(-((long)a));
    }

    @Test(dataProvider = "longUnaryOpProvider", invocationCount = 10)
    static void negLong512VectorTests(IntFunction<long[]> fa) {
        long[] a = fa.apply(SPECIES.length());
        long[] r = new long[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            av.neg().intoArray(r, i);
        }

        assertArraysEquals(a, r, Long512VectorTests::neg);
    }

    @Test(dataProvider = "longUnaryOpMaskProvider")
    static void negMaskedLong512VectorTests(IntFunction<long[]> fa,
                                                IntFunction<boolean[]> fm) {
        long[] a = fa.apply(SPECIES.length());
        long[] r = new long[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Long, Shapes.S512Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            av.neg(vmask).intoArray(r, i);
        }

        assertArraysEquals(a, r, mask, Long512VectorTests::neg);
    }

    static long abs(long a) {
        return (long)(Math.abs((long)a));
    }

    @Test(dataProvider = "longUnaryOpProvider", invocationCount = 10)
    static void absLong512VectorTests(IntFunction<long[]> fa) {
        long[] a = fa.apply(SPECIES.length());
        long[] r = new long[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            av.abs().intoArray(r, i);
        }

        assertArraysEquals(a, r, Long512VectorTests::abs);
    }

    @Test(dataProvider = "longUnaryOpMaskProvider")
    static void absMaskedLong512VectorTests(IntFunction<long[]> fa,
                                                IntFunction<boolean[]> fm) {
        long[] a = fa.apply(SPECIES.length());
        long[] r = new long[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Long, Shapes.S512Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            av.abs(vmask).intoArray(r, i);
        }

        assertArraysEquals(a, r, mask, Long512VectorTests::abs);
    }


    static long not(long a) {
        return (long)(~((long)a));
    }



    @Test(dataProvider = "longUnaryOpProvider", invocationCount = 10)
    static void notLong512VectorTests(IntFunction<long[]> fa) {
        long[] a = fa.apply(SPECIES.length());
        long[] r = new long[a.length];

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            av.not().intoArray(r, i);
        }

        assertArraysEquals(a, r, Long512VectorTests::not);
    }



    @Test(dataProvider = "longUnaryOpMaskProvider")
    static void notMaskedLong512VectorTests(IntFunction<long[]> fa,
                                                IntFunction<boolean[]> fm) {
        long[] a = fa.apply(SPECIES.length());
        long[] r = new long[a.length];
        boolean[] mask = fm.apply(SPECIES.length());
        Vector.Mask<Long, Shapes.S512Bit> vmask = SPECIES.maskFromValues(mask);

        // Computation.
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            LongVector<Shapes.S512Bit> av = SPECIES.fromArray(a, i);
            av.not(vmask).intoArray(r, i);
        }

        assertArraysEquals(a, r, mask, Long512VectorTests::not);
    }

}

