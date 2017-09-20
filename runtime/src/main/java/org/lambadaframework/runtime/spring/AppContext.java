package org.lambadaframework.runtime.spring;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class AppContext {

    private static final AppContext appContext = new AppContext();

    public static AppContext getInstance() {
        return appContext;
    }

    private final AnnotationConfigApplicationContext springContext = new AnnotationConfigApplicationContext();
    private String packageName = null;

    public void destroy() {
        springContext.stop();
    }

    public void setPackageName(final String packageName) {
        if (this.packageName == null) {
            synchronized (this) {
                if (this.packageName == null) {
                    springContext.scan(packageName);
                    final String[] packageSplit = packageName.split("\\.");
                    springContext.scan(packageSplit[0] + "." + packageSplit[1]);
                    try {
                        springContext.refresh();
                        springContext.start();
                        springContext.registerShutdownHook();
                    } catch (final RuntimeException ex) {
                        // might not work in this case
                    }
                    this.packageName = packageName;
                }
            }
        }
    }

    public <T> T getBean(final Class<T> clazz) {
        return springContext.getBean(clazz);
    }

    public String getPackageName() {
        return packageName;
    }

    public AnnotationConfigApplicationContext getSpringContext() {
        return springContext;
    }

}
