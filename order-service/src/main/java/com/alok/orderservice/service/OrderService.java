package com.alok.orderservice.service;

import com.alok.orderservice.dto.InventoryResponse;
import com.alok.orderservice.dto.OrderLineItemsDto;
import com.alok.orderservice.dto.OrderRequest;
import com.alok.orderservice.model.Order;
import com.alok.orderservice.model.OrderLineItems;
import com.alok.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
    public void placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();

        order.setOrderLineItemsList(orderLineItems);

        List<String> skuCode = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        // call(sync) inventory service and
        // place order if product is in stock
        InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCode).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        boolean allProductInStock = Arrays.stream(inventoryResponseArray)
                .allMatch(InventoryResponse::isInStock);
        if(allProductInStock) {
            orderRepository.save(order);
        } else {
            throw new IllegalArgumentException("Product is not in stock");
        }
    }

    public OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());

        return orderLineItems;
    }
}
