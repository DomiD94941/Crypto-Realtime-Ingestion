package io.crypto.realtime.producer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Represents a trade event received from the Binance WebSocket stream.
 * Binance sends JSON messages with short property names (e.g. "p" for price, "q" for quantity).
 * This class maps those fields into descriptive Java fields using Jackson annotations.
 */

@JsonIgnoreProperties(ignoreUnknown = true) // ignore unused fields like "M"
public class TradeEvent {

    /** Event type (e.g. "trade") */
    @JsonProperty("e")
    public String eventType;

    /** Event time in milliseconds since epoch */
    @JsonProperty("E")
    public long eventTime;

    /** Trading pair symbol (e.g. "BTCUSDT") */
    @JsonProperty("s")
    public String symbol;

    /** Trade ID */
    @JsonProperty("t")
    public long tradeId;

    /** Trade price as BigDecimal for precision */
    @JsonProperty("p")
    public BigDecimal price;

    /** Trade quantity as BigDecimal for precision */
    @JsonProperty("q")
    public BigDecimal quantity;

    /** Trade time in milliseconds since epoch */
    @JsonProperty("T")
    public long tradeTime;

    /** Indicates if the buyer is the market maker */
    @JsonProperty("m")
    public boolean isBuyerMaker;
}

