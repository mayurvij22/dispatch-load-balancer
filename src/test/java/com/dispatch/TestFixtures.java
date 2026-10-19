package com.dispatch;

import com.dispatch.dto.OrderRequest;
import com.dispatch.dto.OrdersRequest;
import com.dispatch.dto.VehicleRequest;
import com.dispatch.dto.VehiclesRequest;
import com.dispatch.entity.Order;
import com.dispatch.entity.Priority;
import com.dispatch.entity.Vehicle;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builders and sample data shared by the test suite.
 */
public final class TestFixtures {

    public static final String SAMPLE_ORDERS_JSON = "/samples/orders-request.json";
    public static final String SAMPLE_VEHICLES_JSON = "/samples/vehicles-request.json";

    // Well-known coordinates
    public static final double[] CONNAUGHT_PLACE = {28.6139, 77.2090};
    public static final double[] KAROL_BAGH = {28.7041, 77.1025};
    public static final double[] NOIDA_SECTOR_18 = {28.5355, 77.3910};
    public static final double[] CYBER_HUB_GURGAON = {28.4595, 77.0266};

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestFixtures() {
    }

    public static Order order(String id, double[] location, double weight, Priority priority) {
        return new Order(id, location[0], location[1], "Address of " + id, weight, priority);
    }

    public static Vehicle vehicle(String id, double capacity, double[] location) {
        return new Vehicle(id, capacity, location[0], location[1], "Depot of " + id);
    }

    public static OrderRequest orderRequest(String id, double weight, Priority priority) {
        return new OrderRequest(id, CONNAUGHT_PLACE[0], CONNAUGHT_PLACE[1], "Connaught Place, Delhi, India",
                weight, priority);
    }

    public static VehicleRequest vehicleRequest(String id, double capacity) {
        return new VehicleRequest(id, capacity, KAROL_BAGH[0], KAROL_BAGH[1], "Karol Bagh, Delhi, India");
    }

    public static String readResource(String path) {
        try (InputStream in = TestFixtures.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing test resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The 30 sample orders from the assignment PDF, as entities. */
    public static List<Order> sampleOrders() {
        try {
            OrdersRequest request = MAPPER.readValue(readResource(SAMPLE_ORDERS_JSON), OrdersRequest.class);
            return request.orders().stream()
                    .map(o -> new Order(o.orderId(), o.latitude(), o.longitude(), o.address(),
                            o.packageWeight(), o.priority()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The 5 sample vehicles from the assignment PDF, as entities. */
    public static List<Vehicle> sampleVehicles() {
        try {
            VehiclesRequest request = MAPPER.readValue(readResource(SAMPLE_VEHICLES_JSON), VehiclesRequest.class);
            return request.vehicles().stream()
                    .map(v -> new Vehicle(v.vehicleId(), v.capacity(), v.currentLatitude(), v.currentLongitude(),
                            v.currentAddress()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
