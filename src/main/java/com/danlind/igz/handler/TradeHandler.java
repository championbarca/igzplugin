package com.danlind.igz.handler;

import com.danlind.igz.Zorro;
import com.danlind.igz.config.ZorroReturnValues;
import com.danlind.igz.domain.ContractDetails;
import com.danlind.igz.domain.OrderDetails;
import com.danlind.igz.domain.PriceDetails;
import com.danlind.igz.domain.types.Epic;
import com.danlind.igz.ig.api.client.RestAPI;
import com.danlind.igz.ig.api.client.rest.dto.getDealConfirmationV1.DealStatus;
import com.danlind.igz.ig.api.client.rest.dto.getDealConfirmationV1.GetDealConfirmationV1Response;
import com.danlind.igz.ig.api.client.rest.dto.getDealConfirmationV1.PositionStatus;
import com.danlind.igz.ig.api.client.rest.dto.markets.getMarketDetailsV2.CurrenciesItem;
import com.danlind.igz.ig.api.client.rest.dto.markets.getMarketDetailsV2.GetMarketDetailsV2Response;
import com.danlind.igz.ig.api.client.rest.dto.positions.otc.closeOTCPositionV1.CloseOTCPositionV1Request;
import com.danlind.igz.ig.api.client.rest.dto.positions.otc.closeOTCPositionV1.CloseOTCPositionV1Response;
import com.danlind.igz.ig.api.client.rest.dto.positions.otc.createOTCPositionV2.CreateOTCPositionV2Request;
import com.danlind.igz.ig.api.client.rest.dto.positions.otc.createOTCPositionV2.CreateOTCPositionV2Response;
import com.danlind.igz.ig.api.client.rest.dto.positions.otc.createOTCPositionV2.Direction;
import com.danlind.igz.ig.api.client.rest.dto.positions.otc.createOTCPositionV2.OrderType;
import com.danlind.igz.ig.api.client.rest.dto.positions.otc.updateOTCPositionV2.UpdateOTCPositionV2Request;
import com.danlind.igz.ig.api.client.rest.dto.positions.otc.updateOTCPositionV2.UpdateOTCPositionV2Response;
import net.openhft.chronicle.map.ChronicleMap;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TradeHandler {

    private final static Logger logger = LoggerFactory.getLogger(TradeHandler.class);
    private final RestAPI restApi;
    private final LoginHandler loginHandler;
    private final AssetHandler assetHandler;
    private final MarketHandler marketHandler;
    private final static double rollOverNotSupported = 0.0;

    private final AtomicInteger atomicInteger;
    private final OrderDetails sampleOrderDetails = new OrderDetails(new Epic("IX.D.OMX.IFD.IP"),10000,Direction.BUY,20, "DIAAAAA9QN6L4AU");
    private final ChronicleMap<Integer, OrderDetails> orderReferenceMap;

    @Autowired
    public TradeHandler(RestAPI restApi, LoginHandler loginHandler, AssetHandler assetHandler, MarketHandler marketHandler) {
        this.restApi = restApi;
        this.loginHandler = loginHandler;
        this.assetHandler = assetHandler;
        this.marketHandler = marketHandler;

        File file = new File("./Plugin/ig/orderChronoMap.dat");
        try {
            file.createNewFile();
            orderReferenceMap = ChronicleMap
                    .of(Integer.class, OrderDetails.class)
                    .averageValue(sampleOrderDetails)
                    .entries(50)
                    .createOrRecoverPersistedTo(file, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int max = orderReferenceMap.keySet().stream().max(Integer::compareTo).orElse(1000);
        atomicInteger = new AtomicInteger(max+1);
        logger.debug("Setting OrderID counter to {}", atomicInteger.get());

        orderReferenceMap.keySet().stream().forEach(x -> logger.debug("OrderID: {}, Epic: {}, Direction: {}, size: {}", x, orderReferenceMap.get(x).getEpic().getName(), orderReferenceMap.get(x).getDirection(), orderReferenceMap.get(x).getPositionSize()));
    }

    public int brokerBuy(final Epic epic,
                         final double tradeParams[]) {
        try {
            double numberOfContracts = tradeParams[0];
            double stopDistance = tradeParams[1];
            GetMarketDetailsV2Response marketDetails = restApi.getMarketDetailsV2(loginHandler.getConversationContext(), epic.getName());

            CreateOTCPositionV2Request createPositionRequest = new CreateOTCPositionV2Request();
            createPositionRequest.setEpic(epic.getName());
            createPositionRequest.setExpiry(marketDetails.getInstrument().getExpiry());

            if (numberOfContracts > 0 ) {
                createPositionRequest.setDirection(Direction.BUY);
            } else {
                createPositionRequest.setDirection(Direction.SELL);
            }
            createPositionRequest.setOrderType(OrderType.MARKET);

            List<CurrenciesItem> currencies = marketDetails.getInstrument().getCurrencies();
            createPositionRequest.setCurrencyCode(currencies.size() > 0 ? currencies.get(0).getCode() : "GBP");
            createPositionRequest.setSize(BigDecimal.valueOf(Math.abs(numberOfContracts)));
            createPositionRequest.setGuaranteedStop(false);
            createPositionRequest.setForceOpen(true);

            if (stopDistance != 0) {
                int scalingFactor = marketDetails.getSnapshot().getScalingFactor();
                createPositionRequest.setStopDistance(BigDecimal.valueOf(stopDistance*scalingFactor));
                logger.debug("Setting stop distance: {}", stopDistance*scalingFactor);
            }

            logger.info(">>> Creating long position epic={}, \ndirection={}, \nexpiry={}, \nsize={}, \norderType={}, \ncurrency={}, \nstop loss distance={}",
                    epic.getName(), createPositionRequest.getDirection(), createPositionRequest.getExpiry(),
                    createPositionRequest.getSize(), createPositionRequest.getOrderType(), createPositionRequest.getCurrencyCode(), stopDistance);
            CreateOTCPositionV2Response response = restApi.createOTCPositionV2(loginHandler.getConversationContext(), createPositionRequest);

            GetDealConfirmationV1Response dealConfirmationV1Response = restApi.getDealConfirmationV1(loginHandler.getConversationContext(), response.getDealReference());
            if (dealConfirmationV1Response.getDealStatus() == DealStatus.ACCEPTED) {
                int orderId = atomicInteger.getAndIncrement();
                orderReferenceMap.put(orderId, new OrderDetails(epic, dealConfirmationV1Response.getLevel(), createPositionRequest.getDirection(), dealConfirmationV1Response.getSize().intValue(), dealConfirmationV1Response.getDealId()));
                tradeParams[2] = dealConfirmationV1Response.getLevel();
                logger.info("Returning orderId {}", orderId);
                return orderId;
            } else {
                logger.warn("Unable to execute broker buy, reason was {}", dealConfirmationV1Response.getReason());
                return ZorroReturnValues.BROKER_BUY_FAIL.getValue();
            }

        } catch (Exception e) {
            logger.error("Failed when placing order", e);
            Zorro.indicateError();
            return ZorroReturnValues.BROKER_BUY_FAIL.getValue();
        }
    }


    public int brokerTrade(final int nTradeID,
                               final double orderParams[]) {
        OrderDetails orderDetails = orderReferenceMap.get(nTradeID);
        ContractDetails contractDetails = marketHandler.getContractDetails(orderDetails.getEpic());
        if (Objects.isNull(orderDetails)) {
            //TODO: Handle case when application is closed with open trades (ask API)
//            restApi.get
            return ZorroReturnValues.UNKNOWN_ORDER_ID.getValue();
        }
        else {
            try {
                PriceDetails priceDetails = assetHandler.getAssetDetails(orderDetails.getEpic());

                final double pOpen = orderDetails.getEntryLevel();
                final double pClose = (orderDetails.getDirection() == Direction.BUY)
                        ? priceDetails.getBid()
                        : priceDetails.getAsk();
                final double pRoll = rollOverNotSupported;
                final double pProfit = (orderDetails.getDirection() == Direction.BUY)
                ? (pClose - orderDetails.getEntryLevel()) / contractDetails.getBaseExchangeRate() * orderDetails.getPositionSize() * contractDetails.getPipCost() * contractDetails.getScalingFactor()
                : (pClose - orderDetails.getEntryLevel()) * -1 / contractDetails.getBaseExchangeRate() * orderDetails.getPositionSize() * contractDetails.getPipCost() * contractDetails.getScalingFactor();
                orderParams[0] = pOpen;
                orderParams[1] = pClose;
                orderParams[2] = pRoll;
                orderParams[3] = pProfit;

//                logger.debug("Bid: {}, Ask: {}, Order entry: {}, Current close: {}, Profit: {}, Profit base currency {}",
//                        priceDetails.getBid(),
//                        priceDetails.getAsk(),
//                        pOpen,
//                        pClose,
//                        pClose - orderDetails.getEntryLevel(),
//                        pProfit);

                return orderDetails.getPositionSize();
            } catch (Exception e) {
                logger.error("Error when getting position data", e);
                Zorro.indicateError();
                return ZorroReturnValues.UNKNOWN_ORDER_ID.getValue();
            }
        }
    }

    public int brokerSell(final int nOrderId,
                          final int nAmount) {
        CloseOTCPositionV1Request request = new CloseOTCPositionV1Request();
        OrderDetails sellOrderDetails = orderReferenceMap.get(nOrderId);
        request.setDealId(sellOrderDetails.getDealId());
        request.setSize(BigDecimal.valueOf(Math.abs(nAmount)));

        if (nAmount > 0 ) {
            request.setDirection(com.danlind.igz.ig.api.client.rest.dto.positions.otc.closeOTCPositionV1.Direction.SELL);
        } else {
            request.setDirection(com.danlind.igz.ig.api.client.rest.dto.positions.otc.closeOTCPositionV1.Direction.BUY);
        }

        request.setOrderType(com.danlind.igz.ig.api.client.rest.dto.positions.otc.closeOTCPositionV1.OrderType.MARKET);
        try {
            CloseOTCPositionV1Response response = restApi.closeOTCPositionV1(loginHandler.getConversationContext(), request);
            GetDealConfirmationV1Response dealConfirmationV1Response = restApi.getDealConfirmationV1(loginHandler.getConversationContext(), response.getDealReference());
            if (dealConfirmationV1Response.getDealStatus() == DealStatus.ACCEPTED) {
                if (dealConfirmationV1Response.getStatus() == PositionStatus.CLOSED) {
                    logger.debug("Order {} now fully closed", sellOrderDetails.getDealId());
                    orderReferenceMap.remove(nOrderId);
                    return nOrderId;
                } else {
                    logger.debug("Order {} partially closed", sellOrderDetails.getDealId());
                    int newOrderId = atomicInteger.getAndIncrement();
                    orderReferenceMap.put(newOrderId, new OrderDetails(sellOrderDetails.getEpic(), sellOrderDetails.getEntryLevel(), sellOrderDetails.getDirection(), sellOrderDetails.getPositionSize() - Math.abs(nAmount), dealConfirmationV1Response.getDealId()));
                    return newOrderId;
                }
            } else {
                logger.warn("Unable to execute broker sell, reason was {}", dealConfirmationV1Response.getReason());
                return ZorroReturnValues.BROKER_SELL_FAIL.getValue();
            }
        } catch (HttpClientErrorException e) {
            logger.error("Failed when closing position with dealId {}: {}",sellOrderDetails.getDealId(), e.getResponseBodyAsString(), e);
            Zorro.indicateError();
            return ZorroReturnValues.BROKER_SELL_FAIL.getValue();
        } catch (Exception e) {
            logger.error("Failed when closing position", e);
            Zorro.indicateError();
            return ZorroReturnValues.BROKER_SELL_FAIL.getValue();
        }
    }

    public int brokerStop(final int orderId,
                          final double newSLPrice) {
        logger.debug("Setting new SL price {}", newSLPrice);
        UpdateOTCPositionV2Request request = new UpdateOTCPositionV2Request();
        request.setStopLevel(BigDecimal.valueOf(newSLPrice));
        request.setTrailingStop(false);
        try {
            UpdateOTCPositionV2Response response = restApi.updateOTCPositionV2(loginHandler.getConversationContext(), orderReferenceMap.get(orderId).getDealId(),request);
            logger.debug("Original deal reference {}, updated deal reference {}", orderReferenceMap.get(orderId), response.getDealReference());
            //TODO: Check trade confirmation as well
        } catch (Exception e) {
            logger.error("Failed when adjusting stop loss", e);
            Zorro.indicateError();
            return ZorroReturnValues.ADJUST_SL_FAIL.getValue();
        }

        return ZorroReturnValues.ADJUST_SL_OK.getValue();
    }
}
