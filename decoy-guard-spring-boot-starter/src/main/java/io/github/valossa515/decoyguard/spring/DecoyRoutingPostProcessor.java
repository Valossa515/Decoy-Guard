package io.github.valossa515.decoyguard.spring;

import io.github.valossa515.decoyguard.core.DuressContextHolder;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Finds {@link Decoy} beans, hides them from autowiring and wraps the matching real beans
 * in a proxy that picks the real or the decoy implementation per call.
 * Decoys are hidden even when routing is disabled, so a decoy can never be injected by accident.
 */
public class DecoyRoutingPostProcessor implements BeanFactoryPostProcessor, BeanPostProcessor {

    /** Routed interface to the bean name of its decoy. */
    private final Map<Class<?>, String> decoyByInterface = new LinkedHashMap<>();
    private final boolean routingEnabled;
    private ConfigurableListableBeanFactory beanFactory;

    /**
     * @param routingEnabled when {@code false}, decoys are still hidden from autowiring
     *                       but calls are never routed to them
     */
    public DecoyRoutingPostProcessor(boolean routingEnabled) {
        this.routingEnabled = routingEnabled;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        for (String name : beanFactory.getBeanDefinitionNames()) {
            Class<?> type = beanFactory.getType(name, false);
            if (type == null) {
                continue;
            }
            Decoy decoy = AnnotatedElementUtils.findMergedAnnotation(type, Decoy.class);
            if (decoy == null) {
                continue;
            }
            BeanDefinition definition = beanFactory.getBeanDefinition(name);
            if (definition instanceof AbstractBeanDefinition abstractDefinition) {
                abstractDefinition.setAutowireCandidate(false);
            }
            for (Class<?> routed : routedInterfaces(type, decoy)) {
                String previous = decoyByInterface.putIfAbsent(routed, name);
                if (previous != null) {
                    throw new IllegalStateException("Two @Decoy beans stand in for " + routed.getName()
                            + ": '" + previous + "' and '" + name + "'");
                }
            }
        }
    }

    private static Set<Class<?>> routedInterfaces(Class<?> type, Decoy decoy) {
        if (decoy.value().length > 0) {
            for (Class<?> declared : decoy.value()) {
                if (!declared.isInterface() || !declared.isAssignableFrom(type)) {
                    throw new IllegalStateException("@Decoy on " + type.getName()
                            + " lists " + declared.getName() + ", which it does not implement as an interface");
                }
            }
            return Set.copyOf(Arrays.asList(decoy.value()));
        }
        return ClassUtils.getAllInterfacesForClassAsSet(type);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!routingEnabled || decoyByInterface.isEmpty() || decoyByInterface.containsValue(beanName)) {
            return bean;
        }
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        Map<Class<?>, String> routes = new LinkedHashMap<>();
        decoyByInterface.forEach((routed, decoyName) -> {
            if (routed.isAssignableFrom(targetClass)) {
                routes.put(routed, decoyName);
            }
        });
        if (routes.isEmpty()) {
            return bean;
        }
        ProxyFactory proxyFactory = new ProxyFactory(bean);
        proxyFactory.setProxyTargetClass(false);
        proxyFactory.addAdvice(routingInterceptor(routes));
        return proxyFactory.getProxy(ClassUtils.getDefaultClassLoader());
    }

    private MethodInterceptor routingInterceptor(Map<Class<?>, String> routes) {
        return invocation -> {
            if (!DuressContextHolder.isDuress()) {
                return invocation.proceed();
            }
            Method method = invocation.getMethod();
            String decoyName = decoyFor(routes, method);
            if (decoyName == null) {
                return invocation.proceed();
            }
            Object decoy = beanFactory.getBean(decoyName);
            return AopUtils.invokeJoinpointUsingReflection(decoy, method, invocation.getArguments());
        };
    }

    private static String decoyFor(Map<Class<?>, String> routes, Method method) {
        Class<?> declaring = method.getDeclaringClass();
        for (Map.Entry<Class<?>, String> route : routes.entrySet()) {
            if (declaring.isAssignableFrom(route.getKey())) {
                return route.getValue();
            }
        }
        return null;
    }
}
