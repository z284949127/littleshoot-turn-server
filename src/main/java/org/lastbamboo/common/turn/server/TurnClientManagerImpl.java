package org.lastbamboo.common.turn.server;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.lastbamboo.common.nio.SelectorManager;
import org.lastbamboo.common.protocol.CloseListener;
import org.lastbamboo.common.protocol.ReaderWriter;
import org.lastbamboo.common.turn.message.TurnMessageFactory;
import org.lastbamboo.common.turn.util.RandomNonCollidingPortGenerator;
import org.lastbamboo.common.util.NetworkUtils;

/**
 * Manages endpoint bindings for TURN clients.  This includes allocating
 * bindings, timing out bindings, etc.
 */
public final class TurnClientManagerImpl implements TurnClientManager, 
    CloseListener
    {
    
    /**
     * Logger for this class.
     */
    private static final Log LOG = 
        LogFactory.getLog(TurnClientManagerImpl.class);
    
    /**
     * Map of <code>InetSocketAddress</code>es to TURN clients. 
     */
    private final Map<ReaderWriter, TurnClient> m_clientMappings = 
        new ConcurrentHashMap<ReaderWriter, TurnClient>();

    private final SelectorManager m_selectorManager;

    private final TurnMessageFactory m_turnMessageFactory;

    private final RandomNonCollidingPortGenerator m_portGenerator;
    
    /**
     * Manager for clients this TURN server is relaying data on behalf of.
     * @param manager The manager for the NIO selector.
     * @param messageFactory The factory for creating TURN messages.
     * @param portGenerator Class for generating ports to use for new clients.
     */
    public TurnClientManagerImpl(final SelectorManager manager, 
        final TurnMessageFactory messageFactory,
        final RandomNonCollidingPortGenerator portGenerator)
        {
        this.m_selectorManager = manager;
        this.m_turnMessageFactory = messageFactory;
        this.m_portGenerator = portGenerator;
        }
    
    public TurnClient allocateBinding(final ReaderWriter readerWriter) 
        {
        final int newPort = this.m_portGenerator.createRandomPort();
        try
            {
            final InetSocketAddress allocatedAddress = 
                new InetSocketAddress(NetworkUtils.getLocalHost(), newPort);
            
            final TurnClient turnClient = 
                new TurnClientImpl(allocatedAddress, readerWriter, 
                    this.m_selectorManager, this.m_turnMessageFactory);
            turnClient.startServer();
            this.m_clientMappings.put(readerWriter, turnClient);
            
            // Make sure we're notified of close events so we can clean up 
            // data structures.
            readerWriter.addCloseListener(this);
            return turnClient;
            }
        catch (final UnknownHostException e)
            {
            // Should never happen.
            LOG.error("Could not resolve host", e);
            this.m_portGenerator.removePort(newPort);
            return null;
            }
        }

    public void onClose(final ReaderWriter readerWriter)
        {
        LOG.trace("Could not open socket...removing binding...");
        removeBinding(readerWriter);
        }

    public TurnClient getTurnClient(final ReaderWriter readerWriter)
        {
        return this.m_clientMappings.get(readerWriter);
        }

    public TurnClient removeBinding(final ReaderWriter readerWriter)
        {
        LOG.trace("Removing binding for: "+readerWriter);
        final TurnClient client = this.m_clientMappings.remove(readerWriter);
        if (client != null)
            {
            client.close();
            final int port = client.getAllocatedSocketAddress().getPort();
            this.m_portGenerator.removePort(port);
            }
        return client;
        }
    }