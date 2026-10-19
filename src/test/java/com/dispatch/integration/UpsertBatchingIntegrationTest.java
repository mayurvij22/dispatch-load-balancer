package com.dispatch.integration;

import com.dispatch.dto.OrderRequest;
import com.dispatch.entity.Priority;
import com.dispatch.repository.OrderRepository;
import com.dispatch.service.OrderService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.IntStream;

import static com.dispatch.TestFixtures.orderRequest;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies with Hibernate statistics that bulk upserts use one lookup query plus JDBC batches,
 * not one statement per row.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class UpsertBatchingIntegrationTest {

    private static final int ORDER_COUNT = 250;

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        orderService.deleteAllOrders();
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    @DisplayName("inserting and then updating 250 orders uses a handful of statements, not 250")
    void bulkUpsertIsBatched() {
        orderService.upsertOrders(orders(10));

        assertThat(statistics.getEntityInsertCount()).isEqualTo(ORDER_COUNT);
        // 1 SELECT ... IN (...) + 3 INSERT batches of up to 100 rows
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);

        statistics.clear();
        orderService.upsertOrders(orders(20));

        assertThat(statistics.getEntityUpdateCount()).isEqualTo(ORDER_COUNT);
        assertThat(statistics.getEntityInsertCount()).isZero();
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
        assertThat(orderRepository.count()).isEqualTo(ORDER_COUNT);
    }

    private static List<OrderRequest> orders(double weight) {
        return IntStream.range(0, ORDER_COUNT)
                .mapToObj(i -> orderRequest("BULK-%04d".formatted(i), weight, Priority.MEDIUM))
                .toList();
    }
}
