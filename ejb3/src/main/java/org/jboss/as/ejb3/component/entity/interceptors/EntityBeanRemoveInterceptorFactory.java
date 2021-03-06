/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.ejb3.component.entity.interceptors;

import org.jboss.as.ee.component.ComponentInstance;
import org.jboss.as.ejb3.component.AbstractEJBInterceptor;
import org.jboss.as.ejb3.component.entity.EntityBeanComponent;
import org.jboss.as.ejb3.component.entity.EntityBeanComponentInstance;
import org.jboss.invocation.Interceptor;
import org.jboss.invocation.InterceptorContext;
import org.jboss.invocation.InterceptorFactory;
import org.jboss.invocation.InterceptorFactoryContext;
import org.jboss.logging.Logger;

import javax.ejb.NoSuchEJBException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Interceptor that calls the EJB remove method for BMP entity beans
 *
 * @author Stuart Douglas
 */
public class EntityBeanRemoveInterceptorFactory implements InterceptorFactory {

    private final Logger log = Logger.getLogger(EntityBeanAssociatingInterceptorFactory.class);

    private final Method ejbRemove;
    private final Object primaryKeyContextKey;

    public EntityBeanRemoveInterceptorFactory(final Method ejbRemove, final Object primaryKeyContextKey) {
        this.ejbRemove = ejbRemove;
        this.primaryKeyContextKey = primaryKeyContextKey;
    }

    @Override
    public Interceptor create(final InterceptorFactoryContext context) {
        final AtomicReference<Object> primaryKeyReference = (AtomicReference<Object>) context.getContextData().get(this.primaryKeyContextKey);

        return new AbstractEJBInterceptor() {
            @Override
            public Object processInvocation(final InterceptorContext context) throws Exception {
                final EntityBeanComponent component = getComponent(context, EntityBeanComponent.class);

                final Object primaryKey = primaryKeyReference.get();
                if (primaryKey == null) {
                    throw new NoSuchEJBException("Invocation was not associated with an instance, primary key was null, instance may have been removed");
                }


                final EntityBeanComponentInstance instance = component.getCache().get(primaryKey);
                //Call the ejbRemove method
                Method oldMethod = context.getMethod();
                try {
                    context.putPrivateData(ComponentInstance.class, instance);
                    context.setMethod(ejbRemove);
                    context.setTarget(instance.getInstance());
                    instance.getInterceptor(ejbRemove).processInvocation(context);
                } finally {
                    context.setMethod(oldMethod);
                    context.setTarget(null);
                    context.putPrivateData(ComponentInstance.class, null);
                }
                instance.setRemoved(true);
                return null;
            }
        };
    }
}