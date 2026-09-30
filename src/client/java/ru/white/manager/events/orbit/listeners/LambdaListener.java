package ru.white.manager.events.orbit.listeners;

import ru.white.manager.events.orbit.EventHandler;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class LambdaListener implements IListener {

    public interface Factory {
        MethodHandles.Lookup create(Method lookupInMethod, Class<?> klass) throws ReflectiveOperationException;
    }

    private static final LookupFactory LOOKUP_FACTORY;
    private static final Map<Method, MethodHandle> methodHandleCache = new ConcurrentHashMap<>();

    private final Class<?> target;
    private final boolean isStatic;
    private final int priority;
    private final Consumer<Object> executor;
    public final Class<?> owner;

    public LambdaListener(LambdaListener.Factory factory, Class<?> klass, Object object, Method method) {
        this.target = method.getParameters()[0].getType();
        this.isStatic = Modifier.isStatic(method.getModifiers());
        this.priority = method.getAnnotation(EventHandler.class).priority();
        this.owner = klass;

        try {
            this.executor = isStatic ? (Consumer<Object>) staticLambdaFactory(factory, klass, method).invoke()
                    : (Consumer<Object>) instanceLambdaFactory(factory, klass, method).invoke(object);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    private static MethodHandle staticLambdaFactory(LambdaListener.Factory factory, Class<?> klass, Method method) throws Throwable {
        String name = method.getName();
        MethodHandles.Lookup lookup = LOOKUP_FACTORY.of(factory, klass);

        MethodType methodType = MethodType.methodType(void.class, method.getParameters()[0].getType());

        MethodHandle methodHandle = lookup.findStatic(klass, name, methodType);
        MethodType invokedType = MethodType.methodType(Consumer.class);

        return LambdaMetafactory.metafactory(lookup, "accept", invokedType, MethodType.methodType(void.class, Object.class), methodHandle, methodType).getTarget();
    }

    private static MethodHandle instanceLambdaFactory(LambdaListener.Factory factory, Class<?> klass, Method method) throws Throwable {
        MethodHandle lambdaFactory = methodHandleCache.get(method);
        if (lambdaFactory != null) return lambdaFactory;

        String name = method.getName();
        MethodHandles.Lookup lookup = LOOKUP_FACTORY.of(factory, klass);

        MethodType methodType = MethodType.methodType(void.class, method.getParameters()[0].getType());

        MethodHandle methodHandle = lookup.findVirtual(klass, name, methodType);
        MethodType invokedType = MethodType.methodType(Consumer.class, klass);

        lambdaFactory = LambdaMetafactory.metafactory(lookup, "accept", invokedType, MethodType.methodType(void.class, Object.class), methodHandle, methodType).getTarget();
        methodHandleCache.put(method, lambdaFactory);
        return lambdaFactory;
    }

    @Override
    public void call(Object event) {
        executor.accept(event);
    }

    @Override
    public Class<?> getTarget() {
        return target;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean isStatic() {
        return isStatic;
    }

    public static boolean requireLambdaFactoryRegistration() {
        return LOOKUP_FACTORY.requireLambdaFactory;
    }

    private static abstract class LookupFactory {
        public final boolean requireLambdaFactory;

        public LookupFactory(boolean requireLambdaFactory) {
            this.requireLambdaFactory = requireLambdaFactory;
        }

        public abstract MethodHandles.Lookup of(LambdaListener.Factory lambdaFactory, Class<?> klass) throws ReflectiveOperationException;
    }

    static {
        LookupFactory lookupFactory;

        try {
            try {
                Method privateLookupInMethod = MethodHandles.class.getDeclaredMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
                lookupFactory = new LookupFactory(true) {
                    @Override
                    public MethodHandles.Lookup of(LambdaListener.Factory lambdaFactory, Class<?> klass) throws ReflectiveOperationException {
                        return lambdaFactory.create(privateLookupInMethod, klass);
                    }
                };
            } catch (NoSuchMethodException e) {
                Constructor<MethodHandles.Lookup> lookupConstructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class);
                lookupConstructor.setAccessible(true);
                lookupFactory = new LookupFactory(false) {
                    @Override
                    public MethodHandles.Lookup of(LambdaListener.Factory lambdaFactory, Class<?> klass) throws ReflectiveOperationException {
                        return lookupConstructor.newInstance(klass);
                    }
                };
            }
        } catch (ReflectiveOperationException | SecurityException e) {
            lookupFactory = new LookupFactory(false) {
                @Override
                public MethodHandles.Lookup of(LambdaListener.Factory lambdaFactory, Class<?> klass) {
                    return MethodHandles.publicLookup().in(klass);
                }
            };
        }

        LOOKUP_FACTORY = lookupFactory;
    }
}
