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
package org.hyperledger.besu.plugin.services.storage.rocksdb.configuration;

public class RocksDBFactoryConfiguration {

  private final int maxOpenFiles;
  private final int maxBackgroundCompactions;
  private final int backgroundThreadCount;
  private final long cacheCapacity;
  private final int defaultLockTimeout;

  public RocksDBFactoryConfiguration(
      final int maxOpenFiles,
      final int maxBackgroundCompactions,
      final int backgroundThreadCount,
      final long cacheCapacity,
      final int defaultLockTimeout) {
    this.maxBackgroundCompactions = maxBackgroundCompactions;
    this.backgroundThreadCount = backgroundThreadCount;
    this.maxOpenFiles = maxOpenFiles;
    this.cacheCapacity = cacheCapacity;
    this.defaultLockTimeout = defaultLockTimeout;
  }

  public int getMaxOpenFiles() {
    return maxOpenFiles;
  }

  public int getMaxBackgroundCompactions() {
    return maxBackgroundCompactions;
  }

  public int getBackgroundThreadCount() {
    return backgroundThreadCount;
  }

  public long getCacheCapacity() {
    return cacheCapacity;
  }

  public int getDefaultLockTimeout() {
    return defaultLockTimeout;
  }
}
