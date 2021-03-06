# NIO Configuration
The following ice4j adjustments are available in the NIO version via environmental / system properties.

## Shared NIO server or per-instance
To utilize a single instance of `NioServer` for each `StunStack`, the `NIO_SHARED_MODE` property must be configured as `false`, to spawn a single static `NioServer` for all `StunStack` instances, use the default value of `true`.

## Send buffer
Configuration of the send buffer is handled via the `SO_SNDBUF` property. The default is 1500 and any target amount should take MTU size ~1500 into account.

## Receive buffer
Configuration of the receive buffer is handled via the `SO_RCVBUF` property. The default is 1500 and any target amount should take MTU size ~1500 into account.

## Send and Receive idle timeout
Send or receive may be detected as idle if they exceed the configured (in seconds) `SO_TIMEOUT` property which is defaulted to 30 seconds.

## Acceptor timeout
Timeout in seconds to wait for a bind or unbind operation to complete, the `ACCEPTOR_TIMEOUT` property is modifiable from the default of 2 seconds.

## Aggressive Acceptor reset
To prevent a possible deadlock caused by a failed bind or unbind event making the acceptor unresponsive, the `ACCEPTOR_RESET` option allows the acceptor to be reset on-the-fly.

## I/O thread priority
Setting the I/O thread priority is handled via the `IO_THREAD_PRIORITY` property. The default priority is 6 and the maximum available in Java is 10.

## NIO selector sleep milliseconds
Providing adequate time between NIO selector checks is handled via the `NIO_SELECTOR_SLEEP_MS` property. The default sleep time between checks is 10 milliseconds.

## Blocking or Non-blocking I/O
Setting the `IO_BLOCKING` to `true` will configure the internal services to use blocking I/O with TCP, instead of the default non-blocking implementation. This does not affect UDP connections.

## Private network host candidate handling
To skip the addition of `RemoteCandidate` instances originating on private networks on a `Component`, set `SKIP_REMOTE_PRIVATE_HOSTS` to `true`; otherwise the default value `false` or not-to-skip will be used.

# Server Startup
To add the options to your Red5 / Red5 Pro server startup, update the `JAVA_OPTS` line like so:
```
export JAVA_OPTS="$SECURITY_OPTS $JAVA_OPTS $JVM_OPTS $TOMCAT_OPTS $NATIVE -DSO_RCVBUF=3000 -DIO_THREAD_PRIORITY=6 -DNIO_SELECTOR_SLEEP_MS=10"
```
