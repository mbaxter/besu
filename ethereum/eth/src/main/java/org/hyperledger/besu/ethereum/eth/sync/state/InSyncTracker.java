/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.ethereum.eth.sync.state;

import org.hyperledger.besu.ethereum.chain.ChainHead;
import org.hyperledger.besu.ethereum.core.Synchronizer.InSyncListener;
import org.hyperledger.besu.ethereum.eth.manager.ChainHeadEstimate;
import org.hyperledger.besu.util.Subscribers;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Tracks the sync status of a node within the specific {@code syncTolerance}. The first event
 * emitted to any listener will be an out-of-sync event. If the node is in sync and remains in sync,
 * no events will be emitted to any listeners.
 */
class InSyncTracker {
  private volatile InSyncState state = InSyncState.UNKNOWN;
  // If the local chain is no more than {@code syncTolerance} behind the estimated highest chain,
  // then the tracker considers this local node to be in sync
  private final long syncTolerance;

  private final Subscribers<InSyncListener> inSyncSubscribers = Subscribers.create();

  private InSyncTracker(final long syncTolerance) {
    this.syncTolerance = syncTolerance;
  }

  public static InSyncTracker create(final long syncTolerance) {
    return new InSyncTracker(syncTolerance);
  }

  public static boolean isInSync(
      final ChainHead localChain, final ChainHeadEstimate remoteChain, final long syncTolerance) {
    final boolean inSyncByHeight =
        remoteChain.getEstimatedHeight() - localChain.getHeight() <= syncTolerance;
    return inSyncByHeight || !remoteChain.chainIsBetterThan(localChain);
  }

  synchronized void checkState(
      final ChainHead localChain,
      final Optional<ChainHeadEstimate> syncTargetChain,
      final Optional<ChainHeadEstimate> bestPeerChain) {
    final boolean currentSyncStatus =
        currentSyncStatus(localChain, syncTargetChain, bestPeerChain).orElse(true);

    final InSyncState newState = InSyncState.fromInSync(currentSyncStatus);
    if (state != newState) {
      // Sync status has changed, notify subscribers
      state = newState;
      state.ifKnown(value -> inSyncSubscribers.forEach(c -> c.onInSyncStatusChange(value)));
    }
  }

  private Optional<Boolean> currentSyncStatus(
      final ChainHead localChain,
      final Optional<ChainHeadEstimate> syncTargetChain,
      final Optional<ChainHeadEstimate> bestPeerChain) {
    final Optional<Boolean> inSyncWithSyncTarget =
        syncTargetChain.map(remote -> isInSync(localChain, remote));
    final Optional<Boolean> inSyncWithBestPeer =
        bestPeerChain.map(remote -> isInSync(localChain, remote));
    // If we're out of sync with either peer, we're out of sync
    if (inSyncWithSyncTarget.isPresent() && !inSyncWithSyncTarget.get()) {
      return Optional.of(false);
    }
    if (inSyncWithBestPeer.isPresent() && !inSyncWithBestPeer.get()) {
      return Optional.of(false);
    }
    // Otherwise, if either peer is in sync, we're in sync
    return inSyncWithSyncTarget.or(() -> inSyncWithBestPeer);
  }

  public synchronized long addInSyncListener(final InSyncListener subscriber) {
    // If our state is known, fire an event to let the listener know
    return inSyncSubscribers.subscribe(subscriber);
  }

  public boolean removeInSyncListener(final long subscriptionId) {
    return inSyncSubscribers.unsubscribe(subscriptionId);
  }

  public int getListenerCount() {
    return inSyncSubscribers.getSubscriberCount();
  }

  private boolean isInSync(final ChainHead localChain, final ChainHeadEstimate remoteChain) {
    return isInSync(localChain, remoteChain, syncTolerance);
  }

  private enum InSyncState {
    UNKNOWN(Optional.empty()),
    IN_SYNC(Optional.of(true)),
    OUT_OF_SYNC(Optional.of(false));

    private final Optional<Boolean> inSync;

    InSyncState(final Optional<Boolean> inSync) {
      this.inSync = inSync;
    }

    static InSyncState fromInSync(final boolean inSync) {
      return inSync ? IN_SYNC : OUT_OF_SYNC;
    }

    public void ifKnown(final Consumer<Boolean> handler) {
      inSync.ifPresent(handler::accept);
    }
  }
}
