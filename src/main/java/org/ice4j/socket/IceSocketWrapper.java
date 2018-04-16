/* See LICENSE.md for license information */
package org.ice4j.socket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.Semaphore;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.IoSession;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.nio.IceTransport;
import org.ice4j.stack.RawMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract socket wrapper that define a socket that could be UDP, TCP...
 *
 * @author Paul Gregoire
 */
public abstract class IceSocketWrapper {

    protected final Logger logger = LoggerFactory.getLogger(IceSocketWrapper.class);

    protected Semaphore lock = new Semaphore(1, true);

    protected TransportAddress transportAddress;

    protected TransportAddress remoteTransportAddress;

    public boolean closed;

    /**
     * IoSession for this socket / connection; will be one of type NioDatagramSession for UDP or NioSocketSession for TCP.
     */
    protected IoSession session;

    /**
     * Socket timeout.
     */
    protected int soTimeout;

    /**
     * The message queue is where incoming messages are added that were not otherwise processed (ie. DTLS etc..).
     */
    protected LinkedTransferQueue<RawMessage> rawMessageQueue = new LinkedTransferQueue<>();

    /**
     * Reusable IoFutureListener for connect.
     */
    protected final IoFutureListener<ConnectFuture> connectListener = new IoFutureListener<ConnectFuture>() {

        @Override
        public void operationComplete(ConnectFuture future) {
            if (!future.isConnected()) {
                logger.warn("Connect failed");
            }
        }

    };

    /**
     * Reusable IoFutureListener for writes.
     */
    protected final IoFutureListener<WriteFuture> writeListener = new IoFutureListener<WriteFuture>() {

        @Override
        public void operationComplete(WriteFuture future) {
            if (!future.isWritten()) {
                logger.debug("Write failed");
            }
        }

    };

    IceSocketWrapper(IoSession session) {
        this.session = session;
    }

    /**
     * Sends an IoBuffer from this socket. It is a utility method to provide a common way to send for both UDP and TCP socket.
     *
     * @param buf IoBuffer to send
     * @param destAddress destination SocketAddress to send to
     * @throws IOException if something goes wrong
     */
    public abstract void send(IoBuffer buf, SocketAddress destAddress) throws IOException;

    /**
     * Sends a DatagramPacket from this socket. It is a utility method to provide a common way to send for both UDP and TCP socket.
     *
     * @param p DatagramPacket to send
     * @throws IOException if something goes wrong
     */
    public abstract void send(DatagramPacket p) throws IOException;

    /**
     * Returns true if closed or unbound and false otherwise.
     * 
     * @return true = not open, false = not closed
     */
    public boolean isClosed() {
        if (session != null) {
            closed = session.isClosing(); // covers closing and / or closed
        }
        return closed;
    }

    /**
     * Closes the channel.
     */
    public void close() {
        if (session != null) {
            logger.debug("close: {}", session.getId());
            try {
                @SuppressWarnings("unused")
                CloseFuture future = session.closeNow();
                // wait until the connection is closed
                //future.awaitUninterruptibly();
                // now connection should be closed.
                //assert future.isClosed();
            } catch (Throwable t) {
                logger.warn("Fail on close", t);
            }
        }
        // clear out raw messages lingering around at close
        rawMessageQueue.clear();
    }

    /**
     * Get local address.
     *
     * @return local address
     */
    public abstract InetAddress getLocalAddress();

    /**
     * Get local port.
     *
     * @return local port
     */
    public abstract int getLocalPort();

    /**
     * Get socket address.
     *
     * @return socket address
     */
    public abstract SocketAddress getLocalSocketAddress();

    /**
     * Sets the IoSession for this socket wrapper.
     * 
     * @param session
     */
    public void setSession(IoSession session) {
        logger.trace("setSession - new: {} existing: {}", session, this.session);
        this.session = session;
        if (session != null) {
            session.setAttribute(IceTransport.Ice.CONNECTION, this);
        }
    }

    /**
     * Returns an IoSession or null.
     *
     * @return IoSession if one exists or null otherwise
     */
    public IoSession getSession() {
        return session;
    }

    /**
     * Returns TransportAddress for the wrapped socket implementation.
     * 
     * @return transport address
     */
    public TransportAddress getTransportAddress() {
        logger.debug("getTransportAddress: {} session: {}", transportAddress, session);
        if (transportAddress == null && session != null) {
            if (session.getTransportMetadata().isConnectionless()) {
                transportAddress = new TransportAddress((InetSocketAddress) session.getLocalAddress(), Transport.UDP);
            } else {
                transportAddress = new TransportAddress((InetSocketAddress) session.getLocalAddress(), Transport.TCP);
            }
        }
        return transportAddress;
    }

    /**
     * Sets the TransportAddress of the remote end-point.
     * 
     * @param remoteAddress address
     */
    public void setRemoteTransportAddress(TransportAddress remoteAddress) {
        // only set remote address for TCP
        if (!session.getTransportMetadata().isConnectionless()) {
            this.remoteTransportAddress = remoteAddress;
        }
    }

    public TransportAddress getRemoteTransportAddress() {
        return remoteTransportAddress;
    }

    /**
     * Sets the socket timeout.
     */
    public void setSoTimeout(int timeout) throws SocketException {
        soTimeout = timeout;
    }

    /**
     * Returns the raw message queue, which shouldn't contain any STUN/TURN messages.
     * 
     * @return rawMessageQueue
     */
    public LinkedTransferQueue<RawMessage> getRawMessageQueue() {
        return rawMessageQueue;
    }

    /**
     * Returns whether or not this is a TCP wrapper, based on the instance type.
     * 
     * @return true if TCP and false otherwise
     */
    public boolean isTCP() {
        return (this instanceof IceTcpSocketWrapper);
    }

    /**
     * Returns whether or not this is a UDP wrapper, based on the instance type.
     * 
     * @return true if UDP and false otherwise
     */
    public boolean isUDP() {
        return (this instanceof IceUdpSocketWrapper);
    }

    /**
     * Builder for immutable IceSocketWrapper instance. If the IoSession is connection-less, an IceUdpSocketWrapper is returned; otherwise
     * an IceTcpSocketWrapper is returned.
     * 
     * @param session IoSession for the socket
     * @return IceSocketWrapper for the given session type
     * @throws IOException
     */
    public final static IceSocketWrapper build(IoSession session) throws IOException {
        if (session.getTransportMetadata().isConnectionless()) {
            return new IceUdpSocketWrapper(session);
        }
        return new IceTcpSocketWrapper(session);
    }

}
