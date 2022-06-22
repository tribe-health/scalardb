package com.scalar.db.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.scalar.db.io.Key;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GetBuilderTest {
  private static final String NAMESPACE_1 = "namespace1";
  private static final String NAMESPACE_2 = "namespace2";

  private static final String TABLE_1 = "table1";
  private static final String TABLE_2 = "table2";
  @Mock private Key partitionKey1;
  @Mock private Key partitionKey2;
  @Mock private Key clusteringKey1;
  @Mock private Key clusteringKey2;

  @BeforeEach
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
  }

  @Test
  public void build_WithMandatoryParameters_ShouldBuildGetWithMandatoryParameters() {
    // Arrange Act
    Get actual =
        Get.newBuilder().namespace(NAMESPACE_1).table(TABLE_1).partitionKey(partitionKey1).build();

    // Assert
    assertThat(actual)
        .isEqualTo(new Get(partitionKey1).forNamespace(NAMESPACE_1).forTable(TABLE_1));
  }

  @Test
  public void build_WithClusteringKey_ShouldBuildGetWithClusteringKey() {
    // Arrange Act
    Get get =
        Get.newBuilder()
            .namespace(NAMESPACE_1)
            .table(TABLE_1)
            .partitionKey(partitionKey1)
            .clusteringKey(clusteringKey1)
            .build();

    // Assert
    assertThat(get)
        .isEqualTo(
            new Get(partitionKey1, clusteringKey1).forNamespace(NAMESPACE_1).forTable(TABLE_1));
  }

  @Test
  public void build_WithAllParameters_ShouldBuildGetWithAllParameters() {
    // Arrange Act
    Get get =
        Get.newBuilder()
            .namespace(NAMESPACE_1)
            .table(TABLE_1)
            .partitionKey(partitionKey1)
            .clusteringKey(clusteringKey1)
            .consistency(Consistency.EVENTUAL)
            .projection("c1")
            .projection("c2")
            .projections(Arrays.asList("c3", "c4"))
            .build();

    // Assert
    assertThat(get)
        .isEqualTo(
            new Get(partitionKey1, clusteringKey1)
                .forNamespace(NAMESPACE_1)
                .forTable(TABLE_1)
                .withProjections(Arrays.asList("c1", "c2", "c3", "c4"))
                .withConsistency(Consistency.EVENTUAL));
  }

  @Test
  public void build_FromExistingWithoutChange_ShouldCopy() {
    // Arrange
    Get existingGet =
        new Get(partitionKey1, clusteringKey1)
            .forNamespace(NAMESPACE_1)
            .forTable(TABLE_1)
            .withProjections(Arrays.asList("c1", "c2"))
            .withConsistency(Consistency.LINEARIZABLE);

    // Act
    Get newGet = Get.newBuilder(existingGet).build();

    // Assert
    assertThat(newGet).isEqualTo(existingGet);
  }

  @Test
  public void build_FromExistingAndUpdateAllParameters_ShouldBuildGetWithUpdatedParameters() {
    // Arrange
    Get existingGet =
        new Get(partitionKey1, clusteringKey1)
            .forNamespace(NAMESPACE_1)
            .forTable(TABLE_1)
            .withProjections(Arrays.asList("c1", "c2"))
            .withConsistency(Consistency.LINEARIZABLE);

    // Act
    Get newGet =
        Get.newBuilder(existingGet)
            .partitionKey(partitionKey2)
            .clusteringKey(clusteringKey2)
            .namespace(NAMESPACE_2)
            .table(TABLE_2)
            .consistency(Consistency.EVENTUAL)
            .clearProjections()
            .projections(Arrays.asList("c3", "c4"))
            .projection("c5")
            .build();

    // Assert
    assertThat(newGet)
        .isEqualTo(
            new Get(partitionKey2, clusteringKey2)
                .forNamespace(NAMESPACE_2)
                .forTable(TABLE_2)
                .withConsistency(Consistency.EVENTUAL)
                .withProjections(Arrays.asList("c3", "c4", "c5")));
  }

  @Test
  public void build_FromExistingAndClearClusteringKey_ShouldBuildGetWithoutClusteringKey() {
    // Arrange
    Get existingGet =
        new Get(partitionKey1, clusteringKey1).forNamespace(NAMESPACE_1).forTable(TABLE_1);

    // Act
    Get newGet = Get.newBuilder(existingGet).clearClusteringKey().build();

    // Assert
    assertThat(newGet)
        .isEqualTo(new Get(partitionKey1).forNamespace(NAMESPACE_1).forTable(TABLE_1));
  }
}