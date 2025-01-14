package com.scalar.db.transaction.jdbc;

import com.scalar.db.api.DistributedTransactionAdminIntegrationTestBase;
import com.scalar.db.config.DatabaseConfig;
import com.scalar.db.storage.jdbc.JdbcEnv;
import java.util.Properties;

public class JdbcTransactionAdminIntegrationTest
    extends DistributedTransactionAdminIntegrationTestBase {

  @Override
  protected Properties gerProperties() {
    Properties properties = new Properties();
    properties.putAll(JdbcEnv.getProperties());
    properties.setProperty(DatabaseConfig.TRANSACTION_MANAGER, "jdbc");
    return properties;
  }
}
