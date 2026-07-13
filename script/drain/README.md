# mq-queue-drainer

Java 8 / Maven tool that continuously scans a configurable list of IBM MQ
queues and drains (consumes + acknowledges) any pending messages, logging how
many messages were removed from each queue.

Messages are removed by **consuming** them with `AUTO_ACKNOWLEDGE`, so the
program only needs GET (consume) authority on the queues — no delete or admin
permissions.

## Build

```
mvn clean package
```

This produces a runnable fat jar at `target/mq-queue-drainer-1.0.0.jar`.

## Run

```
java -jar target/mq-queue-drainer-1.0.0.jar path/to/config.properties
```

If no argument is given, `config.properties` is loaded from the classpath
(the sample under `src/main/resources` is baked into the jar).

## Configuration

See `src/main/resources/config.properties` for a commented sample. Key
properties:

| Property              | Meaning                                              |
|-----------------------|------------------------------------------------------|
| `queues`              | Comma-separated list of queues to drain              |
| `pollIntervalSeconds` | Seconds between scans of the queue list (default 10) |
| `mq.user` / `mq.password` | Credentials (leave `mq.user` empty for none)     |

### Direct mode (default)

`jndi.enabled=false`. Connects using `mq.host`, `mq.port`, `mq.channel`,
`mq.queueManager`, and the entries in `queues` are actual MQ queue names
(e.g. `DEV.QUEUE.1`).

### JNDI mode

`jndi.enabled=true`. The connection factory and each queue are looked up by
JNDI name using `jndi.initialContextFactory`, `jndi.providerUrl`, and
`jndi.connectionFactory`; the entries in `queues` are JNDI names.

Note: the file-system context factory
(`com.sun.jndi.fscontext.RefFSContextFactory`, for `.bindings` files created
with JMSAdmin) is **not** part of the JDK or the allclient jar — if you use
it, add the `fscontext` jar to the classpath. The LDAP context factory
(`com.sun.jndi.ldap.LdapCtxFactory`) is built into the JDK.

## Behavior

- Every `pollIntervalSeconds`, each configured queue is drained to empty and
  the number of consumed messages is logged:
  `Drained 42 message(s) from queue [DEV.QUEUE.1]`
- On connection failure the program logs a warning, waits 10 seconds, and
  reconnects — it never exits on its own (stop with Ctrl+C).
