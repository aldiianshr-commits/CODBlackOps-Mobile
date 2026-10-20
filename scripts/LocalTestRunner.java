import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Runs every public @org.junit.Test method of the given classes; exits non-zero on failure. */
public final class LocalTestRunner {
    public static void main(String[] args) throws Exception {
        int run = 0, failed = 0;
        for (String name : args) {
            Class<?> c = Class.forName(name);
            for (Method m : c.getMethods()) {
                if (!m.isAnnotationPresent(org.junit.Test.class)) continue;
                run++;
                try {
                    m.invoke(c.getDeclaredConstructor().newInstance());
                    System.out.println("PASS " + c.getSimpleName() + "." + m.getName());
                } catch (InvocationTargetException e) {
                    failed++;
                    System.out.println("FAIL " + c.getSimpleName() + "." + m.getName() + ": " + e.getCause());
                    e.getCause().printStackTrace(System.out);
                }
            }
        }
        System.out.println(run + " tests, " + failed + " failed");
        if (failed > 0 || run == 0) System.exit(1);
    }
}
