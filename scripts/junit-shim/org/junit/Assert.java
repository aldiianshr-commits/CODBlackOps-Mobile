package org.junit;

import java.util.Objects;

/** Minimal stand-in for JUnit 4's Assert, used only by scripts/local_test.sh. */
public final class Assert {
    private Assert() {
    }

    public static void assertTrue(boolean c) { assertTrue(null, c); }
    public static void assertTrue(String m, boolean c) { if (!c) throw new AssertionError(m == null ? "expected true" : m); }
    public static void assertFalse(boolean c) { assertFalse(null, c); }
    public static void assertFalse(String m, boolean c) { if (c) throw new AssertionError(m == null ? "expected false" : m); }
    public static void assertNull(Object o) { if (o != null) throw new AssertionError("expected null but was " + o); }
    public static void assertNotNull(Object o) { if (o == null) throw new AssertionError("expected non-null"); }
    public static void assertEquals(Object e, Object a) { assertEquals(null, e, a); }
    public static void assertEquals(String m, Object e, Object a) {
        if (!Objects.equals(e, a)) throw new AssertionError((m == null ? "" : m + ": ") + "expected <" + e + "> but was <" + a + ">");
    }
    public static void assertEquals(long e, long a) { assertEquals(null, e, a); }
    public static void assertEquals(String m, long e, long a) {
        if (e != a) throw new AssertionError((m == null ? "" : m + ": ") + "expected <" + e + "> but was <" + a + ">");
    }
    public static void assertEquals(double e, double a, double d) { assertEquals(null, e, a, d); }
    public static void assertEquals(String m, double e, double a, double d) {
        if (Double.compare(e, a) != 0 && !(Math.abs(e - a) <= d))
            throw new AssertionError((m == null ? "" : m + ": ") + "expected <" + e + "> but was <" + a + ">");
    }
    public static void assertEquals(float e, float a, float d) { assertEquals(null, e, a, d); }
    public static void assertEquals(String m, float e, float a, float d) {
        if (Float.compare(e, a) != 0 && !(Math.abs(e - a) <= d))
            throw new AssertionError((m == null ? "" : m + ": ") + "expected <" + e + "> but was <" + a + ">");
    }
}
