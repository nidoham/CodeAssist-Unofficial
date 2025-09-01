package dev.mutwakil.javac;
import com.sun.source.util.Trees;
import java.lang.reflect.Method;

public final class JavacTreesUtil {

    private JavacTreesUtil() {}   // utility class – no instances

    /**
     * Obtain a Trees instance reflectively, independent of the class-loader
     * that loaded tools.jar.
     *
     * @param argType expected parameter type (JavacTask or ProcessingEnvironment)
     * @param arg     actual argument instance
     * @return        a Trees instance for the current compiler
     */
    public static Trees getJavacTrees(Class<?> argType, Object arg) throws Exception {
        try {
            ClassLoader cl = arg.getClass().getClassLoader();
            Class<?> javacTrees = Class.forName("com.sun.tools.javac.api.JavacTrees", false, cl);
            argType = Class.forName(argType.getName(), false, cl);   // reload in the right CL
            Method m = javacTrees.getMethod("instance", argType);
            return (Trees) m.invoke(null, arg);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
