package com.medscan.security.filter;

import java.lang.reflect.Method;

import jakarta.ws.rs.container.ResourceInfo;

public class FakeResourceInfo implements ResourceInfo {

    private final Method method;
    private final Class<?> resourceClass;

    public FakeResourceInfo(Method method, Class<?> resourceClass) {
        this.method = method;
        this.resourceClass = resourceClass;
    }

    @Override
    public Method getResourceMethod() {
        return method;
    }

    @Override
    public Class<?> getResourceClass() {
        return resourceClass;
    }
}
