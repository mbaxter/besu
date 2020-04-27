/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.hyperledger.besu.plugin.services.storage.rocksdb;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

public class RocksDbKeyIterator implements Iterator<byte[]>, AutoCloseable {
  private static final Logger LOG = LogManager.getLogger();

  private final RocksIterator rocksIterator;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  private RocksDbKeyIterator(final RocksIterator rocksIterator) {
    this.rocksIterator = rocksIterator;
  }

  public static RocksDbKeyIterator create(final RocksIterator rocksIt) {
    return new RocksDbKeyIterator(rocksIt);
  }

  @Override
  public boolean hasNext() {
    assertOpen();
    return rocksIterator.isValid();
  }

  @Override
  public byte[] next() {
    assertOpen();
    try {
      rocksIterator.status();
    } catch (final RocksDBException e) {
      LOG.error("RocksDbEntryIterator encountered a problem while iterating.", e);
    }
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    final byte[] key = rocksIterator.key();
    rocksIterator.next();
    return key;
  }

  public Stream<byte[]> toStream() {
    assertOpen();
    final Spliterator<byte[]> split =
        Spliterators.spliteratorUnknownSize(
            this,
            Spliterator.IMMUTABLE
                | Spliterator.DISTINCT
                | Spliterator.NONNULL
                | Spliterator.ORDERED
                | Spliterator.SORTED);

    return StreamSupport.stream(split, false).onClose(this::close);
  }

  private void assertOpen() {
    if (closed.get()) {
      throw new IllegalStateException("Attempt to update a closed transaction");
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      rocksIterator.close();
    }
  }
}
