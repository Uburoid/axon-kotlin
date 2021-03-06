package io.axoniq.foodordering.command

import io.axoniq.foodordering.coreapi.ConfirmOrderCommand
import io.axoniq.foodordering.coreapi.CreateFoodCartCommand
import io.axoniq.foodordering.coreapi.DeselectProductCommand
import io.axoniq.foodordering.coreapi.FoodCartCreatedEvent
import io.axoniq.foodordering.coreapi.FoodCartId
import io.axoniq.foodordering.coreapi.OrderConfirmedEvent
import io.axoniq.foodordering.coreapi.ProductDeselectedEvent
import io.axoniq.foodordering.coreapi.ProductDeselectionException
import io.axoniq.foodordering.coreapi.ProductId
import io.axoniq.foodordering.coreapi.ProductSelectedEvent
import io.axoniq.foodordering.coreapi.SelectProductCommand
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.spring.stereotype.Aggregate
import org.slf4j.LoggerFactory

@Aggregate
internal class FoodCart {

    @AggregateIdentifier
    private lateinit var foodCartId: FoodCartId
    internal lateinit var selectedProducts: MutableMap<ProductId, Int>
    private var confirmed: Boolean = false

    @CommandHandler
    constructor(command: CreateFoodCartCommand) {
        AggregateLifecycle.apply(FoodCartCreatedEvent(command.foodCartId))
    }

    @CommandHandler
    fun handle(command: SelectProductCommand) {
        AggregateLifecycle.apply(ProductSelectedEvent(foodCartId, command.productId, command.quantity))
    }

    @CommandHandler
    @Throws(ProductDeselectionException::class)
    fun handle(command: DeselectProductCommand) {
        val productId = command.productId
        val quantity = command.quantity

        if (!selectedProducts.containsKey(productId)) {
            throw ProductDeselectionException(
                "Cannot deselect a product which has not been selected for this Food Cart"
            )
        }
        if (selectedProducts[productId]?.minus(quantity) ?: -1 < 0) {
            throw ProductDeselectionException(
                "Cannot deselect more products of ID [$productId] than have been selected initially"
            )
        }

        AggregateLifecycle.apply(ProductDeselectedEvent(foodCartId, productId, quantity))
    }

    @CommandHandler
    fun handle(command: ConfirmOrderCommand) {
        if (confirmed) {
            logger.warn("Cannot confirm a Food Cart order which is already confirmed")
            return
        }

        AggregateLifecycle.apply(OrderConfirmedEvent(foodCartId))
    }

    @EventSourcingHandler
    fun on(event: FoodCartCreatedEvent) {
        foodCartId = event.foodCartId
        selectedProducts = mutableMapOf()
        confirmed = false
    }

    @EventSourcingHandler
    fun on(event: ProductSelectedEvent) {
        selectedProducts.merge(
            event.productId,
            event.quantity
        ) { a, b -> a + b }
    }

    @EventSourcingHandler
    fun on(event: ProductDeselectedEvent) {
        selectedProducts.computeIfPresent(
            event.productId
        ) { _, quantity -> quantity - event.quantity }
    }

    @EventSourcingHandler
    fun on(event: OrderConfirmedEvent) {
        confirmed = true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FoodCart::class.java)
    }
}
