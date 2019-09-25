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
package org.hyperledger.besu.tests.acceptance.dsl.account;

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.crypto.SECP256K1.PrivateKey;
import org.hyperledger.besu.crypto.SECP256K1.PublicKey;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.condition.account.ExpectAccountBalance;
import org.hyperledger.besu.tests.acceptance.dsl.condition.account.ExpectAccountBalanceNotChanging;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthTransactions;
import org.hyperledger.besu.util.bytes.Bytes32;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import org.web3j.crypto.Credentials;
import org.web3j.utils.Convert.Unit;

public class Account {

  private final EthTransactions eth;
  private final String name;
  private final Optional<PrivateKey> privateKey;
  private final Optional<PublicKey> publicKey;
  private final Address address;
  private long nonce = 0;

  private Account(
      final EthTransactions eth,
      final String name,
      final Address address,
      final Optional<KeyPair> keyPair) {
    this.name = name;
    this.privateKey = keyPair.map(KeyPair::getPrivateKey);
    this.publicKey = keyPair.map(KeyPair::getPublicKey);
    this.address = address;
    this.eth = eth;
  }

  private Account(final EthTransactions eth, final String name, final KeyPair keyPair) {
    this(
        eth,
        name,
        Address.extract(Hash.hash(keyPair.getPublicKey().getEncodedBytes())),
        Optional.of(keyPair));
  }

  public static Account create(final EthTransactions eth, final Address address) {
    return new Account(eth, address.toString(), address, Optional.empty());
  }

  public static Account create(final EthTransactions eth, final String name) {
    return new Account(eth, name, KeyPair.generate());
  }

  static Account fromPrivateKey(
      final EthTransactions eth, final String name, final String privateKey) {
    return new Account(
        eth, name, KeyPair.create(PrivateKey.create(Bytes32.fromHexString(privateKey))));
  }

  public Optional<Credentials> web3jCredentials() {
    if (!publicKey.isPresent() || !privateKey.isPresent()) {
      return Optional.empty();
    }
    return Optional.of(Credentials.create(privateKey.get().toString(), publicKey.get().toString()));
  }

  public Credentials web3jCredentialsOrThrow() {
    return web3jCredentials()
        .orElseThrow(() -> new IllegalStateException("Account is missing required signing key."));
  }

  public BigInteger getNextNonce() {
    return BigInteger.valueOf(nonce++);
  }

  public String getAddress() {
    return address.toString();
  }

  public Condition balanceEquals(final int expectedBalance) {
    return new ExpectAccountBalance(eth, this, BigDecimal.valueOf(expectedBalance), Unit.ETHER);
  }

  public Condition balanceEquals(final Amount expectedBalance) {
    return new ExpectAccountBalance(
        eth, this, expectedBalance.getValue(), expectedBalance.getUnit());
  }

  public Condition balanceDoesNotChange(final int startingBalance) {
    return new ExpectAccountBalanceNotChanging(
        eth, this, BigDecimal.valueOf(startingBalance), Unit.ETHER);
  }

  @Override
  public String toString() {
    return "Account{"
        + "eth="
        + eth
        + ", name='"
        + name
        + '\''
        + ", address="
        + address
        + ", nonce="
        + nonce
        + '}';
  }
}
