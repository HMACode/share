package com.example.mqretry;

import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.SQLNonTransientException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;
import javax.jms.MessageFormatException;
import javax.jms.MessageNotWriteableException;

import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jms.support.converter.MessageConversionException;

public class JmsErrorClassifier {

    private static final int MQRC_CONNECTION_BROKEN = 2009;
    private static final int MQRC_Q_MGR_NOT_AVAILABLE = 2059;
    private static final int MQRC_Q_MGR_QUIESCING = 2161;
    private static final int MQRC_Q_MGR_STOPPING = 2162;
    private static final int MQRC_CONNECTION_QUIESCING = 2202;
    private static final int MQRC_CONNECTION_STOPPING = 2203;
    private static final int MQRC_CONNECTION_NOT_AVAILABLE = 2568;
    private static final int MQRC_HOST_NOT_AVAILABLE = 2538;
    private static final int MQRC_CHANNEL_NOT_AVAILABLE = 2537;
    private static final int MQRC_Q_FULL = 2053;
    private static final int MQRC_PUT_INHIBITED = 2051;
    private static final int MQRC_GET_INHIBITED = 2016;
    private static final int MQRC_STORAGE_NOT_AVAILABLE = 2071;
    private static final int MQRC_RESOURCE_PROBLEM = 2102;
    private static final int MQRC_UNEXPECTED_ERROR = 2195;
    private static final int MQRC_RECONNECTING = 2544;

    private static final int MQRC_NOT_AUTHORIZED = 2035;
    private static final int MQRC_UNKNOWN_OBJECT_NAME = 2085;
    private static final int MQRC_UNKNOWN_REMOTE_Q_MGR = 2087;

    private static final Set<Integer> TRANSIENT_MQ = unmodifiableSet(
            MQRC_CONNECTION_BROKEN, MQRC_Q_MGR_NOT_AVAILABLE, MQRC_Q_MGR_QUIESCING,
            MQRC_Q_MGR_STOPPING, MQRC_CONNECTION_QUIESCING, MQRC_CONNECTION_STOPPING,
            MQRC_CONNECTION_NOT_AVAILABLE, MQRC_HOST_NOT_AVAILABLE, MQRC_CHANNEL_NOT_AVAILABLE,
            MQRC_Q_FULL, MQRC_PUT_INHIBITED, MQRC_GET_INHIBITED, MQRC_STORAGE_NOT_AVAILABLE,
            MQRC_RESOURCE_PROBLEM, MQRC_UNEXPECTED_ERROR, MQRC_RECONNECTING);

    private static final Set<Integer> PERMANENT_MQ = unmodifiableSet(
            MQRC_NOT_AUTHORIZED, MQRC_UNKNOWN_OBJECT_NAME, MQRC_UNKNOWN_REMOTE_Q_MGR);

    private static final Set<Integer> TRANSIENT_ORACLE = unmodifiableSet(
            18, 20, 54, 60, 1033, 1034, 1089, 3113, 3114, 3135,
            8177, 12170, 12516, 12519, 12520, 12537, 12541,
            17002, 17008, 17410);

    private static final Set<Integer> PERMANENT_ORACLE = unmodifiableSet(
            1, 1400, 1401, 1407, 1438, 1722, 1858, 2290, 2291, 2292,
            12899, 904, 942, 936, 932);

    private final List<Predicate<Throwable>> extraTransient;
    private final List<Predicate<Throwable>> extraPermanent;

    public JmsErrorClassifier() {
        this(Collections.<Predicate<Throwable>>emptyList(), Collections.<Predicate<Throwable>>emptyList());
    }

    public JmsErrorClassifier(List<Predicate<Throwable>> extraTransient,
                              List<Predicate<Throwable>> extraPermanent) {
        this.extraTransient = new ArrayList<Predicate<Throwable>>(extraTransient);
        this.extraPermanent = new ArrayList<Predicate<Throwable>>(extraPermanent);
    }

    public RetryDecision classify(Throwable root) {
        List<Throwable> chain = flatten(root);

        boolean permanentMqPresent = false;
        for (Throwable t : chain) {
            Integer reason = mqReason(t);
            if (reason != null && PERMANENT_MQ.contains(reason)) {
                permanentMqPresent = true;
                break;
            }
        }

        for (Throwable t : chain) {
            if (isTransient(t, permanentMqPresent)) {
                return RetryDecision.TRANSIENT;
            }
        }

        for (Throwable t : chain) {
            if (isPermanent(t)) {
                return RetryDecision.NON_RECOVERABLE;
            }
        }

        return RetryDecision.UNKNOWN;
    }

