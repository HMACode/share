package com.example.mqretry;

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
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import javax.jms.JMSException;
import javax.transaction.xa.XAException;

import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jms.support.converter.MessageConversionException;

public class JmsErrorClassifier {

    // Transient Oracle server (ORA-) errors: availability, resource and connectivity problems.
    private static final int ORA_MAX_SESSIONS_EXCEEDED = 18;
    private static final int ORA_MAX_PROCESSES_EXCEEDED = 20;
    private static final int ORA_RESOURCE_BUSY = 54;
    private static final int ORA_DEADLOCK_DETECTED = 60;
    private static final int ORA_INIT_OR_SHUTDOWN_IN_PROGRESS = 1033;
    private static final int ORA_NOT_AVAILABLE = 1034;
    private static final int ORA_IMMEDIATE_SHUTDOWN_IN_PROGRESS = 1089;
    private static final int ORA_END_OF_FILE_ON_CHANNEL = 3113;
    private static final int ORA_NOT_CONNECTED = 3114;
    private static final int ORA_CONNECTION_LOST_CONTACT = 3135;
    private static final int ORA_CANNOT_SERIALIZE_ACCESS = 8177;
    private static final int ORA_TNS_CONNECT_TIMEOUT = 12170;
    private static final int ORA_TNS_NO_MATCHING_HANDLER = 12516;
    private static final int ORA_TNS_NO_APPROPRIATE_HANDLER = 12519;
    private static final int ORA_TNS_NO_HANDLER_FOR_SERVER = 12520;
    private static final int ORA_TNS_CONNECTION_CLOSED = 12537;
    private static final int ORA_TNS_NO_LISTENER = 12541;

    // Transient Oracle JDBC driver (vendor) error codes.
    private static final int JDBC_IO_ERROR = 17002;
    private static final int JDBC_CONNECTION_CLOSED = 17008;
    private static final int JDBC_NO_MORE_DATA_FROM_SOCKET = 17410;

    // Permanent Oracle (ORA-) errors: constraint, data and schema problems that will not heal on retry.
    private static final int ORA_UNIQUE_CONSTRAINT_VIOLATED = 1;
    private static final int ORA_CANNOT_INSERT_NULL = 1400;
    private static final int ORA_INSERTED_VALUE_TOO_LARGE = 1401;
    private static final int ORA_CANNOT_UPDATE_TO_NULL = 1407;
    private static final int ORA_VALUE_LARGER_THAN_PRECISION = 1438;
    private static final int ORA_INVALID_NUMBER = 1722;
    private static final int ORA_NON_NUMERIC_CHARACTER = 1858;
    private static final int ORA_CHECK_CONSTRAINT_VIOLATED = 2290;
    private static final int ORA_PARENT_KEY_NOT_FOUND = 2291;
    private static final int ORA_CHILD_RECORD_FOUND = 2292;
    private static final int ORA_VALUE_TOO_LARGE_FOR_COLUMN = 12899;
    private static final int ORA_INVALID_IDENTIFIER = 904;
    private static final int ORA_TABLE_OR_VIEW_NOT_FOUND = 942;
    private static final int ORA_MISSING_EXPRESSION = 936;
    private static final int ORA_INCONSISTENT_DATATYPES = 932;

    private static final Set<Integer> TRANSIENT_ORACLE = unmodifiableSet(
            ORA_MAX_SESSIONS_EXCEEDED, ORA_MAX_PROCESSES_EXCEEDED, ORA_RESOURCE_BUSY,
            ORA_DEADLOCK_DETECTED, ORA_INIT_OR_SHUTDOWN_IN_PROGRESS, ORA_NOT_AVAILABLE,
            ORA_IMMEDIATE_SHUTDOWN_IN_PROGRESS, ORA_END_OF_FILE_ON_CHANNEL, ORA_NOT_CONNECTED,
            ORA_CONNECTION_LOST_CONTACT, ORA_CANNOT_SERIALIZE_ACCESS, ORA_TNS_CONNECT_TIMEOUT,
            ORA_TNS_NO_MATCHING_HANDLER, ORA_TNS_NO_APPROPRIATE_HANDLER, ORA_TNS_NO_HANDLER_FOR_SERVER,
            ORA_TNS_CONNECTION_CLOSED, ORA_TNS_NO_LISTENER,
            JDBC_IO_ERROR, JDBC_CONNECTION_CLOSED, JDBC_NO_MORE_DATA_FROM_SOCKET);

