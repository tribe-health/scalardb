package com.scalar.db.util;

import static com.scalar.db.util.ScalarDbUtils.getFullTableName;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Uninterruptibles;
import com.scalar.db.config.DatabaseConfig;
import com.scalar.db.storage.dynamo.DynamoAdmin;
import com.scalar.db.storage.dynamo.DynamoConfig;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

public class DynamoAdminTestUtils extends AdminTestUtils {

  private final DynamoDbClient client;

  public DynamoAdminTestUtils(Properties properties) {
    super();
    DynamoConfig config = new DynamoConfig(new DatabaseConfig(properties));
    StaticCredentialsProvider credentialsProvider =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey()));

    DynamoDbClientBuilder builder = DynamoDbClient.builder();
    config.getEndpointOverride().ifPresent(e -> builder.endpointOverride(URI.create(e)));
    client =
        builder
            .credentialsProvider(credentialsProvider)
            .region(Region.of(config.getRegion()))
            .build();
    metadataNamespace =
        new DynamoConfig(new DatabaseConfig(properties))
            .getTableMetadataNamespace()
            .orElse(DynamoAdmin.METADATA_NAMESPACE);
    metadataTable = DynamoAdmin.METADATA_TABLE;
  }

  @Override
  public void dropMetadataTable() {
    client.deleteTable(
        DeleteTableRequest.builder()
            .tableName(getFullTableName(metadataNamespace, metadataTable))
            .build());
    if (!waitForTableDeletion(metadataNamespace, metadataTable)) {
      throw new RuntimeException("Deleting the metadata table timed out");
    }
  }

  private boolean waitForTableDeletion(String namespace, String table) {
    Duration timeout = Duration.ofSeconds(15);
    long startTime = System.currentTimeMillis();

    while ((System.currentTimeMillis() - startTime) < timeout.toMillis()) {
      if (!tableExists(namespace, table)) {
        return true;
      }
      Uninterruptibles.sleepUninterruptibly(3, TimeUnit.SECONDS);
    }
    return false;
  }

  private boolean tableExists(String namespace, String table) {
    try {
      client.describeTable(
          DescribeTableRequest.builder().tableName(getFullTableName(namespace, table)).build());
      return true;
    } catch (ResourceNotFoundException e) {
      return false;
    }
  }

  @Override
  public void truncateMetadataTable() {
    Map<String, AttributeValue> lastKeyEvaluated = null;
    do {
      ScanResponse scanResponse =
          client.scan(
              ScanRequest.builder()
                  .tableName(getFullTableName(metadataNamespace, metadataTable))
                  .exclusiveStartKey(lastKeyEvaluated)
                  .build());

      for (Map<String, AttributeValue> item : scanResponse.items()) {
        Map<String, AttributeValue> keyToDelete = new HashMap<>();
        keyToDelete.put("table", item.get("table"));

        client.deleteItem(
            DeleteItemRequest.builder()
                .tableName(getFullTableName(metadataNamespace, metadataTable))
                .key(keyToDelete)
                .build());
      }
      lastKeyEvaluated = scanResponse.lastEvaluatedKey();
    } while (!lastKeyEvaluated.isEmpty());
  }

  @Override
  public void corruptMetadata(String namespace, String table) {
    Map<String, AttributeValue> itemValues = new HashMap<>();
    itemValues.put("table", AttributeValue.builder().s(getFullTableName(namespace, table)).build());
    itemValues.put(
        "partitionKey",
        AttributeValue.builder()
            .l(ImmutableList.of(AttributeValue.builder().s("corrupted").build()))
            .build());

    client.putItem(
        PutItemRequest.builder()
            .tableName(getFullTableName(metadataNamespace, metadataTable))
            .item(itemValues)
            .build());
  }
}
