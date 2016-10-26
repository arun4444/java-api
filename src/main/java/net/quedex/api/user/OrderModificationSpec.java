package net.quedex.api.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.math.BigDecimal;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

public class OrderModificationSpec implements OrderSpec
{
    private final long clientOrderId;
    private final Integer newQuantity;
    private final BigDecimal newLimitPrice;

    public OrderModificationSpec(final long clientOrderId, final int newQuantity, final BigDecimal newLimitPrice)
    {
        checkArgument(newQuantity > 0, "newQuantity=%s <= 0", newQuantity);
        checkArgument(newLimitPrice.compareTo(BigDecimal.ZERO) > 0, "limitPrice=%s <= 0", newLimitPrice);
        this.clientOrderId = clientOrderId;
        this.newQuantity = newQuantity;
        this.newLimitPrice = newLimitPrice;
    }

    public OrderModificationSpec(final long clientOrderId, final int newQuantity)
    {
        checkArgument(newQuantity > 0, "newQuantity=%s <= 0", newQuantity);
        this.clientOrderId = clientOrderId;
        this.newQuantity = newQuantity;
        this.newLimitPrice = null;
    }

    public OrderModificationSpec(final long clientOrderId, final BigDecimal newLimitPrice)
    {
        checkArgument(newLimitPrice.compareTo(BigDecimal.ZERO) > 0, "limitPrice=%s <= 0", newLimitPrice);
        this.clientOrderId = clientOrderId;
        this.newQuantity = null;
        this.newLimitPrice = newLimitPrice;
    }

    @JsonProperty("client_order_id")
    @Override
    public long getClientOrderId()
    {
        return clientOrderId;
    }

    @JsonIgnore
    public Optional<Integer> getNewQuantity()
    {
        return Optional.ofNullable(newQuantity);
    }

    @JsonIgnore
    public Optional<BigDecimal> getNewLimitPrice()
    {
        return Optional.ofNullable(newLimitPrice);
    }

    @JsonProperty("new_price")
    private BigDecimal getNewLimitPriceRaw()
    {
        return newLimitPrice;
    }

    @JsonProperty("new_quantity")
    private Integer getNewQuantityRaw()
    {
        return newQuantity;
    }

    @JsonProperty("type")
    private String getType()
    {
        return "modify_order";
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        final OrderModificationSpec that = (OrderModificationSpec) o;
        return clientOrderId == that.clientOrderId &&
            Objects.equal(newQuantity, that.newQuantity) &&
            Objects.equal(newLimitPrice, that.newLimitPrice);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(clientOrderId, newQuantity, newLimitPrice);
    }

    @Override
    public String toString()
    {
        return MoreObjects.toStringHelper(this)
            .add("clientOrderId", clientOrderId)
            .add("newQuantity", newQuantity)
            .add("newLimitPrice", newLimitPrice)
            .toString();
    }
}
