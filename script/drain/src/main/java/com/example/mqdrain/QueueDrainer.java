package com.example.mqdrain;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;

import com.ibm.msg.client.jms.JmsConnectionFactory;
import com.ibm.msg.client.jms.JmsFactoryFactory;
import com.ibm.msg.client.wmq.WMQConstants;

/**
 * Continuously scans a configured list of IBM MQ queues and drains
 * (consumes/acknowledges) any pending messages, logging how many
 * messages were removed from each queue.
 *
 * Consuming a message with AUTO_ACKNOWLEDGE permanently removes it from
 * the queue, so only GET authority is required (no delete/admin rights).
 *
 * Usage: java -jar mq-queue-drainer.jar [path/to/config.properties]
 * If no argument is given, config.properties is loaded from the classpath.
 */
public class QueueDrainer {

    private static final Logger LOG = Logger.getLogger(QueueDrainer.class.getName());

    /** How long (ms) to wait for a message before deciding the queue is empty. */
    private static final long RECEIVE_TIMEOUT_MS = 500;

    private final Properties config;
    private final List<String> queueNames = new ArrayList<String>();
    private final long pollIntervalMs;
    private final boolean useJndi;

    private Connection connection;
    private Session session;
    private InitialContext jndiContext;

    public static void main(String[] args) throws Exception {
        Properties config = loadConfig(args.length > 0 ? args[0] : null);
        new QueueDrainer(config).run();
    }

    public QueueDrainer(Properties config) {
        this.config = config;

        String queues = required(config, "queues");
        for (String q : queues.split(",")) {
            String trimmed = q.trim();
            if (!trimmed.isEmpty()) {
                queueNames.add(trimmed);
            }
        }
        if (queueNames.isEmpty()) {
            throw new IllegalArgumentException("Property 'queues' must contain at least one queue name");
        }

        this.pollIntervalMs = Long.parseLong(config.getProperty("pollIntervalSeconds", "10")) * 1000L;
        this.useJndi = Boolean.parseBoolean(config.getProperty("jndi.enabled", "false"));

        LOG.info("Configured queues: " + queueNames);
        LOG.info("Poll interval: " + (pollIntervalMs / 1000) + "s, mode: " + (useJndi ? "JNDI" : "direct"));
    }

    /** Main loop: connect, then scan all queues forever. Reconnects on failure. */
    public void run() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                closeQuietly();
            }
        }));

        while (true) {
            try {
                connect();
                while (true) {
                    scanAllQueues();
                    Thread.sleep(pollIntervalMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.info("Interrupted, shutting down.");
                return;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Connection problem, reconnecting in 10s: " + e.getMessage(), e);
                closeQuietly();
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void scanAllQueues() throws JMSException {
        JMSException lastError = null;
        int failures = 0;
        for (String queueName : queueNames) {
            try {
                int drained = drainQueue(queueName);
                if (drained > 0) {
                    LOG.info("Drained " + drained + " message(s) from queue [" + queueName + "]");
                } else {
                    LOG.fine("Queue [" + queueName + "] is empty");
                }
            } catch (JMSException e) {
                // A queue-level problem (e.g. MQRC 2045 on a remote/alias queue,
                // 2035 not authorized, 2085 unknown queue) should not stop the
                // scan of the remaining queues.
                failures++;
                lastError = e;
                LOG.warning("Cannot drain queue [" + queueName + "]: " + e.getMessage());
            } catch (RuntimeException e) {
                // e.g. JNDI lookup failure for one queue.
                failures++;
                LOG.warning("Cannot drain queue [" + queueName + "]: " + e.getMessage());
            }
        }
        // If every queue failed the connection itself is probably broken;
        // propagate so the outer loop reconnects.
        if (failures == queueNames.size() && lastError != null) {
            throw lastError;
        }
    }

    /** Consumes every pending message on the queue and returns the count. */
    private int drainQueue(String queueName) throws JMSException {
        Destination destination = resolveQueue(queueName);
        MessageConsumer consumer = session.createConsumer(destination);
        int count = 0;
        try {
            while (true) {
                Message message = consumer.receive(RECEIVE_TIMEOUT_MS);
                if (message == null) {
                    break;
                }
                // AUTO_ACKNOWLEDGE: the receive itself acknowledges and
                // removes the message from the queue.
                count++;
            }
        } finally {
            consumer.close();
        }
        return count;
    }

    private Destination resolveQueue(String queueName) throws JMSException {
        if (useJndi) {
            try {
                return (Destination) jndiContext.lookup(queueName);
            } catch (Exception e) {
                throw new IllegalStateException("JNDI lookup failed for queue '" + queueName + "'", e);
            }
        }
        return session.createQueue("queue:///" + queueName);
    }

    private void connect() throws Exception {
        ConnectionFactory factory = useJndi ? jndiConnectionFactory() : directConnectionFactory();

        String user = config.getProperty("mq.user", "");
        String password = config.getProperty("mq.password", "");
        if (user.isEmpty()) {
            connection = factory.createConnection();
        } else {
            connection = factory.createConnection(user, password);
        }
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        connection.start();
        LOG.info("Connected to MQ.");
    }

    /** Builds a connection factory from mq.* properties (host/port/channel). */
    private ConnectionFactory directConnectionFactory() throws JMSException {
        JmsFactoryFactory ff = JmsFactoryFactory.getInstance(WMQConstants.WMQ_PROVIDER);
        JmsConnectionFactory cf = ff.createConnectionFactory();
        cf.setStringProperty(WMQConstants.WMQ_HOST_NAME, required(config, "mq.host"));
        cf.setIntProperty(WMQConstants.WMQ_PORT, Integer.parseInt(config.getProperty("mq.port", "1414")));
        cf.setStringProperty(WMQConstants.WMQ_CHANNEL, required(config, "mq.channel"));
        cf.setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_CLIENT);
        cf.setStringProperty(WMQConstants.WMQ_QUEUE_MANAGER, required(config, "mq.queueManager"));
        cf.setStringProperty(WMQConstants.WMQ_APPLICATIONNAME, "mq-queue-drainer");
        return cf;
    }

    /** Looks up the connection factory via JNDI (jndi.* properties). */
    private ConnectionFactory jndiConnectionFactory() throws Exception {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, required(config, "jndi.initialContextFactory"));
        env.put(Context.PROVIDER_URL, required(config, "jndi.providerUrl"));
        jndiContext = new InitialContext(env);
        return (ConnectionFactory) jndiContext.lookup(required(config, "jndi.connectionFactory"));
    }

    private void closeQuietly() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (jndiContext != null) {
                jndiContext.close();
            }
        } catch (Exception ignored) {
        }
        session = null;
        connection = null;
        jndiContext = null;
    }

    private static String required(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required configuration property: " + key);
        }
        return value.trim();
    }

    private static Properties loadConfig(String path) throws Exception {
        Properties props = new Properties();
        InputStream in;
        if (path != null) {
            in = new FileInputStream(path);
        } else {
            in = QueueDrainer.class.getClassLoader().getResourceAsStream("config.properties");
            if (in == null) {
                throw new IllegalArgumentException(
                        "No config file argument given and no config.properties found on classpath");
            }
        }
        try {
            props.load(in);
        } finally {
            in.close();
        }
        return props;
    }
}
