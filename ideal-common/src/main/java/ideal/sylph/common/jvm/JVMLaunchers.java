package ideal.sylph.common.jvm;

import java.io.Serializable;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public class JVMLaunchers
{
    private JVMLaunchers() {}

    public static class VmBuilder<T extends Serializable>
    {
        private VmCallable<T> callable;
        private Consumer<String> consoleHandler;
        private final List<URL> tmpJars = new ArrayList<>();

        public VmBuilder<T> setCallable(VmCallable<T> callable)
        {
            this.callable = requireNonNull(callable, "callable is null");
            return this;
        }

        public VmBuilder<T> setConsole(Consumer<String> consoleHandler)
        {
            this.consoleHandler = requireNonNull(consoleHandler, "consoleHandler is null");
            return this;
        }

        public VmBuilder<T> addUserURLClassLoader(URLClassLoader vmClassLoader)
        {
            ClassLoader classLoader = vmClassLoader;
            while (classLoader instanceof URLClassLoader) {
                Collections.addAll(tmpJars, ((URLClassLoader) classLoader).getURLs());
                classLoader = classLoader.getParent();
            }
            return this;
        }

        public VmBuilder<T> addUserjars(Collection<URL> jars)
        {
            tmpJars.addAll(jars);
            return this;
        }

        public JVMLauncher<T> build()
        {
            return new JVMLauncher<T>(callable, consoleHandler, tmpJars);
        }
    }

    public static <T extends Serializable> VmBuilder<T> newJvm()
    {
        return new VmBuilder<T>();
    }
}
