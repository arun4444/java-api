package net.quedex.api.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.quedex.api.common.CommunicationException;
import net.quedex.api.common.MessageReceiver;
import net.quedex.api.market.StreamFailureListener;
import net.quedex.api.pgp.BcEncryptor;
import net.quedex.api.pgp.BcPrivateKey;
import net.quedex.api.pgp.BcPublicKey;
import net.quedex.api.pgp.PGPExceptionBase;
import org.java_websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

class UserMessageSender
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UserMessageSender.class);
    private static final ObjectMapper OBJECT_MAPPER = MessageReceiver.OBJECT_MAPPER;
    private static final ObjectWriter OBJECT_WRITER = OBJECT_MAPPER.writer();

    private final WebSocketClient webSocketClient;
    private final BcEncryptor encryptor;
    private final long accountId;
    private final int nonceGroup;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(); // single threaded for sequencing

    private volatile StreamFailureListener streamFailureListener;
    private volatile long nonce;

    UserMessageSender(
        final WebSocketClient webSocketClient,
        final long accountId,
        final int nonceGroup,
        final BcPublicKey publicKey,
        final BcPrivateKey privateKey)
    {
        checkArgument(nonceGroup >= 0, "nonceGroup=%s < 0", nonceGroup);
        checkArgument(accountId > 0, "accountId=%s <= 0", accountId);
        this.webSocketClient = checkNotNull(webSocketClient, "null webSocketClient");
        this.encryptor = new BcEncryptor(publicKey, privateKey);
        this.accountId = accountId;
        this.nonceGroup = nonceGroup;
    }

    void registerStreamFailureListener(final StreamFailureListener streamFailureListener)
    {
        this.streamFailureListener = streamFailureListener;
    }

    void setStartNonce(final long startNonce)
    {
        LOGGER.debug("setStartNonce({})", startNonce);
        nonce = startNonce;
    }

    void sendGetLastNonce() throws CommunicationException
    {
        try
        {
            sendMessage(OBJECT_MAPPER.createObjectNode().put("type", "get_last_nonce").put("nonce_group", nonceGroup));
        }
        catch (PGPExceptionBase | JsonProcessingException e)
        {
            throw new CommunicationException("Error sending get_last_nonce", e);
        }
    }

    void sendSubscribe()
    {
        sendNoncedMessage(OBJECT_MAPPER.createObjectNode().put("type", "subscribe"));
    }

    void sendOrderSpec(final OrderSpec orderSpec)
    {
        sendNoncedMessage(OBJECT_MAPPER.valueToTree(orderSpec));
    }

    void sendBatch(final List<OrderSpec> batch)
    {
        final ObjectNode messageJson = (ObjectNode) OBJECT_MAPPER.createObjectNode()
            .put("type", "batch")
            .set("batch", OBJECT_MAPPER.valueToTree(batch));
        sendNoncedMessage(messageJson);
    }

    void stop()
    {
        executor.shutdown();
    }

    private void sendNoncedMessage(final ObjectNode jsonMessage)
    {
        executor.execute(() ->
        {
            jsonMessage.put("nonce", getNonce()).put("nonce_group", nonceGroup);
            try
            {
                sendMessage(jsonMessage);
            }
            catch (final Exception e)
            {
                onError(new CommunicationException("Error sending message", e));
            }
        });
    }

    private long getNonce()
    {
        return ++nonce;
    }

    private void sendMessage(final ObjectNode jsonMessage) throws JsonProcessingException, PGPExceptionBase
    {
        jsonMessage.put("account_id", accountId);
        final String messageStr = OBJECT_WRITER.writeValueAsString(jsonMessage);
        webSocketClient.send(encryptor.encrypt(messageStr, true));

        LOGGER.trace("sendMessage({})", messageStr);
    }

    private void onError(final Exception e)
    {
        LOGGER.warn("onError({})", e);
        final StreamFailureListener streamFailureListener = this.streamFailureListener;

        if (streamFailureListener != null)
        {
            streamFailureListener.onStreamFailure(e);
        }
    }
}
