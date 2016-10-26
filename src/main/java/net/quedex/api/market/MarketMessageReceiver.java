package net.quedex.api.market;

import com.fasterxml.jackson.databind.JsonNode;
import net.quedex.api.common.MessageReceiver;
import net.quedex.api.common.SessionStateListener;
import net.quedex.api.pgp.BcPublicKey;
import net.quedex.api.pgp.BcSignatureVerifier;
import net.quedex.api.pgp.PGPExceptionBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

class MarketMessageReceiver extends MessageReceiver
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageReceiver.class);

    private final BcSignatureVerifier bcSignatureVerifier;

    private volatile OrderBookListener orderBookListener;
    private final Set<Integer> orderBookSubscriptions = new HashSet<>(64, 0.75f);
    private final Map<Integer, OrderBook> orderBookCache = new HashMap<>(64, 0.75f);

    private volatile TradeListener tradeListener;
    private final Set<Integer> tradeSubscriptions = new HashSet<>(64, 0.75f);
    private final Map<Integer, Trade> tradeCache = new HashMap<>(64, 0.75f);

    private volatile QuotesListener quotesListener;
    private final Set<Integer> quotesSubscriptions = new HashSet<>(64, 0.75f);
    private final Map<Integer, Quotes> quotesCache = new HashMap<>(64, 0.75f);

    private volatile SessionStateListener sessionStateListener;
    private final Object sessionStateMonitor = new Object();
    private SessionState sessionStateCached;

    MarketMessageReceiver(final BcPublicKey qdxPublicKey)
    {
        super(LOGGER);
        this.bcSignatureVerifier = new BcSignatureVerifier(qdxPublicKey);
    }

    Registration registerOrderBookListener(final OrderBookListener orderBookListener)
    {
        this.orderBookListener = orderBookListener;
        return new CachedRegistration<OrderBook>(orderBookSubscriptions, orderBookCache)
        {
            @Override
            void onSubscribe(final OrderBook element)
            {
                if (orderBookListener != null)
                {
                    orderBookListener.onOrderBook(element);
                }
            }
        };
    }

    Registration registerTradeListener(final TradeListener tradeListener)
    {
        this.tradeListener = tradeListener;
        return new CachedRegistration<Trade>(tradeSubscriptions, tradeCache)
        {
            @Override
            void onSubscribe(final Trade element)
            {
                if (tradeListener != null)
                {
                    tradeListener.onTrade(element);
                }
            }
        };
    }

    Registration registerQuotesListener(final QuotesListener quotesListener)
    {
        this.quotesListener = quotesListener;
        return new CachedRegistration<Quotes>(quotesSubscriptions, quotesCache)
        {
            @Override
            void onSubscribe(final Quotes element)
            {
                if (quotesListener != null)
                {
                    quotesListener.onQuotes(element);
                }
            }
        };
    }

    void registerAndSubscribeSessionStateListener(final SessionStateListener sessionStateListener)
    {
        this.sessionStateListener = sessionStateListener;
        synchronized (sessionStateMonitor)
        {
            final SessionState sessionStateCached = this.sessionStateCached;
            if (sessionStateListener != null && sessionStateCached != null)
            {
                sessionStateListener.onSessionState(sessionStateCached);
            }
        }
    }

    @Override
    protected void processData(final String data) throws IOException, PGPExceptionBase
    {
        LOGGER.trace("processData({})", data);

        final String verified = bcSignatureVerifier.verifySignature(data);
        final JsonNode dataJson = OBJECT_MAPPER.readTree(verified);

        switch (dataJson.get("type").asText())
        {
            case "order_book":
                onOrderBook(OBJECT_MAPPER.treeToValue(dataJson, OrderBook.class));
                break;
            case "quotes":
                onQuotes(OBJECT_MAPPER.treeToValue(dataJson, Quotes.class));
                break;
            case "trade":
                onTrade(OBJECT_MAPPER.treeToValue(dataJson, Trade.class));
                break;
            case "session_state":
                onSessionState(SessionState.valueOf(dataJson.get("state").textValue().toUpperCase()));
                break;
            default:
                // no-op
                break;
        }
    }

    private void onOrderBook(final OrderBook orderBook)
    {
        synchronized (orderBookCache)
        {
            orderBookCache.put(orderBook.getInstrumentId(), orderBook);
            final OrderBookListener orderBookListener = this.orderBookListener;
            if (orderBookListener != null && orderBookSubscriptions.contains(orderBook.getInstrumentId()))
            {
                orderBookListener.onOrderBook(orderBook);
            }
        }
    }

    private void onQuotes(final Quotes quotes)
    {
        synchronized (quotesCache)
        {
            quotesCache.put(quotes.getInstrumentId(), quotes);
            final QuotesListener quotesListener = this.quotesListener;
            if (quotesListener != null && quotesSubscriptions.contains(quotes.getInstrumentId()))
            {
                quotesListener.onQuotes(quotes);
            }
        }
    }

    private void onTrade(final Trade trade)
    {
        synchronized (tradeCache)
        {
            tradeCache.put(trade.getInstrumentId(), trade);
            final TradeListener tradeListener = this.tradeListener;
            if (tradeListener != null && tradeSubscriptions.contains(trade.getInstrumentId()))
            {
                tradeListener.onTrade(trade);
            }
        }
    }

    private void onSessionState(final SessionState sessionState)
    {
        synchronized (sessionStateMonitor)
        {
            sessionStateCached = sessionState;
            final SessionStateListener sessionStateListener = this.sessionStateListener;
            if (sessionStateListener != null)
            {
                sessionStateListener.onSessionState(sessionState);
            }
        }
    }

    private abstract static class CachedRegistration<T> implements Registration
    {
        final Set<Integer> subscriptions;
        final Map<Integer, T> cache;

        CachedRegistration(final Set<Integer> subscriptions, final Map<Integer, T> cache)
        {
            this.subscriptions = checkNotNull(subscriptions, "null subscriptions");
            this.cache = checkNotNull(cache, "null cache");
        }

        abstract void onSubscribe(T element);

        @Override
        public CachedRegistration subscribe(final int instrumentId)
        {
            synchronized (cache)
            {
                subscriptions.add(instrumentId);
                final T element = cache.get(instrumentId);
                if (element != null)
                {
                    onSubscribe(element);
                }
            }
            return this;
        }

        @Override
        public CachedRegistration subscribe(final Collection<Integer> instrumentIds)
        {
            instrumentIds.forEach(this::subscribe);
            return this;
        }

        @Override
        public CachedRegistration unsubscribe(final int instrumentId)
        {
            subscriptions.remove(instrumentId);
            return this;
        }

        @Override
        public CachedRegistration unsubscribe(final Collection<Integer> instrumentIds)
        {
            instrumentIds.forEach(this::unsubscribe);
            return this;
        }

        @Override
        public Registration unsubscribeAll()
        {
            subscriptions.clear();
            return this;
        }
    }
}
