package com.scalar.db.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.scalar.db.config.DatabaseConfig;
import com.scalar.db.exception.storage.ExecutionException;
import com.scalar.db.io.DataType;
import com.scalar.db.service.StorageFactory;
import com.scalar.db.service.TransactionFactory;
import com.scalar.db.transaction.consensuscommit.ConsensusCommitConfig;
import com.scalar.db.transaction.consensuscommit.Coordinator;
import com.scalar.db.util.AdminTestUtils;
import com.scalar.db.util.TestUtils;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class DistributedTransactionAdminRepairTableIntegrationTestBase {

  private static final String TEST_NAME = "tx_admin_repair_table";
  private static final String NAMESPACE = "int_test_" + TEST_NAME;

  private static final String TABLE = "test_table";
  private static final String COL_NAME1 = "c1";
  private static final String COL_NAME2 = "c2";
  private static final String COL_NAME3 = "c3";
  private static final String COL_NAME4 = "c4";
  private static final String COL_NAME5 = "c5";
  private static final String COL_NAME6 = "c6";
  private static final String COL_NAME7 = "c7";
  private static final String COL_NAME8 = "c8";
  private static final String COL_NAME9 = "c9";
  private static final String COL_NAME10 = "c10";
  private static final String COL_NAME11 = "c11";

  protected static final TableMetadata TABLE_METADATA =
      TableMetadata.newBuilder()
          .addColumn(COL_NAME1, DataType.INT)
          .addColumn(COL_NAME2, DataType.TEXT)
          .addColumn(COL_NAME3, DataType.TEXT)
          .addColumn(COL_NAME4, DataType.INT)
          .addColumn(COL_NAME5, DataType.INT)
          .addColumn(COL_NAME6, DataType.TEXT)
          .addColumn(COL_NAME7, DataType.BIGINT)
          .addColumn(COL_NAME8, DataType.FLOAT)
          .addColumn(COL_NAME9, DataType.DOUBLE)
          .addColumn(COL_NAME10, DataType.BOOLEAN)
          .addColumn(COL_NAME11, DataType.BLOB)
          .addPartitionKey(COL_NAME2)
          .addPartitionKey(COL_NAME1)
          .addClusteringKey(COL_NAME4, Scan.Ordering.Order.ASC)
          .addClusteringKey(COL_NAME3, Scan.Ordering.Order.DESC)
          .addSecondaryIndex(COL_NAME5)
          .addSecondaryIndex(COL_NAME6)
          .build();

  protected DistributedTransactionAdmin admin;
  protected AdminTestUtils adminTestUtils;

  protected void initialize() throws Exception {}

  protected abstract Properties getProperties();

  protected Properties getStorageProperties() {
    return getProperties();
  }

  protected String getNamespace() {
    return NAMESPACE;
  }

  protected String getTable() {
    return TABLE;
  }

  private void createTable() throws ExecutionException {
    Map<String, String> options = getCreationOptions();
    admin.createNamespace(getNamespace(), options);
    admin.createTable(getNamespace(), getTable(), TABLE_METADATA, options);
    admin.createCoordinatorTables(true, options);
  }

  protected Map<String, String> getCreationOptions() {
    return Collections.emptyMap();
  }

  private void dropTable() throws ExecutionException {
    admin.dropTable(getNamespace(), TABLE);
    admin.dropNamespace(getNamespace());
    admin.dropCoordinatorTables(true);
  }

  @BeforeEach
  protected void beforeEach() throws Exception {
    TransactionFactory factory =
        TransactionFactory.create(TestUtils.addSuffix(getProperties(), TEST_NAME));
    admin = factory.getTransactionAdmin();
    createTable();
    adminTestUtils = AdminTestUtils.create(TestUtils.addSuffix(getStorageProperties(), TEST_NAME));
  }

  @AfterEach
  protected void afterEach() throws Exception {
    dropTable();
  }

  @AfterAll
  protected void afterAll() throws Exception {
    admin.close();
  }

  @Test
  public void repairTableAndCoordinatorTable_ForDeletedMetadataTable_ShouldRepairProperly()
      throws Exception {
    // Arrange
    adminTestUtils.dropMetadataTable();

    // Act
    admin.repairTable(getNamespace(), getTable(), TABLE_METADATA, getCreationOptions());
    admin.repairCoordinatorTables(getCreationOptions());

    // Assert
    assertThat(admin.tableExists(getNamespace(), TABLE)).isTrue();
    assertThat(admin.getTableMetadata(getNamespace(), TABLE)).isEqualTo(TABLE_METADATA);
    assertThat(admin.coordinatorTablesExist()).isTrue();
    if (hasCoordinatorTables()) {
      assertTableMetadataForCoordinatorTableArePresent();
    }
  }

  @Test
  public void repairTableAndCoordinatorTable_ForTruncatedMetadataTable_ShouldRepairProperly()
      throws Exception {
    // Arrange
    adminTestUtils.truncateMetadataTable();

    // Act
    admin.repairTable(getNamespace(), getTable(), TABLE_METADATA, getCreationOptions());
    admin.repairCoordinatorTables(getCreationOptions());

    // Assert
    assertThat(admin.tableExists(getNamespace(), TABLE)).isTrue();
    assertThat(admin.getTableMetadata(getNamespace(), TABLE)).isEqualTo(TABLE_METADATA);
    if (hasCoordinatorTables()) {
      assertTableMetadataForCoordinatorTableArePresent();
    }
  }

  @Test
  public void repairTable_ForCorruptedMetadataTable_ShouldRepairProperly() throws Exception {
    // Arrange
    adminTestUtils.corruptMetadata(getNamespace(), getTable());

    // Act
    admin.repairTable(getNamespace(), getTable(), TABLE_METADATA, getCreationOptions());

    // Assert
    assertThat(admin.tableExists(getNamespace(), getTable())).isTrue();
    assertThat(admin.getTableMetadata(getNamespace(), getTable())).isEqualTo(TABLE_METADATA);
    if (hasCoordinatorTables()) {
      assertTableMetadataForCoordinatorTableArePresent();
    }
  }

  private void assertTableMetadataForCoordinatorTableArePresent() throws Exception {
    Properties properties = TestUtils.addSuffix(getStorageProperties(), TEST_NAME);
    String coordinatorNamespace =
        new ConsensusCommitConfig(new DatabaseConfig(properties))
            .getCoordinatorNamespace()
            .orElse(Coordinator.NAMESPACE);
    String coordinatorTable = Coordinator.TABLE;
    // Use the DistributedStorageAdmin instead of the DistributedTransactionAdmin because the latter
    // expects the table to hold transaction table metadata columns which is not the case for the
    // coordinator table
    DistributedStorageAdmin storageAdmin =
        StorageFactory.create(TestUtils.addSuffix(getStorageProperties(), TEST_NAME))
            .getStorageAdmin();

    assertThat(storageAdmin.getTableMetadata(coordinatorNamespace, coordinatorTable))
        .isEqualTo(Coordinator.TABLE_METADATA);

    storageAdmin.close();
  }

  protected boolean hasCoordinatorTables() {
    return true;
  }
}
