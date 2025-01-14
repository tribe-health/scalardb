package com.scalar.db.storage.dynamo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.scalar.db.config.DatabaseConfig;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class DynamoConfigTest {

  private static final String ANY_REGION = "any_region";
  private static final String ANY_ACCESS_KEY_ID = "any_access_key_id";
  private static final String ANY_SECRET_ACCESS_ID = "any_secret_access_id";
  private static final String DYNAMO_STORAGE = "dynamo";
  private static final String ANY_ENDPOINT_OVERRIDE = "http://localhost:8000";
  private static final String ANY_TABLE_METADATA_NAMESPACE = "any_namespace";

  @Test
  public void constructor_AllPropertiesGiven_ShouldLoadProperly() {
    // Arrange
    Properties props = new Properties();
    props.setProperty(DatabaseConfig.CONTACT_POINTS, ANY_REGION);
    props.setProperty(DatabaseConfig.USERNAME, ANY_ACCESS_KEY_ID);
    props.setProperty(DatabaseConfig.PASSWORD, ANY_SECRET_ACCESS_ID);
    props.setProperty(DatabaseConfig.STORAGE, DYNAMO_STORAGE);
    props.setProperty(DynamoConfig.ENDPOINT_OVERRIDE, ANY_ENDPOINT_OVERRIDE);
    props.setProperty(DynamoConfig.TABLE_METADATA_NAMESPACE, ANY_TABLE_METADATA_NAMESPACE);

    // Act
    DynamoConfig config = new DynamoConfig(new DatabaseConfig(props));

    // Assert
    assertThat(config.getRegion()).isEqualTo(ANY_REGION);
    assertThat(config.getAccessKeyId()).isEqualTo(ANY_ACCESS_KEY_ID);
    assertThat(config.getSecretAccessKey()).isEqualTo(ANY_SECRET_ACCESS_ID);
    assertThat(config.getEndpointOverride().isPresent()).isTrue();
    assertThat(config.getEndpointOverride().get()).isEqualTo(ANY_ENDPOINT_OVERRIDE);
    assertThat(config.getTableMetadataNamespace()).isPresent();
    assertThat(config.getTableMetadataNamespace().get()).isEqualTo(ANY_TABLE_METADATA_NAMESPACE);
  }

  @Test
  public void constructor_WithoutStorage_ShouldThrowIllegalArgumentException() {
    // Arrange
    Properties props = new Properties();
    props.setProperty(DatabaseConfig.CONTACT_POINTS, ANY_REGION);
    props.setProperty(DatabaseConfig.USERNAME, ANY_ACCESS_KEY_ID);
    props.setProperty(DatabaseConfig.PASSWORD, ANY_SECRET_ACCESS_ID);
    props.setProperty(DynamoConfig.ENDPOINT_OVERRIDE, ANY_ENDPOINT_OVERRIDE);
    props.setProperty(DynamoConfig.TABLE_METADATA_NAMESPACE, ANY_TABLE_METADATA_NAMESPACE);

    // Act Assert
    assertThatThrownBy(() -> new DynamoConfig(new DatabaseConfig(props)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void constructor_PropertiesWithoutEndpointOverrideGiven_ShouldLoadProperly() {
    // Arrange
    Properties props = new Properties();
    props.setProperty(DatabaseConfig.CONTACT_POINTS, ANY_REGION);
    props.setProperty(DatabaseConfig.USERNAME, ANY_ACCESS_KEY_ID);
    props.setProperty(DatabaseConfig.PASSWORD, ANY_SECRET_ACCESS_ID);
    props.setProperty(DatabaseConfig.STORAGE, DYNAMO_STORAGE);
    props.setProperty(DynamoConfig.TABLE_METADATA_NAMESPACE, ANY_TABLE_METADATA_NAMESPACE);

    // Act
    DynamoConfig config = new DynamoConfig(new DatabaseConfig(props));

    // Assert
    assertThat(config.getRegion()).isEqualTo(ANY_REGION);
    assertThat(config.getAccessKeyId()).isEqualTo(ANY_ACCESS_KEY_ID);
    assertThat(config.getSecretAccessKey()).isEqualTo(ANY_SECRET_ACCESS_ID);
    assertThat(config.getEndpointOverride().isPresent()).isFalse();
    assertThat(config.getTableMetadataNamespace()).isPresent();
    assertThat(config.getTableMetadataNamespace().get()).isEqualTo(ANY_TABLE_METADATA_NAMESPACE);
  }

  @Test
  public void constructor_PropertiesWithoutTableMetadataNamespaceGiven_ShouldLoadProperly() {
    // Arrange
    Properties props = new Properties();
    props.setProperty(DatabaseConfig.CONTACT_POINTS, ANY_REGION);
    props.setProperty(DatabaseConfig.USERNAME, ANY_ACCESS_KEY_ID);
    props.setProperty(DatabaseConfig.PASSWORD, ANY_SECRET_ACCESS_ID);
    props.setProperty(DatabaseConfig.STORAGE, DYNAMO_STORAGE);
    props.setProperty(DynamoConfig.ENDPOINT_OVERRIDE, ANY_ENDPOINT_OVERRIDE);

    // Act
    DynamoConfig config = new DynamoConfig(new DatabaseConfig(props));

    // Assert
    assertThat(config.getRegion()).isEqualTo(ANY_REGION);
    assertThat(config.getAccessKeyId()).isEqualTo(ANY_ACCESS_KEY_ID);
    assertThat(config.getSecretAccessKey()).isEqualTo(ANY_SECRET_ACCESS_ID);
    assertThat(config.getEndpointOverride().isPresent()).isTrue();
    assertThat(config.getEndpointOverride().get()).isEqualTo(ANY_ENDPOINT_OVERRIDE);
    assertThat(config.getTableMetadataNamespace()).isNotPresent();
  }
}
