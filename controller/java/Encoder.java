import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

@Aspect
@Component
public class SearchMonitoringAspect {

    @Around("@annotation(monitorSearch)")
    public Object monitor(ProceedingJoinPoint joinPoint, MonitorSearch monitorSearch) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        List<FieldOperator> criteria = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg == null) continue;
            String paramName = (paramNames != null && i < paramNames.length) ? paramNames[i] : "arg" + i;
            if (arg instanceof String) {
                criteria.add(new FieldOperator(paramName, "equals"));
            } else if (!(arg instanceof Number) && !(arg instanceof Boolean) && !(arg instanceof List)) {
                criteria.addAll(extractCriteria(arg));
            }
        }

        System.out.println("Search request: " + methodName + " criteria: " + criteria);

        long start = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            System.out.println(methodName + " executed in " + (System.currentTimeMillis() - start) + " ms");
        }
    }

    private List<FieldOperator> extractCriteria(Object obj) throws IllegalAccessException {
        List<FieldOperator> result = new ArrayList<>();
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            String name = field.getName();
            Object value = field.get(obj);
            if (value == null) continue;

            if (isStringList(field)) {
                result.add(new FieldOperator(name, "in"));
                continue;
            }

            if (!(value instanceof String)) continue;

            if (name.endsWith("Op")) continue;

            if (name.endsWith("From")) {
                result.add(new FieldOperator(name.substring(0, name.length() - 4), "greater_then"));
            } else if (name.endsWith("To")) {
                result.add(new FieldOperator(name.substring(0, name.length() - 2), "less_then"));
            } else {
                String opValue = findOpValue(clazz, obj, name);
                result.add(new FieldOperator(name, opValue != null ? opValue : "equal"));
            }
        }
        return result;
    }

    private String findOpValue(Class<?> clazz, Object obj, String fieldName) throws IllegalAccessException {
        try {
            Field opField = clazz.getDeclaredField(fieldName + "Op");
            opField.setAccessible(true);
            Object opValue = opField.get(obj);
            return opValue instanceof String ? (String) opValue : null;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private boolean isStringList(Field field) {
        if (!List.class.isAssignableFrom(field.getType())) return false;
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType) {
            Type[] typeArgs = ((ParameterizedType) generic).getActualTypeArguments();
            return typeArgs.length == 1 && typeArgs[0] == String.class;
        }
        return false;
    }

    static class FieldOperator {
        final String field;
        final String operator;

        FieldOperator(String field, String operator) {
            this.field = field;
            this.operator = operator;
        }

        @Override
        public String toString() {
            return "{field: \"" + field + "\", operator: \"" + operator + "\"}";
        }
    }
}
