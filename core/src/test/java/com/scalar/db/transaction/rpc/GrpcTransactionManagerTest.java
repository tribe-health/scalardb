package com.scalar.db.transaction.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scalar.db.api.TransactionState;
import com.scalar.db.common.TableMetadataManager;
import com.scalar.db.exception.transaction.TransactionException;
import com.scalar.db.rpc.AbortRequest;
import com.scalar.db.rpc.AbortResponse;
import com.scalar.db.rpc.DistributedTransactionGrpc;
import com.scalar.db.rpc.GetTransactionStateRequest;
import com.scalar.db.rpc.GetTransactionStateResponse;
import com.scalar.db.rpc.RollbackRequest;
import com.scalar.db.rpc.RollbackResponse;
import com.scalar.db.storage.rpc.GrpcConfig;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GrpcTransactionManagerTest {

  private static final String ANY_ID = "id";

  @Mock private GrpcConfig config;
  @Mock private DistributedTransactionGrpc.DistributedTransactionStub stub;
  @Mock private DistributedTransactionGrpc.DistributedTransactionBlockingStub blockingStub;
  @Mock private TableMetadataManager metadataManager;

  private GrpcTransactionManager manager;

  @BeforeEach
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();

    // Arrange
    manager = new GrpcTransactionManager(config, stub, blockingStub, metadataManager);
    manager.with("namespace", "table");
    when(config.getDeadlineDurationMillis()).thenReturn(60000L);
    when(blockingStub.withDeadlineAfter(anyLong(), any())).thenReturn(blockingStub);
  }

  @Test
  public void getState_IsCalledWithoutAnyArguments_StubShouldBeCalledProperly()
      throws TransactionException {
    // Arrange
    GetTransactionStateResponse response = mock(GetTransactionStateResponse.class);
    when(response.getState())
        .thenReturn(com.scalar.db.rpc.TransactionState.TRANSACTION_STATE_COMMITTED);
    when(blockingStub.getState(any())).thenReturn(response);

    // Act
    TransactionState state = manager.getState(ANY_ID);

    // Assert
    assertThat(state).isEqualTo(TransactionState.COMMITTED);
    verify(blockingStub)
        .getState(GetTransactionStateRequest.newBuilder().setTransactionId(ANY_ID).build());
  }

  @Test
  public void getState_StubThrowsInvalidArgumentError_ShouldThrowIllegalArgumentException() {
    // Arrange
    when(blockingStub.getState(any())).thenThrow(Status.INVALID_ARGUMENT.asRuntimeException());

    // Act Assert
    assertThatThrownBy(() -> manager.getState(ANY_ID)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void getState_StubThrowsInternalError_ShouldThrowTransactionException() {
    // Arrange
    when(blockingStub.getState(any())).thenThrow(Status.INTERNAL.asRuntimeException());

    // Act Assert
    assertThatThrownBy(() -> manager.getState(ANY_ID)).isInstanceOf(TransactionException.class);
  }

  @Test
  public void rollback_IsCalledWithoutAnyArguments_StubShouldBeCalledProperly()
      throws TransactionException {
    // Arrange
    RollbackResponse response = mock(RollbackResponse.class);
    when(response.getState())
        .thenReturn(com.scalar.db.rpc.TransactionState.TRANSACTION_STATE_ABORTED);
    when(blockingStub.rollback(any())).thenReturn(response);

    // Act
    TransactionState state = manager.rollback(ANY_ID);

    // Assert
    assertThat(state).isEqualTo(TransactionState.ABORTED);
    verify(blockingStub).rollback(RollbackRequest.newBuilder().setTransactionId(ANY_ID).build());
  }

  @Test
  public void rollback_StubThrowsInvalidArgumentError_ShouldThrowIllegalArgumentException() {
    // Arrange
    when(blockingStub.abort(any())).thenThrow(Status.INVALID_ARGUMENT.asRuntimeException());

    // Act Assert
    assertThatThrownBy(() -> manager.abort(ANY_ID)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void rollback_StubThrowsInternalError_ShouldThrowTransactionException() {
    // Arrange
    when(blockingStub.abort(any())).thenThrow(Status.INTERNAL.asRuntimeException());

    // Act Assert
    assertThatThrownBy(() -> manager.abort(ANY_ID)).isInstanceOf(TransactionException.class);
  }

  @Test
  public void abort_IsCalledWithoutAnyArguments_StubShouldBeCalledProperly()
      throws TransactionException {
    // Arrange
    AbortResponse response = mock(AbortResponse.class);
    when(response.getState())
        .thenReturn(com.scalar.db.rpc.TransactionState.TRANSACTION_STATE_ABORTED);
    when(blockingStub.abort(any())).thenReturn(response);

    // Act
    TransactionState state = manager.abort(ANY_ID);

    // Assert
    assertThat(state).isEqualTo(TransactionState.ABORTED);
    verify(blockingStub).abort(AbortRequest.newBuilder().setTransactionId(ANY_ID).build());
  }

  @Test
  public void abort_StubThrowsInvalidArgumentError_ShouldThrowIllegalArgumentException() {
    // Arrange
    when(blockingStub.abort(any())).thenThrow(Status.INVALID_ARGUMENT.asRuntimeException());

    // Act Assert
    assertThatThrownBy(() -> manager.abort(ANY_ID)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void abort_StubThrowsInternalError_ShouldThrowTransactionException() {
    // Arrange
    when(blockingStub.abort(any())).thenThrow(Status.INTERNAL.asRuntimeException());

    // Act Assert
    assertThatThrownBy(() -> manager.abort(ANY_ID)).isInstanceOf(TransactionException.class);
  }
}