    private static final Set<Integer> PERMANENT_ORACLE = unmodifiableSet(
            ORA_UNIQUE_CONSTRAINT_VIOLATED, ORA_CANNOT_INSERT_NULL, ORA_INSERTED_VALUE_TOO_LARGE,
            ORA_CANNOT_UPDATE_TO_NULL, ORA_VALUE_LARGER_THAN_PRECISION, ORA_INVALID_NUMBER,
            ORA_NON_NUMERIC_CHARACTER, ORA_CHECK_CONSTRAINT_VIOLATED, ORA_PARENT_KEY_NOT_FOUND,
            ORA_CHILD_RECORD_FOUND, ORA_VALUE_TOO_LARGE_FOR_COLUMN, ORA_INVALID_IDENTIFIER,
            ORA_TABLE_OR_VIEW_NOT_FOUND, ORA_MISSING_EXPRESSION, ORA_INCONSISTENT_DATATYPES);

    // WebLogic / JTA exception types (matched by class name so we keep no WebLogic compile dependency).
    // These are all "infrastructure temporarily down" conditions and are treated as transient.
    private static final String[] TRANSIENT_CLASS_MARKERS = {
            "weblogic.common.resourcepool.ResourceLimitException", // JDBC / XA connection pool exhausted
            "weblogic.jms.common.ResourceAllocationException",     // target JMS server out of resources
            "weblogic.messaging.dispatcher.DispatcherException",   // target JMS server unreachable
            "weblogic.jms.dispatcher.DispatcherException",
            "weblogic.rjvm.PeerGoneException",                     // remote JMS/JTA peer gone
            "RollbackException"                                    // javax/weblogic transaction RollbackException
    };

    // Substrings (matched case-insensitively against the exception message) for the same conditions.
    private static final String[] TRANSIENT_MESSAGE_MARKERS = {
            "no resources currently available in pool",            // WebLogic JDBC/XA pool full
            "connection pool",
            "pool is suspended",
            "max capacity",
            "is unavailable",                                      // e.g. XAResource 'WLStore_...' is unavailable
            "resource manager is unavailable",                    // XAER_RMFAIL text
            "already rolled back",                                // transaction already rolled back
            "already been rolled back",
            "transaction has been rolled back",
            "could not connect",                                  // target JMS server unavailable
            "peer gone",
            "unreachable",
            "no longer available",
            "being shut down",
            "is being shutdown"
    };

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

        for (Throwable t : chain) {
            if (isTransient(t)) {
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

    private boolean isTransient(Throwable t) {
        for (Predicate<Throwable> p : extraTransient) {
            if (p.test(t)) {
                return true;
            }
        }

        if (t instanceof RetryableMessageException) {
            return true;
        }

        if (isTransientInfrastructure(t)) {
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

        return false;
    }

    // WebLogic JTA/XA and WebLogic-JMS (jmsTemplate send target) failures that should be retried.
    private boolean isTransientInfrastructure(Throwable t) {
        if (t instanceof XAException && isTransientXaCode(((XAException) t).errorCode)) {
            return true;
        }

        String className = t.getClass().getName();
        for (String marker : TRANSIENT_CLASS_MARKERS) {
            if (className.contains(marker)) {
                return true;
            }
        }

        String message = t.getMessage();
        if (message != null) {
            String lower = message.toLowerCase(Locale.ROOT);
            for (String marker : TRANSIENT_MESSAGE_MARKERS) {
                if (lower.contains(marker)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isTransientXaCode(int code) {
        switch (code) {
            case XAException.XAER_RMFAIL:    // resource manager unavailable
            case XAException.XAER_RMERR:     // unexpected resource manager error
            case XAException.XAER_NOTA:      // XID no longer known, transaction already completed
            case XAException.XA_RETRY:       // routine returned with no effect, may be retried
            case XAException.XA_RBCOMMFAIL:  // rollback: communication failure
            case XAException.XA_RBTIMEOUT:   // rollback: transaction timed out
            case XAException.XA_RBTRANSIENT: // rollback: transient failure
                return true;
            default:
                return false;
        }
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
