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
package org.hyperledger.besu.ethereum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.BlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Collection;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MainnetBlockValidatorTest {

  private final BlockchainSetupUtil chainUtil = BlockchainSetupUtil.forMainnet();
  private final Block badBlock = chainUtil.getBlock(3);
  private final Block badBlockParent = chainUtil.getBlock(2);

  private final MutableBlockchain blockchain = spy(chainUtil.getBlockchain());
  private final ProtocolContext protocolContext = mock(ProtocolContext.class);
  private final WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);
  private final MutableWorldState worldState = mock(MutableWorldState.class);
  private final BadBlockManager badBlockManager = new BadBlockManager();
  private final BlockProcessor blockProcessor = mock(BlockProcessor.class);
  private final BlockHeaderValidator blockHeaderValidator = mock(BlockHeaderValidator.class);
  private final BlockBodyValidator blockBodyValidator = mock(BlockBodyValidator.class);

  private final MainnetBlockValidator mainnetBlockValidator =
      new MainnetBlockValidator(
          blockHeaderValidator, blockBodyValidator, blockProcessor, badBlockManager);

  @BeforeEach
  public void setup() {
    chainUtil.importFirstBlocks(4);
    final BlockProcessingResult successfulProcessingResult =
        new BlockProcessingResult(Optional.empty(), false);

    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    when(protocolContext.getWorldStateArchive()).thenReturn(worldStateArchive);
    when(worldStateArchive.getMutable(any(BlockHeader.class), anyBoolean()))
        .thenReturn(Optional.of(worldState));
    when(worldStateArchive.getMutable(any(Hash.class), any(Hash.class)))
        .thenReturn(Optional.of(worldState));
    when(worldStateArchive.getMutable()).thenReturn(worldState);
    when(blockHeaderValidator.validateHeader(any(), any(), any())).thenReturn(true);
    when(blockHeaderValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);
    when(blockBodyValidator.validateBody(any(), any(), any(), any(), any())).thenReturn(true);
    when(blockBodyValidator.validateBodyLight(any(), any(), any(), any())).thenReturn(true);
    when(blockProcessor.processBlock(any(), any(), any())).thenReturn(successfulProcessingResult);
    when(blockProcessor.processBlock(any(), any(), any(), any()))
        .thenReturn(successfulProcessingResult);
    when(blockProcessor.processBlock(any(), any(), any(), any(), any()))
        .thenReturn(successfulProcessingResult);
    when(blockProcessor.processBlock(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(successfulProcessingResult);

    assertNoBadBlocks();
  }

  @Test
  public void shouldNotMarkBadBlockOnSuccess() {
    BlockProcessingResult result =
        mainnetBlockValidator.validateAndProcessBlock(
            protocolContext,
            badBlock,
            HeaderValidationMode.DETACHED_ONLY,
            HeaderValidationMode.DETACHED_ONLY);

    assertThat(result.isSuccessful()).isTrue();
    assertNoBadBlocks();
  }

  @Test
  public void shouldDetectAndCacheInvalidBlocksWhenParentBlockNotPresent() {
    final Hash parentHash = badBlockParent.getHash();
    doReturn(Optional.empty()).when(blockchain).getBlockHeader(eq(parentHash));

    BlockProcessingResult result =
        mainnetBlockValidator.validateAndProcessBlock(
            protocolContext,
            badBlock,
            HeaderValidationMode.DETACHED_ONLY,
            HeaderValidationMode.DETACHED_ONLY);

    final String expectedError = "Parent block with hash " + parentHash + " not present";
    assertValidationFailed(result, expectedError);
    assertBadBlockIsTracked();
  }

  @Test
  public void shouldDetectAndCacheInvalidBlocksWhenHeaderInvalid() {
    when(blockHeaderValidator.validateHeader(
            any(BlockHeader.class),
            any(BlockHeader.class),
            eq(protocolContext),
            eq(HeaderValidationMode.DETACHED_ONLY)))
        .thenReturn(false);

    BlockProcessingResult result =
        mainnetBlockValidator.validateAndProcessBlock(
            protocolContext,
            badBlock,
            HeaderValidationMode.DETACHED_ONLY,
            HeaderValidationMode.DETACHED_ONLY);

    final String expectedError = "Header validation failed (DETACHED_ONLY)";
    assertValidationFailed(result, expectedError);
    assertBadBlockIsTracked();
  }

  @Test
  public void shouldDetectAndCacheInvalidBlocksWhenBlockBodyInvalid() {
    when(blockBodyValidator.validateBody(any(), eq(badBlock), any(), any(), any()))
        .thenReturn(false);

    BlockProcessingResult result =
        mainnetBlockValidator.validateAndProcessBlock(
            protocolContext,
            badBlock,
            HeaderValidationMode.DETACHED_ONLY,
            HeaderValidationMode.DETACHED_ONLY);

    final String expectedError = "failed to validate output of imported block";
    assertValidationFailed(result, expectedError);
    assertBadBlockIsTracked();
  }

  @Test
  public void shouldDetectAndCacheInvalidBlocksWhenParentWorldStateNotAvailable() {
    when(worldStateArchive.getMutable(eq(badBlockParent.getHeader()), anyBoolean()))
        .thenReturn(Optional.empty());

    BlockProcessingResult result =
        mainnetBlockValidator.validateAndProcessBlock(
            protocolContext,
            badBlock,
            HeaderValidationMode.DETACHED_ONLY,
            HeaderValidationMode.DETACHED_ONLY);

    final String expectedError =
        "Unable to process block because parent world state "
            + badBlockParent.getHeader().getStateRoot()
            + " is not available";
    assertValidationFailed(result, expectedError);
    assertBadBlockIsTracked();
  }

  @Test
  public void shouldDetectAndCacheInvalidBlocksWhenProcessBlockFailed() {
    when(blockProcessor.processBlock(eq(blockchain), any(MutableWorldState.class), eq(badBlock)))
        .thenReturn(BlockProcessingResult.FAILED);

    BlockProcessingResult result =
        mainnetBlockValidator.validateAndProcessBlock(
            protocolContext,
            badBlock,
            HeaderValidationMode.DETACHED_ONLY,
            HeaderValidationMode.DETACHED_ONLY);

    final String expectedError = "processing failed";
    assertValidationFailed(result, expectedError);
    assertBadBlockIsTracked();
  }

  @Test
  public void when_shouldRecordBadBlockIsFalse_Expect_BlockNotAddedToBadBlockManager() {
    when(blockProcessor.processBlock(eq(blockchain), any(MutableWorldState.class), eq(badBlock)))
        .thenReturn(BlockProcessingResult.FAILED);

    BlockProcessingResult result =
        mainnetBlockValidator.validateAndProcessBlock(
            protocolContext,
            badBlock,
            HeaderValidationMode.DETACHED_ONLY,
            HeaderValidationMode.DETACHED_ONLY,
            false,
            false);

    assertThat(result.isFailed()).isTrue();
    assertNoBadBlocks();
  }

  @Test
  public void when_shouldRecordBadBlockIsTrue_Expect_BlockAddedToBadBlockManager() {
    when(blockProcessor.processBlock(eq(blockchain), any(MutableWorldState.class), eq(badBlock)))
        .thenReturn(BlockProcessingResult.FAILED);

    BlockProcessingResult result =
        mainnetBlockValidator.validateAndProcessBlock(
            protocolContext,
            badBlock,
            HeaderValidationMode.DETACHED_ONLY,
            HeaderValidationMode.DETACHED_ONLY,
            false,
            true);

    assertThat(result.isFailed()).isTrue();
    assertBadBlockIsTracked();
  }

  @Test
  public void when_shouldRecordBadBlockIsNotSet_Expect_BlockAddedToBadBlockManager() {
    when(blockProcessor.processBlock(eq(blockchain), any(MutableWorldState.class), eq(badBlock)))
        .thenReturn(BlockProcessingResult.FAILED);

    BlockProcessingResult result =
        mainnetBlockValidator.validateAndProcessBlock(
            protocolContext,
            badBlock,
            HeaderValidationMode.DETACHED_ONLY,
            HeaderValidationMode.DETACHED_ONLY,
            false);

    assertThat(result.isFailed()).isTrue();
    assertBadBlockIsTracked();
  }

  private void assertNoBadBlocks() {
    assertThat(badBlockManager.getBadBlocks()).isEmpty();
  }

  private void assertBadBlockIsTracked() {
    final Collection<Block> badBlocks = badBlockManager.getBadBlocks();
    assertThat(badBlocks).hasSize(1);
    assertThat(badBlocks).containsExactly(badBlock);
    assertThat(badBlockManager.getBadBlock(badBlock.getHash())).contains(badBlock);
  }

  private void assertValidationFailed(
      final BlockProcessingResult result, final String expectedError) {
    assertThat(result.isFailed()).isTrue();
    assertThat(result.errorMessage).isPresent();
    assertThat(result.errorMessage.get()).containsIgnoringWhitespaces(expectedError);
  }
}
