package com.dispatch.service;

import com.dispatch.dto.OrderRequest;
import com.dispatch.entity.Order;
import com.dispatch.entity.Priority;
import com.dispatch.exception.DuplicateIdException;
import com.dispatch.mapper.OrderMapper;
import com.dispatch.repository.OrderRepository;
import com.dispatch.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.dispatch.TestFixtures.orderRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Captor
    private ArgumentCaptor<List<Order>> savedCaptor;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, new OrderMapper());
    }

    @Test
    @DisplayName("new orders are created as new entities and saved in one saveAll call")
    void insertsNewOrders() {
        when(orderRepository.findAllById(anyList())).thenReturn(List.of());

        int saved = orderService.upsertOrders(List.of(
                orderRequest("ORD001", 10, Priority.HIGH),
                orderRequest("ORD002", 20, Priority.LOW)));

        assertThat(saved).isEqualTo(2);
        verify(orderRepository).saveAll(savedCaptor.capture());
        assertThat(savedCaptor.getValue())
                .extracting(Order::getOrderId, Order::isNew)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ORD001", true),
                        org.assertj.core.groups.Tuple.tuple("ORD002", true));
    }

    @Test
    @DisplayName("an existing orderId updates the loaded entity instead of creating a duplicate")
    void updatesExistingOrder() {
        Order existing = new Order("ORD001", 1, 1, "Old address", 5, Priority.LOW);
        when(orderRepository.findAllById(anyList())).thenReturn(List.of(existing));

        orderService.upsertOrders(List.of(
                orderRequest("ORD001", 42, Priority.HIGH),
                orderRequest("ORD002", 7, Priority.MEDIUM)));

        verify(orderRepository).saveAll(savedCaptor.capture());
        List<Order> saved = savedCaptor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0)).isSameAs(existing);
        assertThat(existing.getPackageWeight()).isEqualTo(42);
        assertThat(existing.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(existing.getAddress()).isEqualTo("Connaught Place, Delhi, India");
        assertThat(saved.get(1).isNew()).isTrue();
    }

    @Test
    @DisplayName("duplicate orderIds in one request are rejected before touching the database")
    void rejectsDuplicateIds() {
        List<OrderRequest> request = List.of(
                orderRequest("ORD001", 10, Priority.HIGH),
                orderRequest("ORD002", 10, Priority.HIGH),
                orderRequest("ORD001", 15, Priority.LOW),
                orderRequest("ORD002", 15, Priority.LOW),
                orderRequest("ORD001", 15, Priority.LOW));

        assertThatThrownBy(() -> orderService.upsertOrders(request))
                .isInstanceOf(DuplicateIdException.class)
                .satisfies(ex -> {
                    DuplicateIdException dup = (DuplicateIdException) ex;
                    assertThat(dup.getField()).isEqualTo("orderId");
                    assertThat(dup.getDuplicateIds()).containsExactly("ORD001", "ORD002");
                });
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("large uploads look up existing IDs in chunks rather than one query per row")
    void chunksIdLookup() {
        when(orderRepository.findAllById(anyList())).thenReturn(List.of());
        List<OrderRequest> requests = new ArrayList<>();
        IntStream.range(0, 2_500).forEach(i -> requests.add(orderRequest("ORD" + i, 1, Priority.LOW)));

        orderService.upsertOrders(requests);

        verify(orderRepository, times(3)).findAllById(anyList());
        verify(orderRepository, times(1)).saveAll(anyList());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteAllOrders issues a single bulk delete")
    void deleteAllUsesBatchDelete() {
        orderService.deleteAllOrders();

        verify(orderRepository).deleteAllInBatch();
        verify(orderRepository, never()).deleteAll();
    }
}
