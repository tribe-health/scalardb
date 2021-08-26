package com.scalar.db.service;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.scalar.db.api.DistributedTransactionManager;
import com.scalar.db.config.DatabaseConfig;

/** A factory class to instantiate {@link DistributedTransactionManager} */
public class TransactionFactory {
  private final Injector injector;

  public TransactionFactory(DatabaseConfig config) {
    injector = Guice.createInjector(new TransactionModule(config));
  }

  /**
   * Returns a {@link DistributedTransactionManager} instance
   *
   * @return a {@link DistributedTransactionManager} instance
   */
  public DistributedTransactionManager getTransactionManager() {
    return injector.getInstance(TransactionService.class);
  }
}