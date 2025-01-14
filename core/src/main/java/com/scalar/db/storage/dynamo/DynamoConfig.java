package com.scalar.db.storage.dynamo;

import static com.scalar.db.config.ConfigUtils.getString;

import com.scalar.db.config.DatabaseConfig;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
public class DynamoConfig {

  public static final String PREFIX = DatabaseConfig.PREFIX + "dynamo.";
  public static final String ENDPOINT_OVERRIDE = PREFIX + "endpoint-override";
  public static final String TABLE_METADATA_NAMESPACE = PREFIX + "table_metadata.namespace";

  private final String region;
  private final String accessKeyId;
  private final String secretAccessKey;
  @Nullable private final String endpointOverride;
  @Nullable private final String tableMetadataNamespace;

  public DynamoConfig(DatabaseConfig databaseConfig) {
    String storage = databaseConfig.getProperties().getProperty(DatabaseConfig.STORAGE);
    if (storage == null || !storage.equals("dynamo")) {
      throw new IllegalArgumentException(DatabaseConfig.STORAGE + " should be 'dynamo'");
    }

    region = databaseConfig.getContactPoints().get(0);
    accessKeyId = databaseConfig.getUsername().orElse(null);
    secretAccessKey = databaseConfig.getPassword().orElse(null);
    endpointOverride = getString(databaseConfig.getProperties(), ENDPOINT_OVERRIDE, null);
    tableMetadataNamespace =
        getString(databaseConfig.getProperties(), TABLE_METADATA_NAMESPACE, null);
  }

  public String getRegion() {
    return region;
  }

  public String getAccessKeyId() {
    return accessKeyId;
  }

  public String getSecretAccessKey() {
    return secretAccessKey;
  }

  public Optional<String> getEndpointOverride() {
    return Optional.ofNullable(endpointOverride);
  }

  public Optional<String> getTableMetadataNamespace() {
    return Optional.ofNullable(tableMetadataNamespace);
  }
}
