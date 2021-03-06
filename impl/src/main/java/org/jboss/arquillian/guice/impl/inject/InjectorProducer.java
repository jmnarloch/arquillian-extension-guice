/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.guice.impl.inject;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.ApplicationScoped;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.guice.api.annotation.GuiceConfiguration;
import org.jboss.arquillian.guice.api.annotation.GuiceInjector;
import org.jboss.arquillian.guice.api.annotation.GuiceWebConfiguration;
import org.jboss.arquillian.guice.api.utils.InjectorHolder;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * A producer that creates a instance of {@link Injector}.
 *
 * @author <a href="mailto:jmnarloch@gmail.com">Jakub Narloch</a>
 */
public class InjectorProducer {

    /**
     * The logger used by this class.
     */
    private static final Logger log = Logger.getLogger(InjectorProducer.class.getName());

    /**
     * Producer proxy for {@link Injector}.
     */
    @Inject
    @ApplicationScoped
    private InstanceProducer<Injector> injectorInstance;

    /**
     * Initializes the {@link Injector}.
     *
     * @param beforeClass the before class event
     */
    public void initInjector(@Observes BeforeClass beforeClass) {

        TestClass testClass;
        Injector injector;

        testClass = beforeClass.getTestClass();

        if (isGuiceTest(testClass)) {

            if (hasCustomInjector(testClass)) {

                injector = getCustomInjector(testClass);
            } else if (testClass.isAnnotationPresent(GuiceWebConfiguration.class)) {

                injector = getServletContextInjector();
            } else {

                // otherwise creates the injector
                injector = createInjector(testClass);

                log.fine("Successfully created guice injector for model class: "
                        + testClass.getName());
            }

            if (injector != null) {

                injectorInstance.set(injector);
            }
        }

    }

    /**
     * Creates the {@link Injector} instance.
     *
     * @param testClass the model class
     *
     * @return instance of {@link Injector}
     */
    private Injector createInjector(TestClass testClass) {

        // creates new instance of guice injector
        return Guice.createInjector(getTestClassModules(testClass));
    }

    /**
     * Retrieves the guice injector created
     *
     * @return the guice injector that has been created within the servlet context.
     */
    private Injector getServletContextInjector() {

        return InjectorHolder.getInjector();
    }

    /**
     * Invokes the model declared method for creating custom injector.
     *
     * @param testClass the model class
     *
     * @return the Guice injector instance
     */
    private Injector getCustomInjector(TestClass testClass) {

        try {
            List<Method> methods = SecurityActions.getStaticMethodsWithAnnotation(testClass.getJavaClass(), GuiceInjector.class);

            if (methods.size() > 1) {
                throw new RuntimeException("Test case may declare only one custom injector method.");
            }

            return (Injector) methods.get(0).invoke(null);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Error occurred when invoking custom injector method.", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error occurred when invoking custom injector method.", e);
        }
    }

    /**
     * Returns whether the model defines guice configuration.
     *
     * @param testClass the model class
     *
     * @return true if model defines guice module, false otherwise
     */
    private boolean isGuiceTest(TestClass testClass) {

        return testClass.isAnnotationPresent(GuiceConfiguration.class)
                || testClass.isAnnotationPresent(GuiceWebConfiguration.class)
                || hasCustomInjector(testClass);
    }

    /**
     * Returns whether the model defines custom injector.
     *
     * @param testClass the model class
     *
     * @return true if the model defines custom injector, false otherwise
     */
    private boolean hasCustomInjector(TestClass testClass) {

        return SecurityActions.getStaticMethodsWithAnnotation(
                testClass.getJavaClass(), GuiceInjector.class).size() > 0;
    }

    /**
     * Retrieves Guice modules for the give model class.
     *
     * @param testClass the model class
     *
     * @return modules instances
     */
    private Module[] getTestClassModules(TestClass testClass) {

        GuiceConfiguration guiceConfiguration;
        List<Module> modules = new ArrayList<Module>();

        guiceConfiguration = testClass.getAnnotation(GuiceConfiguration.class);
        Collections.addAll(modules, instantiateModules(guiceConfiguration.value()));

        return modules.toArray(new Module[modules.size()]);
    }

    /**
     * Instantiates the guice module based of passed classes.
     *
     * @param classes classes that implement the {@link Module} interface
     *
     * @return array of module instances
     */
    private Module[] instantiateModules(Class<? extends Module>[] classes) {
        List<Module> modules = new ArrayList<Module>();

        for (Class<? extends Module> c : classes) {

            modules.add(instantiateClass(c));
        }

        return modules.toArray(new Module[modules.size()]);
    }

    /**
     * Creates new instance of the give class.
     *
     * @param clazz the class to instantiate
     *
     * @return new instance of specified class
     */
    private Module instantiateClass(Class<? extends Module> clazz) {

        try {

            return clazz.newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeException("Could not instantiate Guice module.", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not instantiate Guice module.", e);
        }
    }
}
