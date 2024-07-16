package com.example.proxies;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.aot.hint.annotation.Reflective;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.annotation.*;
import java.lang.reflect.Method;

@SpringBootApplication
public class ProxiesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProxiesApplication.class, args);
    }


}

@Configuration
class CglibProxyConfiguration {

    @Bean
    ApplicationRunner cglibDemo(CglibCustomerService customerService) {
        return args -> {
            customerService.create();
        };
    }

    @Bean
    static CglibBPP cglibBPP() {
        return new CglibBPP();
    }

    static class CglibBPP implements SmartInstantiationAwareBeanPostProcessor {

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof CglibCustomerService) {
                try {
                    return cglib(bean, bean.getClass()).getProxy(bean.getClass().getClassLoader());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return SmartInstantiationAwareBeanPostProcessor
                    .super.postProcessAfterInitialization(bean, beanName);
        }

        @Override
        public Class<?> determineBeanType(Class<?> beanClass, String beanName) throws BeansException {

            if (beanClass.isAssignableFrom(CglibCustomerService.class))
                return cglib(null, beanClass).getProxyClass(beanClass.getClassLoader());

            return beanClass;
        }
    }

    @Bean
    CglibCustomerService customerService() {
        return new CglibCustomerService();
    }
    
    private static ProxyFactory cglib(Object target, Class<?> targetClass) {
        var pf = new ProxyFactory();
        pf.setTargetClass(targetClass);
        pf.setInterfaces(targetClass.getInterfaces());
        pf.setProxyTargetClass(true);
        pf.addAdvice((MethodInterceptor) invocation -> {
            var methodName = invocation.getMethod().getName();
            System.out.println("before " + methodName);
            var result = invocation.getMethod().invoke(target, invocation.getArguments());
            System.out.println("after " + methodName);
            return result;
        });
        if (null != target) {
            pf.setTarget(target);
        }
        return pf;
    }

}

//@Configuration
class InterfaceProxyConfiguration {

    static <T> T jdk(T target) throws Exception {
        var pfb = new ProxyFactoryBean();
        pfb.setProxyInterfaces(target.getClass().getInterfaces());
        pfb.setTarget(target);
        pfb.addAdvice((MethodInterceptor) invocation -> {
            try {
                Transactions.handleTxStartFor(invocation.getMethod());
                return invocation.proceed();
            } finally {
                Transactions.handleTxStopFor(invocation.getMethod());
            }
        });
        return (T) pfb.getObject();

    }


    @Bean
    ApplicationRunner interfaceDemo(CustomerService customerService) {
        return args -> {
            customerService.create();
        };
    }

    @Bean
    InterfaceCustomerService interfaceCustomerService() {
        return new InterfaceCustomerService();
    }

    @Bean
    static InterfaceBPP interfaceBPP() {
        return new InterfaceBPP();
    }

    @Bean
    static InterfaceBFIAP interfaceBFIAP() {
        return new InterfaceBFIAP();
    }

    static class InterfaceBFIAP implements BeanFactoryInitializationAotProcessor {
        @Override
        public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
            return (generationContext, beanFactoryInitializationCode) -> generationContext.getRuntimeHints().proxies().registerJdkProxy(
                    CustomerService.class,
                    org.springframework.aop.SpringProxy.class,
                    org.springframework.aop.framework.Advised.class,
                    org.springframework.core.DecoratingProxy.class);
        }

    }

    static class InterfaceBPP implements BeanPostProcessor {

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof InterfaceCustomerService) {
                try {
                    return jdk((CustomerService) bean);
                } //
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
        }
    }

}

class Transactions {


    static void handleTxStartFor(Method method) {
        if (method.getAnnotation(MyTransactional.class) != null)
            System.out.println(method.getName() + ": start");
    }

    static void handleTxStopFor(Method method) {
        if (method.getAnnotation(MyTransactional.class) != null)
            System.out.println(method.getName() + ": stop");
    }

}

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Reflective
@interface MyTransactional {
}


class ForwardingCustomerService
        implements CustomerService {

    private final CustomerService customerService;

    ForwardingCustomerService(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public void create() {
        System.out.println("----------------------------------------");
        System.out.println("create: start");
        this.customerService.create();
        System.out.println("create: stop");
    }
}


interface CustomerService {

    @MyTransactional
    void create();
}

class InterfaceCustomerService implements CustomerService {

    @Override
    public void create() {
        System.out.println(getClass().getName());
    }
}

class CglibCustomerService {

    @MyTransactional
    public void create() {
        System.out.println(getClass().getName());
    }
}

