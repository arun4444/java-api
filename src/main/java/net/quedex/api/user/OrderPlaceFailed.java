package net.quedex.api.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

public class OrderPlaceFailed
{
    public enum Cause
    {
        INVALID_ORDER_ID,
        INVALID_INSTRUMENT_ID,
        NONPOSITIVE_QUANTITY,
        NONPOSITIVE_PRICE,
        SESSION_NOT_ACTIVE,
        INVALID_TICK_SIZE,
        INSUFFICIENT_FUNDS;

        @JsonCreator
        private static Cause deserialize(final String value)
        {
            return valueOf(value.toUpperCase());
        }
    }

    private final long clientOrderId;
    private final Cause cause;

    @JsonCreator
    public OrderPlaceFailed(
        @JsonProperty("client_order_id") final long clientOrderId,
        @JsonProperty("cause") final Cause cause)
    {
        this.clientOrderId = clientOrderId;
        this.cause = checkNotNull(cause, "Null cause");
    }

    public long getClientOrderId()
    {
        return clientOrderId;
    }

    public Cause getCause()
    {
        return cause;
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
        final OrderPlaceFailed that = (OrderPlaceFailed) o;
        return clientOrderId == that.clientOrderId &&
            cause == that.cause;
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(clientOrderId, cause);
    }

    @Override
    public String toString()
    {
        return MoreObjects.toStringHelper(this)
            .add("clientOrderId", clientOrderId)
            .add("cause", cause)
            .toString();
    }
}
