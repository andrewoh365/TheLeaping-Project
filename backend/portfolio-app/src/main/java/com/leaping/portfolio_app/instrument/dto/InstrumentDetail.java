package com.leaping.portfolio_app.instrument.dto;

import com.leaping.portfolio_app.instrument.entity.Instrument;
import com.leaping.portfolio_app.market.entity.Market;
import com.leaping.portfolio_app.market.entity.Price;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class InstrumentDetail {
    private Long instrumentId;
    private String symbol;
    private String name;
    private String instrumentType;
    private String priceCurrencyCode;
    private Boolean isTradeable;
    private Boolean isActive;

    private Long marketId;
    private String marketName;
    private String country;
    private String timezone;
    private String openTime;
    private String closeTime;

    public InstrumentDetail() {
    }

    public InstrumentDetail(
            Instrument instrument,
            Market market,
            Price price
    ) {
        this.instrumentId = instrument.getInstrumentId();
        this.symbol = instrument.getSymbol();
        this.name = instrument.getName();
        this.instrumentType = instrument.getInstrumentType().toString();
        this.priceCurrencyCode = instrument.getPriceCurrency().getCurrencyCode();
        this.isTradeable = instrument.getIsTradeable();
        this.isActive = instrument.getIsActive();

        this.marketId = market.getMarketId();
        this.marketName = market.getMarketName();
        this.country = market.getCountry();
        this.timezone = market.getTimezone();
        this.openTime = market.getOpenTime() != null ? market.getOpenTime().toString() : null;
        this.closeTime = market.getCloseTime() != null ? market.getCloseTime().toString() : null;

    }

    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getInstrumentType() { return instrumentType; }
    public void setInstrumentType(String instrumentType) { this.instrumentType = instrumentType; }

    public String getPriceCurrencyCode() { return priceCurrencyCode; }
    public void setPriceCurrencyCode(String priceCurrencyCode) { this.priceCurrencyCode = priceCurrencyCode; }

    public Boolean getIsTradeable() { return isTradeable; }
    public void setIsTradeable(Boolean isTradeable) { this.isTradeable = isTradeable; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }


    public Long getMarketId() { return marketId; }
    public void setMarketId(Long marketId) { this.marketId = marketId; }

    public String getMarketName() { return marketName; }
    public void setMarketName(String marketName) { this.marketName = marketName; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getOpenTime() { return openTime; }
    public void setOpenTime(String openTime) { this.openTime = openTime; }

    public String getCloseTime() { return closeTime; }
    public void setCloseTime(String closeTime) { this.closeTime = closeTime; }  



}