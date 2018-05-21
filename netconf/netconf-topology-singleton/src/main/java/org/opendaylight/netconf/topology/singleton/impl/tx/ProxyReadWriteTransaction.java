/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.tx;

import akka.actor.ActorRef;
import akka.dispatch.OnComplete;
import akka.util.Timeout;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.concurrent.GuardedBy;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.mdsal.common.api.MappingCheckedFuture;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

/**
 * ProxyReadWriteTransaction uses provided {@link ActorRef} to delegate method calls to master
 * {@link org.opendaylight.netconf.topology.singleton.impl.actors.ReadWriteTransactionActor}.
 */
public class ProxyReadWriteTransaction implements DOMDataReadWriteTransaction {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyReadWriteTransaction.class);

    private final RemoteDeviceId id;
    private final AtomicBoolean opened = new AtomicBoolean(true);

    @GuardedBy("queuedTxOperations")
    private final List<Consumer<ProxyTransactionFacade>> queuedTxOperations = new ArrayList<>();

    private volatile ProxyTransactionFacade transactionFacade;

    public ProxyReadWriteTransaction(final RemoteDeviceId id, final Future<Object> masterTxActorFuture,
            final ExecutionContext executionContext, final Timeout askTimeout) {
        this.id = id;

        masterTxActorFuture.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(final Throwable failure, final Object masterTxActor) {
                final ProxyTransactionFacade newTransactionFacade;
                if (failure != null) {
                    LOG.debug("{}: Failed to obtain master actor", id, failure);
                    newTransactionFacade = new FailedProxyTransactionFacade(id, failure);
                } else {
                    LOG.debug("{}: Obtained master actor {}", id, masterTxActor);
                    newTransactionFacade = new ActorProxyTransactionFacade((ActorRef)masterTxActor, id,
                            executionContext, askTimeout);
                }

                executePriorTransactionOperations(newTransactionFacade);
            }
        }, executionContext);
    }

    @Override
    public boolean cancel() {
        if (!opened.compareAndSet(true, false)) {
            return false;
        }

        processTransactionOperation(facade -> facade.cancel());
        return true;
    }

    @Override
    public CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        LOG.debug("{}: Read {} {}", id, store, path);

        final SettableFuture<Optional<NormalizedNode<?, ?>>> returnFuture = SettableFuture.create();
        processTransactionOperation(facade -> returnFuture.setFuture(facade.read(store, path)));
        return MappingCheckedFuture.create(returnFuture, ReadFailedException.MAPPER);
    }

    @Override
    public CheckedFuture<Boolean, ReadFailedException> exists(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        LOG.debug("{}: Exists {} {}", id, store, path);

        final SettableFuture<Boolean> returnFuture = SettableFuture.create();
        processTransactionOperation(facade -> returnFuture.setFuture(facade.exists(store, path)));
        return MappingCheckedFuture.create(returnFuture, ReadFailedException.MAPPER);
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        checkOpen();
        LOG.debug("{}: Delete {} {}", id, store, path);
        processTransactionOperation(facade -> facade.delete(store, path));
    }

    @Override
    public void put(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                    final NormalizedNode<?, ?> data) {
        checkOpen();
        LOG.debug("{}: Put {} {}", id, store, path);
        processTransactionOperation(facade -> facade.put(store, path, data));
    }

    @Override
    public void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                      final NormalizedNode<?, ?> data) {
        checkOpen();
        LOG.debug("{}: Merge {} {}", id, store, path);
        processTransactionOperation(facade -> facade.merge(store, path, data));
    }

    @Override
    public CheckedFuture<Void, TransactionCommitFailedException> submit() {
        Preconditions.checkState(opened.compareAndSet(true, false), "%s: Transaction is already closed", id);
        LOG.debug("{}: Commit", id);

        final SettableFuture<Void> returnFuture = SettableFuture.create();
        processTransactionOperation(facade -> returnFuture.setFuture(facade.submit()));
        return Futures.makeChecked(returnFuture, failure -> failure instanceof TransactionCommitFailedException
            ? (TransactionCommitFailedException)failure :
                new TransactionCommitFailedException(String.format("%s: Submit of transaction failed", id), failure));
    }

    @Override
    public ListenableFuture<RpcResult<TransactionStatus>> commit() {
        return Futures.transform(submit(), input -> RpcResultBuilder.success(TransactionStatus.SUBMITED).build());
    }

    @Override
    public Object getIdentifier() {
        return id;
    }

    private void processTransactionOperation(final Consumer<ProxyTransactionFacade> operation) {
        final ProxyTransactionFacade facadeOnEntry;
        synchronized (queuedTxOperations) {
            if (transactionFacade == null) {
                LOG.debug("{}: Queuing transaction operation", id);

                queuedTxOperations.add(operation);
                facadeOnEntry = null;
            }  else {
                facadeOnEntry = transactionFacade;
            }
        }

        if (facadeOnEntry != null) {
            operation.accept(facadeOnEntry);
        }
    }

    private void executePriorTransactionOperations(final ProxyTransactionFacade newTransactionFacade) {
        while (true) {
            // Access to queuedTxOperations and transactionFacade must be protected and atomic
            // (ie synchronized) with respect to #processTransactionOperation to handle timing
            // issues and ensure no ProxyTransactionFacade is missed and that they are processed
            // in the order they occurred.

            // We'll make a local copy of the queuedTxOperations list to handle re-entrancy
            // in case a transaction operation results in another transaction operation being
            // queued (eg a put operation from a client read Future callback that is notified
            // synchronously).
            final Collection<Consumer<ProxyTransactionFacade>> operationsBatch;
            synchronized (queuedTxOperations) {
                if (queuedTxOperations.isEmpty()) {
                    // We're done invoking the transaction operations so we can now publish the
                    // ProxyTransactionFacade.
                    transactionFacade = newTransactionFacade;
                    break;
                }

                operationsBatch = new ArrayList<>(queuedTxOperations);
                queuedTxOperations.clear();
            }

            // Invoke transaction operations outside the sync block to avoid unnecessary blocking.
            for (Consumer<ProxyTransactionFacade> oper : operationsBatch) {
                oper.accept(newTransactionFacade);
            }
        }
    }

    private void checkOpen() {
        Preconditions.checkState(opened.get(), "%s: Transaction is closed", id);
    }
}