    private boolean isTransient(Throwable t, boolean permanentMqPresent) {
        for (Predicate<Throwable> p : extraTransient) {
            if (p.test(t)) {
                return true;
            }
        }

        if (t instanceof RetryableMessageException) {
            return true;
        }

        Integer reason = mqReason(t);
        if (reason != null && TRANSIENT_MQ.contains(reason)) {
            return true;
        }

        if (t instanceof SQLTransientException
                || t instanceof SQLRecoverableException
                || t instanceof SQLTimeoutException) {
            return true;
        }

        if (t instanceof SQLException) {
            SQLException sql = (SQLException) t;
            String state = sql.getSQLState();
            if (state != null && state.startsWith("08")) {
                return true;
            }
            int code = sql.getErrorCode();
            if (TRANSIENT_ORACLE.contains(code)) {
                return true;
            }
        }

        if (t instanceof TransientDataAccessException
                || t instanceof RecoverableDataAccessException
                || t instanceof CannotGetJdbcConnectionException
                || t instanceof org.springframework.dao.DataAccessResourceFailureException) {
            return true;
        }

        if (t instanceof SocketTimeoutException
                || t instanceof ConnectException
                || t instanceof NoRouteToHostException
                || t instanceof UnknownHostException
                || t instanceof SocketException
                || t instanceof java.util.concurrent.TimeoutException) {
            return true;
        }

        if (t instanceof IllegalStateException) {
            return true;
        }

        if (t instanceof JMSException && !isPermanentJms(t) && !permanentMqPresent) {
            return true;
        }

        return false;
    }

    private boolean isPermanent(Throwable t) {
        for (Predicate<Throwable> p : extraPermanent) {
            if (p.test(t)) {
                return true;
            }
        }

        if (t instanceof NonRecoverableMessageException) {
            return true;
        }

        if (isPermanentJms(t)) {
            return true;
        }

        Integer reason = mqReason(t);
        if (reason != null && PERMANENT_MQ.contains(reason)) {
            return true;
        }

        if (t instanceof MessageConversionException) {
            return true;
        }

        if (t instanceof NonTransientDataAccessException) {
            return true;
        }

        if (t instanceof SQLNonTransientException) {
            return true;
        }

        if (t instanceof SQLException) {
            int code = ((SQLException) t).getErrorCode();
            if (PERMANENT_ORACLE.contains(code)) {
                return true;
            }
        }

        if (t instanceof NullPointerException
                || t instanceof IllegalArgumentException
                || t instanceof ClassCastException
                || t instanceof IndexOutOfBoundsException
                || t instanceof ArithmeticException) {
            return true;
        }

        return false;
    }

    private boolean isPermanentJms(Throwable t) {
        return t instanceof MessageFormatException
                || t instanceof MessageNotWriteableException
                || t instanceof InvalidDestinationException
                || t instanceof InvalidSelectorException
                || t instanceof JMSSecurityException;
    }

    private Integer mqReason(Throwable t) {
        if (t == null || !t.getClass().getName().startsWith("com.ibm.")) {
            return null;
        }
        try {
            Method m = t.getClass().getMethod("getReason");
            Object value = m.invoke(t);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private List<Throwable> flatten(Throwable root) {
        List<Throwable> result = new ArrayList<Throwable>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        Deque<Throwable> stack = new ArrayDeque<Throwable>();
        if (root != null) {
            stack.push(root);
        }
        while (!stack.isEmpty() && result.size() < 50) {
            Throwable current = stack.pop();
            if (current == null || !visited.add(current)) {
                continue;
            }
            result.add(current);

            Throwable cause = current.getCause();
            if (cause != null) {
                stack.push(cause);
            }
            if (current instanceof JMSException) {
                Exception linked = ((JMSException) current).getLinkedException();
                if (linked != null) {
                    stack.push(linked);
                }
            }
            if (current instanceof SQLException) {
                SQLException next = ((SQLException) current).getNextException();
                if (next != null) {
                    stack.push(next);
                }
            }
        }
        return result;
    }

    private static Set<Integer> unmodifiableSet(int... values) {
        Set<Integer> set = new HashSet<Integer>();
        for (int v : values) {
            set.add(v);
        }
        return Collections.unmodifiableSet(set);
    }
}
