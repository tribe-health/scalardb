package com.scalar.db.storage.jdbc;

import com.scalar.db.api.Scan;
import com.scalar.db.storage.common.metadata.DataType;
import com.scalar.db.storage.jdbc.metadata.JdbcTableMetadata;
import com.scalar.db.storage.jdbc.metadata.TableMetadataManager;
import com.scalar.db.storage.jdbc.test.TestEnv;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcMetadataIntegrationTest {

  private static final String NAMESPACE = "integration_testing";
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

  private TestEnv testEnv;

  @Before
  public void setUp() throws Exception {
    testEnv = new TestEnv();
    testEnv.register(
        NAMESPACE,
        TABLE,
        Arrays.asList(COL_NAME2, COL_NAME1),
        Arrays.asList(COL_NAME4, COL_NAME3),
        new HashMap<String, Scan.Ordering.Order>() {
          {
            put(COL_NAME4, Scan.Ordering.Order.ASC);
            put(COL_NAME3, Scan.Ordering.Order.DESC);
          }
        },
        new HashMap<String, DataType>() {
          {
            put(COL_NAME1, DataType.INT);
            put(COL_NAME2, DataType.TEXT);
            put(COL_NAME3, DataType.TEXT);
            put(COL_NAME4, DataType.INT);
            put(COL_NAME5, DataType.INT);
            put(COL_NAME6, DataType.TEXT);
            put(COL_NAME7, DataType.BIGINT);
            put(COL_NAME8, DataType.FLOAT);
            put(COL_NAME9, DataType.DOUBLE);
            put(COL_NAME10, DataType.BOOLEAN);
            put(COL_NAME11, DataType.BLOB);
          }
        },
        Arrays.asList(COL_NAME5, COL_NAME6),
        new HashMap<String, Scan.Ordering.Order>() {
          {
            put(COL_NAME5, Scan.Ordering.Order.ASC);
            put(COL_NAME6, Scan.Ordering.Order.DESC);
          }
        });
    testEnv.createTables();
  }

  @After
  public void tearDown() throws Exception {
    testEnv.dropTables();
    testEnv.close();
  }

  @Test
  public void test() throws Exception {
    Optional<String> namespacePrefix = testEnv.getJdbcDatabaseConfig().getNamespacePrefix();
    TableMetadataManager tableMetadataManager =
        new TableMetadataManager(testEnv.getDataSource(), namespacePrefix, testEnv.getRdbEngine());

    String fullTableName = namespacePrefix.orElse("") + NAMESPACE + "." + TABLE;
    JdbcTableMetadata tableMetadata = tableMetadataManager.getTableMetadata(fullTableName);

    assertThat(tableMetadata).isNotNull();

    assertThat(tableMetadata.getFullTableName()).isEqualTo(fullTableName);

    assertThat(tableMetadata.getPartitionKeyNames().size()).isEqualTo(2);
    Iterator<String> iterator = tableMetadata.getPartitionKeyNames().iterator();
    assertThat(iterator.next()).isEqualTo(COL_NAME2);
    assertThat(iterator.next()).isEqualTo(COL_NAME1);

    assertThat(tableMetadata.getClusteringKeyNames().size()).isEqualTo(2);
    iterator = tableMetadata.getClusteringKeyNames().iterator();
    assertThat(iterator.next()).isEqualTo(COL_NAME4);
    assertThat(iterator.next()).isEqualTo(COL_NAME3);

    assertThat(tableMetadata.getColumnNames().size()).isEqualTo(11);
    assertThat(tableMetadata.getColumnNames().contains(COL_NAME1)).isTrue();
    assertThat(tableMetadata.getColumnNames().contains(COL_NAME2)).isTrue();
    assertThat(tableMetadata.getColumnNames().contains(COL_NAME3)).isTrue();
    assertThat(tableMetadata.getColumnNames().contains(COL_NAME4)).isTrue();
    assertThat(tableMetadata.getColumnNames().contains(COL_NAME5)).isTrue();
    assertThat(tableMetadata.getColumnNames().contains(COL_NAME6)).isTrue();
    assertThat(tableMetadata.getColumnNames().contains(COL_NAME7)).isTrue();
    assertThat(tableMetadata.getColumnNames().contains(COL_NAME8)).isTrue();
    assertThat(tableMetadata.getColumnNames().contains(COL_NAME9)).isTrue();
    assertThat(tableMetadata.getColumnNames().contains(COL_NAME10)).isTrue();
    assertThat(tableMetadata.getColumnNames().contains(COL_NAME11)).isTrue();

    assertThat(tableMetadata.getColumnDataType(COL_NAME1)).isEqualTo(DataType.INT);
    assertThat(tableMetadata.getColumnDataType(COL_NAME2)).isEqualTo(DataType.TEXT);
    assertThat(tableMetadata.getColumnDataType(COL_NAME3)).isEqualTo(DataType.TEXT);
    assertThat(tableMetadata.getColumnDataType(COL_NAME4)).isEqualTo(DataType.INT);
    assertThat(tableMetadata.getColumnDataType(COL_NAME5)).isEqualTo(DataType.INT);
    assertThat(tableMetadata.getColumnDataType(COL_NAME6)).isEqualTo(DataType.TEXT);
    assertThat(tableMetadata.getColumnDataType(COL_NAME7)).isEqualTo(DataType.BIGINT);
    assertThat(tableMetadata.getColumnDataType(COL_NAME8)).isEqualTo(DataType.FLOAT);
    assertThat(tableMetadata.getColumnDataType(COL_NAME9)).isEqualTo(DataType.DOUBLE);
    assertThat(tableMetadata.getColumnDataType(COL_NAME10)).isEqualTo(DataType.BOOLEAN);
    assertThat(tableMetadata.getColumnDataType(COL_NAME11)).isEqualTo(DataType.BLOB);

    assertThat(tableMetadata.getClusteringOrder(COL_NAME1)).isNull();
    assertThat(tableMetadata.getClusteringOrder(COL_NAME2)).isNull();
    assertThat(tableMetadata.getClusteringOrder(COL_NAME3)).isEqualTo(Scan.Ordering.Order.DESC);
    assertThat(tableMetadata.getClusteringOrder(COL_NAME4)).isEqualTo(Scan.Ordering.Order.ASC);
    assertThat(tableMetadata.getClusteringOrder(COL_NAME5)).isNull();
    assertThat(tableMetadata.getClusteringOrder(COL_NAME6)).isNull();
    assertThat(tableMetadata.getClusteringOrder(COL_NAME7)).isNull();
    assertThat(tableMetadata.getClusteringOrder(COL_NAME8)).isNull();
    assertThat(tableMetadata.getClusteringOrder(COL_NAME9)).isNull();
    assertThat(tableMetadata.getClusteringOrder(COL_NAME10)).isNull();
    assertThat(tableMetadata.getClusteringOrder(COL_NAME11)).isNull();

    assertThat(tableMetadata.getSecondaryIndexNames().size()).isEqualTo(2);
    assertThat(tableMetadata.getSecondaryIndexNames().contains(COL_NAME5)).isTrue();
    assertThat(tableMetadata.getSecondaryIndexNames().contains(COL_NAME6)).isTrue();

    assertThat(tableMetadata.getSecondaryIndexOrder(COL_NAME1)).isNull();
    assertThat(tableMetadata.getSecondaryIndexOrder(COL_NAME2)).isNull();
    assertThat(tableMetadata.getSecondaryIndexOrder(COL_NAME3)).isNull();
    assertThat(tableMetadata.getSecondaryIndexOrder(COL_NAME4)).isNull();
    assertThat(tableMetadata.getSecondaryIndexOrder(COL_NAME5)).isEqualTo(Scan.Ordering.Order.ASC);
    assertThat(tableMetadata.getSecondaryIndexOrder(COL_NAME6)).isEqualTo(Scan.Ordering.Order.DESC);
    assertThat(tableMetadata.getSecondaryIndexOrder(COL_NAME7)).isNull();
    assertThat(tableMetadata.getSecondaryIndexOrder(COL_NAME8)).isNull();
    assertThat(tableMetadata.getSecondaryIndexOrder(COL_NAME9)).isNull();
    assertThat(tableMetadata.getSecondaryIndexOrder(COL_NAME10)).isNull();
    assertThat(tableMetadata.getSecondaryIndexOrder(COL_NAME11)).isNull();
  }
}
