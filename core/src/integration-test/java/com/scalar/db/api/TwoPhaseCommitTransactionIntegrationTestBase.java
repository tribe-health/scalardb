package com.scalar.db.api;

import static com.scalar.db.util.TestUtils.assertResultsAreASubsetOf;
import static com.scalar.db.util.TestUtils.assertResultsContainsExactlyInAnyOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.google.common.collect.ImmutableList;
import com.scalar.db.api.Scan.Ordering;
import com.scalar.db.exception.storage.ExecutionException;
import com.scalar.db.exception.transaction.CommitException;
import com.scalar.db.exception.transaction.CrudException;
import com.scalar.db.exception.transaction.PreparationConflictException;
import com.scalar.db.exception.transaction.TransactionException;
import com.scalar.db.io.DataType;
import com.scalar.db.io.IntColumn;
import com.scalar.db.io.IntValue;
import com.scalar.db.io.Key;
import com.scalar.db.io.Value;
import com.scalar.db.service.TransactionFactory;
import com.scalar.db.util.TestUtils;
import com.scalar.db.util.TestUtils.ExpectedResult;
import com.scalar.db.util.TestUtils.ExpectedResult.ExpectedResultBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class TwoPhaseCommitTransactionIntegrationTestBase {

  private static final String NAMESPACE_BASE_NAME = "integration_testing_";
  private static final String TABLE = "test_table";
  private static final String ACCOUNT_ID = "account_id";
  private static final String ACCOUNT_TYPE = "account_type";
  private static final String BALANCE = "balance";
  private static final String SOME_COLUMN = "some_column";
  private static final int INITIAL_BALANCE = 1000;
  private static final int NUM_ACCOUNTS = 4;
  private static final int NUM_TYPES = 4;

  private DistributedTransactionAdmin admin;
  private TwoPhaseCommitTransactionManager manager;
  private String namespace;

  @BeforeAll
  public void beforeAll() throws Exception {
    initialize();
    String testName = getTestName();
    TransactionFactory factory =
        TransactionFactory.create(TestUtils.addSuffix(gerProperties(), testName));
    admin = factory.getTransactionAdmin();
    namespace = getNamespaceBaseName() + testName;
    createTables();
    manager = factory.getTwoPhaseCommitTransactionManager();
  }

  protected void initialize() throws Exception {}

  protected abstract String getTestName();

  protected abstract Properties gerProperties();

  protected String getNamespaceBaseName() {
    return NAMESPACE_BASE_NAME;
  }

  private void createTables() throws ExecutionException {
    Map<String, String> options = getCreationOptions();
    admin.createNamespace(namespace, true, options);
    admin.createTable(
        namespace,
        TABLE,
        TableMetadata.newBuilder()
            .addColumn(ACCOUNT_ID, DataType.INT)
            .addColumn(ACCOUNT_TYPE, DataType.INT)
            .addColumn(BALANCE, DataType.INT)
            .addColumn(SOME_COLUMN, DataType.INT)
            .addPartitionKey(ACCOUNT_ID)
            .addClusteringKey(ACCOUNT_TYPE)
            .addSecondaryIndex(SOME_COLUMN)
            .build(),
        true,
        options);
    admin.createCoordinatorTables(true, options);
  }

  protected Map<String, String> getCreationOptions() {
    return Collections.emptyMap();
  }

  @BeforeEach
  public void setUp() throws ExecutionException {
    admin.truncateTable(namespace, TABLE);
    admin.truncateCoordinatorTables();
  }

  @AfterAll
  public void afterAll() throws ExecutionException {
    dropTables();
    admin.close();
    manager.close();
  }

  private void dropTables() throws ExecutionException {
    admin.dropTable(namespace, TABLE);
    admin.dropNamespace(namespace);
    admin.dropCoordinatorTables();
  }

  @Test
  public void get_GetGivenForCommittedRecord_ShouldReturnRecord() throws TransactionException {
    // Arrange
    populateRecords();
    TwoPhaseCommitTransaction transaction = manager.start();
    Get get = prepareGet(0, 0);

    // Act
    Optional<Result> result = transaction.get(get);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get().getInt(ACCOUNT_ID)).isEqualTo(0);
    assertThat(result.get().getInt(ACCOUNT_TYPE)).isEqualTo(0);
    assertThat(getBalance(result.get())).isEqualTo(INITIAL_BALANCE);
    assertThat(result.get().getInt(SOME_COLUMN)).isEqualTo(0);
  }

  @Test
  public void get_GetWithProjectionGivenForCommittedRecord_ShouldReturnRecord()
      throws TransactionException {
    // Arrange
    populateRecords();
    TwoPhaseCommitTransaction transaction = manager.start();
    Get get =
        prepareGet(0, 0)
            .withProjection(ACCOUNT_ID)
            .withProjection(ACCOUNT_TYPE)
            .withProjection(BALANCE);

    // Act
    Optional<Result> result = transaction.get(get);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get().getInt(ACCOUNT_ID)).isEqualTo(0);
    assertThat(result.get().getInt(ACCOUNT_TYPE)).isEqualTo(0);
    assertThat(getBalance(result.get())).isEqualTo(INITIAL_BALANCE);
    assertThat(result.get().contains(SOME_COLUMN)).isFalse();
  }

  @Test
  public void scan_ScanGivenForCommittedRecord_ShouldReturnRecords() throws TransactionException {
    // Arrange
    populateRecords();
    TwoPhaseCommitTransaction transaction = manager.start();
    Scan scan = prepareScan(1, 0, 2);

    // Act
    List<Result> results = transaction.scan(scan);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    assertThat(results.size()).isEqualTo(3);
    assertThat(results.get(0).getInt(ACCOUNT_ID)).isEqualTo(1);
    assertThat(results.get(0).getInt(ACCOUNT_TYPE)).isEqualTo(0);
    assertThat(getBalance(results.get(0))).isEqualTo(INITIAL_BALANCE);
    assertThat(results.get(0).getInt(SOME_COLUMN)).isEqualTo(0);

    assertThat(results.get(1).getInt(ACCOUNT_ID)).isEqualTo(1);
    assertThat(results.get(1).getInt(ACCOUNT_TYPE)).isEqualTo(1);
    assertThat(getBalance(results.get(1))).isEqualTo(INITIAL_BALANCE);
    assertThat(results.get(1).getInt(SOME_COLUMN)).isEqualTo(1);

    assertThat(results.get(2).getInt(ACCOUNT_ID)).isEqualTo(1);
    assertThat(results.get(2).getInt(ACCOUNT_TYPE)).isEqualTo(2);
    assertThat(getBalance(results.get(2))).isEqualTo(INITIAL_BALANCE);
    assertThat(results.get(2).getInt(SOME_COLUMN)).isEqualTo(2);
  }

  @Test
  public void scan_ScanWithProjectionsGivenForCommittedRecord_ShouldReturnRecords()
      throws TransactionException {
    // Arrange
    populateRecords();
    TwoPhaseCommitTransaction transaction = manager.start();
    Scan scan =
        prepareScan(1, 0, 2)
            .withProjection(ACCOUNT_ID)
            .withProjection(ACCOUNT_TYPE)
            .withProjection(BALANCE);

    // Act
    List<Result> results = transaction.scan(scan);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    assertThat(results.size()).isEqualTo(3);
    assertThat(results.get(0).getInt(ACCOUNT_ID)).isEqualTo(1);
    assertThat(results.get(0).getInt(ACCOUNT_TYPE)).isEqualTo(0);
    assertThat(getBalance(results.get(0))).isEqualTo(INITIAL_BALANCE);
    assertThat(results.get(0).contains(SOME_COLUMN)).isFalse();

    assertThat(results.get(1).getInt(ACCOUNT_ID)).isEqualTo(1);
    assertThat(results.get(1).getInt(ACCOUNT_TYPE)).isEqualTo(1);
    assertThat(getBalance(results.get(1))).isEqualTo(INITIAL_BALANCE);
    assertThat(results.get(1).contains(SOME_COLUMN)).isFalse();

    assertThat(results.get(2).getInt(ACCOUNT_ID)).isEqualTo(1);
    assertThat(results.get(2).getInt(ACCOUNT_TYPE)).isEqualTo(2);
    assertThat(getBalance(results.get(2))).isEqualTo(INITIAL_BALANCE);
    assertThat(results.get(2).contains(SOME_COLUMN)).isFalse();
  }

  @Test
  public void scan_ScanWithOrderingGivenForCommittedRecord_ShouldReturnRecords()
      throws TransactionException {
    // Arrange
    populateRecords();
    TwoPhaseCommitTransaction transaction = manager.start();
    Scan scan = prepareScan(1, 0, 2).withOrdering(Ordering.desc(ACCOUNT_TYPE));

    // Act
    List<Result> results = transaction.scan(scan);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    assertThat(results.size()).isEqualTo(3);
    assertThat(results.get(0).getInt(ACCOUNT_ID)).isEqualTo(1);
    assertThat(results.get(0).getInt(ACCOUNT_TYPE)).isEqualTo(2);
    assertThat(getBalance(results.get(0))).isEqualTo(INITIAL_BALANCE);
    assertThat(results.get(0).getInt(SOME_COLUMN)).isEqualTo(2);

    assertThat(results.get(1).getInt(ACCOUNT_ID)).isEqualTo(1);
    assertThat(results.get(1).getInt(ACCOUNT_TYPE)).isEqualTo(1);
    assertThat(getBalance(results.get(1))).isEqualTo(INITIAL_BALANCE);
    assertThat(results.get(1).getInt(SOME_COLUMN)).isEqualTo(1);

    assertThat(results.get(2).getInt(ACCOUNT_ID)).isEqualTo(1);
    assertThat(results.get(2).getInt(ACCOUNT_TYPE)).isEqualTo(0);
    assertThat(getBalance(results.get(2))).isEqualTo(INITIAL_BALANCE);
    assertThat(results.get(2).getInt(SOME_COLUMN)).isEqualTo(0);
  }

  @Test
  public void scan_ScanWithLimitGivenForCommittedRecord_ShouldReturnRecords()
      throws TransactionException {
    // Arrange
    populateRecords();
    TwoPhaseCommitTransaction transaction = manager.start();
    Scan scan = prepareScan(1, 0, 2).withLimit(2);

    // Act
    List<Result> results = transaction.scan(scan);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    assertThat(results.size()).isEqualTo(2);
    assertThat(results.get(0).getInt(ACCOUNT_ID)).isEqualTo(1);
    assertThat(results.get(0).getInt(ACCOUNT_TYPE)).isEqualTo(0);
    assertThat(getBalance(results.get(0))).isEqualTo(INITIAL_BALANCE);
    assertThat(results.get(0).getInt(SOME_COLUMN)).isEqualTo(0);

    assertThat(results.get(1).getInt(ACCOUNT_ID)).isEqualTo(1);
    assertThat(results.get(1).getInt(ACCOUNT_TYPE)).isEqualTo(1);
    assertThat(getBalance(results.get(1))).isEqualTo(INITIAL_BALANCE);
    assertThat(results.get(1).getInt(SOME_COLUMN)).isEqualTo(1);
  }

  @Test
  public void get_GetGivenForNonExisting_ShouldReturnEmpty() throws TransactionException {
    // Arrange
    populateRecords();
    TwoPhaseCommitTransaction transaction = manager.start();
    Get get = prepareGet(0, 4);

    // Act
    Optional<Result> result = transaction.get(get);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    assertThat(result.isPresent()).isFalse();
  }

  @Test
  public void scan_ScanGivenForNonExisting_ShouldReturnEmpty() throws TransactionException {
    // Arrange
    populateRecords();
    TwoPhaseCommitTransaction transaction = manager.start();
    Scan scan = prepareScan(0, 4, 4);

    // Act
    List<Result> results = transaction.scan(scan);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    assertThat(results.size()).isEqualTo(0);
  }

  @Test
  public void get_GetGivenForIndexColumn_ShouldReturnRecords() throws TransactionException {
    // Arrange
    TwoPhaseCommitTransaction transaction = manager.start();
    transaction.put(
        Put.newBuilder()
            .namespace(namespace)
            .table(TABLE)
            .partitionKey(Key.ofInt(ACCOUNT_ID, 1))
            .clusteringKey(Key.ofInt(ACCOUNT_TYPE, 2))
            .intValue(BALANCE, INITIAL_BALANCE)
            .intValue(SOME_COLUMN, 2)
            .build());
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    transaction = manager.start();
    Get getBuiltByConstructor =
        new Get(Key.ofInt(SOME_COLUMN, 2))
            .forNamespace(namespace)
            .forTable(TABLE)
            .withConsistency(Consistency.LINEARIZABLE);

    Get getBuiltByBuilder =
        Get.newBuilder()
            .namespace(namespace)
            .table(TABLE)
            .indexKey(Key.ofInt(SOME_COLUMN, 2))
            .build();

    // Act
    Optional<Result> result1 = transaction.get(getBuiltByConstructor);
    Optional<Result> result2 = transaction.get(getBuiltByBuilder);
    transaction.get(getBuiltByBuilder);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    assertThat(result1).isPresent();
    assertThat(result1.get().getInt(ACCOUNT_ID)).isEqualTo(1);
    assertThat(result1.get().getInt(ACCOUNT_TYPE)).isEqualTo(2);
    assertThat(getBalance(result1.get())).isEqualTo(INITIAL_BALANCE);
    assertThat(result1.get().getInt(SOME_COLUMN)).isEqualTo(2);

    assertThat(result2).isEqualTo(result1);
  }

  @Test
  public void scan_ScanGivenForIndexColumn_ShouldReturnRecords() throws TransactionException {
    // Arrange
    populateRecords();
    TwoPhaseCommitTransaction transaction = manager.start();
    Scan scanBuiltByConstructor =
        new Scan(Key.ofInt(SOME_COLUMN, 2))
            .forNamespace(namespace)
            .forTable(TABLE)
            .withConsistency(Consistency.LINEARIZABLE);

    Scan scanBuiltByBuilder =
        Scan.newBuilder()
            .namespace(namespace)
            .table(TABLE)
            .indexKey(Key.ofInt(SOME_COLUMN, 2))
            .build();

    // Act
    List<Result> results1 = transaction.scan(scanBuiltByConstructor);
    List<Result> results2 = transaction.scan(scanBuiltByBuilder);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    assertThat(results1.size()).isEqualTo(2);
    assertThat(results1.get(0).getInt(ACCOUNT_ID)).isEqualTo(1);
    assertThat(results1.get(0).getInt(ACCOUNT_TYPE)).isEqualTo(2);
    assertThat(getBalance(results1.get(0))).isEqualTo(INITIAL_BALANCE);
    assertThat(results1.get(0).getInt(SOME_COLUMN)).isEqualTo(2);

    assertThat(results1.get(1).getInt(ACCOUNT_ID)).isEqualTo(2);
    assertThat(results1.get(1).getInt(ACCOUNT_TYPE)).isEqualTo(1);
    assertThat(getBalance(results1.get(1))).isEqualTo(INITIAL_BALANCE);
    assertThat(results1.get(1).getInt(SOME_COLUMN)).isEqualTo(2);

    assertThat(results2).isEqualTo(results1);
  }

  @Test
  public void putAndCommit_PutGivenForNonExisting_ShouldCreateRecord() throws TransactionException {
    // Arrange
    int expected = INITIAL_BALANCE;
    Put put = preparePut(0, 0).withValue(BALANCE, expected);
    TwoPhaseCommitTransaction transaction = manager.start();

    // Act
    transaction.put(put);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    Get get = prepareGet(0, 0);
    TwoPhaseCommitTransaction another = manager.start();
    Optional<Result> result = another.get(get);
    another.prepare();
    another.validate();
    another.commit();
    assertThat(result.isPresent()).isTrue();
    assertThat(getBalance(result.get())).isEqualTo(expected);
  }

  @Test
  public void putAndCommit_PutGivenForExistingAfterRead_ShouldUpdateRecord()
      throws TransactionException {
    // Arrange
    populateRecords();
    Get get = prepareGet(0, 0);
    TwoPhaseCommitTransaction transaction = manager.start();

    // Act
    Optional<Result> result = transaction.get(get);
    assertThat(result.isPresent()).isTrue();
    int expected = getBalance(result.get()) + 100;
    Put put = preparePut(0, 0).withValue(BALANCE, expected);
    transaction.put(put);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    TwoPhaseCommitTransaction another = manager.start();
    Optional<Result> actual = another.get(get);
    another.prepare();
    another.validate();
    another.commit();

    assertThat(actual.isPresent()).isTrue();
    assertThat(getBalance(actual.get())).isEqualTo(expected);
  }

  @Test
  public void putWithNullValueAndCommit_ShouldCreateRecordProperly() throws TransactionException {
    // Arrange
    Put put = preparePut(0, 0).withIntValue(BALANCE, null);
    TwoPhaseCommitTransaction transaction = manager.start();

    // Act
    transaction.put(put);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    Get get = prepareGet(0, 0);
    TwoPhaseCommitTransaction another = manager.start();
    Optional<Result> result = another.get(get);
    another.prepare();
    another.validate();
    another.commit();
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get().isNull(BALANCE)).isTrue();
  }

  @Test
  public void putAndCommit_GetsAndPutsGiven_ShouldCommitProperly() throws TransactionException {
    // Arrange
    populateRecords();
    int amount = 100;
    int fromId = 0;
    int toId = NUM_TYPES;

    // Act
    TwoPhaseCommitTransaction transaction = manager.start();
    List<Get> gets = prepareGets();

    Optional<Result> fromResult = transaction.get(gets.get(fromId));
    assertThat(fromResult.isPresent()).isTrue();
    IntValue fromBalance = new IntValue(BALANCE, getBalance(fromResult.get()) - amount);

    Optional<Result> toResult = transaction.get(gets.get(toId));
    assertThat(toResult.isPresent()).isTrue();
    IntValue toBalance = new IntValue(BALANCE, getBalance(toResult.get()) + amount);

    List<Put> puts = preparePuts();
    puts.get(fromId).withValue(fromBalance);
    puts.get(toId).withValue(toBalance);
    transaction.put(puts.get(fromId));
    transaction.put(puts.get(toId));

    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    TwoPhaseCommitTransaction another = manager.start();
    fromResult = another.get(gets.get(fromId));
    assertThat(fromResult.isPresent()).isTrue();
    assertThat(getBalance(fromResult.get())).isEqualTo(INITIAL_BALANCE - amount);

    toResult = another.get(gets.get(toId));
    assertThat(toResult.isPresent()).isTrue();
    assertThat(getBalance(toResult.get())).isEqualTo(INITIAL_BALANCE + amount);
    another.prepare();
    another.validate();
    another.commit();
  }

  @Test
  public void putAndRollback_ShouldNotCreateRecord() throws TransactionException {
    // Arrange
    Put put = preparePut(0, 0).withValue(BALANCE, INITIAL_BALANCE);
    TwoPhaseCommitTransaction transaction = manager.start();

    // Act
    transaction.put(put);
    transaction.rollback();

    // Assert
    Get get = prepareGet(0, 0);
    TwoPhaseCommitTransaction another = manager.start();
    Optional<Result> result = another.get(get);
    another.prepare();
    another.validate();
    another.commit();
    assertThat(result.isPresent()).isFalse();
  }

  @Test
  public void deleteAndCommit_DeleteGivenForExistingAfterRead_ShouldDeleteRecord()
      throws TransactionException {
    // Arrange
    populateRecords();
    Get get = prepareGet(0, 0);
    Delete delete = prepareDelete(0, 0);
    TwoPhaseCommitTransaction transaction = manager.start();

    // Act
    Optional<Result> result = transaction.get(get);
    transaction.delete(delete);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    assertThat(result.isPresent()).isTrue();
    TwoPhaseCommitTransaction another = manager.start();
    Optional<Result> result1 = another.get(get);
    another.prepare();
    another.validate();
    another.commit();
    assertThat(result1.isPresent()).isFalse();
  }

  @Test
  public void deleteAndRollback_ShouldNotDeleteRecord() throws TransactionException {
    // Arrange
    populateRecords();
    Get get = prepareGet(0, 0);
    Delete delete = prepareDelete(0, 0);
    TwoPhaseCommitTransaction transaction = manager.start();

    // Act
    Optional<Result> result = transaction.get(get);
    transaction.delete(delete);
    transaction.rollback();

    // Assert
    assertThat(result).isPresent();
    TwoPhaseCommitTransaction another = manager.start();
    Optional<Result> result1 = another.get(get);
    another.prepare();
    another.validate();
    another.commit();
    assertThat(result1).isPresent();
  }

  @Test
  public void mutateAndCommit_ShouldMutateRecordsProperly() throws TransactionException {
    // Arrange
    populateRecords();
    Get get1 = prepareGet(0, 0);
    Get get2 = prepareGet(1, 0);
    Put put = preparePut(0, 0).withIntValue(BALANCE, INITIAL_BALANCE - 100);
    Delete delete = prepareDelete(1, 0);

    TwoPhaseCommitTransaction transaction = manager.start();

    // Act
    transaction.get(get1);
    transaction.get(get2);
    transaction.mutate(Arrays.asList(put, delete));
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    TwoPhaseCommitTransaction another = manager.start();
    Optional<Result> result1 = another.get(get1);
    Optional<Result> result2 = another.get(get2);
    another.prepare();
    another.validate();
    another.commit();

    assertThat(result1.isPresent()).isTrue();
    assertThat(result1.get().getInt(BALANCE)).isEqualTo(INITIAL_BALANCE - 100);
    assertThat(result2.isPresent()).isFalse();
  }

  @Test
  public void mutateAndCommit_WithMultipleSubTransactions_ShouldMutateRecordsProperly()
      throws TransactionException {
    // Arrange
    populateRecords();
    Get get1 = prepareGet(0, 0);
    Get get2 = prepareGet(1, 0);
    Put put = preparePut(0, 0).withIntValue(BALANCE, INITIAL_BALANCE - 100);
    Delete delete = prepareDelete(1, 0);

    TwoPhaseCommitTransaction transaction1 = manager.start();
    TwoPhaseCommitTransaction transaction2;

    // Act
    transaction1.get(get1);
    transaction1.put(put);

    transaction2 = manager.join(transaction1.getId());
    transaction2.get(get2);
    manager.suspend(transaction2);

    transaction2 = manager.resume(transaction1.getId());
    transaction2.delete(delete);
    manager.suspend(transaction2);

    // Prepare
    transaction1.prepare();

    transaction2 = manager.resume(transaction1.getId());
    transaction2.prepare();
    manager.suspend(transaction2);

    // Validate
    transaction1.validate();

    transaction2 = manager.resume(transaction1.getId());
    transaction2.validate();
    manager.suspend(transaction2);

    // Commit
    transaction1.commit();

    transaction2 = manager.resume(transaction1.getId());
    transaction2.commit();

    // Assert
    TwoPhaseCommitTransaction another = manager.start();
    Optional<Result> result1 = another.get(get1);
    Optional<Result> result2 = another.get(get2);
    another.prepare();
    another.validate();
    another.commit();

    assertThat(result1.isPresent()).isTrue();
    assertThat(result1.get().getInt(BALANCE)).isEqualTo(INITIAL_BALANCE - 100);
    assertThat(result2.isPresent()).isFalse();
  }

  @Test
  public void mutateAndRollback_WithMultipleSubTransactions_ShouldMutateRecordsProperly()
      throws TransactionException {
    // Arrange
    populateRecords();
    Get get1 = prepareGet(0, 0);
    Get get2 = prepareGet(1, 0);
    Put put = preparePut(0, 0).withIntValue(BALANCE, INITIAL_BALANCE - 100);
    Delete delete = prepareDelete(1, 0);

    TwoPhaseCommitTransaction transaction1 = manager.start();
    TwoPhaseCommitTransaction transaction2;

    // Act
    transaction1.get(get1);
    transaction1.put(put);

    transaction2 = manager.join(transaction1.getId());
    transaction2.get(get2);
    manager.suspend(transaction2);

    transaction2 = manager.resume(transaction1.getId());
    transaction2.delete(delete);
    manager.suspend(transaction2);

    // Prepare
    transaction1.prepare();

    transaction2 = manager.resume(transaction1.getId());
    transaction2.prepare();
    manager.suspend(transaction2);

    // Validate
    transaction1.validate();

    transaction2 = manager.resume(transaction1.getId());
    transaction2.validate();
    manager.suspend(transaction2);

    // Rollback
    transaction1.rollback();

    transaction2 = manager.resume(transaction1.getId());
    transaction2.rollback();

    // Assert
    TwoPhaseCommitTransaction another = manager.start();
    Optional<Result> result1 = another.get(get1);
    Optional<Result> result2 = another.get(get2);
    another.prepare();
    another.validate();
    another.commit();

    assertThat(result1.isPresent()).isTrue();
    assertThat(result1.get().getInt(BALANCE)).isEqualTo(INITIAL_BALANCE);
    assertThat(result2.isPresent()).isTrue();
    assertThat(result2.get().getInt(BALANCE)).isEqualTo(INITIAL_BALANCE);
  }

  @Test
  public void getState_forSuccessfulTransaction_ShouldReturnCommittedState()
      throws TransactionException {
    // Arrange
    TwoPhaseCommitTransaction transaction = manager.start();
    transaction.get(prepareGet(0, 0));
    transaction.put(preparePut(0, 0).withValue(BALANCE, 1));
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Act
    TransactionState state = manager.getState(transaction.getId());

    // Assert
    assertThat(state).isEqualTo(TransactionState.COMMITTED);
  }

  @Test
  public void getState_forFailedTransaction_ShouldReturnAbortedState() throws TransactionException {
    // Arrange
    TwoPhaseCommitTransaction transaction1 = manager.start();
    transaction1.get(prepareGet(0, 0));
    transaction1.put(preparePut(0, 0).withValue(BALANCE, 1));

    TwoPhaseCommitTransaction transaction2 = manager.start();
    transaction2.get(prepareGet(0, 0));
    transaction2.put(preparePut(0, 0).withValue(BALANCE, 1));
    transaction2.prepare();
    transaction2.validate();
    transaction2.commit();

    assertThatCode(transaction1::prepare).isInstanceOf(PreparationConflictException.class);
    transaction1.rollback();

    // Act
    TransactionState state = manager.getState(transaction1.getId());

    // Assert
    assertThat(state).isEqualTo(TransactionState.ABORTED);
  }

  @Test
  public void abort_forOngoingTransaction_ShouldAbortCorrectly() throws TransactionException {
    // Arrange
    TwoPhaseCommitTransaction transaction = manager.start();
    transaction.get(prepareGet(0, 0));
    transaction.put(preparePut(0, 0).withValue(BALANCE, 1));

    // Act
    manager.abort(transaction.getId());

    transaction.prepare();
    transaction.validate();
    assertThatCode(transaction::commit).isInstanceOf(CommitException.class);
    transaction.rollback();

    // Assert
    TransactionState state = manager.getState(transaction.getId());
    assertThat(state).isEqualTo(TransactionState.ABORTED);
  }

  @Test
  public void scan_ScanAllGivenForCommittedRecord_ShouldReturnRecords()
      throws TransactionException {
    // Arrange
    populateRecords();
    TwoPhaseCommitTransaction transaction = manager.start();
    ScanAll scanAll = prepareScanAll();

    // Act
    List<Result> results = transaction.scan(scanAll);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    List<ExpectedResult> expectedResults = new ArrayList<>();
    IntStream.range(0, NUM_ACCOUNTS)
        .forEach(
            i ->
                IntStream.range(0, NUM_TYPES)
                    .forEach(
                        j -> {
                          ExpectedResultBuilder erBuilder =
                              new ExpectedResultBuilder()
                                  .partitionKey(Key.ofInt(ACCOUNT_ID, i))
                                  .clusteringKey(Key.ofInt(ACCOUNT_TYPE, j))
                                  .nonKeyColumns(
                                      ImmutableList.of(
                                          IntColumn.of(BALANCE, INITIAL_BALANCE),
                                          IntColumn.of(SOME_COLUMN, i * j)));
                          expectedResults.add(erBuilder.build());
                        }));
    assertResultsContainsExactlyInAnyOrder(results, expectedResults);
  }

  @Test
  public void scan_ScanAllGivenWithLimit_ShouldReturnLimitedAmountOfRecords()
      throws TransactionException {
    // Arrange
    TwoPhaseCommitTransaction putTransaction = manager.start();
    putTransaction.put(
        Arrays.asList(
            new Put(Key.ofInt(ACCOUNT_ID, 1), Key.ofInt(ACCOUNT_TYPE, 1))
                .forNamespace(namespace)
                .forTable(TABLE),
            new Put(Key.ofInt(ACCOUNT_ID, 1), Key.ofInt(ACCOUNT_TYPE, 2))
                .forNamespace(namespace)
                .forTable(TABLE),
            new Put(Key.ofInt(ACCOUNT_ID, 2), Key.ofInt(ACCOUNT_TYPE, 1))
                .forNamespace(namespace)
                .forTable(TABLE),
            new Put(Key.ofInt(ACCOUNT_ID, 3), Key.ofInt(ACCOUNT_TYPE, 0))
                .forNamespace(namespace)
                .forTable(TABLE)));
    putTransaction.prepare();
    putTransaction.validate();
    putTransaction.commit();

    TwoPhaseCommitTransaction scanAllTransaction = manager.start();
    ScanAll scanAll = prepareScanAll().withLimit(2);

    // Act
    List<Result> results = scanAllTransaction.scan(scanAll);
    scanAllTransaction.prepare();
    scanAllTransaction.validate();
    scanAllTransaction.commit();

    // Assert
    assertResultsAreASubsetOf(
        results,
        ImmutableList.of(
            new ExpectedResultBuilder()
                .partitionKey(Key.ofInt(ACCOUNT_ID, 1))
                .clusteringKey(Key.ofInt(ACCOUNT_TYPE, 1))
                .nonKeyColumns(
                    Arrays.asList(IntColumn.ofNull(BALANCE), IntColumn.ofNull(SOME_COLUMN)))
                .build(),
            new ExpectedResultBuilder()
                .partitionKey(Key.ofInt(ACCOUNT_ID, 1))
                .clusteringKey(Key.ofInt(ACCOUNT_TYPE, 2))
                .nonKeyColumns(
                    Arrays.asList(IntColumn.ofNull(BALANCE), IntColumn.ofNull(SOME_COLUMN)))
                .build(),
            new ExpectedResultBuilder()
                .partitionKey(Key.ofInt(ACCOUNT_ID, 2))
                .clusteringKey(Key.ofInt(ACCOUNT_TYPE, 1))
                .nonKeyColumns(
                    Arrays.asList(IntColumn.ofNull(BALANCE), IntColumn.ofNull(SOME_COLUMN)))
                .build(),
            new ExpectedResultBuilder()
                .partitionKey(Key.ofInt(ACCOUNT_ID, 3))
                .clusteringKey(Key.ofInt(ACCOUNT_TYPE, 0))
                .nonKeyColumns(
                    Arrays.asList(IntColumn.ofNull(BALANCE), IntColumn.ofNull(SOME_COLUMN)))
                .build()));
    assertThat(results).hasSize(2);
  }

  @Test
  public void scan_ScanAllWithProjectionsGiven_ShouldRetrieveSpecifiedValues()
      throws TransactionException {
    // Arrange
    populateRecords();
    TwoPhaseCommitTransaction transaction = manager.start();
    ScanAll scanAll = prepareScanAll().withProjection(ACCOUNT_TYPE).withProjection(BALANCE);

    // Act
    List<Result> results = transaction.scan(scanAll);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    List<ExpectedResult> expectedResults = new ArrayList<>();
    IntStream.range(0, NUM_ACCOUNTS)
        .forEach(
            i ->
                IntStream.range(0, NUM_TYPES)
                    .forEach(
                        j -> {
                          ExpectedResultBuilder erBuilder =
                              new ExpectedResultBuilder()
                                  .clusteringKey(Key.ofInt(ACCOUNT_TYPE, j))
                                  .nonKeyColumns(
                                      ImmutableList.of(IntColumn.of(BALANCE, INITIAL_BALANCE)));
                          expectedResults.add(erBuilder.build());
                        }));
    assertResultsContainsExactlyInAnyOrder(results, expectedResults);
    results.forEach(
        result -> {
          assertThat(result.contains(ACCOUNT_ID)).isFalse();
          assertThat(result.contains(SOME_COLUMN)).isFalse();
        });
  }

  @Test
  public void scan_ScanAllGivenForNonExisting_ShouldReturnEmpty() throws TransactionException {
    // Arrange
    TwoPhaseCommitTransaction transaction = manager.start();
    ScanAll scanAll = prepareScanAll();

    // Act
    List<Result> results = transaction.scan(scanAll);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    assertThat(results.size()).isEqualTo(0);
  }

  @Test
  public void
      get_GetWithProjectionOnNonPrimaryKeyColumnsForGivenForCommittedRecord_ShouldReturnOnlyProjectedColumns()
          throws TransactionException {
    // Arrange
    populateSingleRecord();
    TwoPhaseCommitTransaction transaction = manager.start();
    Get get = prepareGet(0, 0).withProjections(Arrays.asList(BALANCE, SOME_COLUMN));

    // Act
    Optional<Result> result = transaction.get(get);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get().getContainedColumnNames()).containsOnly(BALANCE, SOME_COLUMN);
    assertThat(result.get().getInt(BALANCE)).isEqualTo(INITIAL_BALANCE);
    assertThat(result.get().isNull(SOME_COLUMN)).isTrue();
  }

  @Test
  public void
      scan_ScanWithProjectionsGivenOnNonPrimaryKeyColumnsForCommittedRecord_ShouldReturnOnlyProjectedColumns()
          throws TransactionException {
    // Arrange
    TwoPhaseCommitTransaction transaction = manager.start();
    populateSingleRecord();
    Scan scan = prepareScan(0, 0, 0).withProjections(Arrays.asList(BALANCE, SOME_COLUMN));

    // Act
    List<Result> results = transaction.scan(scan);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    results.forEach(
        result -> {
          assertThat(result.getContainedColumnNames()).containsOnly(BALANCE, SOME_COLUMN);
          assertThat(result.getInt(BALANCE)).isEqualTo(INITIAL_BALANCE);
          assertThat(result.isNull(SOME_COLUMN)).isTrue();
        });
  }

  @Test
  public void
      scan_ScanAllWithProjectionsGivenOnNonPrimaryKeyColumnsForCommittedRecord_ShouldReturnOnlyProjectedColumns()
          throws TransactionException {
    // Arrange
    populateSingleRecord();
    TwoPhaseCommitTransaction transaction = manager.start();
    ScanAll scanAll = prepareScanAll().withProjections(Arrays.asList(BALANCE, SOME_COLUMN));

    // Act
    List<Result> results = transaction.scan(scanAll);
    transaction.prepare();
    transaction.validate();
    transaction.commit();

    // Assert
    ExpectedResult expectedResult =
        new ExpectedResultBuilder()
            .nonKeyColumns(
                ImmutableList.of(
                    IntColumn.of(BALANCE, INITIAL_BALANCE), IntColumn.ofNull(SOME_COLUMN)))
            .build();
    assertResultsContainsExactlyInAnyOrder(results, Collections.singletonList(expectedResult));
  }

  private void populateRecords() throws TransactionException {
    TwoPhaseCommitTransaction transaction = manager.start();
    IntStream.range(0, NUM_ACCOUNTS)
        .forEach(
            i ->
                IntStream.range(0, NUM_TYPES)
                    .forEach(
                        j -> {
                          Key partitionKey = new Key(ACCOUNT_ID, i);
                          Key clusteringKey = new Key(ACCOUNT_TYPE, j);
                          Put put =
                              new Put(partitionKey, clusteringKey)
                                  .forNamespace(namespace)
                                  .forTable(TABLE)
                                  .withIntValue(BALANCE, INITIAL_BALANCE)
                                  .withIntValue(SOME_COLUMN, i * j);
                          try {
                            transaction.put(put);
                          } catch (CrudException e) {
                            throw new RuntimeException(e);
                          }
                        }));
    transaction.prepare();
    transaction.validate();
    transaction.commit();
  }

  private void populateSingleRecord() throws TransactionException {
    Put put =
        new Put(Key.ofInt(ACCOUNT_ID, 0), Key.ofInt(ACCOUNT_TYPE, 0))
            .forNamespace(namespace)
            .forTable(TABLE)
            .withIntValue(BALANCE, INITIAL_BALANCE);
    TwoPhaseCommitTransaction transaction = manager.start();
    transaction.put(put);
    transaction.prepare();
    transaction.validate();
    transaction.commit();
  }

  private Get prepareGet(int id, int type) {
    Key partitionKey = new Key(ACCOUNT_ID, id);
    Key clusteringKey = new Key(ACCOUNT_TYPE, type);
    return new Get(partitionKey, clusteringKey)
        .forNamespace(namespace)
        .forTable(TABLE)
        .withConsistency(Consistency.LINEARIZABLE);
  }

  private List<Get> prepareGets() {
    List<Get> gets = new ArrayList<>();
    IntStream.range(0, NUM_ACCOUNTS)
        .forEach(i -> IntStream.range(0, NUM_TYPES).forEach(j -> gets.add(prepareGet(i, j))));
    return gets;
  }

  private Scan prepareScan(int id, int fromType, int toType) {
    Key partitionKey = new Key(ACCOUNT_ID, id);
    return new Scan(partitionKey)
        .forNamespace(namespace)
        .forTable(TABLE)
        .withConsistency(Consistency.LINEARIZABLE)
        .withStart(new Key(ACCOUNT_TYPE, fromType))
        .withEnd(new Key(ACCOUNT_TYPE, toType));
  }

  private ScanAll prepareScanAll() {
    return new ScanAll()
        .forNamespace(namespace)
        .forTable(TABLE)
        .withConsistency(Consistency.LINEARIZABLE);
  }

  private Put preparePut(int id, int type) {
    Key partitionKey = new Key(ACCOUNT_ID, id);
    Key clusteringKey = new Key(ACCOUNT_TYPE, type);
    return new Put(partitionKey, clusteringKey)
        .forNamespace(namespace)
        .forTable(TABLE)
        .withConsistency(Consistency.LINEARIZABLE);
  }

  private List<Put> preparePuts() {
    List<Put> puts = new ArrayList<>();
    IntStream.range(0, NUM_ACCOUNTS)
        .forEach(i -> IntStream.range(0, NUM_TYPES).forEach(j -> puts.add(preparePut(i, j))));

    return puts;
  }

  private Delete prepareDelete(int id, int type) {
    Key partitionKey = new Key(ACCOUNT_ID, id);
    Key clusteringKey = new Key(ACCOUNT_TYPE, type);
    return new Delete(partitionKey, clusteringKey)
        .forNamespace(namespace)
        .forTable(TABLE)
        .withConsistency(Consistency.LINEARIZABLE);
  }

  private int getBalance(Result result) {
    Optional<Value<?>> balance = result.getValue(BALANCE);
    assertThat(balance).isPresent();
    return balance.get().getAsInt();
  }
}
