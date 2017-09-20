package org.lambadaframework.runtime;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.glassfish.jersey.server.model.Invocable;
import org.lambadaframework.jaxrs.model.ResourceMethod;
import org.lambadaframework.runtime.models.Request;
import org.lambadaframework.runtime.spring.AppContext;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ResourceMethodInvoker {

    static final Logger logger = Logger.getLogger(ResourceMethodInvoker.class);

    private static AppContext appContext = AppContext.getInstance();

    private ResourceMethodInvoker() {
    }

    private static Object toObject(final String value, final Class<?> clazz) {
        if (clazz == Integer.class || Integer.TYPE == clazz) {
            return Integer.parseInt(value);
        }
        if (clazz == Long.class || Long.TYPE == clazz) {
            return Long.parseLong(value);
        }
        if (clazz == Float.class || Float.TYPE == clazz) {
            return Float.parseFloat(value);
        }
        if (clazz == Boolean.class || Boolean.TYPE == clazz) {
            return Boolean.parseBoolean(value);
        }
        if (clazz == Double.class || Double.TYPE == clazz) {
            return Double.parseDouble(value);
        }
        if (clazz == Byte.class || Byte.TYPE == clazz) {
            return Byte.parseByte(value);
        }
        if (clazz == Short.class || Short.TYPE == clazz) {
            return Short.parseShort(value);
        }
        return value;
    }

    public static Object invoke(final ResourceMethod resourceMethod, final Request request, final Context lambdaContext)
            throws InvocationTargetException, IllegalAccessException, InstantiationException {

        logger.debug("Request object is: " + request);

        final Invocable invocable = resourceMethod.getInvocable();

        final Method method = invocable.getHandlingMethod();
        final Class<?> clazz = invocable.getHandler().getHandlerClass();

        appContext.setPackageName(request.getPackage());
        final Object instance = appContext.getBean(clazz);

        final List<Object> varargs = new ArrayList<>();

        /**
         * Get consumes annotation from handler method
         */
        final Consumes consumesAnnotation = method.getAnnotation(Consumes.class);

        for (final Parameter parameter : method.getParameters()) {

            final Class<?> parameterClass = parameter.getType();

            /**
             * Path parameter
             */
            if (parameter.isAnnotationPresent(PathParam.class)) {
                final PathParam annotation = parameter.getAnnotation(PathParam.class);
                varargs.add(toObject(request.getPathParameters().get(annotation.value()), parameterClass));
                continue;
            }

            /**
             * Query parameter
             */
            if (parameter.isAnnotationPresent(QueryParam.class)) {
                final QueryParam annotation = parameter.getAnnotation(QueryParam.class);
                varargs.add(toObject(request.getQueryParams().get(annotation.value()), parameterClass));
                continue;
            }

            /**
             * Header parameter
             */
            if (parameter.isAnnotationPresent(HeaderParam.class)) {
                final HeaderParam annotation = parameter.getAnnotation(HeaderParam.class);
                varargs.add(toObject(request.getRequestHeaders().get(annotation.value()), parameterClass));
                continue;
            }

            /**
             * Lambda Context can be automatically injected
             */
            if (parameter.getType() == Context.class) {
                varargs.add(lambdaContext);
                continue;
            }

            if (consumesAnnotation != null && consumesSpecificType(consumesAnnotation, MediaType.APPLICATION_JSON)) {
                final ObjectMapper mapper = new ObjectMapper();
                try {
                    final Object body = request.getRequestBody();
                    Object deserializedParameter = null;
                    if (body instanceof String) {
                        if (parameterClass == String.class) {
                            deserializedParameter = body;
                        } else {
                            deserializedParameter = mapper.readValue((String) body, parameterClass);
                        }
                    } else if (parameterClass.isInstance(body)) {
                        deserializedParameter = body;
                    } else {
                        deserializedParameter = mapper.readValue(mapper.writeValueAsString(body), parameterClass);
                    }
                    varargs.add(deserializedParameter);
                } catch (final IOException ioException) {
                    logger.error("Could not serialized " + request.getRequestBody() + " to " + parameterClass + ":",
                            ioException);
                    varargs.add(null);
                }
            }

        }

        return method.invoke(instance, varargs.toArray());
    }

    private static boolean consumesSpecificType(final Consumes annotation, final String type) {

        final String[] consumingTypes = annotation.value();
        for (final String consumingType : consumingTypes) {
            if (type.equals(consumingType)) {
                return true;
            }
        }

        return false;
    }
}
